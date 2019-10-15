# Module Initialization

This section explains how to add initialization to a service module. For information on how the initialization steps are executed internally, see the internal docs on intialization.

Every service module should contain a `HengamInit.kt` file in it's package root. Inside there should be an initialization class extending the abstract class `HengamComponentInitProvider`.

```kotlin
// HengamInit.kt

class AwesomeInitializer : HengamComponentInitProvider() {
    override fun preInitialize(context: Context) { }

    override fun postInitialize(context: Context) { }
}

```

Module initialization is performed in two pre-initialization and post-initialization steps. You will need to override the two methods `preInitialize` and `postInitialize` in your initialization class and define the actions to perform in each step. 

The SDK's initialization is performed in the following order:

- The pre-initialization step for the `core` module is perfomed
- The pre-initialization step for all service modules are run in no particular order
- The post-initialization step for the `core` module is perfomed
- The post-initialization step for all service modules are run in no pariticular order

!!! question "Pre-Initialization vs Post-Initialization"
    Pre-init is performed on the Main thread at the very start of the application before the `onCreate()` of activities or even the application
    class is called. You should use this step to create and register the components or objects needed for your module to operate. 
    
    :warning: 
    Any code running in the pre-init step will delay the starting of the application so avoid long running tasks in this step, use post-init instead.

    Post-init is performed on the [CPU thread](/guide/threads#cpu-thread)  and will run after the application has started. Any code in post-init will _probably_ run after the application and activity `onCreate()` methods but is guaranteed to run before any other code which is scheduled on the CPU thread.
    
    
    You should generally prefer post-init over pre-init. Put any initialization task which does not _have to be_ in pre-init in post-init.

### Pre-Initialization

The following actions should be perfomed inside the `preInitialize` method.

!!! caution "Beware of Main Thread"
    The `preInitialize(context: Context)` method is called on the main thread. Avoid running long tasks in this method. In general, avoid performing any actions other the ones described below in the pre-initialization step unless absolutely necessary.

#### 1. Create component instance
The first step is to create an instance of your module's [Dagger Component](#dagger-component). Once you have created your Dagger Component interface as explained [here](#dagger-component), build the project in Android Studio to have Dagger generate a component implemenation for you (make sure you have added Dagger to your gradle dependencies). The generated component in our case will be called `DaggerAwesomeComponent`. Create an instance of the component using the component builder like below:

```kotlin
val awesomeComponent = DaggerAwesomeComponent.builder()
                .coreComponent(HengamInternals.getComponent(CoreComponent::class.java))
                .build()
```

Note, since the `AwesomeComponent` has the `CoreComponent` as a dependency, it needs to be passed an instance of the `CoreComponent`. Use the `coreComponent()` method of the builder and provide it with a `CoreComponent` instance as shown above.

#### 2. Extend Moshi
If you want to use custom Moshi adapters related to your service, you could register them here to make them accessible throughout your module. See [Json Serialization](/guide/moshi) for more details

```kotlin
extendMoshi(awesomeComponent.moshi())
```

#### 3. Start listening for messages
As explained in the [Message Handling](#handling-messages-using-messagedispatcher) section, all downstream messages listeners are registered in the `MessageDispatcher` class. The `MessageDispatcher.listenForMessages()` method must be called in the pre-initialization step. After the pre-initialization step has finished, the `PostOffice` will start receiving messages and any received messages which do not have listeners will be discarded.

```kotlin
awesomeComponent.messageDispatcher().listenForMessages()
```

#### 4. Register Component
You must register the service module component you have created in this step using the `HengamInternals.registerComponent()` class. This method accepts three arguments, the name of the component, the component class (`AwesomeComponent::class.java`) and the component instance (`awesomeComponent`). You will want to define the component name as a constant string in the `Hengam` class.


```kotlin
HengamInternals.registerComponent(Hengam.AWESOME, AwesomeComponent::class.java, awesomeComponent)
```

Once the component has been registered, the component instance can be retrived from the `HengamInternals` class like this:

```kotlin
HengamInternals.getComponent(AwesomeComponent::class.java)
```

Note, the post-initialize step of your component will not be run if you do not register the component.


#### 5. Register the Service API (optional)
If your service module has an external API then you must register the API for it to become available to the user (see [Providing APIs for Developers](/guide/hengam-api)). 

Use the `HengamInternals.registerAPI()` method for registering an API class. The method accepts three arguments: the component name, the component class and the component API class instance.

```kotlin
HengamInternals.registerApi(Hengam.AWESOME, HengamAwesome::class.java, awesomeComponent.api())
```

#### 6. Register Debug Commands (optional)
TODO

### Post-Initialization
The post-intialization step will happen after all service modules have been pre-initialized. The `postInitialize` method will be called on the [CPU thread](/guide/threads#cpu-thread). In general, any intialization steps specific to your service module which do not fit in the pre-initialization steps should be perfomed here. 

There's a good chance you will required an instance of your component class in the `postInitialize` method. Do **not** create a new instance of the component, instead make the component a member of the intializer class. Since the component is initialized in `preInitialize()` it should be available to use in `postInitialize()`.

The final version of the initializer class will look similar to this:
```kotlin
// HengamInit.kt

class AwesomeInitializer : HengamInitProvider() {
    lateinit var awesomeComponent: AwesomeComponent

    override fun preInitialize(context: Context) {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?:  throw ComponentNotAvailableException(Hengam.CORE)

        /* Create component instance */
        awesomeComponent = DaggerAwesomeComponent.builder()
                .coreComponent(core)
                .build()

        /* Extend Moshi Adapters */
        extendMoshi(awesomeComponent.moshi())

        /* Listen for Downstream Messages */
        awesomeComponent.messageDispatcher().listenForMessages().

        /* Register Component */
        HengamInternals.registerComponent(AwesomeComponent::class.java, awesomeComponent, this)

        /* Register API */
        HengamInternals.registerApi(Hengam.AWESOME, HengamAwesome::class.java, awesomeComponent.api())

        /* Register Debug Commands */
        HengamInternals.registerDebugCommands(awesomeComponent.debugCommands())
    }

    override fun postInitialize(context: Context) { 
        doFirstAwesomeInitialization()
        doSecondAwesomeInitialization()
    }

    private fun doFirstAwesomeInitialization() { 
        // ...
    }

    private fun doSecondAwesomeInitialization() { 
        // ...
    }
}
```
