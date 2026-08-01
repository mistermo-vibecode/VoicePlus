package voice.core.data

import io.kotest.matchers.shouldBe
import org.junit.Test

class ListeningEventTypeTest {

  @Test
  fun `each entry has the expected stable id`() {
    ListeningEventType.entries.size shouldBe 7
    ListeningEventType.Back.id shouldBe 0
    ListeningEventType.Forward.id shouldBe 1
    ListeningEventType.Next.id shouldBe 2
    ListeningEventType.Previous.id shouldBe 3
    ListeningEventType.SetPosition.id shouldBe 4
    ListeningEventType.AutoAdvance.id shouldBe 5
    ListeningEventType.GoToChapter.id shouldBe 6
  }

  @Test
  fun `fromId round-trips every entry`() {
    ListeningEventType.entries.forEach { entry ->
      ListeningEventType.fromId(entry.id) shouldBe entry
    }
  }

  @Test
  fun `fromId returns null for unknown id`() {
    ListeningEventType.fromId(99) shouldBe null
  }
}
