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
) : TypedUpstreamMessage<BookMessage>(20, { /* Adapter()  */ })
```

The `TypedUpstreamMessage` takes a type parameter which should always be the type of your message class. Also, the `TypedUpstreamMessage` constructor takes two parameters. The first parameter is the message type which should be unique among all upstream message types. The second parameter should be a function which takes a `Moshi` instance as in input and returns a `JsonAdapter` capable of serializing your upstream message into JSON format.

!!! tip "Coding Style"
    Avoid using integer literals as the message types directly in your message classes like we have done in the example above. Instead, store all upstream message types in `MessageTypes.Upstream` and refer to the type defined there, as shown in the example below.

!!! info "Auto timestamps"
    All upstream messages will automatically be given a timestamp field (through the superclass). There is no need to manually include one in your own messages.

To make life easier and avoid having to write your own adapters, use the Moshi generated adapters for your upstream messages by adding the `@JsonClass(generateAdapter=true)` annotation to your message class. 
By adding this annotation, Moshi will automatically generate a adapter class for you (you may need to rebuild the project). In the case of our example the class will be named `BookMessageJsonAdapter`. Provide this adapter to the second parameter of the `TypedUpstreamMessage` constructor like the example below:


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

Also note in the example above we used the `@Json(name="field_name")` annotation to specify what we want the field names to be in the resulting JSON string.

!!! hint "Coding Style"
    Use `snake_case` for field names in JSON. Also, for consistency add the `@Json(name="")` annotation to all of the message fields even if the JSON name is not different than it's member name.

### Create Upstream Message Instance
Now that you have your message structure defined in your upstream message class, you need to create an instance of the class and provide the message parameters for that instance.

```kotlin
val message = BookMessage(
    title = "2001 Space Odyssey",
    author = "Arthur C. Clark"
)

