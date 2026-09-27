package com.fardeenkhan.moodtune.core.wearsync

/**
 * Fixed limits on how much watch storage MoodTune's downloaded songs may use - internal
 * constants, not a user-facing setting (confirmed in `implementation/WearPlan.md` §1.3).
 *
 * Both the phone (estimating whether a selection will fit before sending) and the watch
 * (re-validating on arrival, since it's the one with ground truth on its own free space) read
 * from these same numbers so the two sides can't disagree on what "fits" means.
 */
object WearStorageBudget {
    /** Ceiling on MoodTune's own downloaded-song usage on the watch - not a claim on all free space. */
    const val CAP_BYTES: Long = 500L * 1024 * 1024

    /** Never let a download leave fewer than this much of the watch's actual free space remaining. */
    const val SAFETY_MARGIN_BYTES: Long = 150L * 1024 * 1024
}
