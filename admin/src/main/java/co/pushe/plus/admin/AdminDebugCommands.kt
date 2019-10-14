package co.pushe.plus.admin

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import co.pushe.plus.Pushe
import co.pushe.plus.internal.DebugInput
import io.reactivex.Single
import sh.hadi.bark.BarkCommands


class AdminDebugCommands(private val activity: AppCompatActivity) : BarkCommands() {
    override fun onCommandCalled(handler: () -> Unit) {
        handler()
    }

    @SuppressLint("NewApi")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun initializeCommands() {
        val pusheDebug = Pushe.debugApi()
        val commands = pusheDebug.commands
        val prompt = Prompt()

        fun addSubMenuCommands(subMenuCreator: SubMenuCreator, commands: Map<String, Any>) {
            commands.forEach {
                val commandName = it.key
                val command = it.value

                if (command is Map<*, *>) {
                    val submenu = subMenuCreator.subMenu(commandName)
                    addSubMenuCommands(submenu, command as Map<String, Any>)
                } else if (command is String) {
                    subMenuCreator.addCommand(commandName) { pusheDebug.handleCommand(command, prompt) }
                }
            }
        }

        commands.forEach {
            val commandName = it.key
            val command = it.value

            if (command is Map<*, *>) {
                val submenu = subMenu(commandName)
                if (submenu != null) {
                    addSubMenuCommands(submenu, command as Map<String, Any>)
                }
            } else if (command is String) {
                addCommand(commandName) { pusheDebug.handleCommand(command, prompt) }
            }
        }
    }

    inner class Prompt : DebugInput {
        override fun prompt(title: String, name: String, default: String?): Single<String> {
            return Single.create { emitter ->
                sh.hadi.bark.Prompt.showInputDialog(activity, title, name, default) {
                    emitter.onSuccess(it)
                }
            }
        }

        override fun promptNumber(title: String, name: String, default: Long?): Single<Long> {
            return Single.create { emitter ->
                sh.hadi.bark.Prompt.showInputDialog(activity, title, name, default?.toString(), InputType.TYPE_CLASS_NUMBER) {
                    emitter.onSuccess(it.toLong())
                }
            }
        }

        override fun requestPermissions(vararg permissions: String) {
            ActivityCompat.requestPermissions(activity, permissions, 0x0)
        }
    }
}
