package co.pushe.plus.notification.actions

import android.content.Context
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import io.mockk.*
import org.junit.Before
import org.junit.Test

class ActionTest {
    private lateinit var actionContext: ActionContext
    private val context: Context = mockk(relaxed = true)
    private val moshi: PusheMoshi = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { context.startActivities(any()) } just Runs
    }

    @Test
    fun execute_AppActionExecuteCorrectly() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "some title",
            content = "some content",
            action = AppAction()
        )

        actionContext = ActionContext(notificationMessage, context, moshi)
        notificationMessage.action.execute(actionContext)

        verify(exactly = 1) { context.startActivity(any()) }
    }
}