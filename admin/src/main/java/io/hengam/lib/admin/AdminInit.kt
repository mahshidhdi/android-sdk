package io.hengam.lib.admin

import android.content.Context
import android.graphics.Color
import io.hengam.lib.admin.LogTag.T_ADMIN
import io.hengam.lib.admin.proguard.StacktraceDeobfuscator
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.ioThread
import io.hengam.lib.utils.InitProvider
import io.hengam.lib.utils.log.LogHandler
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.PublishRelay
import io.hengam.lib.utils.rx.justDo
import com.squareup.moshi.JsonAdapter
import sh.hadi.bark.Bark
import sh.hadi.bark.BarkLevel
import sh.hadi.bark.LogColor
import java.util.concurrent.TimeUnit

class AdminInit : InitProvider() {
    private var anyAdapterInstance: JsonAdapter<Any>? = null
    private val anyAdapter: JsonAdapter<Any>
        get() {
            return anyAdapterInstance ?:
                HengamInternals.getComponent(CoreComponent::class.java)?.moshi()?.adapter(Any::class.java)?.serializeNulls()
                        .apply { anyAdapterInstance = this }
                    ?: HengamMoshi().adapter(Any::class.java).serializeNulls()
        }

    override fun initialize(context: Context) {
        val stacktraceDeobfuscator = StacktraceDeobfuscator(context)
        stacktraceDeobfuscator.initialize()

        val bark = Bark.getInstance(context)
        val insertDebouncer = PublishRelay.create<Int>()
        bark.internalPackageName = "co.ronash"

        bark.customizeLogColor { logItem ->
            when {
                "Parcel Received" in logItem.message -> LogColor(
                        Color.parseColor("#7e57c2"),
                        Color.WHITE,
                        Color.parseColor("#9170cb"),
                        Color.WHITE
                )
                logItem.message == "Sending parcel" -> LogColor(
                        Color.parseColor("#5c6bc0"),
                        Color.WHITE,
                        Color.parseColor("#7481c9"),
                        Color.WHITE
                )
                else -> null
            }
        }

        bark.modifyStacktrace { stacktraceDeobfuscator.deobfuscate(it) }

        var bulk = bark.bulk()
        Plog.addHandler(LogHandler { logItem ->
            val t = logItem.throwable
            val message = logItem.message?.takeIf { it.isNotEmpty() } ?: logItem.throwable?.message
            val level = logItem.level

            ioThread {
                bulk.log(
                        level = when(level) {
                            LogLevel.TRACE -> BarkLevel.TRACE
                            LogLevel.DEBUG -> BarkLevel.DEBUG
                            LogLevel.INFO -> BarkLevel.INFO
                            LogLevel.WARN -> BarkLevel.WARN
                            LogLevel.ERROR -> BarkLevel.ERROR
                            LogLevel.WTF -> BarkLevel.WTF
                        },
                        tags = logItem.tags,
                        message = message ?: "",
                        error = t,
                        data = logItem.logData.mapValues {
                            val value = it.value
                            if (value is String) {
                                value
                            } else {
                                try {
                                    anyAdapter.toJson(value)
                                } catch (ex: Exception) {
                                    Plog.error(T_ADMIN, "Could not serialize log data", ex,
                                            "Message" to logItem.message,
                                            "Level" to logItem.level,
                                            "Tags" to logItem.tags
                                    )
                                    ""
                                }
                            }
                        },
                        timestamp = logItem.timestamp
                )
                insertDebouncer.accept(0)
            }
        })

        insertDebouncer.debounce(200, TimeUnit.MILLISECONDS, ioThread())
                .observeOn(ioThread())
                .justDo {
                    bulk.execute()
                    bulk = bark.bulk()
                }
    }
}
