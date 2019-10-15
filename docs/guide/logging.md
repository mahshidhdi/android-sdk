# Logging

Logging is performed using the `Plog` logging library available in the `utils` module. 

To make a log entry, call one of the available methods on `Plog` based on the desired log level.

```kotlin
Plog.debug(/* params */)
Plog.warn(/* params */)
```

Each log entry takes several parameters (e.g., message, tags, ...). There are typically two ways for providing these parameters, through the method call arguments or through the log context. 

The method parameters method is self explanatory.
```kotlin
Plog.info("This is a log")
```

The log context is a lambda parameter which you can provide as the final argument to any of the logging methods. Through this lambda parameter you are able to make special function calls for manipulating the parameters of the log entry.

```kotlin
Plog.info {
    tag("ThisIsATag")
    message("This is the log message")
}
```

In the remainder of this section we will explain the different parameters a log entry takes and the different ways for providing those parameters.

!!! info "This is not LogCat"
    Plog is used for Hengam's internal logging. When running in production code, no logs made from the Plog system will be shown to the user. However, this does not mean no logs _should_ be shown to the user, you should manage log messages which will be shown to the user using LogCat directly.

### Log Level
There are six log levels available. The log level is defined based on the method used for logging (e.g. `Plof.info()`, `Plog.debug()`, etc.) and cannot be changed afterwards.

The following levels are available (ordered from least to most urgent)

**TRACE**

The `TRACE` level should be used for log messages which are used to track the progress of an event or the different steps of a function for debugging purposes. These log entries typically don't provide meaningful information outside of the context of debugging a particular situation.

**DEBUG**

The `DEBUG` level should be used for log entries which provide meaningful information about a situation but are typically too detailed for them to be useful all the time.

**INFO**

The `INFO` level should be used for log entries which provide useful and important information about expected events happening throughout Hengam's execution.

**WARN**

The `WARN` level should be used for _undesired_ events which are expected to happen from time to time but indicate that a manageable problem has occurred within the system. Log entries with the `WARN` level will **not** be reported to crash reporting services.

**ERROR**

The `ERROR` level should be used when an unexpected error has occured within the system indicating that it was caused by a problem which should be fixed or handled more apropiately. Events resulting in this log level are typically still manageable and will not result in total loss of Hengam's functionality. Log entries with the `ERROR` level will be reported to crash reporting services.

**WTF**

The `WTF` level should be used in two cases. 

1. When an error or situation occurs which absolutely should not happen and interferes with the basic operation of Hengam
2. When an unhandled exception is caught by the `ExceptionCatcher`, meaning an unexpected error occurred and it was not handled and logged in the appropiate place.

Log entries with the `WTF` level will be reported to crash reporting services.


### Log Message
The log message is a single String describing the event that has occurred which requires logging. The log message could be provided either as the first argument of the log method or using the `message()` method in the log context.

```kotlin
Plog.info("This is a message")
```

```kotlin
Plog.info { 
    message("This is a message")
}
```

Each log entry can only have a single message. If the message is provided both as an argument and in the context, the message in the context will be used. If multiple messages are provided in the context, the last one will be used.

All log entries should be given a message. The only case where not providing a message is OK is if the log entry is also given a `Throwable` object in which case the `Throwable`'s message will be used as the message.


### Log Tag
A tag is a String which can be used to categorize log entries. Each log entry could be assigned any number of tags. Tags are useful for quickly identifying what a log entry is related to and also for filtering log messages.

!!! info "Yes, any number of tags allowed"
    Unlike the LogCat logging system, which requires exactly one tag per log,
    any number of tags may be used with Plog (including no tags at all). When printing log entries to LogCat, only the first tag of the log entry will be used. If the log entry has no tag, then a default tag will be used for the LogCat log.

There are two ways for providing tags for a log entry. The first is by using the `tag()` method in the log context. The `tag()` method accepts multiple tags and can also be called multiple times.

```kotlin
Plog.info("This is a log") {
    tag("Wow", "Such", "Log")
    tag("So", "Cool")
}
```

The second method is by using the index `[]` operator on the `Plog` object itself. Again, multiple tags can be provided this way.

```kotlin
Plog["Tag1"].info("This log has tag TAG1")

Plog["Tag1", "Tag2"].info("This log has two tags")
```

The two methods can be mixed and matched, but it's best to use only one method for each log entry.

!!! tip "Don't get carried away with tags"
    Aim at not making tags too specific and keeping the number of tags limited. Typically, each hengam service module should have one main tag which is included in all log entries inside that module and possibly a couple other tags for it's different main subcomponents.

#### Tag Constants

Every hengam service module should have a `LogTag` object defined in the `Constants.kt` file. All log tags used within the module should be defined as constants in this object. 

```kotlin
// Constants.kt

object LogTag {
    const val T_INIT = "Initialization"
    const val T_REGISTER = "Registration"
    // ...
}
```

!!! tip "Coding Style"
    While there is no rule for the tag's value itself, the tag constant name should be prefixed with `T_` and should be kept as short as possible.

    When using tag constants in making log entries, use static imports for importing tag constants.

    ```kotlin
    // Use this
    Plog[T_INIT].info("...")

    // And not this
    Plog[LogTag.T_INIT].info("...")
    ```

### Exceptions and Throwables
Log entries also accept a single `Throwable` parameter. While this could be provided for any log, it makes most sense for the `WARN`, `ERROR`, and `WTF` log levels.

The `Throwable` can be provided in the log method arguments or by calling the `error()` method in the log context. Only a single `Throwable` object is accepted, if it provided multiple times the last one will be used.

```kotlin
try {
    // stuff
} catch(ex: IOException) {
    Plog.error("An error occurred while doing stuff", ex)
    // Or
    Plog.error("An error occurred while doing stuff") {
        error(ex)
    }
}
```

### Log Data
Each log entry also accepts an arbitrary amount of extra data in the form of key-value pairs.

Log data key's should be Strings but the values can be of any type. However note, when storing log entries or sending log data over the network, values are typically converted to Strings using their `toString()` method.

The log data can only be provided in the logcontext using the `to` infix function on Strings.

```kotlin
Plog.info("This is a log") {
    "Key1" to "Value1"
    "Key2" to 1004
    tag("AWildTagAppeared")
    "More" to "Data"
}
```

## Log Handlers
You could subscribe to Plog to be notified whenever a log entry is created by registering a `LogHandler` implementation.

```kotlin
Plog.addHandler(object: LogHandler {
    fun onLog(log: Log) {
        println(log.message)
        println(log.tags)
        println(log.error)
        println(log.throwable)
        println(log.logData)
    }
})
```

When running in debug mode, a default log handler is added to `Plog` which prints all logs to LogCat.