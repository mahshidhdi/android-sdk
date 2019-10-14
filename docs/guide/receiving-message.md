# Receiving Messages


There are two ways for receiving downstream messages, receiving raw messages or parsed messages. Parsed messages are good for when your message has a defined structure and raw messages are good for when you don't know the structure of the message beforehand. In most cases you will probably want to use parsed messages.


## Receiving Raw Messages
Raw messages should be used in cases where your message type does not have a predefined structure and can take any value. Try to avoid using raw messages if possible and use [Parsed Messages](#receiving-parsed-messages) instead.

Receiving raw messages is super simple. Just use the `PostOffice.mailBox(messageType, handler)` method and pass in the message type which you want to receive along with the a lambda function. The lambda function will be called with a `RawDownstreamMessage` every time a message with this type is received.

```kotlin
postOffice.mailBox(messageType) {
    // it: RawDownstreamMessage
    println("Receive new raw message $it")
}
```

Use the `RawDownstreamMessage.rawData` property to gain access to the message data. The `rawData` property is of type `Any?`. Depending on the contents of the message it could be cast to different values:

```kotlin
postOffice.mailBox(messageType) { message ->
    // message: RawDownstreamMessage
    when(message.rawData) {
        is Map<*,*> -> println("Received Map data $it")
        is List<*> -> println("Received List data $it")
        is String -> println("Received String $it")
        // ...
    }
}
```

## Receiving Parsed Messages
With parsed messages, you can define a structure for your message by defining separate classes for your different message types. When a message of your message type is received by the `PostOffice`, it will convert the message data to an instance of your message class.


### 1. Creating a Downstream Message Class
Every parsed downstream message type requires a separate message class which defines it's structure. The message fields should be define as parameters in the class constructor. Optional fields of the message should be given a default value. Any parameter which is not given a default value is required and the message will not be built successfully if it is not given.


```kotlin
class NotificationMessage(
    val title: String,
    val content: String,
    val imageUrl: String? = null
)
```

!!! info "No super class"
    A downstream message class does not need to extend any superclass.


In order to be able to deserialize the downstream message from JSON strings we need to build a `JsonAdapter` for our message type. We will use Moshi's code generated adapters for this purpose.

Annotate the downstream message with the `@JsonClass(generateAdapter=true)` annotation to tell Moshi to generate a `JsonAdapter` for our message class. Also, annotate the message parameters with `@Json` and provide the JSON field names.

```kotlin
@JsonClass(generateAdapter=true)
class NotificationMessage(
    @Json(name="title") val title: String,
    @Json(name="content") val content: String,
    @Json(name="image") val imageUrl: String? = null
) { companion object }
```

!!! hint "Coding Style"
    Include the `@Json` annotation for all fields even if the JSON name is not different from the class field name.

Rebuild the project after adding the `@JsonClass(generateAdapter=true)` annotation. Moshi should build a `JsonAdapter` for your message class. In the case of our example, the `JsonAdapter` name will be `NotificationMessageJsonAdapter`. If you add a _companion object_ to your class like the example above, the `JsonAdapter` will also be obtainable by calling `jsonAdapter()` method on your class (e.g, `NotificationMessage.jsonAdapter(moshi)`)

### 2. Creating Parser
The next step is to create an instance of `DownstreamMessageParser` which can parse raw messages of our message type into instances of our message class. We will do so by creating a singleton object which extends `DownstreamMessageParser`.
The `DownstreamMessageParser` is a typed class, provide the new message class (`NotificationMessage` in our example) as it's type.
The `DownstreamMessageParser` constructor takes two arguments, the message type code of our message and a function which takes a Moshi instance and returns a `JsonAdapter` capabale of deserializing a message of our type.


```kotlin
object Parser : DownstreamMessageParser<NotificationMessage>(
    MessageType.Downstream.NOTIFICATION,
    { NotificationMessage.jsonAdapter(it) }
)
```

!!! hint "Coding Style"
    Do not provide message type codes as integer literals, instead define the message types as `const` variables in `MessageType.Downstream`.


Place the `Parser` inside the body of the message class. The final code of our new downstream message class will look like this:


```kotlin
@JsonClass(generateAdapter=true)
class NotificationMessage(
    @Json(name="title") val title: String,
    @Json(name="content") val content: String,
    @Json(name="image") val imageUrl: String? = null
) {
    object Parser : DownstreamMessageParser<NotificationMessage>(
        MessageType.Downstream.NOTIFICATION,
        { NotificationMessage,jsonAdapter(it) }
    )

    companion object
}
```

### 3. Receiving Messages from PostOffice
To receive messages of our new downstream message type all we need to do is pass an instance of the `Parser` to `PostOffice.mailBox()`. The method also accepts a lambda argument which will be called with instances of our defined message class when a message with the specified type is received.
```kotlin

postOffice.mailBox(NotificationMessage.Parser) {
    // it: NotificationMessage
    Plog.info("New notification message received $it")
}
```

## Response Messages

In some situations, after sending an upstream message to the server we expect the server to reply with aUpstream message letting us know whether the request was handled successfully or not. We have a special downstUpstreamream message class for handling these types of _confirmation_ messages called `RespoUpstreamnseMessage`. 

Response messages received by the server have the same message type code as the upstream message which triggered the response. To handle response messages for a particular upstream message, create a `ResponseMessage.Parser` instance and provide the upstream message type in it's constructor.

```kotlin
postOffice.mailBox(ResponseMessage.Parser(messageType)) {
    // it: ResponseMessage
    println("Response received: $it")
}
```

A `ResponseMessage` instance has two properties. A `status` property which takes one of the values `ResponseMessage.Status.SUCCESS`, `ResponseMessage.Status.ERROR` or `ResponseMessage.Status.NONE` and an `error` property which may contain an error message.

!!! example "Response message example"
    An example of where response messages are used is for registration. After sending an upstream registration message to the server we expect to receive a response confirming that the device has been registered.

    ```kotlin
    // Send registration request
    val registrationMessage = RegistrationMessage(
        // ...
    )
    postOffice.sendMessage(registrationMessage)    
    ```

    ```kotlin
    // Receive response
    postOffice.mailBox(ResponseMessge.Parser(Message.Upstream.REGISTRATION)) {
        when(it.status) {
            ResponseMessge.Status.SUCCESS -> println("Registration Succesful")
            else -> { println("Registration failed" }
        }
    }

    ```

## Downstream Message Identifers

All downstream messages have an identifier provided with a `message_id` field in the message JSON. While it isn't mandatory to include this field in your message class, if you want to have access to the message identifier you should do so.

```kotlin
class NotificationMessage(
    @Json(name="message_id") val messageId: String,
    @Json(name="title") val title: String,
    // ...
)
```

!!! important "Message Ids are not unique! They're kinda unique."
    Message identifiers are **not** unique among messages which are received in the same _parcel_ (See [Parcel](parcel) for more information on the different between messages and parcels). Rather, they are unique among all messages of a particular message type. 
    
    You should not use the message id as a way to uniquely identify the message among all downstream messages, e.g., for storing downstream messages. If you wish to do so, combine the message type and message id to obtain a unique identifier.
    
     An example use case of the message id is when you want to refer to a previously received message in a new upstream message. For example, when sending a notification clicked upstream message to notify the server that a notification has been clicked, you may want to refer to the downstream notification message which this notification was created for. You could do so by sending the received downstream message id in your upstream message.


## Where to register message handlers?

Downstream messages are received by listening for messages using the `PostOffice.mailBox()` method. While it is technically correct to call the `mailBox()` function anywhere in a within a service module, it's best to keep all calls to this function in the same place. This would allow us to easily view all message types handled by a service module in one glance.

For this purpose, you should create a `MessageDispatcher` class in the `messages` package of your service module. This class should contain a  `listenForMessages()` method and inside you should place your calls to `PostOffice.mailBox()` for the different message types which you want to handle.
However, do not place any message handling logic in the `MessageDispatcher` class. Instead, the `MessageDispatcher` should listen for different messages and call the appropiate methods in other classes for handling these messages.


A sample `MessageDispatcher` class would look similar to this:

```kotlin
class MessageDispatcher @Inject constructor(
    private val postOffice: PostOffice,
    private val awesomeHandlerA: AwesomeMessageHandlerA
    private val awesomeHandlerB: AwesomeMessageHandlerB
) {
    fun listenForMessages() {
        /* Handle Message A */
        postOffice.mailBox(AwesomeMessageA.Parser) {
            awesomeHandlerA.handleMessage(it)
        }

        /* Handle Message B */
        postOffice.mailBox(AwesomeMessageB.Parser) {
            awesomeHandlerB.handleMessage(it)   
        }

        // ...
    }
}
```

## When to register message handlers?

Downstream messages are not stored or replayed. This means that if a downstream message is received before you have registered a handler using the `PostOffice.mailBox()` method, then you will not receive the message even if you register the handler later. Typically, you will want to register your handlers by calling the `MessageDispatcher.listenForMessages()` method on the very start of the application. The appropiate place for doing so is in the [pre-initialization](/guide/initialization/#pre-initialization) step.
