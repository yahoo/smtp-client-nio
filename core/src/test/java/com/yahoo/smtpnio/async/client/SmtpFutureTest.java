/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpAsyncResponse;
import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * Unit test for {@link SmtpFuture}.
 */
public class SmtpFutureTest {

    /** Timeout time. */
    private static final long TIME_OUT_MILLIS = 1000L;

    /** The {@link SmtpAsyncResponse} for the future. */
    private SmtpAsyncResponse smtpAsyncResp;

    /**
     * An initial setup to mock and set the dependencies used by the {@link SmtpFuture}.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @BeforeMethod
    public void beforeMethod() throws SmtpAsyncClientException {
        final Collection<SmtpResponse> smtpResponses = new ArrayList<>();
        final SmtpResponse oneSmtpResponse = new SmtpResponse("500 Syntax error");
        smtpResponses.add(oneSmtpResponse);
        smtpAsyncResp = new SmtpAsyncResponse(smtpResponses);
    }

    /**
     * Tests to verify {@code isCancelled} method.
     */
    @Test
    public void testIsCancelledT() {
        assertFalse(new SmtpFuture<SmtpAsyncResponse>().isCancelled(), "isCancelled should be false upon creation");
    }

    /**
     * Tests to verify {@code isDone} method.
     */
    @Test
    public void testIsDone() {
        assertFalse(new SmtpFuture<SmtpAsyncResponse>().isDone(), "isDone should be false upon creation");
    }

    /**
     * Tests to verify {@code cancel} method when {@code mayInterruptIfRunning} is true, it will be cancelled.
     */
    @Test
    public void testCancel() {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();
        final boolean mayInterruptIfRunning = true;
        final boolean cancelSuccess = smtpFuture.cancel(mayInterruptIfRunning);

        assertTrue(cancelSuccess, "cancel operation should return true to reflect success");
        assertTrue(smtpFuture.isCancelled(), "isCancelled() should be true after cancel() operation.");
    }

    /**
     * Tests to verify {@code get} method when the response is received and {@code isDone} is true.
     *
     * @throws ExecutionException if thread is interrupted
     * @throws InterruptedException if thread fails
     */
    @Test
    public void testGetIsDoneTrueWithSmtpResult() throws InterruptedException, ExecutionException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();

        Assert.assertFalse(smtpFuture.isDone(), "Future should not be done before done is called");
        smtpFuture.done(smtpAsyncResp);
        // do done again, it should have no harm
        smtpFuture.done(smtpAsyncResp);

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        final SmtpAsyncResponse result = smtpFuture.get();

