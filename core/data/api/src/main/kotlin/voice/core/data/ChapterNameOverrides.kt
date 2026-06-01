package voice.core.data

/**
 * Builds a lookup of override name keyed by `(chapterId, markStartMs)` — the composite key that
 * uniquely identifies a chapter mark (see [ChapterNameOverride]). Every surface that resolves
 * chapter names (playback screen, bookmarks, listening log, widget, media session, editor) uses
 * this so the keying lives in one place.
 */
public fun List<ChapterNameOverride>.byMarkKey(): Map<Pair<String, Long>, String> =
  associateBy({ Pair(it.chapterId, it.markStartMs) }, { it.name })
