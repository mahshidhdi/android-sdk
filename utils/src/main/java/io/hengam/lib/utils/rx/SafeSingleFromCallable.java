package io.hengam.lib.utils.rx;

import java.util.concurrent.Callable;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

public final class SafeSingleFromCallable<T> extends Single<T> {
    final Callable<? extends T> callable;

    public SafeSingleFromCallable(Callable<? extends T> callable) {
        this.callable = callable;
    }

    @Override
    protected void subscribeActual(SingleObserver<? super T> observer) {
        Disposable d = Disposables.empty();
        observer.onSubscribe(d);

        if (d.isDisposed()) {
            return;
        }
        T value;

        try {
            value = callable.call();
            if (value == null) {
                throw new NullPointerException("The callable returned a null value");
            }
        } catch (Throwable ex) {
            if (!d.isDisposed()) {
                observer.onError(ex);
            }
            return;
        }

        if (!d.isDisposed()) {
            observer.onSuccess(value);
        }
    }
}