# Module Initialization

This section explains how to add initialization to a service module. For information on how the initialization steps are executed internally, see the internal docs on intialization.

Every service module should contain a `PusheInit.kt` file in it's package root. Inside there should be an initialization class extending the abstract class `PusheComponentInitProvider`.

```kotlin
// PusheInit.kt

class AwesomeInitializer : PusheComponentInitProvider() {
    override fun preInitialize(context: Context) { }

    override fun postInitialize(context: Context) { }
}

```

The initialization class is actually an Android `ContentProvider` which you will need to [register in your manifest](#add-initializer-class-to-manifest).

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
                .coreComponent(PusheInternals.getComponent(CoreComponent::class.java))
                .build()
```

Note, since the `AwesomeComponent` has the `CoreComponent` as a dependency, it needs to be passed an instance of the `CoreComponent`. Use the `coreComponent()` method of the builder and provide it with a `CoreComponent` instance as shown above.

#### 2. Extend Moshi (optional)
If you want to use custom Moshi adapters related to your service, you could register them here to make them accessible throughout your module. As shown in the [Dagger Component](#dagger-component) section your component should contain a method which returns a `PusheMoshi` instance. Use this instance to extend Moshi as shown below:

```kotlin
awesomeComponent.moshi().extend { moshiBuilder: Moshi.Builder ->
    // Add custom adapters here
    moshiBuilder.add(/* Custom Adapter */)
}
```

#### 3. Start listening for messages
As explained in the [Message Handling](#handling-messages-using-messagedispatcher) section, all downstream messages listeners are registered in the `MessageDispatcher` class. The `MessageDispatcher.listenForMessages()` method must be called in the pre-initialization step. After the pre-initialization step has finished, the `PostOffice` will start receiving messages and any received messages which do not have listeners will be discarded.

```kotlin
awesomeComponent.messageDispatcher().listenForMessages()
```

#### 4. Register Component
You must register the service module component you have created in this step using the `PusheInternals.registerComponent()` class. This method accepts three arguments, the component class (`AwesomeComponent::class.java`), the component instance (`awesomeComponent`) and an instance of the component initializer.

```kotlin
PusheInternals.registerComponent(AwesomeComponent::class.java, awesomeComponent, this)
```

!!! info "Optional argument"
    The third argument is actually optional, however if you do not pass in an  initializer the `postInitializer()` method of your module will not be called in the post-initialization step.


#### 5. Register the Service API (optional)
If your service module has an external API then you must register the API for it to become available to the user. Use the `PusheInternals.registerAPI()` method for registering an API class. The first argument of this method is a string which will be used identify your service module. Your service name should be defined as a `const val` in the `Pushe` class. 

```kotlin
PusheInternals.registerApi(Pushe.AWESOME, PusheAwesome::class.java, awesomeComponent.api())
```

### Post-Initialization
The post-intialization step will happen after all service modules have been pre-initialized. The `postInitialize` method will be called on the [CPU thread](/guide/threads#cpu-thread). In general, any intialization steps specific to your service module which do not fit in the pre-initialization steps should be perfomed here. 

There's a good chance you will required an instance of your component class in the `postInitialize` method. Do **not** create a new instance of the component, instead make the component a member of the intializer class. Since the component is initialized in `preInitialize()` it should be available to use in `postInitialize()`.

The final version of the initializer class will look similar to this:
```kotlin
// PusheInit.kt

class AwesomeInitializer : PusheInitProvider() {
    lateinit var awesomeComponent: AwesomeComponent

    override fun preInitialize(context: Context) {
        /* Create component instance */
        awesomeComponent = DaggerAwesomeComponent.builder()
                .coreComponent(PusheInternals.getComponent(CoreComponent::class.java))
                .build()

        /* Extend Moshi Adapters */
        awesomeComponent.moshi().extend { moshiBuilder: Moshi.Builder ->
            // Add custom adapters here
            moshiBuilder.add(/* Custom Adapter */)
        }

        /* Listen for Downstream Messages */
        awesomeComponent.messageDispatcher().listenForMessages().

        /* Register Component */
        PusheInternals.registerComponent(AwesomeComponent::class.java, awesomeComponent, this)

        /* Register API */
        PusheInternals.registerApi(Pushe.AWESOME, PusheAwesome::class.java, awesomeComponent.api())
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

### Add initializer class to Manifest
The initializer class is an Android `ContentProvider` which needs to be registered in `AndroidManifest.xml` to work.

Add the following `provider` to the `application` tag in your service module's `AndroidManifest.xml` file.

```xml
<provider
    android:name=".AwesomeInitializer"
    android:authorities="${applicationId}.pusheawesomeinitializer"
    android:initOrder="5"
    android:exported="false"
    android:enabled="true" />
```

The `android.name` field must point to your initializer class. 

The `android.authorities` must be a globally unique string on the user's device. To make sure the string is indeed unique, use the format `"${applicationId}.pushe<service-name>initializer"`.

The `android:initOrder` should be set to `5` for all service modules. If for some reason you **prefer** your module to be initialized sooner than other modules, you could set `android:initOrder` to a higher value but it should be no higher than `9`.