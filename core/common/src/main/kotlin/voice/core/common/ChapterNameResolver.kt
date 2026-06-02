package voice.core.common

private val DIGIT_REGEX = Regex("""^(.*?)(\d+)(.*)$""", RegexOption.DOT_MATCHES_ALL)

/**
 * Resolves the display name for a chapter mark.
 *
 * Priority:
 * 1. [override] if non-null
 * 2. [offset] applied to digits or cardinal words (1–100) in [rawName]
 * 3. [rawName] unchanged
 */
public fun resolveChapterName(
  rawName: String,
  offset: Int,
  override: String?,
): String {
  if (override != null) return override
  if (offset == 0) return rawName

  val trimmed = rawName.trim()

  // Digit match: extract first run of digits, apply offset, strip zero-padding.
  // Parse as Long (a digit run can exceed Int range, e.g. a numeric timestamp file name); if it
  // overflows even Long, leave the name untouched rather than crashing on the unparseable run.
  DIGIT_REGEX.matchEntire(trimmed)?.let { match ->
    val prefix = match.groupValues[1]
    val numStr = match.groupValues[2]
    val suffix = match.groupValues[3]
    val parsed = numStr.toLongOrNull() ?: return rawName
    val newNum = (parsed + offset).coerceAtLeast(1L)
    return "$prefix$newNum$suffix"
  }

  // Word match: try last two tokens first (e.g. "one hundred"), then last single token.
  // Split on whitespace runs so doubled/odd spacing never yields an empty token.
  val tokens = trimmed.split(Regex("\\s+"))
  val lastTwoPhrase = if (tokens.size >= 2) "${tokens[tokens.size - 2]} ${tokens.last()}" else null
  val twoWordParsed = lastTwoPhrase?.let { CardinalWordParser.parse(it) }
  val (parsed, dropCount) = when {
    twoWordParsed != null -> Pair(twoWordParsed, 2)
    else -> Pair(CardinalWordParser.parse(tokens.last()), 1)
  }
  if (parsed != null) {
    val referenceToken = tokens[tokens.size - dropCount] // first token of the matched phrase
    val newN = (parsed + offset).coerceAtLeast(1)
    val newWord = CardinalWordParser.toWord(newN) ?: newN.toString()
    val capitalised = if (referenceToken.firstOrNull()?.isUpperCase() == true) {
      // Capitalise each hyphen-separated segment (e.g. "twenty-three" → "Twenty-Three")
      newWord.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
    } else {
      newWord
    }
    val prefix = tokens.dropLast(dropCount).joinToString(" ").let { if (it.isNotEmpty()) "$it " else "" }
    return "$prefix$capitalised"
  }

  return rawName
}
