package com.fardeenkhan.moodtune.wear.download

/**
 * Tracks what's actually stored on the watch's disk - one entry per downloaded song
 * (`songId`, file path, source `playlistId`, `sizeBytes`, `downloadedAt`) - backed by a flat
 * kotlinx.serialization JSON manifest file. No Room: the data shape doesn't need it at this
 * scale (see `implementation/WearPlan.md` §2.4 for why).
 *
 * TODO(§2.4, §5 Section 4): manifest read/write, `hasRoomFor(newBytes)` (checks
 * `WearStorageBudget.CAP_BYTES` and actual free space via `StatFs` minus
 * `WearStorageBudget.SAFETY_MARGIN_BYTES`), `songsFor(playlistId)`, `remove(songId)`,
 * `removeAll(playlistId)`.
 *
 * See `implementation/WearPlan.md` §1.3, §2.4.
 */
class WearDownloadStore
