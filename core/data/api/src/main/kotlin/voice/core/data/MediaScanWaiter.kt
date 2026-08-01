package voice.core.data

/**
 * Triggers a media scan and suspends until it has fully completed — including the active-book reconcile that
 * runs inside the scan. The OS-wipe restore uses this to wait for freshly-scanned books to exist under their
 * new (post-re-grant) URIs before re-keying snapshot data onto them, without the data layer having to depend
 * on the scanner implementation.
 */
public interface MediaScanWaiter {

  /** Always scans with `restartIfScanning = true`: a restore's scan must never be collapsed into an in-flight scan. */
  public suspend fun scanAndAwait()
}
