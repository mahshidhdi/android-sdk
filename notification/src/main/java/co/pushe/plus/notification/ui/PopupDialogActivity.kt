package co.pushe.plus.notification.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.R
import co.pushe.plus.notification.actions.Action
import co.pushe.plus.notification.actions.ActionContextFactory
import co.pushe.plus.notification.actions.DialogAction
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.messages.downstream.jsonAdapter
import co.pushe.plus.notification.messages.upstream.UserInputDataMessage
import co.pushe.plus.notification.utils.ImageDownloader
import co.pushe.plus.utils.log.Plog
import javax.inject.Inject

class PopupDialogActivity : AppCompatActivity() {
    @Inject lateinit var moshi: PusheMoshi
    @Inject lateinit var context: Context
    @Inject lateinit var actionContextFactory: ActionContextFactory
    @Inject lateinit var postOffice: PostOffice
    @Inject lateinit var imageDownloader: ImageDownloader

    private var alertDialog: AlertDialog? = null
    private var isInputDialog: Boolean = false
    private var inputs = mutableMapOf<String, EditText>()



    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            if (intent.action != ACTION_OPEN_DIALOG) {
                return
            }

            val notificationComponent = PusheInternals.getComponent(NotificationComponent::class.java)
            val actionJson = intent.extras?.getString(DATA_ACTION)
            val notificationJson = intent.extras?.getString(DATA_NOTIFICATION)

            if (notificationComponent == null) {
                Plog.error(T_NOTIF, "Notification Component was null in PopUpDialogActivity")
                return
            } else if (actionJson == null) {
                Plog.error(T_NOTIF, "PopupDialogActivity called with no action data")
                return
            } else if (notificationJson == null) {
                Plog.error(T_NOTIF, "PopupDialogActivity called with no notification data")
                return
            }

            notificationComponent.inject(this)

            val actionAdapter = moshi.adapter(DialogAction::class.java)
            val action = try {
                actionAdapter.fromJson(actionJson) ?: throw NullPointerException()
            } catch (ex: Exception) {
                Plog.error(T_NOTIF, "Parsing action data was unsuccessful in PopupDialogActivity", ex)
                return
            }

            val notificationAdapter = NotificationMessage.jsonAdapter(moshi.moshi)
            val notificationMessage = try {
                notificationAdapter.fromJson(notificationJson) ?: throw NullPointerException()
            } catch (ex: Exception) {
                Plog.error(T_NOTIF, "Parsing notification data was unsuccessful in PopupDialogActivity", ex)
                return
            }

            createAndShowDialog(action , notificationMessage)
        }catch (e: Exception){
            Plog.error(T_NOTIF, "Error in loading dialog activity", e)
            finish()
        }
    }

    private fun createAndShowDialog(action: DialogAction , notificationMessage: NotificationMessage) {
        val context = this
        isInputDialog = false

        val builder = AlertDialog.Builder(context)

        builder.setTitle(action.title)
        builder.setMessage(action.content)

        var buttonNumber = 0
        if (action.buttons.isNotEmpty()) {
            for (button in action.buttons) {
                // Don't show Open Dialog actions in dialog buttons
                if (button.action is DialogAction) {
                    continue
                }

                val clickListener = DialogInterface.OnClickListener { _, _ ->
                    saveInputData(notificationMessage)
                    executeActionAndClose(button.action , notificationMessage)
                    if (alertDialog?.isShowing == true) {
                        alertDialog?.dismiss()
                    }
                    isDialogShowing = false
                }

                when (buttonNumber++) {
                    0 -> builder.setNegativeButton(button.text, clickListener)
                    1 -> builder.setPositiveButton(button.text, clickListener)
                    2 -> builder.setNeutralButton(button.text, clickListener)
                    else -> {}
                }
            }
        }

        if (buttonNumber == 0) {
            builder.setNegativeButton(R.string.pushe_close_dialog) { _, _ ->
                executeActionAndClose(null, notificationMessage)
                isDialogShowing = false
            }
        }

        builder.setOnCancelListener {
            isDialogShowing = false
            finish()
        }

        if (action.inputs.isNotEmpty()) {
            isInputDialog = true

            val ll = LinearLayout(context)
            ll.layoutParams = ActionBar.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            ll.orientation = LinearLayout.VERTICAL

            action.inputs.forEach { inputLabel ->
                val label = TextView(context)
                label.text = inputLabel
                label.gravity = Gravity.CENTER

                val editText = EditText(context)
                val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT)
                editText.layoutParams = lp
                editText.gravity = Gravity.CENTER

                ll.addView(label)
                ll.addView(editText)
                inputs[inputLabel] = editText
            }
            builder.setView(ll)
        }

        if (action.iconUrl != null && !action.iconUrl.isBlank()) {
            try {
                val icon = imageDownloader.loadImageFromCache(action.iconUrl)
                builder.setIcon(icon)
            } catch (ex: Exception) {
                Plog.warn(T_NOTIF, "Failed to load cached dialog icon", ex)
            }
        }

        alertDialog = builder.create()
        alertDialog?.show()
    }

    private fun saveInputData(notificationMessage: NotificationMessage) {
        if (isInputDialog) {
            val dataMap= mutableMapOf<String,String>()
            inputs.forEach{
                dataMap[it.key] = it.value.text.toString()
            }

            val reportMessage = UserInputDataMessage(
                originalMessageId = notificationMessage.messageId,
                data = dataMap
            )
            postOffice.sendMessage(reportMessage)

        }
    }

    private fun executeActionAndClose(action: Action?, notificationMessage: NotificationMessage) {
        finish()
        try {
            action?.execute(actionContextFactory.createActionContext(notificationMessage))
        }catch (e: Exception){
            Plog.error(T_NOTIF, T_NOTIF_ACTION, "Executing Action was unsuccessful in PopupDialogActivity", e)
        }
    }

    companion object {
        const val ACTION_OPEN_DIALOG = "co.pushe.plus.OPEN_DIALOG"
        const val DATA_ACTION = "data_action"
        const val DATA_NOTIFICATION = "data_notification"

        var isDialogShowing = false
    }
}
