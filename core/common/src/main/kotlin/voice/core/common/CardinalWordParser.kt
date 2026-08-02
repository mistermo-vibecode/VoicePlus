package voice.core.common

public object CardinalWordParser {

  private val wordToInt: Map<String, Int> = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
    "nineteen" to 19, "twenty" to 20,
    "thirty" to 30, "forty" to 40, "fifty" to 50,
    "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
  )

  private val intToWord: Map<Int, String> = mapOf(
    1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five",
    6 to "six", 7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten",
    11 to "eleven", 12 to "twelve", 13 to "thirteen", 14 to "fourteen",
    15 to "fifteen", 16 to "sixteen", 17 to "seventeen", 18 to "eighteen",
    19 to "nineteen", 20 to "twenty",
    30 to "thirty", 40 to "forty", 50 to "fifty",
    60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety",
  )

  /** Returns the integer value of an English cardinal word (1–100), or null if not recognised. */
  public fun parse(word: String): Int? {
    val lower = word.lowercase().trim()
    wordToInt[lower]?.let { return it }
    val parts = lower.split(Regex("[-\\s]+"))
    if (parts.size == 2) {
      if (parts[0] == "one" && parts[1] == "hundred") return 100
      val tens = wordToInt[parts[0]] ?: return null
      val ones = wordToInt[parts[1]] ?: return null
      if (tens >= 20 && ones in 1..9) return tens + ones
    }
    return null
  }

  /** Returns the lowercase English cardinal word for [n] (1–100), or null if out of range. */
  public fun toWord(n: Int): String? {
    if (n <= 0 || n > 100) return null
    if (n == 100) return "one hundred"
    intToWord[n]?.let { return it }
    val tens = (n / 10) * 10
    val ones = n % 10
    val tensWord = intToWord[tens] ?: return null
    val onesWord = intToWord[ones] ?: return null
    return "$tensWord-$onesWord"
  }
}
