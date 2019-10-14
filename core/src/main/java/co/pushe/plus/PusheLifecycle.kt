package co.pushe.plus


import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.rx.BehaviorRelay

import io.reactivex.Completable
import io.reactivex.Observable
import javax.inject.Inject

/**
 * Provides methods for observing events that happen in the Pushe lifecycle.
 */
@CoreScope
class PusheLifecycle @Inject constructor(val context: Context) : LifecycleObserver {

    private val preInitRelay = BehaviorRelay.createDefault(false)
    private val postInitRelay = BehaviorRelay.createDefault(false)
    private val registrationRelay = BehaviorRelay.createDefault<Boolean>(false)
    private val appOpenedRelay: BehaviorRelay<Boolean> = BehaviorRelay.create()
    private val bootCompleteRelay = BehaviorRelay.create<Boolean>()
    private val workManagerInitializeRelay = BehaviorRelay.create<Boolean>()

    /**
     * An observable which will have it's onNext called whenever the application comes to the
     * foreground
     */
    val onAppOpened: Observable<Boolean> = appOpenedRelay
            .observeOn(cpuThread())
            .distinctUntilChanged()
            .filter { it }

    /**
     * An observable which will have it's onNext called whenever the application goes to the
     * background
     */
    val onAppClosed: Observable<Boolean> = appOpenedRelay
            .observeOn(cpuThread())
            .distinctUntilChanged()
            .filter { !it }

    /**
     * An observable which will have it's onNext called whenever the application boot is completed
     */
    val onBootCompleted: Observable<Boolean> = bootCompleteRelay
        .observeOn(cpuThread())
        .filter { it }

    /**
     * Returns true if the application is currently in the foreground and false if it's in the
     * background
     */
    val isAppOpened: Boolean get() = if (appOpenedRelay.hasValue()) appOpenedRelay.value ?: false else false

    /**
     * Wait for pre-initialization to complete
     * Returns a [Completable] which completes once the SDK pre-initialization has completed.
     * If pre-initialization has already finished, the [Completable] will immediately complete.
     */
    fun waitForPreInit(): Completable = preInitRelay.filter { it }.take(1).ignoreElements().observeOn(cpuThread())

    /**
     * Should be called once pre-initialization has completed to trigger [waitForPreInit]
     */
    internal fun preInitComplete() = preInitRelay.accept(true)

    /**
     * Wait for post-initialization to complete
     * Returns a [Completable] which completes once the SDK post-initialization has completed
     * If post-initialization has already finished, the [Completable] will immediately complete.
     */
    fun waitForPostInit(): Completable = postInitRelay.filter { it }.take(1).ignoreElements().observeOn(cpuThread())

    fun isPostInitComplete(): Boolean = postInitRelay.value ?: false

    /**
     * Should be called once post-initialization has completed to trigger [waitForPostInit]
     */
    internal fun postInitComplete() = postInitRelay.accept(true)

    /**
     * Wait for the client to be registered.
     *
     * Will return a [Completable] that will complete once the client has been registered. If the
     * client is already registered the Completable will complete immediately.
     *
     * @see [RegistrationManager.isRegistered] To see what it means for the client to be registered
     */
    fun waitForRegistration(): Completable = registrationRelay.filter { it }.take(1).ignoreElements().observeOn(cpuThread())

    /**
     * Should be called once registration has completed to trigger [waitForRegistration]
     */
    internal fun registrationComplete() = registrationRelay.accept(true)

    /**
     * Wait for the workManager to be initialized.
     */
    fun waitForWorkManagerInitialization(): Completable = workManagerInitializeRelay
        .filter { it }
        .take(1)
        .ignoreElements()
        .observeOn(cpuThread())

    fun forgetRegistration() = registrationRelay.accept(false)

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun moveToForeground() {
        appOpenedRelay.accept(true)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun moveToBackground() {
        appOpenedRelay.accept(false)
    }

    fun bootCompleted() {
        bootCompleteRelay.accept(true)
    }

    fun workManagerInitialized() {
        workManagerInitializeRelay.accept(true)
    }
}