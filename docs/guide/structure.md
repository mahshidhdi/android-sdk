# Project Structure

The Hengam code base is comprised of a `core` module and several separate modules (Hengam Service Modules) each implementing a service provided by Hengam. 
The service modules include modules such as the `notification` module which implements showing notifications and handling different notification actions, the `analytics` module which sends app usage analytics and other such modules. All service modules are dependent on the `core` module.

The `core` module contains a messaging framework which is used by itself and other modules for communicating with the server. The main idea is that the `core` module is mandatory and should be included as a dependency by any app which wishes to use Hengam. However, by itself it provides no useful functionality to the user. All other modules are optional and could be included to provide different functionality.

For the sake of consistency, there are certain structures and guidelines which the service modules should follow. This section aims at explaining these guidelines. 

!!! note "The Awesome Module"
    Throughout the documentation we will often suppose that we are creating an example service module called the _Awesome_ (فوق‌العاده) module. When creating your own modules, change any file/class names containing the word  _Awesome_ to an appropriate name for your own module.


## Package Structure

Each module should be contained in it's own separate package prefixed with `io.hengam.lib`.
The following is the base package and file structure which all service modules should have.

    io/hengam/lib/awesome/
        dagger/
            AwesomeComponent.kt
            AwesomeModule.kt
            AwesomeScope.kt
        messages/
            downstream/
            upstream/
            mixin/
            MessageDispatcher.kt
        tasks/
        Constants.kt
        HengamApi.kt
        HengamInit.kt


## Initialization
Each service module should contain a subclass of `HengamComponentInitProvider` which performs the initialization for the module. See [Initialization](/guide/initialization) for details on how to perform initialization.

The initialization class should be placed in the `HengamInit.kt` file in the service module's root package.

## Dependency Injection
We use the Dagger2 library for dependency injection. See the [Dependency Injection](/guide/dependency-injection) section for information on how we use Dagger in Hengam.

Each service module should create it's own Dagger scope (e.g., `AwesomeScope`) which will be used by singleton classes inside the module. 

Each service module should also contain a single Dagger Component which represents the module (e.g, `AwesomeComponent`).

All files related to Dagger should be placed in the `dagger` package within the module.


## Messages
See sections [Sending Messages](/guide/sending-message) and [Receiving Messages](/guide/receiving-message) for details on using the messaging framework.

All message classes related to the service module should be contained in the `messages` package. Upstream and downstream should be seperated into the `messages.upstream` and `messages.downstream` packages respectively. 

Each message class should be placed in a seperate file. If a message's structure is composed of several smaller classes, it's ok and preferred to keep those classes in the same file as the message class. 

Message classes should be pure data classes and should not contain any logic.


## Task Structure
See [Tasks](/guide/tasks) for information Tasks and how to run them.

All `HengamTask` subclasses should be placed in the `tasks` package inside the module. 


## Developer API
Hengam service module's may wish to provide APIs to developer to enable them to interact with the service. See [Providing APIs for Developers](/guide/hengam-api) for information on how to do so.

The API class should be placed in the `HengamApi.kt` file in the service module's root package.
