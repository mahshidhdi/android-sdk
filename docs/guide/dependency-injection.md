# Dependency Injection

Hengam uses the Dagger 2 library for dependency injection. This section explains how to use Dagger with Hengam and some conventions which we follow when using Dagger. Note, this is not a complete reference or in depth explaination of the Dagger library, it assumes basic understanding of Dagger.


## Dagger Basics

Suppose we have a class in the `awesome` module which is going to do some stuff. We have to let dagger know of the existance of our class, this is easily done by annotating the class constructor with the `@Inject` annotation.


```kotlin
class AwesomeStuff @Inject constructor() {
    ...
}
```

By using the `@Inject` annotation we allow Dagger to handle dependency injections for this class. In other words, it allows us to:

1. Add dependencies to the `AwesomeStuff` class. Dagger will provide the dependencies for us when creating an instance of the class.
2. Use the `AwesomeStuff` class as a depencency in other classes

Adding dependencies to the class is easy. Simply list the dependencies in the constructor:

```kotlin
class AwesomeStuff @Inject constructor (
    private val postOffice: PostOffice
) {
    fun doAwesomeStuff() {
        poseOffice.sendMessage(...)
    }
}
```

In our example, the `AwesomeStuff` class has listed the `PostOffice` class as a dependency. Dagger will now pass in a `PostOffice` object when creating instances of our class.


!!! tip "Coding Style"
    Dagger provides an alternative way for listing the dependencies of a class using `lateinit vars`.

    ```kotlin
    class AwesomeStuff @Inject constructor() {
        @Inject lateinit var postOffice: PostOffice

        fun doAwesomeStuff() {
            poseOffice.sendMessage(...)
        }   
    }
    ```

    While this is a valid way to use Dagger, avoid doing so and use the constructor approach except in cases where it is not possible (see ...).

The `@Inject` annotation allows our `AwesomeStuff` class to be used as a dependency in other classes as well.

```kotlin
class BoringStuff @Inject constructor (
    private val awesomeStuff: AwesomeStuff
) {
    fun lightenUp() {
        awesomeStuff.doAwesomeStuff()
    }
}
```

## Components

We need to define Dagger Components in order to use dependency injection with Dagger. Dagger Components contain the dependency graph and factory classes for creating instances of our classes. 

All we need to do is provide an interface for the Component and annotate it with `@Component`. Dagger will then generate an implementation of our interface which we can use to create a dependency graph.

The Hengam core module defines a `CoreComponent` Component in the `core.dagger` package.

```kotlin
// core/dagger/CoreComponent.kt
@Component
interface CoreComponent {
    ...
}
```

After defining the Component interface and building the project, Dagger will generate a class which implements the interface. We may then create an instance of the generated Component class to build the dependency graph and start using it. The generated class's name will be the interface name prefixed with `Dagger`.

```kotlin
val core = DaggerCoreComponent.Builder().build()
assertTrue(core is CoreComponent)

core.topicManager().subscribe("topic")
```


Notice how in the example above we were able to get an instance of the `TopicManager` class by calling the `core.topicManager()` method. In order to be able to do so we need to add method declarations to the Component interface for classes which we wish to access directly from the dependency graph.

```kotlin
@Component
interface CoreComponent {
    fun topicManager(): TopicManager
}
```

The name of the declared method is arbitraty. Dagger uses the return type of the method to know what object to create and return once the method is called.


#### Hengam Service Module Components

Each Hengam service module (see [Project Structure](/guide/structure)) should define it's own separate Dagger Component and should list the `CoreComponent` as a dependency. The Component interface should be placed in the `dagger` package of the related module.


```kotlin
// awesome/dagger/AwesomeComponent.kt
@Component(dependencies = [(CoreComponent::class)])
interface AwesomeComponent {
    fun awesomeStuff(): AwesomeStuff
}
```

We created a Component for our `awesome` module. We can now build an instance of the dependency graph using the generated `DaggerAwesomeComponent` class and use it to access the `AwesomeStuff` class.

There is an important note to take into account here. If you recall, the `AwesomeStuff` class required an instance of `PostOffice` as a dependency. The `PostOffice` class is a singleton class (see [Singletons]()) defined in the `CoreComponent` dependency graph. Since, the `AwesomeComponent` has listed the `CoreComponent` as a dependency it will be able to use singletons defined in the `CoreComponent`, however in order to do so an accessor to the singleton must be provided in the `CoreComponent`. See the example below:

```kotlin
// core/dagger/CoreComponent.kt
@Component
interface CoreComponent {
    fun topicManager(): TopicManager
    fun postOffice(): PostOffice   // This is required so that dependant components may use PostOffice
}

// awesome/dagger/AwesomeComponent.kt
@Component(dependencies = [(CoreComponent::class)])
interface AwesomeComponent {
    fun awesomeStuff(): AwesomeStuff
}
```

```kotlin

val awesome = AwesomeComponent
                    .Builder()
                    .coreComponent(core)
                    .build()
                    
awesome.awesomeStuff().doAwesomeStuff()

```

Every service module Component should provide direct access methods in the interface for providing the following four classes:

- **Context**: The Android Context class
- **HengamMoshi**: The `HengamMoshi` class. This should be provided through the `moshi()` method.
- **MessageDispatcher**: A `MessageDispatcher` for the module. See [Where To Register Message Handlers](/guide/receiving-message/#where-to-register-message-handlers)
- **A HengamApi subclass**: If the module provides an API to the developer (see [Providing APIs for Developers](guide/receiving-message/#where-to-register-message-handlers)), it should be available in the Component interface through the `api()` method.
```kotlin
interface AwesomeComponent {
    fun context(): Context
    fun moshi(): HengamMoshi
    fun messageDispatcher(): MessageDispatcher()
    fun api(): HengamAwesome
}
```



## Singleton Classes

In the [Dagger Basics](#dagger-basics) section we created a class `AwesomeStuff` and using the `@Inject` annotation we asked Dagger to provide this class to any other class which needs it as a dependency. By default, Dagger will create new instances of our class everytime it is needs to be passed to another class. However, sometimes we require several classes to share the same instance of a dependency. Or we may require that a single instance of the dependency exist throughout the application (i.e., a singleton class). This behaviour can be achieved using Dagger Scopes.

!!! tip "@Singleton"
    Dagger comes with a predefined scope `@Singleton` which could be used for defining singleton classes. However, we do not use this scope in Hengam. Instead, each component should define it's own custom scope as shown below.


Each service module should contains it's own custom Dagger scope, defined in it's `dagger` package.

```kotlin
// dagger/AwesomeScope.kt

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class AwesomeScope
```

This scope should be given to the service module's Component by annoting the Component interface with the scope annotation.

```kotlin
// awesome/dagger/AwesomeComponent.kt

@AwesomeScope   // Add the scope to the component
@Component(dependencies = [(CoreComponent::class)])
interface AwesomeComponent {
    ...
}
```

Any class in the module which needs to be a singleton should use this scope.

```kotlin
@AwesomeScope
class AwesomeStuff @Injects constructor(
    private val postOffice: PostOffice
)
```

The `AwesomeStuff` class now behaves like a singleton class. Dagger will create a single instance of `AwesomeStuff` and provide that instance to any classes which need it as a dependency.
