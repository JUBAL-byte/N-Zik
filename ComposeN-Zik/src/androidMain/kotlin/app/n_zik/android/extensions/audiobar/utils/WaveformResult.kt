package app.n_zik.android.extensions.audiobar.utils

/**
 * Outcome of a waveform lookup/extraction attempt ([WaveformExtractor.getOrExtractWaveform]).
 *
 * The previous `List<Int>?` contract conflated "not ready yet, retry" with "failed for good",
 * which caused callers with a single attempt (menu buttons) to report a transient cache state
 * as a fatal error while the retrying seekbar path silently succeeded a moment later.
 */
sealed class WaveformResult {

    /** Amplitudes were loaded from cache or freshly extracted. */
    data class Success(val amplitudes: List<Int>) : WaveformResult()

    /**
     * The download cache is transiently incomplete (e.g. spans not yet merged, or the
     * `CacheDataSource` briefly reports a zero-length/placeholder read). Retryable: the state
     * is expected to resolve on its own once the download finishes.
     */
    data object NotReady : WaveformResult()

    /** No cached span exists at all for this media — nothing to extract from. Fatal. */
    data object NoCache : WaveformResult()

    /** Extraction ran but failed for a reason unrelated to cache readiness. Fatal. */
    data class Failed(val reason: String) : WaveformResult()
}
