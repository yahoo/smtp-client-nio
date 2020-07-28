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

import javax.net.ssl.SSLException;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.client.SmtpAsyncClient;
import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionConfig;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Unit test for {@link SslDetectHandlerTest}.
 */
public class SslDetectHandlerTest {
    /** Dummy session id. */
    private static final long SESSION_ID = 123456;

    /** Debug record string template for Ssl detection. */
    private static final String SSL_DETECT_REC = "[{},{}] finish checking native SSL availability. "
            + "result={}, host={}, port={}, sslEnabled={}, startTlsEnabled={}, sniNames={}";

    /** Fields to check for cleanup. */
    private Set<Field> fieldsToCheck;

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        // Use reflection to get all declared non-primitive non-static fields (We do not care about inherited fields)
        final Class<?> c = SslDetectHandler.class;
        fieldsToCheck = new HashSet<>();

        for (final Field declaredField : c.getDeclaredFields()) {
            if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                fieldsToCheck.add(declaredField);
            }
        }
    }

    /**
     * Tests {@code decode} method when SslHandler successfully decrypts server response with logger trace enabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testDecodeSuccessTraceOn() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final ByteBuf in = Mockito.mock(ByteBuf.class);
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, in, out);

        // verify smtpFuture
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(true), Mockito.eq(null));
        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        // Verify cleanup
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method when SslHandler successfully decrypts server response with logger trace disabled but debug enabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testDecodeConnectionSuccessTraceOffDebugOn() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final ByteBuf in = Mockito.mock(ByteBuf.class);
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, in, out);

        // verify smtpFuture
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(true), Mockito.eq(null));
        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        // Verify cleanup
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method when SslHandler successfully decrypts server response with both debug and logger trace disabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testDecodeConnectionSuccessDebugOffTraceOff() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_OFF, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final ByteBuf in = Mockito.mock(ByteBuf.class);
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, in, out);

        // verify smtpFuture
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        // verify logger
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyObject());
        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        // Verify cleanup
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught and starttls is enabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testNotSslRecordExceptionCaughtStarttlsEnabled() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(true);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SSLException sslEx = Mockito.mock(SSLException.class);
        final NotSslRecordException notSslRecordException = Mockito.mock(NotSslRecordException.class);
        Mockito.when(sslEx.getCause()).thenReturn(notSslRecordException);
        handler.exceptionCaught(ctx, sslEx);

        // starttls is enabled, try to re-connect
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        Mockito.verify(smtpAsyncClient, Mockito.times(1)).createStarttlsSession(sessionData, sessionConfig, DebugMode.DEBUG_ON, smtpFuture);
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Not available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(true),
                Mockito.eq(null));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught but starttls is disabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testtNotSslRecordExceptionCaughtStarttlsDisabled()
            throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_OFF, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SSLException sslEx = Mockito.mock(SSLException.class);
        final NotSslRecordException notSslRecordException = Mockito.mock(NotSslRecordException.class);
        Mockito.when(sslEx.getCause()).thenReturn(notSslRecordException);
        handler.exceptionCaught(ctx, sslEx);

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

        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Not available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(false),
                Mockito.eq(null));
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq("failure"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(false),
                Mockito.eq(null), Mockito.isA(SmtpAsyncClientException.class));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught but starttls is disabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testtNotSslRecordExceptionCaughtStarttlsDisabledDebugOff()
            throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_OFF, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SSLException sslEx = Mockito.mock(SSLException.class);
        final NotSslRecordException notSslRecordException = Mockito.mock(NotSslRecordException.class);
        Mockito.when(sslEx.getCause()).thenReturn(notSslRecordException);
        handler.exceptionCaught(ctx, sslEx);

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

        // verify logger
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyObject());

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when other exception is caught.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testtOtherExceptionCaught() throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

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

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] Connection failed due to encountering exception:{}."),
                Mockito.eq(SESSION_ID), Mockito.eq("myCtx"), Mockito.isA(TimeoutException.class));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests exceptionCaught method when channel has already closed.
     *
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testExceptionCaughtChannelWasClosedAlready() throws InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

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
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventReadIdle()
            throws IllegalArgumentException, InterruptedException, TimeoutException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

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

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] Connection failed due to taking longer than configured allowed time."),
                Mockito.eq(SESSION_ID), Mockito.eq("myCtx"));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code userEventTriggered} method and the event is {@link IdleStateEvent}, state is NOT {@code READ_IDLE}.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventNotReadIdle() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

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
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredNotIdleStateEvent() throws IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

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
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON, smtpAsyncClient,
                smtpFuture);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);

        handler.channelInactive(ctx);
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
    }
}
