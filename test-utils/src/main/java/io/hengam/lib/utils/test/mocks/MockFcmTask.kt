package io.hengam.lib.utils.test.mocks

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor

class MockFcmTask<T>: Task<T>() {
    private var isComplete = false
    private var isSuccessful = false
    private var isCancelled = false
    private var exception: Exception? = null
    private var result: T? = null
    private var failureListeners: MutableList<OnFailureListener> = mutableListOf()
    private var successListeners: MutableList<OnSuccessListener<in T>> = mutableListOf()

    fun success(result: T) {
        isSuccessful = true
        isComplete = true
        this.result = result
        successListeners.forEach { it.onSuccess(result) }
    }

    fun fail(e: Exception) {
        exception = e
        isComplete = true
        failureListeners.forEach { it.onFailure(e) }
    }

    override fun isComplete(): Boolean = isComplete
    override fun isSuccessful(): Boolean = isSuccessful
    override fun isCanceled(): Boolean = isCancelled

    override fun getResult(): T? = result
    override fun <X : Throwable?> getResult(p0: Class<X>): T?  = result
    override fun getException(): Exception? = exception

    override fun addOnFailureListener(c: OnFailureListener): Task<T> {
        failureListeners.add(c)
        if (isComplete && !isSuccessful) {
            c.onFailure(exception!!)
        }
        return this
    }

    override fun addOnFailureListener(e: Executor, c: OnFailureListener): Task<T> = addOnFailureListener(c)
    override fun addOnFailureListener(a: Activity, c: OnFailureListener): Task<T> = addOnFailureListener(c)

    override fun addOnSuccessListener(c: OnSuccessListener<in T>): Task<T> {
        successListeners.add(c)
        if (isSuccessful) {
            c.onSuccess(result)
        }
        return this
    }

    override fun addOnSuccessListener(e: Executor, c: OnSuccessListener<in T>): Task<T> = addOnSuccessListener(c)
    override fun addOnSuccessListener(a: Activity, c: OnSuccessListener<in T>): Task<T> = addOnSuccessListener(c)
}