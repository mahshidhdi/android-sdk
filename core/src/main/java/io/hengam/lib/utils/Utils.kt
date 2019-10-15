package io.hengam.lib.utils

import android.util.Patterns
import io.hengam.lib.utils.log.Plog
import java.lang.StringBuilder


inline fun String.letIfNotBlank(block: (String) -> Unit) {
    if (!this.isBlank()) {
        block(this)
    }
}

fun isValidWebUrl(url: String?): Boolean {
    return url != null && url.isNotEmpty() && Patterns.WEB_URL.matcher(url).matches()
}

/**
 * Convert byte array to hex string
 * Takes the byteArray of the signature and transforms it to Hex format
 */
fun bytesToHex(bytes: ByteArray): String {
    val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    val hexChars = CharArray(bytes.size * 2)
    var v: Int
    for (j in bytes.indices) {
        v = bytes[j].toInt() and 0xFF
        hexChars[j * 2] = hexArray[v.ushr(4)]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    val hashedString = String(hexChars)
    val hashBuilder = StringBuilder()
    for (i in 0 until hashedString.length) {
        if (i % 2 == 0 && i != 0) {
            val index = hashedString[i]
            hashBuilder.append(":").append(index)
        } else {
            hashBuilder.append(hashedString[i])
        }
    }
    return hashBuilder.toString() // Can also be upper cased
}

/**
 * Calls a given function and catches any exceptions which may be thrown by it.
 * If an exception is thrown it will be logged with the [Plog.error] function
 *
 * @param errorLogTags Log tags to be used when logging exceptions
 */
inline fun tryAndCatch(vararg errorLogTags: String, block: () -> Unit) {
    try {
        block()
    } catch (ex: Exception) {
        Plog.error.withError(ex).withTag(*errorLogTags).log()
    }
}

fun ordinal(i: Int): String {
    val sufixes = arrayOf("th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th")
    return when (i % 100) {
        11, 12, 13 -> i.toString() + "th"
        else -> i.toString() + sufixes[i % 10]
    }
}
