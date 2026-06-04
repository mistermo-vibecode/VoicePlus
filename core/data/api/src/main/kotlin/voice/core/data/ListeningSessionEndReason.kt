package voice.core.data

public enum class ListeningSessionEndReason(public val id: Int) {
  Paused(0),
  Sleep(1),
  EndOfBook(2),
  BookSwitch(3),
  ;

  public companion object {
    public fun fromId(id: Int?): ListeningSessionEndReason? = if (id == null) null else entries.firstOrNull { it.id == id }
  }
}
