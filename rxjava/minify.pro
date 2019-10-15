-dontpreverify

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod,InnerClasses


-keep public class io.reactivex.Observable {
    public static io.reactivex.Observable defer(java.util.concurrent.Callable);
    public static io.reactivex.Observable fromIterable(java.lang.Iterable);
    public static io.reactivex.Observable merge(java.lang.Iterable);
    public static io.reactivex.Observable just(java.lang.Object);
    public static io.reactivex.Observable fromCallable(java.util.concurrent.Callable);
    public static io.reactivex.Observable empty();
    public static io.reactivex.Observable create(io.reactivex.ObservableOnSubscribe);

    public ... subscribe(...);
    public io.reactivex.Observable observeOn(io.reactivex.Scheduler);
    public io.reactivex.Observable subscribeOn(io.reactivex.Scheduler);

    public io.reactivex.Observable doOnNext(io.reactivex.functions.Consumer);
    public io.reactivex.Observable doOnError(io.reactivex.functions.Consumer);
    public io.reactivex.Observable onErrorResumeNext(io.reactivex.ObservableSource);
    public io.reactivex.Observable onErrorReturn(io.reactivex.functions.Function);

    public io.reactivex.Observable filter(io.reactivex.functions.Predicate);
    public io.reactivex.Observable take(long);
    public io.reactivex.Observable takeUntil(io.reactivex.functions.Predicate);
    public io.reactivex.Observable distinctUntilChanged();
    public io.reactivex.Observable throttleLast(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);
    public io.reactivex.Observable throttleLatest(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler, boolean);
    public io.reactivex.Observable switchIfEmpty(io.reactivex.ObservableSource);
    public io.reactivex.Observable groupBy(io.reactivex.functions.Function);
    public io.reactivex.Observable delay(long, java.util.concurrent.TimeUnit);
    public io.reactivex.Observable buffer(int);
    public io.reactivex.Observable map(io.reactivex.functions.Function);

    public io.reactivex.Single all(io.reactivex.functions.Predicate);
    public io.reactivex.Single any(io.reactivex.functions.Predicate);
    public io.reactivex.Single collect(java.util.concurrent.Callable, io.reactivex.functions.BiConsumer);
    public io.reactivex.Single toList();
    public io.reactivex.Completable ignoreElements();
    public io.reactivex.Maybe firstElement();

    public io.reactivex.Completable flatMapCompletable(io.reactivex.functions.Function);
    public io.reactivex.Observable flatMapSingle(io.reactivex.functions.Function);
    public io.reactivex.Observable flatMapMaybe(io.reactivex.functions.Function);
}

-keep public class io.reactivex.Completable {
    public static io.reactivex.Completable defer(java.util.concurrent.Callable);
    public static io.reactivex.Completable create(io.reactivex.CompletableOnSubscribe);
    public static io.reactivex.Completable error(java.lang.Throwable);
    public static io.reactivex.Completable merge(java.lang.Iterable);
    public static io.reactivex.Completable timer(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);
    public static io.reactivex.Completable fromCallable(java.util.concurrent.Callable);
    public static io.reactivex.Completable complete();

    public ... subscribe(...);
    public io.reactivex.Completable observeOn(io.reactivex.Scheduler);
    public io.reactivex.Completable subscribeOn(io.reactivex.Scheduler);

    public io.reactivex.Completable doOnComplete(io.reactivex.functions.Action);
    public io.reactivex.Completable doOnSubscribe(io.reactivex.functions.Consumer);
    public io.reactivex.Completable doOnError(io.reactivex.functions.Consumer);
    public io.reactivex.Completable onErrorComplete();

    public io.reactivex.Completable delay(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);
    public io.reactivex.Completable timeout(long, java.util.concurrent.TimeUnit);

    public io.reactivex.Completable andThen(io.reactivex.CompletableSource);
    public io.reactivex.Single andThen(io.reactivex.SingleSource);

    public io.reactivex.Single toSingle(java.util.concurrent.Callable);
    public io.reactivex.Single toSingleDefault(java.lang.Object);
}

