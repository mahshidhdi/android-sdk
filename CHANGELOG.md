# Hengam SDK ChangeLog

## Unreleased

#### Core
- Add `package_name` and `platform` fields to all upstream parcels

#### Notification
- Add `delay` field to Notification message

#### Datalytics
- Add `rate_limit` field to Geofence messages
- The values used for the Geofence message (`t71`) `trigger` field have been changed from 0 and 1 to
  1 and to.
- Make the public-ip APIs configurable and change the default list to use the Hengam API first

## 2.0.0-beta03 (1 July, 2019)
- Added a new module called **LogCollection** to collect SDK logs and send them to a private server for
  debug purposes (This module is not included in the `Base` module and is added separately to limited
  number of apps)
- Added a new downstream message, `t63`, to run predefined debug commands previously used in hengam admin app.
  This message alongside the `LogCollection` module will be used to find possible bugs in the sdk.

#### Analytics
- Increased the session id length to 16 characters
- By sending each activity separately in a `t100` message for session flow info, the `SessionActivityMessageWrapper`
  became redundant and removed
- Users can now send custom data with the `event`s

#### Datalytics
- Changed `t24` message (screen on/off) send priority to `Whenever` to improve battery consumption (This
  caused a lot of such messages to stay pending and be ignored, so this was changed in the next release)

#### Sentry
- Use string concatenation to put the default sentry dsn in the code to avoid the Google Play warning
  (This change was not sufficient and the the warning was prevented in the next release)

## 2.0.0-beta02 (8 June, 2019)
- Removed the rx-relay, rx-android and rx-preferences dependencies from all modules. Rx-preference's
  functionality has been implemented in the `HengamStorage` utility class. The classes and functions
  which were used from the other dependencies have been copied over to the `io.hengam.lib.utils.rx`
  package.
- Debug commands have been moved from the admin app to their related modules
- Will use a custom minified RxJava build instead of the original one (except for running tests).
  This is to reduce the library's impact on APKs size for applications which don't use proguard.
