package voice.core.data

public enum class ListeningEventType(public val id: Int) {
  Back(0),
  Fwd(1),
  Next(2),
  Prev(3),
  SetPosition(4),
  AutoAdvance(5),
  ;

  public companion object {
    public fun fromId(id: Int): ListeningEventType? = entries.firstOrNull { it.id == id }
  }
}
