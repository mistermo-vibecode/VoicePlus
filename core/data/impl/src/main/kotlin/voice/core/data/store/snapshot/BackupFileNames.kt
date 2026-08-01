package voice.core.data.store.snapshot

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The file name IS the backup metadata (crawlfit pattern): kind from the prefix, timestamp parsed
 * back out of the digits, "is this one of ours" from the shape. No index file, no manifest.
 *
 * `voiceplus-backup-yyyyMMdd-HHmmss.json` — automatic save, pruned to the newest few.
 * `voiceplus-manual-yyyyMMdd-HHmmss.json` — manual save point, never pruned.
 *
 * Timestamps are UTC so file names sort identically everywhere.
 */
internal object BackupFileNames {

  const val LEGACY_PRIMARY = "voiceplus-backup.json"
  const val LEGACY_PREVIOUS = "voiceplus-backup.previous.json"

  private const val AUTO_PREFIX = "voiceplus-backup-"
  private const val MANUAL_PREFIX = "voiceplus-manual-"
  private const val SUFFIX = ".json"

  private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
  private val NAME = Regex("""^voiceplus-(backup|manual)-(\d{8}-\d{6})\.json$""")

  fun fileName(
    manual: Boolean,
    at: Instant,
  ): String = (if (manual) MANUAL_PREFIX else AUTO_PREFIX) + FORMAT.format(at) + SUFFIX

  data class Parsed(
    val savedAt: Instant?,
    val manual: Boolean,
    val legacy: Boolean,
  )

  /** Null when [displayName] is not a VoicePlus backup file. */
  fun parse(displayName: String): Parsed? {
    if (displayName == LEGACY_PRIMARY || displayName == LEGACY_PREVIOUS) {
      return Parsed(savedAt = null, manual = false, legacy = true)
    }
    val match = NAME.matchEntire(displayName) ?: return null
    val savedAt = runCatching { Instant.from(FORMAT.parse(match.groupValues[2])) }.getOrNull() ?: return null
    return Parsed(savedAt = savedAt, manual = match.groupValues[1] == "manual", legacy = false)
  }
}
