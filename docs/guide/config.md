# Configurations

Every service module may contain different configurations, e.g. how many to try building notifications before 
failing, what URL to use for a certain API, etc. Such configurations are handled with the
`HengamConfig` class in the SDK. 

You can get an instance of the `HengamConfig` class by declaring it as a dependency of your class in the 
class constructor. The Dagger framework will provide an instance when creating your class.

!!! question "Why use HengamConfig?"
    While you could manually handle and store configurations (e.g. using the `HengamStorage` class),
    using the `HengamConfig` class for handling configurations has certain benefits. The most important
    of which is that configurations stored with the `HengamConfig` class can be updated from the server
    using the `t25` downstream message. So any configuration you use can later be changed from the server
    for some or all of the users.



### How not to use the `HengamConfig` class

The simplest way to use the `HengamConfig` class is to just use one of the `getX()` methods to get the 
configuration. To do so you will need to also pass in the default value you want to use for the configuration.

```kotlin
class AwesomeStuff @Inject constructor (
    private val hengamConfig: HengamConfig
) {
    fun doAwesomeStuff() {
        val awesomeStuffCount = hengamConfig.getInteger("awesome_stuff_count", 5)

        (1 until awesomeStuffCount).forEach { // do awesome stuff }
    }
}
```

Note, the SDK starts with an empty configuration. This means the `HengamConfig` class will always simply
 return the default value unless the configuration is updated (i.e., through an _update configuration_ dowstream message).

While this approach to using configurations works it is not recommended, since all the different configurations and 
their default values will be spread in different parts of the code.


## How to use the `HengamConfig` class

The recommended way of handling configurations is to have a file named `HengamConfig.kt` in your module. In this file, 
create an [extension property](https://kotlinlang.org/docs/reference/extensions.html#extension-properties) on the `HengamConfig`
class for every configuration you want to use your module. Implement the `get()` function of the property and use the `getX()`
methods of the `HengamConfig` class to get the configuration.

See the example below:

```kotlin
// awsome/HengamConfig.kt

val HengamConfig.awesomeStuffCount: Boolean 
    get() = getInteger("awesome_stuff_count", 5)
```

```kotlin
// awesome/AwesomeStuff.kt

class AwesomeStuff @Inject constructor (
    private val hengamConfig: HengamConfig
) {
    fun doAwesomeStuff() {
        (1 until hengamConfig.awesomeStuffCount).forEach { // do awesome stuff }
    }
}
```

Using the `HengamConfig` class like this allows us to keep all the configurations of a module, along with their constants and default values, in one place.