        assertNotNull(result, "result should not be null");
        assertEquals(result, smtpAsyncResp, "result mismatched");
    }

    /**
     * Tests to verify {@code get} method when Exception is received and {@code isDone} is true.
     *
     * @throws ExecutionException if thread is interrupted
     * @throws InterruptedException if thread fails
     */
    @Test(expectedExceptions = ExecutionException.class, expectedExceptionsMessageRegExp = "java.lang.Exception: first")
    public void testGetIsDoneTrueWithException() throws InterruptedException, ExecutionException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();

        smtpFuture.done(new Exception("first"));
        // do done again, it should be no-op
        smtpFuture.done(new Exception("second"));

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        smtpFuture.get();
    }

    /**
     * Tests to verify {@code get} method when future is cancelled.
     *
     * @throws InterruptedException if thread fails
     */
    @Test
    public void testGetFutureCancelled() throws InterruptedException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();
        smtpFuture.cancel(true);

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get();
        } catch (final ExecutionException ee) {
            ex = ee;
        }
        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        Assert.assertNotNull(ex.getCause(), "Expect cause.");
        Assert.assertEquals(ex.getCause().getClass(), CancellationException.class, "Expected result mismatched.");
    }

    /**
     * Tests to verify get with timeout method when the response is received and {@code isDone} is true.
     *
     * @throws ExecutionException if thread is interrupted
     * @throws InterruptedException if thread fails
     * @throws TimeoutException if result is not found after timeout
     */
    @Test
    public void testGetResultWithTimeoutLimit() throws InterruptedException, ExecutionException, TimeoutException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();
        smtpFuture.done(smtpAsyncResp);
        final SmtpAsyncResponse result = smtpFuture.get(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        assertNotNull(result, "result should not be null");
        assertEquals(result, smtpAsyncResp, "result mismatched");
    }

    /**
     * Tests to verify {@code get} with timeout method when a exception is received and {@code isDone} is true.
     *
     * @throws ExecutionException if thread is interrupted
     * @throws InterruptedException if thread fails
     * @throws TimeoutException if result is not found after timeout
     */
    @Test(expectedExceptions = ExecutionException.class, expectedExceptionsMessageRegExp = "java.lang.Exception: test")
    public void testGetExceptionWithTimeoutLimit() throws InterruptedException, ExecutionException, TimeoutException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();

        smtpFuture.done(new Exception("test"));
        smtpFuture.get(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Tests to verify {@code get} with timeout method when future is timed out and {@link TimeoutException} is thrown.
     *
     * @throws ExecutionException if thread is interrupted
     * @throws InterruptedException if thread fails
     * @throws TimeoutException if result is not found after timeout
     */
    @Test(expectedExceptions = TimeoutException.class, expectedExceptionsMessageRegExp = "Timeout reached.")
    public void testGetTimeoutException() throws InterruptedException, ExecutionException, TimeoutException {
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<>();
        final long mockTimeoutForFailure = 1L;
        smtpFuture.get(mockTimeoutForFailure, TimeUnit.MILLISECONDS);
    }

    /**
     * Tests to verify error callback is not called when the future is cancelled.
     */
    @Test
    public void testFutureErrorCallbackUponCancelled() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });
        smtpFuture.cancel(true);

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify error callback is not called when the future is done.
     */
    @Test
    public void testFutureErrorCallbackUponDone() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });
        smtpFuture.done(false);

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify error callback is called when the future fails.
     */
    @Test
    public void testFutureErrorCallbackUponException() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });
        smtpFuture.done(new RuntimeException());

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify done callback is not called when the future is cancelled.
     */
    @Test
    public void testFutureDoneCallbackUponCancelled() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.setDoneCallback(new Consumer<SmtpAsyncResponse>() {
            @Override
            public void accept(final SmtpAsyncResponse imapAsyncResponse) {
                called.set(true);
            }
        });
        smtpFuture.cancel(true);

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify done callback is called when the future is done.
     */
    @Test
    public void testFutureDoneCallbackUponDone() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setDoneCallback(new Consumer<Boolean>() {
            @Override
            public void accept(final Boolean r) {
                called.set(true);
            }
        });
        smtpFuture.done(false);

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify done callback is not called when the future fails.
     */
    @Test
    public void testFutureDoneCallbackUponException() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setDoneCallback(new Consumer<Boolean>() {
            @Override
            public void accept(final Boolean r) {
                called.set(true);
            }
        });
        smtpFuture.done(new RuntimeException());

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify cancel callback is called when the future is cancelled.
     */
    @Test
    public void testFutureCancelCallbackUponCancelled() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });
        smtpFuture.cancel(true);

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify cancel callback is not called when the future is done.
     */
    @Test
    public void testFutureCancelCallbackUponDone() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });
        smtpFuture.done(false);

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify cancel callback is not called when the future fails.
     */
    @Test
    public void testFutureCancelCallbackUponException() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });
        smtpFuture.done(new RuntimeException());

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify error callback is not called when the future is cancelled.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureErrorCallbackUponCancelledWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.cancel(true);
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify error callback is not called when the future is done.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureErrorCallbackUponDoneWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(false);
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify error callback is called when the future fails.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureErrorCallbackUponExceptionWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(new RuntimeException());
        smtpFuture.setExceptionCallback(new Consumer<Exception>() {
            @Override
            public void accept(final Exception e) {
                called.set(true);
            }
        });

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify done callback is not called when the future is cancelled.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureDoneCallbackUponCancelledWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.cancel(true);
        smtpFuture.setDoneCallback(new Consumer<SmtpAsyncResponse>() {
            @Override
            public void accept(final SmtpAsyncResponse imapAsyncResponse) {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify done callback is called when the future is done.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureDoneCallbackUponDoneWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(false);
        smtpFuture.setDoneCallback(new Consumer<Boolean>() {
            @Override
            public void accept(final Boolean r) {
                called.set(true);
            }
        });

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify done callback is not called when the future fails.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureDoneCallbackUponExceptionWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(new RuntimeException());
        smtpFuture.setDoneCallback(new Consumer<Boolean>() {
            @Override
            public void accept(final Boolean r) {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify cancel callback is called when the future is cancelled.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureCancelCallbackUponCancelledWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<SmtpAsyncResponse> smtpFuture = new SmtpFuture<SmtpAsyncResponse>();
        smtpFuture.cancel(true);
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });

        Assert.assertTrue(called.get(), "Callback should be run");
    }

    /**
     * Tests to verify cancel callback is not called when the future is done.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureCancelCallbackUponDoneWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(false);
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

    /**
     * Tests to verify cancel callback is not called when the future fails.
     *
     * The future execution is already finished when the callback is registered.
     */
    @Test
    public void testFutureCancelCallbackUponExceptionWhenFinished() {
        final AtomicBoolean called = new AtomicBoolean(false);
        final SmtpFuture<Boolean> smtpFuture = new SmtpFuture<Boolean>();
        smtpFuture.done(new RuntimeException());
        smtpFuture.setCanceledCallback(new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });

        Assert.assertFalse(called.get(), "Callback should not be run");
    }

}
