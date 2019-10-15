package io.hengam.lib.notification.actions

import android.content.Context
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.mockk.*
import org.junit.Before
import org.junit.Test

class ActionTest {
    private lateinit var actionContext: ActionContext
    private val context: Context = mockk(relaxed = true)
    private val moshi: HengamMoshi = mockk(relaxed = true)

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