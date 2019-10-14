package co.pushe.plus.messaging

import android.content.Context
import android.content.SharedPreferences
import co.pushe.plus.extendMoshi
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.rx.subscribeBy
import co.pushe.plus.utils.seconds
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import co.pushe.plus.utils.test.TestUtils.mockIoThread
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MessageStoreTest {
    private val context: Context = mockk(relaxed=true)
    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val moshi = PusheMoshi()
    private val pusheConfig = PusheConfig(MockSharedPreference(), moshi)
    private lateinit var messageStore: MessageStore

    private val ioThread = mockIoThread()
    private val cpuThread = mockCpuThread()

    @Before
    fun setUp() {
        extendMoshi(moshi)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        messageStore = MessageStore(moshi,pusheConfig, context)
    }

    @Test
    fun storesAndReadsMessages() {
        val message1 = UpstreamMockMessageBook("Song of Ice and Fire", Genre.FANTASY, MockPerson("George", "Martin", 100))
        val message2 = UpstreamMockMessageMovie("Sunshine", Genre.SCIFI, 2007)

        messageStore.storeMessage(message1, SendPriority.IMMEDIATE, false, false, null, seconds(1))
        messageStore.storeMessage(message2, SendPriority.SOON, true, true, "parcel-group", seconds(2))

        val values = messageStore.readMessages()
                .test()
                .values()
        values.sortBy { it.message.messageType }

        assertEquals(2, values.size)
        assertEquals(message1, values[0].message)
        assertEquals(SendPriority.IMMEDIATE, values[0].sendPriority)
        assertFalse(values[0].requiresRegistration)
        assertEquals(1000L, values[0].expireAfter?.toMillis())

        assertEquals(message2, values[1].message)
        assertEquals(SendPriority.SOON, values[1].sendPriority)
        assertTrue(values[1].requiresRegistration)
        assertEquals("parcel-group", values[1].parcelGroupKey)
        assertEquals(2000L, values[1].expireAfter?.toMillis())
    }

    @Test
    fun readMessages_DoesNotErrorIfMessagesAreAddedConcurrentlyWithRead() {
        val message1 = UpstreamMockMessageBook("Song of Ice and Fire", Genre.FANTASY, MockPerson("George", "Martin", 100))
        val message2 = UpstreamMockMessageMovie("Sunshine", Genre.SCIFI, 2007)
        val message3 = UpstreamMockMessageMovie("Matrix", Genre.SCIFI, 1998)

        messageStore.storeMessage(message1, SendPriority.IMMEDIATE, false, false, null, seconds(1))
        messageStore.storeMessage(message2, SendPriority.SOON, true, true, "parcel-group", seconds(2))

        val messageRead = mutableListOf<Boolean>()
        messageStore.readMessages()
                .subscribe {
                    messageRead.add(true)
                    messageStore.storeMessage(message3, SendPriority.SOON, true, true, "parcel-group", seconds(2))
                }
        assertEquals(2, messageRead.size)
    }

    @Test
    fun storeMessage_SavesMessageIfPersistIsTrue() {
        assertEquals(0, sharedPreferences.all.size)

        val message = UpstreamMockMessageMovie("Sunshine", Genre.SCIFI, 2007)
        messageStore.storeMessage(message, SendPriority.IMMEDIATE, true, true, null, seconds(1))
        cpuThread.advanceTimeBy(10, TimeUnit.SECONDS)

        val persistedMessageJson = sharedPreferences.getString(message.messageId, null)
        assertNotNull(persistedMessageJson)
        val persistedMessageAdapter = PersistedUpstreamMessageWrapperJsonAdapter(moshi.moshi)
        val persistedMessage = persistedMessageAdapter.fromJson(persistedMessageJson!!)
        assertEquals(message.messageId, persistedMessage?.messageId)
        assertEquals(message.messageType, persistedMessage?.messageType)
        assertEquals(SendPriority.IMMEDIATE, persistedMessage?.sendPriority)
        assertEquals(moshi.adapter(UpstreamMessage::class.java).toJsonValue(message), persistedMessage?.messageData)
    }

    @Test
    fun storeMessage_MessagesWillHaveAStoredState() {
        val message = UpstreamMockMessageBook("Song of Ice and Fire", Genre.FANTASY, MockPerson("George", "Martin", 100))

        messageStore.storeMessage(message, SendPriority.IMMEDIATE, false, false, "parcel-group", seconds(1))

        val values = messageStore.readMessages()
                .test()
                .values()
        values.sortBy { it.message.messageType }

        assertEquals(1, values.size)
        assertEquals(message, values[0].message)
        assertTrue(values[0].messageState is UpstreamMessageState.Stored)
        assertEquals(null, (values[0].messageState as UpstreamMessageState.Stored).parcelSubGroupKey)
    }

    @Test
    fun storeMessage_EstimatesMessageSize() {
        val message = spyk(UpstreamMockMessageMovie("Sunshine", Genre.SCIFI, 2007))
        val messageSize = moshi.adapter(UpstreamMessage::class.java).toJson(message).length
        messageStore.storeMessage(message, SendPriority.IMMEDIATE, true, true, null, seconds(1))
        val values = messageStore.readMessages()
                .test()
                .values()
        assertEquals(messageSize, values[0].messageSize)
    }
    
    private fun setUpRestoreTestsWithSingleMessage(): StoredUpstreamMessage {
        val json = """
            {
                "type": 50,
                "id": "123",
                "priority": "soon",
                "size": 10,
                "time": 15000,
                "state": {
                    "state": "stored",
                    "parcel_subgroup": "parcel-subgroup"
                },
                "attempts": {
                    "courier1": 4
                },
                "group": "parcel-group",
                "expire": 1000,
                "data": {
                    "title": "Sunshine",
                    "genre": "sci-fi",
                    "year": 2007,
                    "time": 15000
                }
            }
        """

        sharedPreferences.edit().putString("123", json).commit()
        assertEquals(0, messageStore.size)

        messageStore.restoreMessages().subscribeOn(ioThread).subscribeBy()
        ioThread.triggerActions()

        assertEquals(1, messageStore.size)
        val values=  messageStore.readMessages()
                .test()
                .values()
        assertEquals(1, values.size)
        return values[0]
    }

    @Test
    fun restoreMessages_CorrectlyRestoresPersistedMessage() {
        val storedMessage = setUpRestoreTestsWithSingleMessage()

        assertEquals(SendPriority.SOON, storedMessage.sendPriority)
        assertEquals(10, storedMessage.messageSize)
        assertEquals(true, storedMessage.requiresRegistration)
        assertTrue(storedMessage.message is RecoveredUpstreamMessage)
        assertEquals("123", storedMessage.message.messageId)
        assertEquals(50, storedMessage.message.messageType)
        assertEquals(15000, storedMessage.message.time.toMillis())
        assertTrue(storedMessage.messageState is UpstreamMessageState.Stored)
        assertEquals("parcel-subgroup", (storedMessage.messageState as? UpstreamMessageState.Stored)?.parcelSubGroupKey)
        assertEquals(mapOf("courier1" to 4), storedMessage.sendAttempts)
        assertEquals("parcel-group", storedMessage.parcelGroupKey)
        assertEquals(1000L, storedMessage.expireAfter?.toMillis())
    }

    @Test
    fun restoreMessages_GracefullySkipAndRemovePersistedMessagesWithInvalidFormat() {
        val editor = sharedPreferences.edit()
        editor.putString("122", """
            {
                "noMessageType": 50,
                "noMessageId": "123",
                "priority": "soon",
                "size": 10,
                "time": 15000,
                "data": {}
            }
        """)
        editor.putString("123", """
            {
                "type": 50,
                "id": "123",
                "priority": "soon",
                "size": 10,
                "time": 15000,
                "state": {
                    "state": "stored",
                    "parcel_subgroup": "parcel-subgroup"
                },
                "attempts": {},
                "expire": 1000,
                "data": {
                    "title": "Sunshine",
                    "genre": "sci-fi",
                    "year": 2007,
                    "time": 15000
                }
            }
        """)
        editor.putString("124", """ {"incorrectJson}  """)
        editor.commit()

        assertEquals(0, messageStore.size)
        assertEquals(3, sharedPreferences.all.size)

        messageStore.restoreMessages().subscribeOn(cpuThread).subscribeBy()
        cpuThread.advanceTimeBy(10, TimeUnit.SECONDS)

        assertEquals(1, messageStore.size)
        assertEquals(1, sharedPreferences.all.size)
        assertEquals("123", messageStore.readMessages().test().values()[0].message.messageId)
    }

    @Test
    fun restoreMessages_ReturnsHighestPriorityAmongstPersistedMessages() {
        val editor = sharedPreferences.edit()
        editor.putString("122", """
            {
                "type": 50,
                "id": "123",
                "priority": "soon",
                "size": 10,
                "time": 15000,
                "state": {
                    "state": "stored",
                    "parcel_subgroup": "parcel-subgroup"
                },
                "attempts": {},
                "expire": 1000,
                "data": {
                    "title": "Sunshine",
                    "genre": "sci-fi",
                    "year": 2007,
                    "time": 15000
                }
            }
        """)
        editor.putString("123", """
            {
                "type": 50,
                "id": "123",
                "priority": "whenever",
                "size": 10,
                "timestamp": 15001,
                "state": {
                    "state": "stored",
                    "parcel_subgroup": "parcel-subgroup"
                },
                "attempts": {},
                "expire": 1000,
                "data": {
                    "title": "Matrix",
                    "genre": "sci-fi",
                    "year": 1998,
                    "time": 15001
                }
            }
        """)
        editor.commit()

        messageStore.restoreMessages()
                .test()
                .assertValueCount(1)
                .assertValue(SendPriority.SOON)
    }

    @Test
    fun restoreMessages_ReturnsNothingIfAllMessagesAreInvalid() {
        val editor = sharedPreferences.edit()
        editor.putString("122", """
            {
                "type": 50,
                "id": "123",
                "priority": "soon",
                "size": 10
            }
        """)
        editor.putString("123", """
            {
                "type": 50,
                "id": "123",
                "priority": "whenever",
                "size": 10
            }
        """)
        editor.commit()

        messageStore.restoreMessages()
                .test()
                .assertValueCount(0)
                .assertComplete()
    }

    @Test
    fun restoreMessages_ReturnsNothingIfNoMessagesArePersisted() {
        messageStore.restoreMessages()
                .test()
                .assertValueCount(0)
                .assertComplete()
    }

    @Test
    fun multiplePersistingAndRestoringWorksCorrectly() {
        val messageAdapter = moshi.adapter(UpstreamMessage::class.java)
        val message = UpstreamMockMessageMovie("Prospect", Genre.SCIFI, 2018)

        var messageStore = MessageStore(moshi,pusheConfig, context)
        messageStore.storeMessage(message, SendPriority.IMMEDIATE, persist = true, parcelGroupKey = null, expireAfter = null, requiresRegistration = false)
        cpuThread.triggerActions()
        cpuThread.advanceTimeBy(MessageStore.STORE_WRITE_RATE_LIMIT * 2, TimeUnit.MILLISECONDS)

        messageStore = MessageStore(moshi,pusheConfig, context)
        messageStore.restoreMessages().test()
        cpuThread.triggerActions()
        var messages = messageStore.readMessages().test().values()
        assertEquals(1, messages.size)
        assertEquals(messageAdapter.toJsonValue(message), messageAdapter.toJsonValue(messages[0].message))

        messageStore.persistMessage(messages[0], true)
        cpuThread.triggerActions()
        cpuThread.advanceTimeBy(MessageStore.STORE_WRITE_RATE_LIMIT * 2, TimeUnit.MILLISECONDS)

        messageStore = MessageStore(moshi,pusheConfig, context)
        messageStore.restoreMessages().test()
        cpuThread.triggerActions()
        messages = messageStore.readMessages().test().values()
        assertEquals(1, messages.size)
        assertEquals(messageAdapter.toJsonValue(message), messageAdapter.toJsonValue(messages[0].message))
    }
}