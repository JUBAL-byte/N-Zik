package app.n_zik.android.musicbrainz.utils

/**
 * Converts a 2-letter ISO country code into its flag emoji.
 *
 * @return the flag emoji, or an empty string if this is not a valid 2-letter code
 */
fun String.toFlagEmoji(): String {
    if (this.length != 2) return ""
    val countryCode = this.uppercase()
    val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

/**
 * Cleans a raw Wikipedia extract for on-screen display:
 * removes invisible characters, normalizes line breaks and drops
 * the trailing "From Wikipedia..." attribution note.
 */
fun String.cleanWikipediaText(): String {
    return this
        // Remove the most common invisible characters from Wikipedia
        // \u200B-\u200D: Zero-width spaces
        // \uFEFF: Byte Order Mark
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")

        // Convert non-breaking spaces and unusual spaces into regular spaces
        .replace(Regex("[\\u00A0\\u202F]"), " ")

        // Handle Unicode line/paragraph separators (they force a line break)
        .replace(Regex("[\\u2028\\u2029]"), "\n")

        // Normalize classic carriage returns
        .replace("\r\n", "\n")
        .replace("\r", "\n")

        // Protect double line breaks (real paragraph breaks)
        .replace(Regex("\n{2,}"), "[PARAGRAPH_BREAK]")

        // Join interrupted lines: replace single \n with a space
        .replace("-\n", "") // Word split by a hyphen at the end of a line
        .replace("\n", " ")

        // Restore real paragraphs
        .replace("[PARAGRAPH_BREAK]", "\n\n")

        // Remove multiple spaces created by the substitutions
        .replace(Regex(" +"), " ")

        // Trim the start and end of every line
        .lines().joinToString("\n") { it.trim() }

        // Remove the trailing Wikipedia attribution note
        .replace(Regex("\\s*From Wikipedia.*$"), "")

        .trim()
}
