package com.fardeenkhan.moodtune.core.ui.util

import com.fardeenkhan.moodtune.domain.model.LoadState

/**
 * Which "shape" of UI a [LoadState] currently calls for - loading, failed, loaded-but-empty, or
 * loaded-with-content. Meant as a stable `Crossfade`/`AnimatedContent` `targetState`: two
 * [LoadState.Loaded] values with different data both collapse to [CONTENT], so a data refresh
 * within the same shape doesn't replay the crossfade - it should only animate when the shape
 * itself changes (e.g. spinner -> content), not on every emission.
 */
enum class LoadStateShape { LOADING, ERROR, EMPTY, CONTENT }

/** See [LoadStateShape]. [isEmpty] decides whether [LoadState.Loaded] data counts as empty. */
fun <T> LoadState<T>.shape(isEmpty: (T) -> Boolean): LoadStateShape = when (this) {
    is LoadState.Idle, is LoadState.Loading -> LoadStateShape.LOADING
    is LoadState.Error -> LoadStateShape.ERROR
    is LoadState.Loaded -> if (isEmpty(data)) LoadStateShape.EMPTY else LoadStateShape.CONTENT
}