-keep public class io.reactivex.Single {
    public static io.reactivex.Single just(java.lang.Object);
    public static io.reactivex.Single fromCallable(java.util.concurrent.Callable);
    public static io.reactivex.Single create(io.reactivex.SingleOnSubscribe);
    public static io.reactivex.Single error(java.lang.Throwable);
    public static io.reactivex.Single zip(java.lang.Iterable, io.reactivex.functions.Function);
    public static io.reactivex.Single defer(java.util.concurrent.Callable);

    public io.reactivex.disposables.Disposable subscribe(io.reactivex.functions.Consumer, io.reactivex.functions.Consumer);
    public io.reactivex.Single observeOn(io.reactivex.Scheduler);
    public io.reactivex.Single subscribeOn(io.reactivex.Scheduler);

    public io.reactivex.Single doOnSuccess(io.reactivex.functions.Consumer);
    public io.reactivex.Single doOnError(io.reactivex.functions.Consumer);
    public io.reactivex.Single onErrorReturn(io.reactivex.functions.Function);
    public io.reactivex.Single onErrorResumeNext(io.reactivex.functions.Function);

    public io.reactivex.Single map(io.reactivex.functions.Function);

    public io.reactivex.Single flatMap(io.reactivex.functions.Function);
    public io.reactivex.Completable flatMapCompletable(io.reactivex.functions.Function);
    public io.reactivex.Maybe flatMapMaybe(io.reactivex.functions.Function);
    public io.reactivex.Observable flatMapObservable(io.reactivex.functions.Function);

    public io.reactivex.Single delaySubscription(long, java.util.concurrent.TimeUnit);

    public io.reactivex.Single timeout(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);

    public io.reactivex.Observable toObservable();
    public io.reactivex.Completable ignoreElement();

    public io.reactivex.Maybe filter(io.reactivex.functions.Predicate);
}

-keep public class io.reactivex.Maybe {
    public static io.reactivex.Maybe empty();
    public static io.reactivex.Maybe defer(java.util.concurrent.Callable);
    public static io.reactivex.Maybe create(io.reactivex.MaybeOnSubscribe);
    public static io.reactivex.Maybe fromCallable(java.util.concurrent.Callable);

    public io.reactivex.disposables.Disposable subscribe(io.reactivex.functions.Consumer, io.reactivex.functions.Consumer);
    public io.reactivex.disposables.Disposable subscribe(io.reactivex.functions.Consumer, io.reactivex.functions.Consumer, io.reactivex.functions.Action);
    public io.reactivex.Maybe observeOn(io.reactivex.Scheduler);
    public io.reactivex.Maybe subscribeOn(io.reactivex.Scheduler);

    public io.reactivex.Maybe doOnError(io.reactivex.functions.Consumer);
    public io.reactivex.Maybe onErrorComplete();

    public io.reactivex.Maybe switchIfEmpty(io.reactivex.MaybeSource);
    public io.reactivex.Maybe timeout(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler, io.reactivex.MaybeSource);
    public io.reactivex.Maybe timeout(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);

    public io.reactivex.Single toSingle(java.lang.Object);

    public io.reactivex.Completable flatMapCompletable(io.reactivex.functions.Function);
}

-keep public class io.reactivex.Flowable {
    public io.reactivex.Observable toObservable();
}

-keep public abstract class io.reactivex.Scheduler {
    public long now(java.util.concurrent.TimeUnit);
}

-keep public class io.reactivex.exceptions.Exceptions {
    public static java.lang.RuntimeException propagate(java.lang.Throwable);
}

-keep public class io.reactivex.disposables.Disposables {
    public static io.reactivex.disposables.Disposable disposed();
}

-keep public class io.reactivex.plugins.RxJavaPlugins {
    public static java.lang.Runnable onSchedule(java.lang.Runnable);
}

-keep public class io.reactivex.subjects.PublishSubject {
    public static ... create(...);
    public ... debounce(long, java.util.concurrent.TimeUnit, io.reactivex.Scheduler);
}

