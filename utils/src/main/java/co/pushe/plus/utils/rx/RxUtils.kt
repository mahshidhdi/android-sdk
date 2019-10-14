package co.pushe.plus.utils.rx

import android.annotation.SuppressLint
import co.pushe.plus.utils.log.Plog
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins

fun <T : Any> Observable<T>.bufferWithValue(maxValue: Int, criteria: (T) -> Int): Observable<List<T>> {
    var currentList = mutableListOf<T>()
    var currentCount = 0

    return Observable.create<List<T>> { emitter ->
        fun checkAndEmit() {
            if (currentCount >= maxValue) {
                emitter.onNext(currentList as List<T>)
                currentCount = 0
                currentList = mutableListOf()
            }
        }

        this.subscribeBy(
                onNext = {
                    currentCount += criteria(it)
                    currentList.add(it)
                    checkAndEmit()
                },
                onError = { emitter.onError(it) },
                onComplete = {
                    emitter.onNext(currentList as List<T>)
                    emitter.onComplete()
                }
        )
    }
}


/**
 * Alternative to calling `subscribe` on an Observable.
 * Will subscribe to the observable and ignore the disposable returned.
 *
 * If the Observable encounters an error, the error will be logged but otherwise ignored.
 *
 *
 * @param errorLogTags Log tags to be used when logging if an error occurs
 * @param onNext The `onNext` callback for the Observable
 */
@SuppressLint("CheckResult")
fun <T: Any?> Observable<T>.justDo(vararg errorLogTags: String, onNext: ((T) -> Unit)? = null) {
    this.subscribe(
            onNext ?: {},
            {  ex -> Plog.error.withTag(*errorLogTags).withError(ex).log() }
    )
}

@SuppressLint("CheckResult")
fun <T: Any?> Single<T>.justDo(vararg errorLogTags: String, onSuccess: ((T) -> Unit)? = null) {
    this.subscribe(
            onSuccess ?: {},
            {  ex -> Plog.error.withTag(*errorLogTags).withError(ex).log() }
    )
}

@SuppressLint("CheckResult")
fun <T: Any?> Maybe<T>.justDo(vararg errorLogTags: String, onSuccess: ((T) -> Unit)? = null) {
    this.subscribe(
            onSuccess ?: {},
            {  ex -> Plog.error.withTag(*errorLogTags).withError(ex).log() }
    )
}

@SuppressLint("CheckResult")
fun Completable.justDo(vararg errorLogTags: String, onComplete: (() -> Unit)? = null) {
    this.subscribe(
            onComplete ?: {},
            {  ex -> Plog.error.withTag(*errorLogTags).withError(ex).log()  }
    )
}

/**
 * Alternative to calling `subscribe` on an Observable which will keep the subscription alive even
 * if unhandled exceptions occur in the observable, operations or the consumer.
 * The disposable returned by the subscription will be ignored.
 *
 * Any exceptions which are caused by the observable or operations or any exceptions which occur in the
 * passed `onNext` parameter will be logged and ignored.
 *
 * @param errorLogTags Log tags to be used when logging if an error occurs
 * @param onNext The `onNext` callback for the Observable
 */
@SuppressLint("CheckResult")
fun <T: Any?> Observable<T>.keepDoing(vararg errorLogTags: String, onHandlerError: ((Throwable) -> Unit)? = null, onNext: ((T) -> Unit)? = null) {
    this.doOnError { ex -> Plog.error.withTag(*errorLogTags).withError(ex).log() }
        .onErrorResumeNext(Observable.empty<T>())
        .subscribe(
            {
                try {
                    onNext?.invoke(it)
                } catch (ex: Exception) {
                    if (onHandlerError != null) {
                        onHandlerError(ex)
                    } else {
                        Plog.error.withTag(*errorLogTags).withError(ex).log()
                    }
                }
            },
            {  ex -> Plog.error.message("Unhandled exception occurred in relay causing it to terminate").withTag(*errorLogTags).withError(ex).log() }
    )
}

/**
 * Similar to [Completable.fromCallable] but will **not** throw an [io.reactivex.exceptions.UndeliverableException]
 * if the subscription is disposed when an error occurs in the callable.
 */
fun <T> safeSingleFromCallable(callable: () -> T): Single<T> = RxJavaPlugins.onAssembly(SafeSingleFromCallable(callable))

/**
 * Similar to [Single.fromCallable] but will **not** throw an [io.reactivex.exceptions.UndeliverableException]
 * if the subscription is disposed when an error occurs in the callable.
 */
fun <T> safeCompletableFromCallable(callable: () -> T): Completable = RxJavaPlugins.onAssembly(SafeCompletableFromCallable(callable))

