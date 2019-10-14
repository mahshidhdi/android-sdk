package co.pushe.plus.internal

import co.pushe.plus.LogTag.T_DEBUG
import co.pushe.plus.messages.downstream.RunDebugCommandMessage
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single

class PusheDebug {
    val commands: Map<String, Any> get() {
       return PusheInternals.debugCommandProviders
               .map { it.commands }
               .fold(emptyMap()) { acc, next -> mergeCommands(acc, next) }
    }

    fun handleCommand(commandId: String, input: DebugInput) {
        cpuThread {
            try {
                PusheInternals.debugCommandProviders.any { it.handleCommand(commandId, input) }
            } catch (ex: Exception) {
                Plog.error(T_DEBUG, ex)
            }
        }
    }

    private fun mergeCommands(c1: Map<String, Any>, c2: Map<String, Any>): Map<String, Any> {
        return c1.keys.union(c2.keys)
                .map { k ->
                    if (k in c1 && k in c2) {
                        k to mergeCommands(c1[k] as Map<String, Any>, c2[k] as Map<String, Any>)
                    } else if (k in c1) {
                        k to (c1[k] ?: "")
                    } else {
                        k to (c2[k] ?: "")
                    }
                }
                .toMap()
    }

    fun handleCommand(message: RunDebugCommandMessage) {
        cpuThread {
            try {
                Plog.debug(T_DEBUG, "Running debug command...",
                    "Command Id" to message.command,
                    "Params" to message.params
                )
                val params = message.params.toMutableList()

                PusheInternals.debugCommandProviders.any {
                    it.handleCommand(
                        message.command,
                        object : DebugInput {
                            override fun prompt(title: String, name: String, default: String?): Single<String> {
                                return Single.fromCallable {
                                    return@fromCallable if (params.isEmpty()) {
                                        if (default == null) {
                                            Plog.warn(T_DEBUG, "Insufficient parameters given for debug command", "Missing Param" to title)
                                            ""
                                        } else {
                                            default
                                        }
                                    } else {
                                        params.removeAt(0)
                                    }
                                }
                            }

                            override fun promptNumber(title: String, name: String, default: Long?): Single<Long> {
                                return Single.fromCallable {
                                    return@fromCallable if (params.isEmpty()) {
                                        if (default == null) {
                                            Plog.warn(T_DEBUG, "Insufficient parameters given for debug command", "Missing Param" to title)
                                            0L
                                        } else {
                                            default
                                        }
                                    } else {
                                        params.removeAt(0).toLongOrNull() ?: 0L
                                    }
                                }
                            }

                            override fun requestPermissions(vararg permission: String) {
                                Plog.error(T_DEBUG, "requesting for permission is not possible when running commands with message.")
                            }
                        }
                    )
                }
            } catch (ex: Exception) {
                Plog.error(T_DEBUG, ex)
            }
        }
    }
}

interface DebugCommandProvider {
    val commands: Map<String, Any>
    fun handleCommand(commandId: String, input: DebugInput): Boolean
}

interface DebugInput {
    fun prompt(title: String, name: String, default: String? = null): Single<String>
    fun promptNumber(title: String, name: String, default: Long? = null): Single<Long>
    fun requestPermissions(vararg permission: String)
}