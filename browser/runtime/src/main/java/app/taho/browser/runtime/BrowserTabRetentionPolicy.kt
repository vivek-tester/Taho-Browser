package app.taho.browser.runtime

data class BrowserTabRetentionCandidate(
    val id: String,
    val lastAccessedAtEpochMs: Long,
    val selected: Boolean,
    val pinned: Boolean,
)

object BrowserTabRetentionPolicy {
    fun staleTabIds(
        tabs: List<BrowserTabRetentionCandidate>,
        olderThanEpochMs: Long,
    ): List<String> =
        tabs
            .asSequence()
            .filterNot { it.selected || it.pinned }
            .filter { it.lastAccessedAtEpochMs < olderThanEpochMs }
            .map { it.id }
            .toList()
}
