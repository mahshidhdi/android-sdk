# Json Serialization

We the Moshi library for serializing objects into JSON. This section describes how we use Moshi but it assumes
you are already familiar with the library. If not,you will want to take a quick look at the library documentations before continuing. 

The SDK provides a wrapper around the `Moshi` class called `HengamMoshi`.
You can gain access to a `HengamMoshi` instance by declaring it as a dependency in your class constructor.
The Dagger framework will provide you with an instance of the class.

```kotlin
class AwesomeStuff @Inject constructor(private val hengamMoshi: HengamMoshi) {
    // ...
}
```

## Using HengamMoshi

Similar to the original `Moshi` class, `HengamMoshi` has an `adapter()` method which you could use
to obtain a JSON adapter for a particilar type.

```kotlin
class AwesomeStuff @Inject constructor(private val hengamMoshi: HengamMoshi) {
    fun doAwesomeStuff(awesomeThing: AwesomeThing) {
        val jsonAdapter: JsonAdapter<AwesomeThing>  
            = hengamMoshi.adapter(AwesomeThing::class.java)

        println(jsonAdapter.toJson(awesomeThing))
    }
}
```

However, for the code above to work we will need to have already registered a `JsonAdapter` 
for the `AwesomeThing` class.


## Extending HengamMoshi

Internally, the `HengamMoshi` class keeps an instance of the `Moshi` class. Initially, this 
instance doesn't have any adapters registered. However, the `HengamMoshi` class provides a way
for you to update the `Moshi` instance to a new instance with your `JsonAdapter`s registered.

You can do this by calling the `HengamMoshi.enhance()` method and passing a function which accepts 
a `Moshi.Builder` instance as it's only parameter. Use this `Moshi.Builder` instance to register 
any `JsonAdapter`s you need. Once the `enhance()` method completes, it will use the `Moshi.Builder` to 
update the internal `Moshi` instance.

```kotlin
moshi.enhance { builder: Moshi.Builder
    builder.add(AwesomeThingJsonAdapter())        
}
```


!!! info "Extend Moshi on Initialization"
    Typically there should be only one place in your module where you use the `HengamMoshi.enhance()` 
    method and that is inside a `HengamMoshi.kt` file in the root of your module directory.

    Inside this file create a function `extendMoshi(hengamMoshi: HengamMoshi)` and use the `HengamMoshi.enhance()`
    method to register the `JsonAdapter`s you need in your module.

    Then call the `extendMoshi(hengamMoshi: HengamMoshi)` function during the [pre-initialization](/guide/initialization) step
    of your module.


