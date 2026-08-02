package voice.core.common

private val DIGIT_REGEX = Regex("""^(.*?)(\d+)(.*)$""", RegexOption.DOT_MATCHES_ALL)
private val WORD_REGEX = Regex("[A-Za-z]+(?:-[A-Za-z]+)*")

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
    val shifted = runCatching { Math.addExact(parsed, offset.toLong()) }
      .getOrElse { if (offset > 0) Long.MAX_VALUE else Long.MIN_VALUE }
    val newNum = shifted.coerceAtLeast(1L)
    return "$prefix$newNum$suffix"
  }

  // Search backwards so names such as "Book One · Chapter Five" adjust the chapter number,
  // and accept punctuation or title text after it ("Chapter Five: The Return").
  val words = WORD_REGEX.findAll(trimmed).toList()
  for (index in words.indices.reversed()) {
    val current = words[index]
    val previous = words.getOrNull(index - 1)
    val pair = previous?.takeIf {
      trimmed.substring(it.range.last + 1, current.range.first).all(Char::isWhitespace)
    }
    val parsedWithRange = pair
      ?.let { first ->
        CardinalWordParser.parse(trimmed.substring(first.range.first, current.range.last + 1))
          ?.let { parsed -> parsed to first.range.first..current.range.last }
      }
      ?: CardinalWordParser.parse(current.value)?.let { parsed -> parsed to current.range }

    if (parsedWithRange != null) {
      val (parsed, range) = parsedWithRange
      val referenceToken = trimmed.substring(range).substringBefore(' ')
      val newN = (parsed.toLong() + offset.toLong()).coerceAtLeast(1L)
      val newWord = newN.takeIf { it <= Int.MAX_VALUE }
        ?.let { CardinalWordParser.toWord(it.toInt()) }
        ?: newN.toString()
      val capitalised = if (referenceToken.firstOrNull()?.isUpperCase() == true) {
        // Capitalise each hyphen-separated segment (e.g. "twenty-three" → "Twenty-Three")
        newWord.split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
      } else {
        newWord
      }
      return trimmed.replaceRange(range, capitalised).replace(Regex("\\s+"), " ")
    }
  }

  return rawName
}
