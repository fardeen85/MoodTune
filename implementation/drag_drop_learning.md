# Building drag-and-drop reorder + auto-scroll in a LazyColumn

A from-scratch build order for the pattern used in
`feature/playlist/src/main/java/com/fardeenkhan/moodtune/feature/playlist/ui/components/DragDropListState.kt`,
with the reasoning at each step. This is a feature where the *order* you build it in matters —
each piece exists to fix a problem the previous piece exposes.

## Step 0: Decide what "dragging" even means in a lazy list

Before writing code, get the mental model right: a `LazyColumn` recycles items and constantly
changes their measured positions as data shifts. You can't drag by mutating a raw `x/y` offset
per item — you need a **single piece of state that says "this ID is being dragged, by this many
pixels from where it started,"** and every dragged item's visual position is *derived* from that,
not stored per-item. That's why `DragDropListState` is one class holding `draggedItemId` +
`dragdistance`, not a list of per-item offsets.

## Step 1: Capture the gesture

Start with just detecting the long-press-drag and logging deltas — no reordering yet.

```kotlin
Modifier.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> /* find which item */ },
        onDrag = { _, dragAmount -> /* accumulate */ },
        onDragEnd = { /* reset */ }
    )
}
```

**Why long-press:** so a plain scroll gesture on the list doesn't get eaten as a drag.

**Why `onDragStart` needs the touch `offset`:** you need to know *which item* the finger landed
on. You get that by walking `listState.layoutInfo.visibleItemsInfo` and finding the item whose
`offset..offset+size` range contains the touch y. That's `onDragStart` in the real code.

## Step 2: Track by key, not index — and say why immediately

The moment you capture `draggedItemId = it.key` instead of `draggedIndex = it.index`, write the
reason down as a comment, because it's the single most common bug in a naive implementation: as
you swap items during a drag, indices change out from under you, but the *identity* of "the song
I'm holding" doesn't. If you track by index, a mid-drag swap silently starts dragging the wrong
row. Always derive index live:

```kotlin
listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedItemId }?.index
```

This is why the LazyColumn needs `key = { song -> song.id }` on `itemsIndexed` — the whole scheme
is unusable without stable keys.

## Step 3: The critical design decision — decouple position from layout offset

This is the part that's easy to get wrong and worth understanding before you write it, not after
debugging it.

Naive approach: "current drag position = the item's live `layoutInfo.offset` + accumulated
delta." This breaks the moment the list *recomposes for any other reason* (scrolling, item
resizing, a swap happening) — the offset baseline shifts and your drag position jumps.

Correct approach, the one in this code:

```kotlin
val currentTop = initialOffset + dragdistance
```

where `initialOffset` is a **snapshot** taken once (at drag start, or rebased on a swap) and
`dragdistance` is **pure accumulated finger movement**, never touching live layout data. This
value is stable regardless of what the list does underneath it. Build this as its own function
early (`checkForSwap` uses it) because everything downstream — swap detection *and* auto-scroll —
depends on this invariant holding.

## Step 4: Swap detection

Now that you have a stable `currentTop`/`currentBottom` for the dragged item, compare it against
the *other* visible items' live offsets (their offsets are fine to read live — they're not being
dragged):

```kotlin
listState.layoutInfo.visibleItemsInfo
    .filter { it.index != fromindex && it.overlaps(currentTop, currentBottom) }
    .firstOrNull { candidate ->
        if (dragdistance > 0) currentBottom > candidate.offset + candidate.size
        else currentTop < candidate.offset
    }
```

**Why the directional check (`dragdistance > 0` branch):** without it you'd trigger a swap the
instant the dragged item's bounding box merely *touches* a neighbor, causing jittery
back-and-forth swaps. The direction check requires the drag to have *passed* the neighbor's far
edge, which feels stable.

**Why rebase after a swap** (`initiallyDraggedElement = targetItem; dragdistance = 0f`): if you
don't reset the baseline, `dragdistance` keeps growing across multiple swaps and `currentTop`
drifts away from where the finger visually is. Rebasing to the item you just swapped past keeps
`initialOffset + dragdistance` continuously accurate.

## Step 5: Render the visual position

```kotlin
fun getDraggedOffset() = original + dragdistance - currentItemInfo.offset
```

used as a `graphicsLayer { translationY = animatedOffset }` on top of the item's normal
LazyColumn-assigned position (`currentItemInfo.offset`).

Work out on paper why this is written as a *subtraction* of the live offset: the item's actual
Compose layout position already equals `currentItemInfo.offset`. Add the translation and the two
cancel:

```
final screen position = currentItemInfo.offset + (original + dragdistance − currentItemInfo.offset)
                       = original + dragdistance
```

So the rendered item sits at a fixed, scroll-independent screen position no matter what the
underlying list layout does. **This cancellation is the fact that makes Step 6 easy** — write it
down, because you'll need it to justify why auto-scroll doesn't need any special-casing.

## Step 6: Auto-scroll (only attempt this after 1–5 work and feel solid)

Now ask: "what happens if the finger sits still near the top edge?" Nothing — `onDrag` only fires
on finger movement, so nothing scrolls. You need an independent, time-driven loop.

1. **Detect edge proximity** using the *same* stable `currentTop`/`currentBottom` from Step 3,
   compared against `listState.layoutInfo.viewportStartOffset`/`viewportEndOffset`. Ramp the
   scroll speed by how deep into the edge zone you are (division by edge threshold) — a constant
   speed feels jarring at the boundary.
2. **Drive it with a coroutine, not a Compose recomposition** — scrolling needs to keep happening
   even though the finger isn't moving, so it can't live inside `onDrag`. This needs a
   `CoroutineScope` (`rememberCoroutineScope()` in the composable, passed into the state class),
   and a loop:

   ```kotlin
   while (isActive) {
       val speed = autoScrollSpeed()
       if (speed == 0f) break
       listState.scrollBy(speed)
       checkForSwap()   // <-- easy to forget
       delay(16L)
   }
   ```

3. **Why call `checkForSwap()` inside the loop:** the list is now moving content underneath a
   *stationary* finger. New items are scrolling into range of the dragged item's fixed screen
   position, so swaps need to be re-evaluated on every scroll tick, not just on finger-movement
   events.
4. **Why you don't need to touch `dragdistance` when scrolling:** this is where Step 5's
   cancellation fact pays off. Because rendered position = `original + dragdistance` regardless
   of `currentItemInfo.offset`, the dragged item automatically stays visually pinned near the edge
   as the list scrolls under it — no manual compensation needed. (This is the step people usually
   get wrong first: they try to "add the scrolled amount back into dragdistance" to compensate,
   and it actually breaks the pin.)
5. **Cancel the job in two places:** `onDragEnd()` (finger lifted) and whenever
   `autoScrollSpeed()` returns 0 again (finger moved back to a safe zone) — otherwise the list
   keeps crawling after you've stopped needing it to.

## Suggested build/test order if doing it yourself

1. Drag + swap, no visuals, print state — verify index tracking survives a swap.
2. Add the `graphicsLayer` translation + shadow/scale — verify the item visually follows the
   finger smoothly, including across a swap (no jump).
3. Only then add auto-scroll — test at both ends of a list long enough to require scrolling, and
   test that letting go mid-autoscroll stops the job (no runaway scrolling).

The failure mode to watch for at each stage: index-based bugs show up as "wrong item starts
moving"; offset-coupling bugs show up as "item jitters or teleports on recomposition/scroll";
missing-job-cancellation bugs show up as "list keeps scrolling after I lift my finger."