```

### Send Upstream Message
To send the message we will use the `PostOffice.sendMessage()` method and pass the created message to it. 

```kotlin
postOffice.sendMessage(message)
```

After calling the `postOffice.sendMessage()` method, the message will be scheduled to be sent. The `PostOffice` guarantees delivery (except for when `persistAcrossRuns` is `false`, see below), so you do not need to handle retries on sending failures.

!!! info "Use Dagger for dependency injection"
    To send messages you need an instance of the `PostOffice` class. See the section on Dagger and dependency injection to see how you could have access to a `PostOffice` instance.

The `sendMessage` method accepts further arguments for configuring how the message will be sent. See [Send Options](#send-options).

## Send Options
The `sendMessage` method accepts additional arguments after the message. The arguments and their default values are shown below 

```kotlin
postOffice.sendMessage(
    message = message,
    sendPriority = SendPriority.SOON,
    persistAcrossRuns = true
    requiresRegistration: Boolean = true,
    parcelGroupKey: String? = null,
    expireAfter: Time? = null
)
```

### Send Priority `(sendPriority)`
When sending a message using the `PostOffice`, you could provide a `SendPriority` option which lets you define how soon you want the message to be sent. If not provided, the default send priority for messages is `SendPriority.SOON`.

!!! caution "Send Priority not Send Delay"
    By using a relaxed send priority you notify the `PostOffice` that *it is ok* if the message is sent later and thus enable merging multiple messages into a single parcel. The send priority should be used for determining the upper bound of when you want the message to be sent and should **not** be used for setting delays on message sending.

The available send priorities are:

#### SendPriority.IMMEDIATE
By using this priority you notify that you want the message to be sent immediately.

#### SendPriority.SOON
By using this priority you notify that you are ok with a few **seconds** delay in sending the message.

#### SendPriority.LATE
By using this priority you notify that you are ok with a few **minutes** delay in sending the message.

#### SendPriority.BUFFER
By using this priority you notify that you are ok if the `PostOffice` holds off sending the message until enough
messages are available to create a full-sized parcel.

#### SendPriority.WHENEVER
By using this priority you notify that you don't care when the message is sent. Messages using this priority will not be sent until another message with a higher priority arrives.

### Persistance `(persistAcrossRuns)`
In several cases a message which is scheduled to be sent with the `PostOffice.sendMessage()` method may fail to be sent before the application closes. For example, if a message is scheduled with `SenderPriority.WHENEVER` and no other message with higher priority arrives in time or if message sending fails due to bad network connectivity. In such cases you may want the message to be scheduled for sending again once the application starts. 

The `persistAcrossRuns` determines whether this should happen. If `persistAcrossRuns` is `true` then the message will be stored and if it is not sent before the application exits, it will be restored once the application restarts.


!!! example "Example of non-persistance"
    An example of when we don't want the message to be persistant is the registration message. On application start, Hengam checks whether registration has been completed and if it has not will send a registration message. 
    
    Suppose the message cannot successfully be sent before the application closes, if registration message was persistant then on the next application start the message will be restored and sent. However, Hengam will also send another registration message because registration is still not complete. 
    
    To avoid having mulitple in flight registration messages, we will send the registration messages with `persistAcrossRuns` set to `false`.


### Requires Registration `(requiresRegistration)`
If the `requiresRegistration` parameter is true the message will only be sent if Hengam
registration has successfully been performed. If the SDK has not
been registered yet, the message will be queued and sent when
registration is successful.

### Grouping Messages into Parcels `(parcelGroupKey)`
In some cases you may want to avoid certain messages from being grouped together in the same parcel. You can use the
`parcelGroupKey` to achieve this. Messages with have been sent with different `parcelGroupKey` values will not be sent
in the same parcel.


### Expiring Messages `(expireAfter)`
Upstream messages expire if they are not successfully sent after a certain time period. The default expiration time is one week but a custom expiration time can be set with the `expireAfter` parameter. Note, expiration times are calculated from the moment the message instance was created and **not** when the `sendMessage()` method was called.

## Message Mixins

In some cases different upstream messages may have similar fields. Instead of duplicating the code for creating these messages 
you could use message mixins. 

For example suppose we have two upstream messages `AwsesomeMessage` and `BoringMessage` like below:

```kotlin
class AwesomeMessage(
   @Json(name="awesome_field") awesomeField: String
   @Json(name="location_lat") locationLat: String
   @Json(name="AwesomeMessage") locationLong: String
) : TypedUpstreamMessage<BookMessage>(
    MessageType.Upstream.AWESOME, 
    { AwesomeMessageJsonAdapter(it) }
)

class BoringMessage(
   @Json(name="boring_field") boringField: String
   @Json(name="location_lat") locationLat: String
   @Json(name="location_long") locationLong: String
) : TypedUpstreamMessage<BoringMessage>(
    MessageType.Upstream.BORING, 
    { BoringMessageJsonAdapter(it) }
)

```

As you can see both messages include fields for sending the user location. We can move these fields into a message mixin. A message mixin is a class that extends the `MessageMixin` class and overrides the `collectMixinData` method. The method should 
return a `Map` object. The contents of this `Map` will be added to the messages which use this mixin.

For example a mixin for adding location data to messages could look like this:

```kotlin
class LocationMixin : MessageMixin() {
    override fun collectMixinData(): Single<Map<String, Any?>> {
        return getLocation()
            .map { location -> 
                mapOf(
                    "location_lat" to location.lattitude,
                    "location_long" to location.longitude
                )
            }
    }
}
```

Now we can define the upstream messages to use the mixin like below:

```kotlin
class AwesomeMessage(
   @Json(name="awesome_field") awesomeField: String
) : TypedUpstreamMessage<BookMessage>(
    MessageType.Upstream.AWESOME, 
    { AwesomeMessageJsonAdapter(it) },
    listOf(LocationMixin())
)

class BoringMessage(
   @Json(name="boring_field") boringField: String
) : TypedUpstreamMessage<BoringMessage>(
    MessageType.Upstream.BORING, 
    { BoringMessageJsonAdapter(it) },
    listOf(LocationMixin())
)
```

Even though the messages don't directly include the location fields, the location data will be added to 
them through the `LocationMixin`