-keep public class io.reactivex.schedulers.Schedulers {
    public io.reactivex.Scheduler from(java.util.concurrent.Executor);
}

-keep public class io.reactivex.ObservableOnSubscribe { *; }
-keep public class io.reactivex.SingleOnSubscribe { *; }
-keep public class io.reactivex.CompletableOnSubscribe { *; }
-keep public class io.reactivex.MaybeOnSubscribe { *; }

-keep public class io.reactivex.Emitter { *; }
-keep public class io.reactivex.ObservableEmitter { *; }
-keep public class io.reactivex.SingleEmitter { *; }
-keep public class io.reactivex.MaybeEmitter { *; }
-keep public class io.reactivex.CompletableEmitter { *; }
-keep public class io.reactivex.ObservableSource { *; }
-keep public class io.reactivex.SingleSource { *; }
-keep public class io.reactivex.MaybeSource { *; }
-keep public class io.reactivex.CompletableSource { *; }
-keep public class io.reactivex.ObservableObserver { *; }
-keep public class io.reactivex.SingleObserver { *; }
-keep public class io.reactivex.CompletableObserver { *; }
-keep public class io.reactivex.MaybeObserver { *; }

-keep public class io.reactivex.functions.BiConsumer { *; }
-keep public class io.reactivex.functions.BiFunction { *; }

-keep public class io.reactivex.ObservableConverter { *; }
-keep public class io.reactivex.ObservableOperator { *; }
-keep public class io.reactivex.ObservableTransformer { *; }
-keep public class io.reactivex.annotations.NonNull { *; }
-keep public class io.reactivex.annotations.CheckReturnValue { *; }
-keep public class io.reactivex.annotations.Nullable { *; }

# Needed if using `groupBy` which we are
-keep public class io.reactivex.observables.GroupedObservable { *; }

# Stuff from here on forward is needed if you obfuscate
-keepnames public class io.reactivex.Scheduler { *; }
-keepnames public class io.reactivex.Scheduler$Worker { *; }
-keepnames public class io.reactivex.schedulers.Schedulers { *; }
-keepnames public class io.reactivex.subjects.PublishSubject { *; }
-keepnames public class io.reactivex.disposables.Disposable { *; }
-keepnames public class io.reactivex.functions.Action { *; }
-keepnames public class  io.reactivex.functions.Consumer { *; }
-keepnames public class io.reactivex.internal.functions.Functions { *; }
-keepnames public class io.reactivex.functions.Function { *; }
-keepnames public class io.reactivex.Observer { *; }
-keepnames public class io.reactivex.functions.Predicate { *; }
-keepnames public class io.reactivex.plugins.RxJavaPlugins { ... onError(...); }
-keepnames public class io.reactivex.exceptions.** { *; }
-keepnames public class io.reactivex.Single { void subscribeActual(io.reactivex.SingleObserver); }
-keepnames public class io.reactivex.Completable { void subscribeActual(io.reactivex.CompletableObserver); }
-keepnames public class io.reactivex.plugins.RxJavaPlugins {
    io.reactivex.Single onAssembly(io.reactivex.Single);
    io.reactivex.Completable onAssembly(io.reactivex.Completable);
}
-keepnames public class io.reactivex.disposables.Disposables { io.reactivex.disposables.Disposable empty(); }

# Don't understand why, but tests won't work unless this is here. Which is strange since tests
# should not be using the minified version anyway :/
-keep public class io.reactivex.schedulers.TestScheduler { *; }


## These are needed because of retrofit in log collection
-keepnames public class io.reactivex.BackpressureStrategy { *; }
-keepnames public class io.reactivex.plugins.RxJavaPlugins { io.reactivex.Flowable toFlowable(io.reactivex.BackpressureStrategy); }
-keepnames public class io.reactivex.plugins.RxJavaPlugins { io.reactivex.Observable onAssembly(io.reactivex.Observable); }
##