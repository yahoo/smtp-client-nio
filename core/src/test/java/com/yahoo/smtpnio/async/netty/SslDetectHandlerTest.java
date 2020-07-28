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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.NotSslRecordException;

/**
 * Unit test for {@link SslDetectHandler}.
 */
public class SslDetectHandlerTest {
    /** Dummy session id. */
    private static final long SESSION_ID = 123456;

    /** Debug record string template for Ssl detection. */
    private static final String SSL_DETECT_REC = "[{},{}] finish checking native SSL availability. "
            + "result={}, host={}, port={}, sslEnabled={}, sniNames={}";

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
                Mockito.eq("Available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(null));
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
                Mockito.eq("Available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(null));
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
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyObject());
        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        // Verify cleanup
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught and logger trace is enabled.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testNotSslRecordExceptionCaughtStarttlsTraceOn() throws IllegalArgumentException, IllegalAccessException {
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
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SSLException sslEx = Mockito.mock(SSLException.class);
        final NotSslRecordException notSslRecordException = Mockito.mock(NotSslRecordException.class);
        Mockito.when(sslEx.getCause()).thenReturn(notSslRecordException);
        handler.exceptionCaught(ctx, sslEx);

        // starttls is enabled, try to re-connect
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        Mockito.verify(smtpAsyncClient, Mockito.times(1)).createStarttlsSession(sessionData, sessionConfig, DebugMode.DEBUG_ON, smtpFuture);
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Not available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true),
                Mockito.eq(null));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught and debug is on.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testtNotSslRecordExceptionCaughtDebugOn()
            throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig().setEnableStarttls(false);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final SmtpAsyncClient smtpAsyncClient = Mockito.mock(SmtpAsyncClient.class);

        final SslDetectHandler handler = new SslDetectHandler(SESSION_ID, sessionData, sessionConfig, logger, DebugMode.DEBUG_ON,
                smtpAsyncClient,
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

        // starttls is enabled, try to re-connect
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        Mockito.verify(smtpAsyncClient, Mockito.times(1)).createStarttlsSession(sessionData, sessionConfig, DebugMode.DEBUG_ON, smtpFuture);
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Not available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true),
                Mockito.eq(null));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when NotSslRecordException is caught and debug is off.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testtNotSslRecordExceptionCaughtDebugOff()
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

        // starttls is enabled, try to re-connect
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        Mockito.verify(smtpAsyncClient, Mockito.times(1)).createStarttlsSession(sessionData, sessionConfig, DebugMode.DEBUG_OFF, smtpFuture);
        // verify logger
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyObject());

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

        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");

        Mockito.verify(ctx, Mockito.times(0)).close();
        Mockito.verify(channel, Mockito.times(0)).isActive();
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyObject());
    }

    /**
     * Tests exceptionCaught method when channel has already closed.
     *
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testExceptionCaughtChannelWasClosedAlready()
            throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException {
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

        final SSLException sslEx = Mockito.mock(SSLException.class);
        final NotSslRecordException notSslRecordException = Mockito.mock(NotSslRecordException.class);
        Mockito.when(sslEx.getCause()).thenReturn(notSslRecordException);
        handler.exceptionCaught(ctx, sslEx);

        // starttls is enabled, try to re-connect
        Assert.assertFalse(smtpFuture.isDone(), "Future shouldn't be done");
        Mockito.verify(smtpAsyncClient, Mockito.times(1)).createStarttlsSession(sessionData, sessionConfig, DebugMode.DEBUG_ON, smtpFuture);
        // verify logger
        // verify logger
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.eq(SSL_DETECT_REC), Mockito.eq(SESSION_ID), Mockito.eq("myCtx"),
                Mockito.eq("Not available"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(465), Mockito.eq(true), Mockito.eq(null));

        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
        Mockito.verify(ctx, Mockito.times(0)).close(); // should not close since channel is already closed
        Mockito.verify(channel, Mockito.times(1)).isActive();
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