- Migrated project to AndroidX
- Refactored `Plog` (and it's API) to make it more memory efficient and reduce the method count imposed
  by it on the final APK.

#### Core
- Add support for using separate Firebase apps along side Hengam. Hengam will become the default Firebase
  app only if no other default apps exist in the project.
- Fix bug caused by persisting and restoring a message multiple times which resulted in malformed
  messages like `t5: {"a": {"a": {"a": {...}}}`
- Fix bug in subscribing to topic
- Fix `UndeliverableException` being thrown sometimes when using `Observable.create()`
- Fix `ConcurrentModificationException` caused by upstream message ACKs and errors not being handled
  on the cpu thread.
- Fix bug in handling message-too-big messages which occurred when all but on of a parcels messages
  had been expired when receiving the event causing it to think the parcel had only one large message.
- Make changes to the `MessageStore` which should improve memory usage

#### Notification
- Modified notification callbacks in HengamNotificationListener. The custom content is now also
  accessible from the `NotificationData` class.
- Added capability for showing Notifications based on Geofencing events
- Added support for native notification badges
- Make sure duplicate notifications are not shown by persisting the message ids of seen and published
  notification messages (with an expiration time)
- Refactor notification building to better utilize RxJava, place timeouts on each notification build
  step and also prevent `UndeliverableException` from being raised by notification image and sound
  downloads.
- Change the structure of notification action and status messages (`t1` and `t8`) to include network
  data with a nested structure.

#### Datalytics
- Implemented blacklisting package names when collecting installed applications. However, the app
  list collection has been disabled and removed from the SDK due to problems with Google Play.
- Application installed message (`t13`) and the screen event message (`24`) will be collected and
  sent for Android SDK versions below O
- Fix bug causing an exception to be thrown when collecting cell info if location services is disabled
  on the OS

#### Analytics
- ViewPager Fragments are removed from the Session info. They will be added again later with a
  better implementation.
- Session info are sent as separate (`t100`) messages to avoid creating messages which exceed the parcel size
  limit. Each Session Activity will be sent as a separate message.

## 2.0.0-beta01 (26 Feb, 2019)
- Added a new 'base' module which includes all other modules as dependencies. It is sufficient to
  only add the base module in the application gradle file if you don't want a custom selection of
  the modules.
- [fix] Fixed various issues related to proguard rules
- [fix] Some errors (such as NoSuchMethodError or ClassNotFoundError) were being swallowed by the
  custom schedulers (cpuThread and ioThread). Changes have been made to ensure these errors are
  reported correctly.

#### Core
- Added developer APIs for `Hengam.initialize()` and `Hengam.getHengamId()` for better backward
  compatibility but marked them as deprecated.
- Combined the SharedPreference file used by `RxPreferences` and the `StorageFactory` class.

#### Analytics
- Sessions now have IDs and the session's ID in which a goal is reached is included the goal-reached
  upstream message.


## 2.0.0-alpha10 (20 Feb, 2019)
- Updated kotlin to `1.3.21` and Android WorkManager to `1.0.0-rc01`

#### Core
- Changed the architecture of component initialization to work with reflection instead of requiring
  each module to have it's own content provider.
- All _info_ logs and above are now printed to LogCat by default in production and staging builds.
- Introduced a limit for the maximum number of pending messages which are allowed for any given type,
  newly created upstream messages are ignored if this limit is reached. The limit is configurable for
  each individual message type.

#### Notification
- Added new developer API for removing notification channels


## 2.0.0-alpha9 (19 Feb, 2019)
- Various task options, such as registration backoff policy, notification building max attempts and
  others are now configurable.

#### Core
- The Upstream Sender will be scheduled to run every 24 hours to send any stored upstream messages.
- The FCM instance id is now saved in the SharedPreferences and the same instance id will always be
  sent to the server even if FCM generates a new id.
- Implemented the `t23` downstream message which commands the SDK to send a registration message
- [fix] Fix concurrent modification bug in message store caused when persisting messages in the io
  thread
- Maximum upstream parcel size and upstream message timeouts and expiration times are now configurable
- Combined manifest meta tag for `app_token` and `fcm_sender_id` into a single `hengam_token` tag
- The Hengam Id (pid) identifier no longer exists. The available user id's are now google
  advertisement id (gaid), android id (aid) or custom id (cid).
- Several log options for logging to LogCat, such as log level, can now be configured through the manifest
- Application first-install-time is now included in the registration message

#### Notification
- Publishing notifications will now proceed even if the notification contains an invalid action.
  Invalid actions are replaced with fallback actions which perform no operations.
- The downstream message type `t30` may also be used for publishing notifications. If both `t1` and
  `t30` exist in a parcel only one notification will be shown.
- Notification publishing can now be selectively disabled for certain users or apps using the configs.
- The notification report message (`t8`) will now include location, wifi and cell info.

#### Datalytics
- Data collection will be skipped if the collection task is executed sooner than expected (i.e., if
  the collectable has already been collected in the current interval).
- Collector settings are moved to the centralized configs instead of being handled separately.
- The is-app-hidden data will only be collected if it's value is different from the previously sent
  message.

#### Analytics
- The session is ended 8 seconds after the app is closed (either moved to the background or closed
  by swiping it from the recent apps). The session message is sent immediately when the session is
  ended. This is to increase the chance of receiving the last session data before the user uninstalls
  the app.
- If an activity is opened as a result of a notification click action, the notification message id
  will be included in the session info of the opened activity.
- The message types 100 through 150 are now reserved for analytics related messages.


## 2.0.0-alpha8 (07 Feb, 2019)
- Updated Android WorkManager to beta03 which resolves a bug in retrying tasks

#### Core
- Sentry settings are now configurable using the `t25` message or through the configuration
  update message
- Added a developer API for checking whether the SDK has successfully registered with the server or not
- The hosting application's sign key is included in the registration message (`t10`)
- Application uninstall events are reported to the server with a `t13` message
- Device boot events are reported to the server with a `t21` message

#### Notification
- The first notification build attempt is now also performed in a WorkManager task instead of being
  performed directly. Also, there is no longer a strict network availability requirement for the
  notification build task. Network will only be required if the notification message contains a URL
  in one of it's fields.
- Changed notification build backoff policy to linear backoff with a 20 second delay.
- [fix] Fixed a bug in notification image download retry limits.
- [fix] Fixed bug in notification building where failed image download responses will would be cached,
  resulting in the image never being shown in succeeding attempts until the cache expires.
- User Activity Action now accepts both relative (relative to the application package name)
  and absolute class names
- Notification callbacks for scheduled notifications will now be called when the notification is being
  published (at the scheduled time) instead of when the message is received.


## 2.0.0-alpha7 (04 Feb, 2019)
- Implemented a centralized configuration which can be updated using a downstream message. This allows
  different parts of the SDK to have their options configurable through the server.
- Empty locations (lat=0 long=0) will not be included in messages anymore

#### Core
- The default Sentry client is not used anymore. This allows developers to also use Sentry for their own purposes
- Sentry report task added which sends periodic stats to Sentry. It is disabled by default for production builds
  but can be enabled through the config.
- Implemented expiration times for stored data (such as notification interaction stats).
- Downstream messages can now contain multiple messages of the same type (this may be removed again in the future).
- [fix] Fix bug where NACKed messages were not retried until they timeout.

#### Notification
- Errors which occur during the different steps in the notification build process will now be handled
  separately and the notification will always be shown with best-effort even if parts of the build
  fail. The notification report message (`t8`) will also include stats on which parts of the build
  have failed.
- Notification action messages (click, dismiss) will now include Connected Wifi and Cell Info.
- Provided developers with callbacks to receive notification click and dismiss events.

#### Analytics
- [fix] Session duration is no longer cumulative


## 2.0.0-alpha6 (23 Jan, 2019)
- Update Android WorkManager to beta version and use RxWorker API.

#### Core
- Allow the developer to set user email and phone number in addition to custom id.
- [fix] Resolved concurrent modification error in upstream message sending.
- The App ID and Hengam version will now be sent in all upstream parcels.

#### Datalytics
- Data collection tasks initialization and scheduling will now happen after registration has been
  successfully performed.
- [fix] Fixed various formatting and data issues in data collection messages.


## 2.0.0-alpha5 (14 Jan, 2019)
- Add improvements to Admin app

## 2.0.0-alpha4 (12 Jan, 2019)
- Renamed analytics module to datalytics and statistics module to analytics

##### Core
- [change] Registration is now retried with exponential back-off until response is received
- Introduced message mixins to allow easily adding reoccurring data (e.g, location) to different messages.
- The SDK automatically subscribes to the broadcast channel when initiating registration. However,
  it will not automatically subscribe if the App is a hidden app.
- [fix] Fixed incorrect topic names in topic update messages (i.e., t12)
- Disabled firebase analytics (https://firebase.google.com/support/guides/disable-analytics)
- Using Advertisement Id can now be disabled by the developer from the manifest
- The developer can set a custom identifier for the user using `Hengam.setCustomId`. The custom id
  will be sent with all upstream messages. The developer can use this id for sending push notifications.

##### Notification
- [change] Notification callbacks are now added using API functions (e.g,
  `HengamNotification.setNotificationListener()`) instead of extending `Application`. The callbacks
  will be called on the Main thread.
- Notification small icons can now be URLs (only for Android >= 23) instead of resource names
- [fix] Fixed parsing LED colors in notification messages
- Notification publish time will now be sent in notification click/dismiss messages
- Added `DownloadAppAction` for downloading and installing APK. Upstream messages will be sent for
  APK download and APK install events.

##### Datalytics
- [change] Use a list of public APIs instead of just one for obtaining the device public IP. The
  APIs will be tried sequentially until one of them succeeds. The Hengam hosted API will be tried last.
- When retrieving cellular data if the cell type is unknown we will now attempt to obtain cell data
  by parsing the output of `toString()` on the cell object.
- Includes application signatures when collecting app data
- [change] The `FusedLocationProviderClient` is now used for obtaining the device location

##### Analytics
- [change] Changed the Events API to not accept custom data in events and added a special event type
  for E-commerce events.
- Collecting fragment flow information in sessions is now configurable with push messages. It can be
  disabled or it can be limited to a certain fragment depth.
- Fragment goals are now supported even when the app is proguarded. The proguarded fragment name for
  (for different app versions) must now be included in the goal message.


## 2.0.0-alpha3 (21 Oct, 2018)
##### Core
- A new Sending Priority has been added to the messaging framework called the `BUFFER` priority. A
  message sent with this priority will wait for enough messages to be available to make up a parcel
  with maximum size before triggering the upstream sender.
- Adds support for setting retry limits on background tasks
- The messaging framework will now respect ACK and error messages and also the message size limits
  of upstream couriers. If a message has been in-flight for longer than a day without receiving an
  ACK or error, it will be resent.
- If a "message-is-too-big" error is received from the courier when sending a parcel, the parcel
  will be split into two smaller parcels and retried.
- Upstream messages will be discarded if they have not been sent after an expiration time. The
  default expiration time is one week.

##### Notification
- Additional notification actions have been implemented
    - Dialog Action
    - User Activity Action
    - Download File Action
    - Download And WebView Action
- Added support for custom notification sounds. In Android O and above playing to play custom sounds
  we need to use a notification channel which has sounds disabled (otherwise the default notification
  sound will be played as well. For this purpose, if a notification with a custom sound is received
  a new channel will be created (named the 'Alternative Channel') which has sounds disabled.
- A received notification will now be discarded if notification building fails 5 times.

##### Analytics
- Now able to uniquely identify a nested fragment using the list of it's parent fragments
- All UI operations and view data extraction is now performed on the Android Main thread

##### Datalytics
- Performs periodic data collection for:
    - Device app list
    - Whether application is hidden
    - Device cellular info
    - Constant data (e.g, device brand, etc.)
    - Variable data (e.g, operator, google play services version, etc.)
    - Floating data (e.g, location)
- Periodic data collections tasks are run with a default schedule but the schedules can be changed
  with a downstream message
- Data collection could also be triggered to be sent immediately with a downstream message


## 2.0.0-alpha2 (9 Oct, 2018)
##### Core
- [change] Upstream messages will not be sent unless registration has been performed.
  - This is the default behavior, however you can also force a message to be sent before registration.
  - If a message is scheduled before registration, it will wait for registration to complete before being sent.
  - We consider registration to be completed when the first successful registration response is received from the server. From then on, even if the FCM tokens are invalidated,       we still consider the client to be registered.
- [fix] Downstream parcels which don't contain a message id will use the _FCM Message Id_ as their Id.
  - Previously the server guaranteed to send a message id with each parcel, however this is not the case anymore. The code still assumes that each parcel has a unique message id,    but if it not present in the parcel itself, the _FCM Message Id_ will be used for this purpose.

##### Notification
- Notifications may now include a `tag` field. If a notification arrives with the same tag as a previous one, it will replace it in the notification bar.
- Notifications may now be scheduled to be shown at a specific time using the `schedule_time` field.

##### Analytics
- Handles cases in which a goal message's view goal encounters an error.
    - An error may occur due to the view goal not existing or it having a different type than what was specified in the goal.
    - When sending goal-reached upstream messages we now distinguish between errored view goals and view goals which have not been seen.
- Uses a fragment's parent fragment list to uniquely identify fragments and views.

## 2.0.0-alpha1 (25 Sept, 2018)
##### Core
- Reads FCM Sender ID and App Token from AndroidManifest.xml.
- Prevents FCM from initializing automatically and then initializes manually using the Sender ID in the manifest.
- Implements the FCM message courier for sending upstream and receiving downstream messages using FCM.
    - A _courier_ is a service used for sending and receiving messages, e.g., FCM courier, HTTP courier, Befrest courier etc. .
- Implements subscribing to and unsubscribing from FCM topics and provides an API to developers for subscribing to topics.
- Performs registration with server on app start if registration is needed.
- [change] Initialization happens automatically on app start and before activities are started (no `Hengam.initialize()` call is needed).
- Sends message deliveries for downstream messages which request deliveries.
- Uses sentry for collecting error reports.
- [change] Uses Android WorkManager for scheduling tasks (instead of Evernote scheduler)
- [change] Uses Advertisement Id as the device ID (instead of Hengam Id)
- Allows sending upstream messages with three different priorities:
    - **IMMEDIATE**: A message sent with this priority will be sent immediately.
    - **SOON**: A message sent with this priority will wait a few seconds before sending.
    - **WHENEVER**: A message with this priority will never trigger the upstream sender itself,
      but will be sent once the upstream sender is triggered by any other message.
- [change] Introduces the notion of _Parcels_ and _Messages_ as opposed to just Messages.
    - A *Message* is a structured piece of information with an integer type and specific data fields. For example a notification message has type `1` and has a structure like below:

    ```json
    {
        "title": "Notification Title",
        "content": "Notification Content"
    }
    ```

    - A *Parcel* is a collection of messages and is what is transffered between the client and the server. A parcel may contain any number of different messages, each message will be extracted and handled separately.

    ```javascript
    // Downstream Parcel
    {
        "t1": {
            "title": "Notification Title",
            "content": "Notification Content"
        },
        "t12": {
            "topic": "mytopic"
        }
    }

    // Upstream Parcel
    {
        "t12": [
            {"topic": "mytopic", "status": 0},
            {"topic": "othertopic", "status": 1}
        ],
        "t8": [
            {"orig_msg_id": "abcde", "status": 1}
        ]
    }
    ```

##### Notification
- Handles notification messages and shows notifications. Retries notification building with backoffs if it fails.
- Sends notification publish status to server.
- Implements the following notification actions:
    - App Action
    - Cafe Bazaar Action
    - Dialog Action (needs testing and revisions)
    - Dismiss Action
    - Intent Action
    - Url Action
- Provides API for disabling notifications and creating notification channels.
- Provides API for registering notification listeners and calls the developers notification listeners when notifications arrive.

##### Analytics
- Listens to activity lifecycle and deduces session duration, session activity flows and session fragment flows.
- Sends previous session message to server when new session starts.
- Allows setting goals for when an Activity is reached, when a Fragment is reached or when a button is clicked. Sends message to server when the goal has been reached. Goals may include the following parameters:
    - A goal may define a series of activities or fragments which should be seen before reaching the goal for it to be valid.
    - A goal may define a set of Views (view goals), once the goal has been reached the values of those views will be sent with the upstream message. The view goal may be in a different (previous) activity than the one the goal is reached in.
- Handles downstream messages for adding and removing goals.
- Provides an API to developers for sending custom events to server.