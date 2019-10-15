package io.hengam.lib.notification.messages.downstream

import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.RawDownstreamMessage
import io.hengam.lib.notification.actions.*
import io.hengam.lib.utils.minutes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationMessageTest {
    private val moshi = HengamMoshi()

    @Before
    fun setUp() {
        io.hengam.lib.extendMoshi(moshi)
        io.hengam.lib.notification.extendMoshi(moshi)
    }

    private fun createRawMessage(message: String): RawDownstreamMessage {
        return RawDownstreamMessage(
            "", 0,
            moshi.adapter(Any::class.java).fromJson(message) ?: emptyMap<String, Any>()
        )
    }

    private fun parseMessage(messageJson: String): NotificationMessage {
        val message = NotificationMessage.Parser()
            .parseMessage(moshi, createRawMessage(messageJson))
        return message ?: throw NullPointerException()
    }

    @Test
    fun parse_SimpleMessage() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content"
            }
        """
        )

        assertEquals("Simple Message", message.title)
        assertEquals("Simple Content", message.content)
        assertEquals(0, message.buttons.size)
        assertTrue(message.action is AppAction)
    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_DismissAction() {

        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "D"
                }
            }
        """
        )
        assertTrue(message.action is DismissAction)

    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_AppAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "A"
                }
            }
        """
        )
        assertTrue(message.action is AppAction)

    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_UrlAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "U",
                    "url": "Some url"
                }
            }
        """
        )
        assertTrue(message.action is UrlAction)
        val messageAction = (message.action as UrlAction)
        assertEquals("Some url", messageAction.url)

    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_IntentAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "I",
                    "uri": "Some Data",
                    "action":"Some Action",
                    "category":["Some Category"],
                    "market_package_name":"Some Package name",
                    "resolvers":["resolver","another resolver"]
                }
            }
        """
        )
        assertTrue(message.action is IntentAction)
        val messageAction = (message.action as IntentAction)
        assertEquals("Some Data", messageAction.data)
        assertEquals("Some Action", messageAction.action)
        assertEquals("Some Category", messageAction.categories?.get(0))
        assertEquals("Some Package name", messageAction.packageName)
        assertEquals(2, messageAction.resolvers?.size)


    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_CafeBazaarRateAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "C"
                }
            }
        """
        )
        assertTrue(message.action is CafeBazaarRateAction)


    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_DialogAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "G",
                    "title": "some title",
                    "content": "some content",
                    "icon": "some icon",
                    "buttons":[
                        {
                            "btn_id": 1,
                            "btn_content": "Button Text",
                            "btn_action": {
                                "action_type": "A"
                            }
                        },
                        {
                            "btn_id": 2,
                            "btn_content": "Button Text 2",
                            "btn_action": {
                                "action_type": "D"
                            }
                        }
                    ],
                    "inputs": ["some input", "another input", "input again"]
                }
            }
        """
        )
        assertTrue(message.action is DialogAction)
        val messageAction = (message.action as DialogAction)
        assertEquals("some title", messageAction.title)
        assertEquals("some content", messageAction.content)
        assertEquals("some icon", messageAction.iconUrl)
        assertEquals("Button Text", messageAction.buttons[0].text)
        assertEquals(2, messageAction.buttons.size)
        assertEquals("another input", messageAction.inputs[1])
        assertEquals(3, messageAction.inputs.size)
    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_DownloadAppAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "L",
                    "dl_url": "some url",
                    "package_name": "some package_name",
                    "open_immediate": true,
                    "notif_title": "some title",
                    "time_to_install": 600000
                }
            }
        """
        )
        assertTrue(message.action is DownloadAppAction)
        val messageAction = (message.action as DownloadAppAction)
        assertEquals("some url", messageAction.downloadUrl)
        assertEquals("some package_name", messageAction.packageName)
        assertEquals(true, messageAction.openImmediate)
        assertEquals("some title", messageAction.fileTitle)
        assertEquals(minutes(10), messageAction.timeToInstall)
    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_WebViewAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "W",
                    "url": "some url"
                }
            }
        """
        )
        assertTrue(message.action is WebViewAction)
        val messageAction = (message.action as WebViewAction)
        assertEquals("some url", messageAction.webUrl)
    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_DownloadAndWebViewAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "O",
                    "url": "some web_url",
                    "dl_url": "some dl_url",
                    "package_name": "some package_name",
                    "open_immediate": true,
                    "notif_title": "some title",
                    "time_to_install": 600000
                }
            }
        """
        )
        assertTrue(message.action is DownloadAndWebViewAction)
        val messageAction = (message.action as DownloadAndWebViewAction)
        assertEquals("some dl_url", messageAction.downloadUrl)
        assertEquals("some web_url", messageAction.webUrl)
        assertEquals("some package_name", messageAction.packageName)
        assertEquals(true, messageAction.openImmediate)
        assertEquals("some title", messageAction.fileTitle)
        assertEquals(minutes(10), messageAction.timeToInstall)
    }

    @Test
    fun parse_ActionsAreParsedCorrectlyBasedOnActionType_UserActivityAction() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "T",
                    "hengam_activity_extra": "some extra",
                    "action_data": "some data"
                }
            }
        """
        )
        assertTrue(message.action is UserActivityAction)
        val messageAction = (message.action as UserActivityAction)
        assertEquals("some extra", messageAction.activityExtra)
        assertEquals("some data", messageAction.activityClassName)
    }

    @Test
    fun parse_ParseButtons() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "buttons": [
                    {
                        "btn_id": 1,
                        "btn_content": "Button Text",
                        "btn_action": {
                            "action_type": "A"
                        }
                    },
                    {
                        "btn_id": 2,
                        "btn_content": "Button Text 2",
                        "btn_action": {
                            "action_type": "D"
                        }
                    }
                ]
            }
        """
        )
        val buttons = message.buttons
        assertEquals(2, buttons.size)
        assertEquals("1", buttons[0].id)
        assertEquals("Button Text", buttons[0].text)
        assertTrue(buttons[0].action is AppAction)
        assertEquals("2", buttons[1].id)
        assertEquals("Button Text 2", buttons[1].text)
        assertTrue(buttons[1].action is DismissAction)
    }

    @Test
    fun parse_HandleDefaults() {
        val message = parseMessage(
            """
            {
                "message_id": "123",
                "title": "some title",
                "content": "some content",
                "notif_icon": "some notif_icon",
                "buttons": [{
                        "btn_id": 2
                    }],
                "action": {
                    "action_type": "L",
                    "dl_url": "some url",
                    "package_name": "some package_name",
                    "notif_title": "some title",
                    "time_to_install": 600000
                }
            }
        """
        )


        assertEquals(true, (message.action as DownloadAppAction).openImmediate)
        assertEquals(0, message.buttons[0].order)
        assertEquals(NotificationMessage.DEFAULT_PRIORITY, message.priority)
        assertEquals(false, message.useHengamIcon)
        assertEquals(NotificationMessage.DEFAULT_LED_ON_TIME, message.ledOnTime)
        assertEquals(NotificationMessage.DEFAULT_LED_OFF_TIME, message.ledOffTime)
        assertEquals(false, message.wakeScreen)
        assertEquals(true, message.showNotification)
        assertEquals(false, message.permanentPush)
        assertEquals(false, message.forcePublish)
    }
}