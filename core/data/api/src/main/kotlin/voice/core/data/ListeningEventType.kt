package voice.core.data

public enum class ListeningEventType(public val id: Int) {
  Back(0),
  Forward(1),
  Next(2),
  Previous(3),
  SetPosition(4),
  AutoAdvance(5),

  // A deliberate jump to a chosen destination: the chapter list, a bookmark, or a listening-log
  // entry. Distinct from SetPosition (scrubber drag / raw seek) so the log can say what the user
  // actually did instead of a generic "position set".
  GoToChapter(6),
  ;

  public companion object {
    public fun fromId(id: Int): ListeningEventType? = entries.firstOrNull { it.id == id }
  }
}
