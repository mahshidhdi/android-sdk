# Logging

Logging is performed using the `Plog` logging library available in the `utils` module. 

To make a log entry, call one of the available methods on `Plog` based on the desired log level.

```kotlin
Plog.debug(/* params */)
Plog.warn(/* params */)
```

Each log entry takes several parameters (e.g., message, tags, ...). There are typically two ways for providing these parameters, which is shown in the two examples below. 

```kotlin
Plog.info(
    "Tag", 
    "This is the log message",
    "Key1" to "Log Data Value 1",
    "Key 2" to "Log Data Value 2"
)
```

```kotlin
Plog.info.message("This is the log message")
    .withTag("Tag1")
    .withData("Key1", "Log Data Value 1")
    .withData("Key 2", "Log Data Value 2")
    .log() // This is important, the log won't be published until the log() method is called
```


In the remainder of this section we will explain the different parameters a log entry takes and the different ways for providing those parameters.

!!! info "This is not LogCat"
    Plog is separate from android's LogCat but has a log handler which can publish logs to LogCat as well.
     When running in production code, only logs with level `info` or higher will be published to LogCat.
    You could change this by providing the following meta tag in the applicaton manifest:

    ```xml
    <meta-data android:name="hengam_log_level" android:value="debug" />
    ```

## Log Options

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
The log message is a single String describing the event that has occurred which requires logging. Each log entry can only have a single message. All log entries should be given a message. The only case where not providing a message is OK is if the log entry is also given a `Throwable` object in which case the `Throwable`'s message will be used as the message.


```kotlin
Plog.info("This is a message")
```

```kotlin
Plog.info.message("This is a message").log()
```

### Log Tag
A tag is a String which can be used to categorize log entries. Each log entry could be assigned any number of tags. Tags are useful for quickly identifying what a log entry is related to and also for filtering log messages.

!!! info "Yes, any number of tags allowed"
    Unlike the LogCat logging system, which requires exactly one tag per log,
    any number of tags may be used with Plog (including no tags at all). When printing log entries to LogCat, only the first tag of the log entry will be used. If the log entry has no tag, then a default tag will be used for the LogCat log.

```kotlin
Plog.info("Tag1", "This is the log message")
Plog.info("Tag1", "Tag2", "This is the log message")
Plog.info.withTag("Tag1", "Tag2", "Tag3").message("This is the log message").log()
```

!!! tip "Don't get carried away with tags"
    Aim at not making tags too specific and keeping the number of tags limited. Typically, each hengam service module should have one main tag which is included in all log entries inside that module and possibly a couple other tags for it's different main subcomponents.

##### Tag Constants

Every hengam service module should have a `LogTag` object defined in the `Constants.kt` file. All log tags used within the module should be defined as constants in this object. 

```kotlin
// Constants.kt

object LogTag {
    const val T_INIT = "Initialization"
    const val T_REGISTER = "Registration"
    // ...
}
```

### Exceptions and Throwables
Log entries also accept a single `Throwable` parameter. While this could be provided for any log, it makes most sense for the `WARN`, `ERROR`, and `WTF` log levels.

The `Throwable` can be provided in the log method arguments or by calling the `error()` method in the log context. Only a single `Throwable` object is accepted, if it provided multiple times the last one will be used.

```kotlin
try {
    // stuff
} catch(ex: IOException) {
    Plog.error("Tag1", "An error occurred while doing stuff", ex)
    // Or
    Plog.error.message("An error occurred while doing stuff").withError(ex).log()
}
```

### Log Data
Each log entry also accepts an arbitrary amount of extra data in the form of key-value pairs.

Log data key's should be Strings but the values can be of any type. However note, when storing log entries or sending log data over the network, values are typically converted to Strings using their `toString()` method.

The log data can only be provided in the logcontext using the `to` infix function on Strings.

```kotlin
Plog.info("Tag1", "This is a log", "Key1" to "Value1", "Key2" to 1004)
```

```kotlin
Plog.info.message("This is a log")
    .withTag("Tag1")
    .withData("Key1", "Value1")
    .withData("Key2", 1004)
    .log()
```

## Log Handlers
You could subscribe to Plog to be notified whenever a log entry is created by registering a `LogHandler` implementation.

```kotlin
Plog.addHandler(object: LogHandler {
    fun onLog(log: Plogger.LogItem) {
        println(log.message)
        println(log.tags)
        println(log.error)
        println(log.throwable)
        println(log.logData)
    }
})
```

## Aggregating Logs

In certain cases it might be useful to combine multiple logs into a single log. For example suppose we have 
an event that may occur multiple times in a short period and we make an info log every time it occurs.

```kotlin
Plog.info("AwesomeTag", "An awesome event occured", "AwesomeNumber" to awesomeNumber)
```

In the log output we would get something like this

```
AwesomeTag [info] An awesome event occured {AwesomeNumber=10}
AwesomeTag [info] An awesome event occured {AwesomeNumber=2}
AwesomeTag [info] An awesome event occured {AwesomeNumber=142}
BoringTag  [warn] Another boring event also happened in between
AwesomeTag [info] An awesome event occured {AwesomeNumber=56}
AwesomeTag [info] An awesome event occured {AwesomeNumber=22}
AwesomeTag [info] An awesome event occured {AwesomeNumber=17}
AwesomeTag [info] An awesome event occured {AwesomeNumber=19}
```

To avoid cluttering the log output it may sometimes be useful to combine related logs which occur in a short period into
a single log. We can do this with the help the `Plog.aggregate()` method. We will show how by using the `aggregate` method
on the example above:

```kotlin
Plog.info.message("An awesome event occured")
    .withTag("AwesomeTag")
    .withData("AwesomeNumber" to awesomeNumber)
    .aggregate("awesome-event", seconds(2)) {
        message("Wow! ${logs.size} awesome events occured")
        withData("AwesomeNumbers", logs.map { it.logData["AwesomeNumber"] })
    }
    .log()
```

The `aggregate` method takes three arguments. The first argument is the aggregation key and should be a unique string. 
`Plog` will aggregate logs which are made with the same aggregation key into a single log. The second argument is the
aggregation debounce interval. Every time a new log is created with the aggregation key `Plog` will wait for this period, 
if no new log is made with the same aggregation key in this time then the log will be published. If a new log is created in 
this period then the timer will be reset.

The third argument which should be passed is a function which you could use to set the options for the combined 
log. For example you could specify a new message or add new log data for the combined log.

After using log aggregation on the example above, the output would look something like this:

```
BoringTag  [warn] Another boring event also happened in between
AwesomeTag [info] Wow! 7 awesomes events occured {AwesomeNumbers=[10,2,142,56,22,17,19]}
```