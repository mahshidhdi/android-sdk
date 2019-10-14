package co.pushe.plus.utils.rx;

import java.util.concurrent.Callable;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;


public final class SafeCompletableFromCallable extends Completable {
    final Callable<?> callable;

    public SafeCompletableFromCallable(Callable<?> callable) {
        this.callable = callable;
    }

    @Override
    protected void subscribeActual(CompletableObserver observer) {
        Disposable d = Disposables.empty();
        observer.onSubscribe(d);
        try {
            callable.call();
        } catch (Throwable e) {
            if (!d.isDisposed()) {
                observer.onError(e);
            }
            return;
        }
        if (!d.isDisposed()) {
            observer.onComplete();
        }
    }
}
