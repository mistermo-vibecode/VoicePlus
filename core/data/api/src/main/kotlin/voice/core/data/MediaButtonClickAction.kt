package voice.core.data

import kotlinx.serialization.Serializable

@Serializable
public enum class MediaButtonClickAction {
  NONE,
  SKIP_FORWARD,
  SKIP_BACKWARD,
  SKIP_FORWARD_CHAPTER,
  SKIP_BACKWARD_CHAPTER,
  QUICK_BOOKMARK,
}
