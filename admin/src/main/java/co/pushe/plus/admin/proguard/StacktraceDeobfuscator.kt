package co.pushe.plus.admin.proguard

import android.content.Context
import co.pushe.plus.admin.LogTag.T_ADMIN
import co.pushe.plus.utils.log.Plog
import java.io.IOException

class StacktraceDeobfuscator(private val context: Context) {
    private var retrace: ReTrace? = null

    fun initialize() {
        try {
            retrace = ReTrace(ReTrace.STACK_TRACE_EXPRESSION, false, context.assets.open("mapping.txt"))
        } catch (ex: IOException) {
            Plog.warn(T_ADMIN, "Could not find proguard mapping file for stacktrace deobfuscation")
        }
    }

    fun deobfuscate(stacktrace: String): String {
        return try {
            return retrace?.execute(stacktrace) ?: stacktrace
        } catch (ex: IOException) {
            System.err.println(ex)
            stacktrace
        }
    }
}