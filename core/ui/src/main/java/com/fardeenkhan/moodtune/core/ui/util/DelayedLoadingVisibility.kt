package com.fardeenkhan.moodtune.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long a load has to be in flight before its spinner is allowed to actually appear. */
private const val LOADING_INDICATOR_APPEAR_DELAY_MS = 200L

/**
 * True only once [isLoading] has been continuously true for [delayMillis]. A local, already-
 * populated Room query (Home's most-played, All Songs, Search) typically resolves in a handful
 * of milliseconds - showing a spinner immediately for that case means it's visible for a single
 * frame and yanked away before it's animated at all, which reads as a stuck/dropped frame rather
 * than a smooth loading state. Delaying its appearance means a fast load shows no spinner at
 * all (correct - there was nothing worth indicating), and a genuinely slow load still gets one,
 * shown for long enough to actually look like smooth motion.
 */
@Composable
fun rememberDelayedLoadingVisible(
    isLoading: Boolean,
    delayMillis: Long = LOADING_INDICATOR_APPEAR_DELAY_MS
): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(delayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}
