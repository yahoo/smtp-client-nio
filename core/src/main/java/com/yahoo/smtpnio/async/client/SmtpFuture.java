/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

/**
 * Future object for async operations.
 *
 * @param <V> the type of value the future holds
 */
public class SmtpFuture<V> implements Future<V> {

    /** Wait interval when the user calls get(). */
    private static final int GET_WAIT_INTERVAL_MILLIS = 1000;

    /** Is this future task done? */
    private final AtomicBoolean isDone = new AtomicBoolean(false);

    /** Is this future task cancelled? */
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    /** Holds the failure cause. */
    private final AtomicReference<Exception> causeRef = new AtomicReference<>();

    /** Used to provide mutex in threads. */
    private final Object lock = new Object();

    /** holds the result object. */
    private final AtomicReference<V> resultRef = new AtomicReference<>();

    /**
     * Is this Future cancelled?
     *
     * @return true if the future was cancelled
     */
    @Override
    public boolean isCancelled() {
        return isCancelled.get();
    }

    /**
     * Is the future task complete?
     *
     * @return true if the future task is complete
     */
    @Override
    public boolean isDone() {
        return isDone.get();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        done(new CancellationException(), true);
        return true; // returned flag means success in setting cancel state.
    }

    /**
     * Invoked when the worker has completed its processing.
     *
     * @param result the result to be set
     */
    public void done(@Nonnull final V result) {
        synchronized (lock) {
            if (!isDone.get()) {
                resultRef.set(result);
                isDone.set(true);
            }
            lock.notify();
        }
    }

    /**
     * Invoked when the service throws an exception.
     *
     * @param cause the exception that caused execution to fail
     */
    public void done(final Exception cause) {
        done(cause, false);
    }

    /**
     * Invoked when the service throws an exception.
     *
     * @param cause the exception that caused execution to fail
     * @param cancelled true if the call was the result of a cancellation
     */
    private void done(final Exception cause, final boolean cancelled) {
        synchronized (lock) {
            if (!isDone.get()) {
                causeRef.set(cause);
                isDone.set(true);
                isCancelled.set(cancelled);
            }
            lock.notify();
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (lock) {
            while (!isDone.get()) {
                lock.wait(GET_WAIT_INTERVAL_MILLIS);
            }
            lock.notify();
        }
        if (causeRef.get() != null) {
            throw new ExecutionException(causeRef.get());
        } else {
            return resultRef.get();
        }
    }

    @Override
    public V get(final long timeout, @Nonnull final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (lock) {
            if (!isDone.get()) {
                lock.wait(unit.toMillis(timeout));
            }
            lock.notify();
        }
        if (isDone.get()) {
            if (causeRef.get() != null) {
                throw new ExecutionException(causeRef.get());
            } else {
                return resultRef.get();
            }
        } else {
            throw new TimeoutException("Timeout reached.");
        }
    }
}
