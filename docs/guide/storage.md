# Storing Data

The `HengamStorage` class provides utilities to help you persist data in the SDK. It uses
the `SharedPreferences` for storing the data but provides a layer of abstraction over it to
simplify storing different data types.


In order to use these utilities you will need to declare the `HengamStorage` class as as a dependency 
of your own class. Dagger will provide your class with an instance of `HengamStorage`.

```kotlin
class AwesomeClass @Inject constructor (
    private val hengamStorage: HengamStorage
)
```

## Storing primitive data types

The `HengamStorage` class contains the following methods for storing primitive data types:

```kotlin
HengamStorage.storedString(key: String, default: String): PerisistedItem<String>
HengamStorage.storedBoolean(key: String, default: Boolean): PerisistedItem<Boolean>
HengamStorage.storedInt(key: String, default: Int): PerisistedItem<Int>
HengamStorage.storedLong(key: String, default: Long): PerisistedItem<Long>
HengamStorage.storedFloat(key: String, default: Float): PerisistedItem<Float>
```

To use these methods you will need to pass in the key which the data will be stored under (i.e., in the `SharedPreferences`) 
and also the default value to use in case the key doesn't exist. The methods will return an instance of the 
`PersistedItem` class which you could use to store or retrieve the data with (using the classes `get` and `set` methods).

In the usual case, you will want to get and keep the `PersistedItem` instance as a class variable and use the same instance
in the different methods throughout your class.

```kotlin
class AwesomeClass @Inject constructor (private val hengamStorage: HengamStorage) {

    private val hasAwesomeStuffHappened = hengamStorage.storedBoolean("has_awesome_stuff_happened", false)

    fun doAwesomeStuff() {
        if (hasAwesomeStuffHappened.get()) {
            return
        } else {
            hasAwesomeStuffHappened.set(true)
        }        
    }
}
```

The `PersistedItem` class also supports kotlin delegation. You could also use the delegation syntax 
to make things prettier:

```kotlin
class AwesomeClass @Inject constructor (private val hengamStorage: HengamStorage) {

    private val hasAwesomeStuffHappened by hengamStorage.storedBoolean("has_awesome_stuff_happened", false)

    fun doAwesomeStuff() {
        if (hasAwesomeStuffHappened) {
            return
        } else {
            hasAwesomeStuffHappened = true
        }        
    }
}
```

Notice how instead of the `=` sign we use the `by` keyword. By doing this we can treat the `hasAwesomeStuffHappened`
like a normal `Boolean`. Any changes made to this `Boolean` will be persisted to the storage.

## Storing data structures

The `HengamStorage` class also contains the following methods for storing data structures.

```kotlin
HengamStorage.createdStoredList(key: String, valueType: Class<T>, jsonAdapter: Any? = null, expirationTime: Time? = null): PersistedList<T>
HengamStorage.createdStoredMap(key: String, valueType: Class<T>, jsonAdapter: Any? = null, expirationTime: Time? = null): PersistedMap<T>
HengamStorage.createdStoredSet(key: String, valueType: Class<T>, jsonAdapter: Any? = null, expirationTime: Time? = null): PersistedSet<T>
```

To use these methods you will need to provide the key under which the data structure will be stored
 (i.e., in the `SharedPreferences`) and the class of the objects which you will be storing in the data structure.

If the objects you are storing are not primitive types you will also need to pass in a Moshi Json adapter for 
serializing and deserializing the objects. Note, this is not needed if the Json adapter has already been added to
the project-wide moshi instance.

You can also pass in an optional expiration time which will cause items stored in the data structure to be removed 
if it has been stored (without being updated) for longer than this period.

These methods will return an instance of `PersistedList`, `PersistedMap` or `PersistedSet` based on the method called.
These classes are subclasses of the original data structure classes, however any updates made to the data structure will 
be persisted to the storage.

Note, that the `PersistedMap` always has a key type of `String`. The type you specify in the `createStoredMap` method
determines the type of the values stored in the map. An example usage of this is shown below:

```kotlin
class AwesomeClass @Inject constructor (private val hengamStorage: HengamStorage) {

    private val awesomeMap = hengamStorage.createStoredMap(
        "awesome_map", 
        AwesomeThing::class.java,
        AwesomeThingJsonAdapter()
    )

    fun addThingToMap(key: String, value: AwesomeThing) {
        awesomeMap[key] = value 
    }
}

class AwesomeThing (
    awsomeNumber: Int
    awesomeString: String
)

class AwesomeThingAdapter {
    @FromJson
    fun fromJson(json: Map<String, Any>) 
        = AwesomeThing(json["awesome_number"], json["awesome_string"])

    @ToJson
    fun toJson(thing: Awesomething) = mapOf(
        "awesome_number" to thing.awesomeNumber,
        "awesome_string" to thing.awesomeString
    )
}
```

!!! warning "Changes made directly to the objects will not be stored automatically"
    Only changes made to the structure of the data structure will be persisted automatically.
    In order to save changes made directly to the actual objects stored you will need to call
    the `save()` method. 

    See the example below:

    ```kotlin
    class AwesomeClass @Inject constructor (private val hengamStorage: HengamStorage) {

        private val awesomeMap = hengamStorage.createStoredMap(
            "awesome_map", 
            AwesomeThing::class.java,
            AwesomeThingJsonAdapter()
        )

        fun updateThingInMap(key: String, awesomeNumber: Int) {
            awesomeMap[key]?.awesomeNumber = awesomeNumber
            awesomeMap.save() // Must call this to persist changes in the stored object
        }
    }
    ```

!!! warning "Storing primitive types in data structures"
    If you want to store a primitive data type in your data structures you should use `::class.javaObjectType` 
    (and not `::class.java`) when passing the type to the `createStoredX()` methods.

    ```kotlin
    val awesomeMap = hengamStorage.createStoredMap("awesome_map", Int::class.javaObjectType)
    ```
