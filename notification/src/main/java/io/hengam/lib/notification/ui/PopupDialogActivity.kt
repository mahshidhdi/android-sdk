package io.hengam.lib.notification.ui

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
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.R
import io.hengam.lib.notification.actions.Action
import io.hengam.lib.notification.actions.ActionContextFactory
import io.hengam.lib.notification.actions.DialogAction
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.messages.downstream.jsonAdapter
import io.hengam.lib.notification.messages.upstream.UserInputDataMessage
import io.hengam.lib.notification.utils.ImageDownloader
import io.hengam.lib.utils.log.Plog
import javax.inject.Inject

class PopupDialogActivity : AppCompatActivity() {
    @Inject lateinit var moshi: HengamMoshi
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

            val notificationComponent = HengamInternals.getComponent(NotificationComponent::class.java)
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

        builder.setTitle(action.title ?: notificationMessage.bigTitle ?: notificationMessage.title)
        builder.setMessage(action.content ?: notificationMessage.bigContent ?: notificationMessage.content)

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
            builder.setNegativeButton(R.string.hengam_close_dialog) { _, _ ->
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
        const val ACTION_OPEN_DIALOG = "io.hengam.lib.OPEN_DIALOG"
        const val DATA_ACTION = "data_action"
        const val DATA_NOTIFICATION = "data_notification"

        var isDialogShowing = false
    }
}
