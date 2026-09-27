package com.fardeenkhan.moodtune.feature.playlist.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-press-drag-to-reorder for a [LazyColumn][androidx.compose.foundation.lazy.LazyColumn].
 * Tracks the dragged item purely by its stable key ([draggedItemId]) rather than its list index,
 * so it keeps following the same logical item across reorders even as the underlying data (and
 * therefore each item's index) shifts mid-drag.
 *
 * [onDrag] compares the dragged item's current on-screen position against every other *visible*
 * item's bounds; once it has been dragged past another item's midpoint-ish overlap threshold,
 * [onMoveState] is invoked to swap them in the backing data, and the drag's reference point
 * ("initiallyDraggedElement") is rebased to the item it just swapped with so the running
 * [dragdistance] stays relative to the new position instead of compounding drift.
 */
class DragDropListState(
    private val listState: LazyListState,
    private val onMoveState: State<(Int, Int) -> Unit>,
    private val scope: CoroutineScope
) {

    var draggedItemId by mutableStateOf<Any?>(null)
        private set

    val draggedIndex: Int?
        get() = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId }
            ?.index

    var dragdistance by mutableStateOf(0f)
        private set


    private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    private var autoScrollJob: Job? = null

     fun onDragStart(offset: Offset){

        listState.layoutInfo.visibleItemsInfo.firstOrNull {
            offset.y.toInt() in it.offset..(it.offset + it.size)
        }?.also {
            draggedItemId = it.key
            initiallyDraggedElement = it
        }
    }



    fun onDrag(delta: Offset){
        dragdistance += delta.y
        checkForSwap()
        updateAutoScroll()
    }

    private fun checkForSwap() {
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return

        val fromindex = currentItemInfo.index
        val initialOffset = initiallyDraggedElement?.offset ?: return
        val currentTop = initialOffset + dragdistance
        val currentBottom = currentTop + currentItemInfo.size

        listState.layoutInfo.visibleItemsInfo
            .filter {
                it.index != fromindex && it.overlaps(currentTop.toInt(), currentBottom.toInt())
            }
            .firstOrNull { candidates ->
                if (dragdistance > 0) {
                    currentBottom > candidates.offset + candidates.size
                } else {
                    currentTop < candidates.offset
                }
            }?.let { targetItem ->
                onMoveState.value(fromindex, targetItem.index)
                // When we move, we update the "initial" element to the target's info
                // so the relative dragdistance calculation stays correct.
                initiallyDraggedElement = targetItem
                dragdistance = 0f
            }

    }

    /**
     * Starts/stops a continuous auto-scroll while the dragged item sits within
     * [AUTO_SCROLL_EDGE_PX] of the top or bottom of the visible viewport, so long items lists
     * can be reordered past what's currently on screen (mirrors the "drag near the edge to
     * scroll" behavior of web drag-and-drop lists).
     */
    private fun autoScrollSpeed(): Float {
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return 0f
        val initialOffset = initiallyDraggedElement?.offset ?: return 0f
        val currentTop = initialOffset + dragdistance
        val currentBottom = currentTop + currentItemInfo.size
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset

        val distanceFromTop = currentTop - viewportStart
        val distanceFromBottom = viewportEnd - currentBottom

        return when {
            distanceFromTop < AUTO_SCROLL_EDGE_PX && listState.canScrollBackward -> {
                -AUTO_SCROLL_MAX_SPEED * (1f - (distanceFromTop.coerceIn(0f, AUTO_SCROLL_EDGE_PX) / AUTO_SCROLL_EDGE_PX))
            }
            distanceFromBottom < AUTO_SCROLL_EDGE_PX && listState.canScrollForward -> {
                AUTO_SCROLL_MAX_SPEED * (1f - (distanceFromBottom.coerceIn(0f, AUTO_SCROLL_EDGE_PX) / AUTO_SCROLL_EDGE_PX))
            }
            else -> 0f
        }
    }

    private fun updateAutoScroll() {
        if (autoScrollSpeed() == 0f) {
            stopAutoScroll()
            return
        }
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = scope.launch {
            while (isActive) {
                val speed = autoScrollSpeed()
                if (speed == 0f) break
                listState.scrollBy(speed)
                checkForSwap()
                delay(AUTO_SCROLL_TICK_MS)
            }
            autoScrollJob = null
        }
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun onDragEnd(){
        stopAutoScroll()
        draggedItemId = null
        dragdistance = 0f
        initiallyDraggedElement = null
    }

    fun getDraggedOffset(): Float{
        val original = initiallyDraggedElement?.offset ?: return 0f
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return 0f
        return original + dragdistance - currentItemInfo.offset
    }

    private companion object {
        const val AUTO_SCROLL_EDGE_PX = 150f
        const val AUTO_SCROLL_MAX_SPEED = 22f
        const val AUTO_SCROLL_TICK_MS = 16L
    }
}

private fun LazyListItemInfo.overlaps(top: Int, bottom: Int): Boolean {
    return offset < bottom && (offset + size) > top
}

@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit
): DragDropListState{
    val onMoveState = rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    return remember {
        DragDropListState(lazyListState, onMoveState, scope)
    }
}