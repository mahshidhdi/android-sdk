package co.pushe.plus.utils

import java.util.*

/***
 * A helper class to generate UUID and random Integer
 */
object IdGenerator {
    private val random = Random()

    /***
     * Generate Id
     *
     * @param length UUID length
     * @return generated UUID
     */
    fun generateId(length: Int = 12): String {
        val source = "abcdefghijklmnopqrstuvxyz0123456789"
        val builder = StringBuilder()
        val random = Random()
        for (i in 1..length) {
            builder.append(source[random.nextInt(source.length)])
        }
        return builder.toString()
    }

    /***
     * generate random Integer
     *
     * @return generated random Integer
     */
    fun generateIntegerId(): Int {
        return random.nextInt()
    }
}
