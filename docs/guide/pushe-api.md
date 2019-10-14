# Providing APIs for Developers

In order to allow the developer to interact with Pushe, we need to provide a set of API calls which he or she may call.

API methods related to the `core` module should be placed as static methods in the `Pushe.java` file.

API methods relatd to other service modules should not be placed in the `Pushe.java` file. Instead, for each Pushe service module a seperate API class should be created which the developer will use to interact with that particular module. In the remainder of this section we will describe how you could provide API methods for different service modules.

## Providing Service Module API
### Creating API class
Each Pushe service module should contain a `PusheApi.kt` file. Inside there should be a class extending the `PusheServiceApi` interface. 

!!! info "Pick a good name"
    This class will be accessible to the the developer so pick a good name. A good convention to follow is `Pushe${ComponentName}` such as `PusheAwesome`.

Provide any API calls you want to be accessible to the developer as public methods in this class.

```kotlin
class PusheAwesome @Injects constructor(
    // dependencies
) : PusheServiceAPI {

    fun awesomeApiCall() {
        // do awesome stuff
    }
}
```

### Add to Dagger Component
Since the API methods will be accessing different parts of your service module, use Dagger to inject dependencies into the API class.

Also, add the API class to your service module's dagger component interface.

```kotlin
// dagger/AwesomeComponent.kt

@Component(/* ... */)
interface AwesomeComponent {
    // ...
    fun api(): PusheAwesome
}
```

### Register API class
For the API class to be accessible you will need to register it. This is one of the [pre-initialization](/guide/initialization/#5-register-the-service-api-optional) steps.

```kotlin
// PusheInit.kt / AwesomeInitializer.preInitialize():

PusheInternals.registerApi(Pushe.AWESOME, PusheAwesome::class.java, awesomeComponent.api())
```

Note, that the API is registered with a String name. All service names should be defined as constants in the `Pushe` class.

## Using Service Module API

To use a service module API from the hosting application code, you must obtain an instance of the API class using `Pushe.getPusheService()`. The method accepts either the service name or the service class.

```kotlin
// Using service name
val pusheAwesome = (PusheAwesome)Pushe.getPusheService(Pushe.AWESOME)
pusheAwesome.awesomeApiCall()

// Using service class
val pusheAwesome = Pushe.getPusheService(PusheAwesome::class.java)
pusheAwesome.awesomeApiCall()
```
