package com.fardeenkhan.moodtune.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The lifecycle of an asynchronously-loaded value a screen renders: nothing requested yet, a
 * load in flight, a successful result (whose [Loaded.data] may itself be empty - that's a UI
 * concern, not a load failure), or a failed load with a message safe to show the user directly.
 *
 * Reused across every ViewModel that shows a loaded list on screen, instead of each one
 * inventing its own `Boolean` isLoading flag plus a bare list with no way to represent "the load
 * failed" distinctly from "loaded, but empty."
 */
sealed class LoadState<out T> {
    data object Idle : LoadState<Nothing>()
    data object Loading : LoadState<Nothing>()
    data class Loaded<T>(val data: T) : LoadState<T>()
    data class Error(val message: String) : LoadState<Nothing>()
}

/** Fallback shown when a caught exception has no message of its own. */
private const val DEFAULT_LOAD_ERROR_MESSAGE = "Something went wrong. Please try again."

/**
 * Wraps this flow's values as [LoadState]: emits [LoadState.Loading] the moment collection
 * starts, then [LoadState.Loaded] for every value the source emits, or [LoadState.Error] if the
 * source throws. This is the one place the Loading/Loaded/Error reduction is implemented - a
 * ViewModel just collects the result and copies it onto its state, rather than re-deriving this
 * three-way branch itself.
 */
fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> =
    map<T, LoadState<T>> { LoadState.Loaded(it) }
        .onStart { emit(LoadState.Loading) }
        .catch { e -> emit(LoadState.Error(e.message ?: DEFAULT_LOAD_ERROR_MESSAGE)) }
