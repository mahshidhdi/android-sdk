# Sending Messages

## Upstream Messaging Basics
In order to send an upstream message, you need to create a class which defines the structure of your message, create an instance of that class and use `PostManager.sendMessage()` to send the message instance.

### Create Upstream Message Class
For every upstream message type which you want to send, you need to create a seperate subclass of the `TypedUpstreamMessage` class and place it in the `messages.upstream` package of your module. 

Include all parameters of your message in the primary constructor of the message class and use default values for parameters which are optional. Upstream message classes typically have an empty body.


```kotlin
class BookMessage(
    val title: String,
    val author: String,
    val publishYear: Int? = null
    val timestamp: Int = System.currentMillis()
) : TypedUpstreamMessage<BookMessage>(20, { /* Adapter()  */ })
```

The `TypedUpstreamMessage` takes a type parameter which should always be the type of your message class. Also, the `TypedUpstreamMessage` constructor takes two parameters. The first parameter is the message type which should be unique among all upstream messages. The second parameter should be a function which takes a `Moshi` instance as in input and returns a `JsonAdapter` capable of serializing your upstream message into JSON format.

!!! tip "Coding Style"
    Avoid using integer literals as the message types directly in your message classes like we have done in the example above. Instead, store all upstream message types in `MessageTypes.Upstream` and refer to the type defined there, as shown in the example below.

!!! info "Auto timestamps"
    In the `BookMessage` example class we have a `timestamp` which will be assigned a default value with the current time when the message is created. Use this practice for adding timestamps to your messages.

To make life easier and avoid having to write your own adapters, use the Moshi generated adapters for your upstream messages. To do so, add the `@JsonClass(generateAdapter=true)` annotation to your message class. Also, if you want the name of the serialized field of one of your message parameters to be different than it's name in the class, use the `@Json(name="field_name")` annotation for that field. See the example below.


After adding the `JsonClass(generateAdapter=true)` annotation to your class build the project and the Moshi library should generate a adapter class for you. In the case of our example the class will be named `BookMessageJsonAdapter`. Provide this adapter to the second parameter of the `TypedUpstreamMessage` constructor. The generated adapter's constructor will require a Moshi instance, use the Moshi instance given to you in the lambda for this purpose.


```kotlin
@JsonClass(generateAdapter=true)
class BookMessage(
    @Json(name="title") val title: String,
    @Json(name="author") val author: String,
    @Json(name="publish_date") val publishDate: Int? = null
) : TypedUpstreamMessage<BookMessage>(
    MessageType.Upstream.BOOK, 
    { BookMessageJsonAdapter(it) }
)
```

!!! hint "Coding Style"
    Use `snake_case` for field names in JSON. Also, for consistency add the `@Json(name="")` annotation to all of the message fields even if the JSON name is not different than it's member name.

If after building your module you still don't see the generated `JsonAdapter` for your message, see the section on setting up Moshi for more details.

### Create Upstream Message Instance
Now that you have your message structure defined in your upstream message class, you need to create an instance of the class and provide the message parameters for that instance.

```kotlin
val message = BookMessage(
    title = "2001 Space Odyssey",
    author = "Arthur C. Clark"
)

```

### Send Upstream Message
To send the message we will use the `PostOffice.sendMessage()` method and pass are created message to it. 

```kotlin
postOffice.sendMessage(message)
```

The message will now be scheduled to be sent to the server. The `PostOffice` guarantees delivery (except for when `persistAcrossRuns` is `false`, see below), so you do not need to handle retries on sending failures.

!!! info "Use Dagger for dependency injection"
    To send messages you need an instance of the `PostOffice` class. See the section on Dagger and dependency injection to see how you could have access to a `PostOffice` instance.

The `sendMessage` method accepts further arguments for configuring how the message will be sent. See [Send Options](#send-options).

## Send Options
The `sendMessage` method accepts two additional arguments after the message. The arguments and their default values are shown below 

```kotlin
postOffice.sendMessage(
    message = message,
    sendPriority = SendPriority.SOON,
    persistAcrossRuns = true
)
```

### Send Priority
When sending a message using the `PostOffice`, you could provide a `SendPriority` option which lets you define how soon you want the message to be sent. If not provided, the default send priority for messages is `SendPriority.SOON`.

!!! caution "Send Priority not Send Delay"
    By using a relaxed send priority you notify the `PostOffice` that it is ok if the message is sent later than sooner and thus enable merging multiple messages into a single parcel. The send priority should be used for determining the upper bound of when you want the message to be sent and should **not** be used for setting delays on message sending.

The available send priorities are:

#### SendPriority.IMMEDIATE
By using this priority you notify that you want the message to be sent immediately.

#### SendPriority.SOON
By using this priority you notify that you are ok with a few seconds delay in sending the message.

#### SendPriority.WHENEVER
By using this priority you notify that you don't care when the message is sent. Messages using this priority will not be sent until another message with a higher priority arrives.

### Persistance
In several cases a message which is scheduled to be sent with the `PostOffice.sendMessage()` method may not be sent before the application closes. For example, if a message is scheduled with `SenderPriority.WHENEVER` and no other message with higher priority arrives in time or if message sending fails due to bad network connectivity. In such cases you may want the message to be scheduled for sending again once the application starts. 

The `persistAcrossRuns` determines whether this should happen. If `persistAcrossRuns` is `true` then the message will be stored and if it is not sent before the application exits, it will be restored once the application restarts.


!!! example "Example of non-persistance"
    An example of when we don't want the message to be persistant is the registration message. On application start, Hengam checks whether registration has been completed and if it has not will send a registration message. 
    
    Suppose the message cannot successfully be sent before the application closes, if registration message was persistant then on the next application start the message will be restored and sent. However, Hengam will also send another registration message because registration is still not complete. 
    
    To avoid having mulitple in flight registration messages, we will send the registration messages with `persistAcrossRuns` set to `false`.


## Observing Message Events
Once a message is created it will go through three states defiend by the `UpstreamMessageState` enum class.

- **CREATED**: The message has been created
- **PENDING**: The message has been given to the `PostOffice` for sending
- **SENT**: The message has been successfully sent

The state of the message is accessable as a `Observable<UpstreamMessageState>` object through the `UpstreamMessage.state` property. You can observe the state changes of the message by subscribing to this `Observable`.

```kotlin
val message = BookMessage(
    // ...
)

message.state.subscribe {
    when(it) {
        UpstreamSenderState.CREATED -> {}
        UpstreamSenderState.PENDING -> { Log.i("Message has been queued for sending") }
        UpstreamSenderState.SENT -> { Log.i("Message has been sent") }
    }
}
```

All subscriptions created on a message state will automatically be disposed once the message has been sent so there is no need to manually dispose of subcscriptions.

!!! caution "Message state across application runs"
    Once you subscribe to a message's state, you will only receive state changes which occur in the same application run as when the message was created.
    
    If the application closes before the message has successfully been sent, you will no longer receive state changes for that particular message in the application's next run even if the message was persitant.
