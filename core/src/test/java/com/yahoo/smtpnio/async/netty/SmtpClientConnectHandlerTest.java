/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Unit test for {@link SmtpClientConnectHandler}.
 */
public class SmtpClientConnectHandlerTest {

    /** Dummy session id. */
    private static final int SESSION_ID = 123456;

    /** Fields to check for cleanup. */
    private Set<Field> fieldsToCheck;

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        // Use reflection to get all declared non-primitive non-static fields (We do not care about inherited fields)
        final Class<?> c = SmtpClientConnectHandler.class;
        fieldsToCheck = new HashSet<>();

        for (final Field declaredField : c.getDeclaredFields()) {
            if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                fieldsToCheck.add(declaredField);
            }
        }
    }

    /**
     * Tests {@code decode} method when successful on a positive completion response.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws ExecutionException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testDecodeConnectionSuccess() throws IllegalArgumentException, InterruptedException, ExecutionException, TimeoutException,
            SmtpAsyncClientException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);

        final SmtpResponse resp = new SmtpResponse("220 Hello there");
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, resp, out);

        Mockito.verify(pipeline, Mockito.times(1)).remove(Mockito.anyString());
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        final SmtpAsyncCreateSessionResponse asyncSession = smtpFuture.get(5, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(asyncSession, "Expect SmtpAsyncSession not to be null");
    }

    /**
     * Tests {@code decode} method when failed.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testDecodeConnectFailed() throws IllegalArgumentException, InterruptedException, TimeoutException, SmtpAsyncClientException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(ctx.channel()).thenReturn(channel);

        final String msg = "504 some error response";
        final SmtpResponse resp = new SmtpResponse(msg);
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, resp, out);

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(pipeline, Mockito.times(1)).remove(Mockito.anyString());

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get(5, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ee) {
            ex = ee;
        }

        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        Assert.assertNotNull(ex.getCause(), "Expect cause.");
        Assert.assertEquals(ex.getCause().getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
        Mockito.verify(ctx, Mockito.times(1)).close();
        Mockito.verify(channel, Mockito.times(1)).isActive();
    }

    /**
     * Tests {@code exceptionCaught} method.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testExceptionCaught() throws IllegalArgumentException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(ctx.channel()).thenReturn(channel);

        final TimeoutException timeoutEx = new TimeoutException("too late, my friend");
        handler.exceptionCaught(ctx, timeoutEx);

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get(5, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ee) {
            ex = ee;
        }
        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        Assert.assertNotNull(ex.getCause(), "Expect cause.");
        Assert.assertEquals(ex.getCause().getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");

        Mockito.verify(ctx, Mockito.times(1)).close();
        Mockito.verify(channel, Mockito.times(1)).isActive();
    }

    /**
     * Tests exceptionCaught method.
     *
     * @throws IllegalArgumentException will not throw
     * @throws InterruptedException will not throw
     * @throws TimeoutException will not throw
     */
    @Test
    public void testExceptionCaughtChannelWasClosedAlready() throws IllegalArgumentException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<SmtpAsyncCreateSessionResponse>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(false); // return false to reflect channel closed
        Mockito.when(ctx.channel()).thenReturn(channel);

        final TimeoutException timeoutEx = new TimeoutException("too late, my friend");
        handler.exceptionCaught(ctx, timeoutEx);

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get(5, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ee) {
            ex = ee;
        }
        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        Assert.assertNotNull(ex.getCause(), "Expect cause.");
        Assert.assertEquals(ex.getCause().getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
        Mockito.verify(ctx, Mockito.times(0)).close(); // should not close since channel is already closed
        Mockito.verify(channel, Mockito.times(1)).isActive();
    }

    /**
     * Tests {@code userEventTriggered} method and the event is {@link IdleStateEvent}, state is {@code READ_IDLE}.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventReadIdle() throws IllegalArgumentException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(ctx.channel()).thenReturn(channel);
        final IdleStateEvent idleEvent = Mockito.mock(IdleStateEvent.class);
        Mockito.when(idleEvent.state()).thenReturn(IdleState.READER_IDLE);
        handler.userEventTriggered(ctx, idleEvent);

        // verify ChannelHandlerContext close happens
        Mockito.verify(ctx, Mockito.times(1)).close();
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get(5, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ee) {
            ex = ee;
        }
        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        final Throwable cause = ex.getCause();
        Assert.assertNotNull(cause, "Expect cause.");
        Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
        final SmtpAsyncClientException aEx = (SmtpAsyncClientException) cause;
        Assert.assertEquals(aEx.getFailureType(), FailureType.CONNECTION_FAILED_EXCEED_IDLE_MAX, "Failure type mismatched");

        Mockito.verify(ctx, Mockito.times(1)).close();
        Mockito.verify(channel, Mockito.times(1)).isActive();

        // call channelInactive, should not encounter npe
        handler.channelInactive(ctx);
    }

    /**
     * Tests {@code userEventTriggered} method and the event is {@link IdleStateEvent}, state is NOT {@code READ_IDLE}.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventNotReadIdle() throws IllegalArgumentException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final IdleStateEvent idleEvent = Mockito.mock(IdleStateEvent.class);
        Mockito.when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);
        handler.userEventTriggered(ctx, idleEvent);
        Assert.assertFalse(smtpFuture.isDone(), "Future should NOT be done");
    }

    /**
     * Tests {@code userEventTriggered} method and the event is NOT {@link IdleStateEvent}.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredNotIdleStateEvent() throws IllegalArgumentException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final String otherEvent = "king is coming!!!";
        handler.userEventTriggered(ctx, otherEvent);
        Assert.assertFalse(smtpFuture.isDone(), "Future should NOT be done");
    }

    /**
     * Tests {@code channelInactive} method.
     *
     * @throws IllegalAccessException will not throw in this test
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testChannelInactive() throws IllegalArgumentException, IllegalAccessException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final Logger logger = Mockito.mock(Logger.class);

        final String sessCtx = "Titanosauria@long.neck";
        final SmtpClientConnectHandler handler = new SmtpClientConnectHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessCtx);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        handler.channelInactive(ctx);

        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        ExecutionException ex = null;
        try {
            smtpFuture.get(5, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException ee) {
            ex = ee;
        }
        Assert.assertNotNull(ex, "Expect exception to be thrown.");
        Assert.assertNotNull(ex.getCause(), "Expect cause.");
        Assert.assertEquals(ex.getCause().getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }

        // call channelInactive again, should not encounter npe
        handler.channelInactive(ctx);
    }
}
