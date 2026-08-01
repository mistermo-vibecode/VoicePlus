package voice.core.data

public enum class ListeningSessionEndReason(public val id: Int) {
  Paused(0),
  Sleep(1),
  EndOfBook(2),
  BookSwitch(3),

  // The process died mid-listen (crash, force-stop, reboot); the session was reconstructed from
  // the periodic open-session checkpoint on the next app start.
  Interrupted(4),
  ;

  public companion object {
    public fun fromId(id: Int?): ListeningSessionEndReason? = id?.let { v -> entries.firstOrNull { it.id == v } }
  }
}
