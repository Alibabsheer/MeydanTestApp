package com.example.meydantestapp.utils

/**
 * Normalises any Unicode decimal digits to their Latin counterparts (0-9).
 */
fun String.toLatinDigits(): String {
    if (isEmpty()) return this
    var changed = false
    val builder = StringBuilder(length)
    for (char in this) {
        val normalized = char.normalizeToLatinDigit()
        builder.append(normalized)
        if (normalized != char) changed = true
    }
    return if (changed) builder.toString() else this
}

private fun Char.normalizeToLatinDigit(): Char {
    return when {
        this in '0'..'9' -> this
        this in '\u0660'..'\u0669' -> ('0'.code + (code - '\u0660'.code)).toChar()
        this in '\u06F0'..'\u06F9' -> ('0'.code + (code - '\u06F0'.code)).toChar()
        Character.isDigit(this) -> {
            val numeric = Character.getNumericValue(this)
            if (numeric in 0..9) {
                ('0'.code + numeric).toChar()
            } else {
                this
            }
        }
        else -> this
    }
}
