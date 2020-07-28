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
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.StarttlsHandler.SSLHandShakeCompleteListener;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;

/**
 * Unit test for {@link StarttlsHandlerTest}.
 */
public class StarttlsHandlerTest {
    /** Dummy session id. */
    private static final long SESSION_ID = 123456;

    /** Fields to check for cleanup. */
    private Set<Field> fieldsToCheck;

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        // Use reflection to get all declared non-primitive non-static fields (We do not care about inherited fields)
        final Class<?> c = StarttlsHandler.class;
        fieldsToCheck = new HashSet<>();

        for (final Field declaredField : c.getDeclaredFields()) {
            if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                fieldsToCheck.add(declaredField);
            }
        }
    }

    /**
     * Tests {@code decode} method on a successful startTls connection.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testDecodeStartTlsSuccess() throws Exception {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext(
                "myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_OFF, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive EHLO response and send STARTTLS
        handler.decode(ctx, new SmtpResponse("250-STARTTLS\r\n"), out);
        handler.decode(ctx, new SmtpResponse("250 PIPELINE"), out);
        // receive starttls greeting and add SslHandler
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // ssl connection is successful
        final SmtpAsyncCreateSessionResponse createSessionResponse = Mockito.mock(SmtpAsyncCreateSessionResponse.class);
        final SSLHandShakeCompleteListener sslListener = handler.new SSLHandShakeCompleteListener(createSessionResponse, ctx);
        final Future<Channel> sslConnectionFuture = Mockito.mock(Future.class);
        Mockito.when(sslConnectionFuture.isSuccess()).thenReturn(true);
        sslListener.operationComplete(sslConnectionFuture);

        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        Mockito.verify(logger, Mockito.times(1)).debug("[{},{}] Starttls succeeds. Connection is now encrypted.", SESSION_ID, "myCtx");
        final SmtpAsyncCreateSessionResponse asyncSession = smtpFuture.get(5, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(asyncSession, "Expect SmtpAsyncSession not to be null");
    }

    /**
     * Tests {@code decode} method on a successful startTls connection by sending HELO as fallback.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testDecodeStartTlsSuccessFallbackHelo() throws Exception {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive bad EHLO response, send HELO
        handler.decode(ctx, new SmtpResponse("500 Command not recognized"), out);
        // receive HELO response and send STARTTLS
        handler.decode(ctx, new SmtpResponse("250-STARTTLS\r\n"), out);
        handler.decode(ctx, new SmtpResponse("250 PIPELINE"), out);
        // receive starttls greeting and add SslHandler
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);

        // ssl connection is successful
        final SmtpAsyncCreateSessionResponse createSessionResponse = Mockito.mock(SmtpAsyncCreateSessionResponse.class);
        final SSLHandShakeCompleteListener sslListener = handler.new SSLHandShakeCompleteListener(createSessionResponse, ctx);
        final Future<Channel> sslConnectionFuture = Mockito.mock(Future.class);
        Mockito.when(sslConnectionFuture.isSuccess()).thenReturn(true);
        sslListener.operationComplete(sslConnectionFuture);

        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        Mockito.verify(logger, Mockito.times(1)).debug("[{},{}] Starttls succeeds. Connection is now encrypted.", SESSION_ID, "myCtx");
        final SmtpAsyncCreateSessionResponse asyncSession = smtpFuture.get(5, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(asyncSession, "Expect SmtpAsyncSession not to be null");
    }

    /**
     * Tests {@code decode} method on a successful startTls connection with debug off.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testDecodeStartTlsSuccessDebugOff() throws Exception {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_OFF, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive EHLO response and send STARTTLS
        handler.decode(ctx, new SmtpResponse("250-STARTTLS\r\n"), out);
        handler.decode(ctx, new SmtpResponse("250 PIPELINE"), out);
        // receive starttls greeting and add SslHandler
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // ssl connection is successful
        final SmtpAsyncCreateSessionResponse createSessionResponse = Mockito.mock(SmtpAsyncCreateSessionResponse.class);
        final SSLHandShakeCompleteListener sslListener = handler.new SSLHandShakeCompleteListener(createSessionResponse, ctx);
        final Future<Channel> sslConnectionFuture = Mockito.mock(Future.class);
        Mockito.when(sslConnectionFuture.isSuccess()).thenReturn(true);
        sslListener.operationComplete(sslConnectionFuture);

        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString());
        final SmtpAsyncCreateSessionResponse asyncSession = smtpFuture.get(5, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(asyncSession, "Expect SmtpAsyncSession not to be null");
    }

    /**
     * Tests {@code decode} method failed on bad server greeting.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testDecodeStartTlsFailBadServerGreeting()
            throws IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive bad server greeting
        final String errMsg = "504 some error response";
        final SmtpResponse resp = new SmtpResponse(errMsg);
        handler.decode(ctx, resp, out);
        Mockito.verify(channel, Mockito.times(0)).writeAndFlush(Mockito.anyObject());
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] startTls failed, server response: {}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq(errMsg));

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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method failed on bad EHLO response and HELO response.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testDecodeStartTlsFailBadHeloResponse()
            throws IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive bad EHLO response, send HELO
        handler.decode(ctx, new SmtpResponse("500 Command not recognized"), out);
        // receive bad HELO response
        final String errMsg = "504 some error response";
        final SmtpResponse resp = new SmtpResponse(errMsg);
        handler.decode(ctx, resp, out);
        Mockito.verify(channel, Mockito.times(2)).writeAndFlush(Mockito.anyObject());
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] startTls failed, server response: {}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq(errMsg));

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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method failed on no STARTTLS capability in EHLO responses.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testDecodeFailNoStarttlsCapabilityEHLO()
            throws IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive no starttls capability
        final String errMsg = "250 PIPELINE";
        final SmtpResponse resp = new SmtpResponse(errMsg);
        handler.decode(ctx, resp, out);

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] startTls failed, server response: {}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq(errMsg));

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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method failed on no STARTTLS capability in HELO responses.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testDecodeFailNoStarttlsCapabilityHELO()
            throws IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive bad EHLO response, send HELO
        handler.decode(ctx, new SmtpResponse("500 Command not recognized"), out);
        // receive bad HELO response
        final String errMsg = "250 PIPELINE";
        final SmtpResponse resp = new SmtpResponse(errMsg);
        handler.decode(ctx, resp, out);
        Mockito.verify(channel, Mockito.times(2)).writeAndFlush(Mockito.anyObject());
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] startTls failed, server response: {}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq(errMsg));

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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method failed on bad STARTTLS response.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     */
    @Test
    public void testDecodeStartTlsFailBadStarttlsResponse()
            throws IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException, InterruptedException, TimeoutException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive bad EHLO response, send HELO
        handler.decode(ctx, new SmtpResponse("250 STARTTLS\r\n"), out);
        // receive bad starttls greeting
        final String errMsg = "504 some error response";
        final SmtpResponse resp = new SmtpResponse(errMsg);
        handler.decode(ctx, resp, out);

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq("[{},{}] startTls failed, server response: {}"), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"), Mockito.eq(errMsg));

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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code decode} method failed on SSL connection failure.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testDecodeStartTlsFailBadSSLConnection() throws Exception {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext(
                "myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);

        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_OFF, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        final List<Object> out = new ArrayList<>();

        // receive server greeting and send EHLO
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // receive EHLO response and send STARTTLS
        handler.decode(ctx, new SmtpResponse("250-STARTTLS\r\n"), out);
        handler.decode(ctx, new SmtpResponse("250 PIPELINE"), out);
        // receive starttls greeting and add SslHandler
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        // ssl connection is successful
        final SmtpAsyncCreateSessionResponse createSessionResponse = Mockito.mock(SmtpAsyncCreateSessionResponse.class);
        final SSLHandShakeCompleteListener sslListener = handler.new SSLHandShakeCompleteListener(createSessionResponse, ctx);
        final Future<Channel> sslConnectionFuture = Mockito.mock(Future.class);
        Mockito.when(sslConnectionFuture.isSuccess()).thenReturn(false);
        sslListener.operationComplete(sslConnectionFuture);

        Mockito.verify(pipeline, Mockito.times(1)).remove(handler);
        Assert.assertTrue(smtpFuture.isDone(), "Future should be done");
        Mockito.verify(logger, Mockito.times(1)).error("[{},{}] SslConnection failed after adding SslHandler.", SESSION_ID, "myCtx");
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

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests {@code exceptionCaught} method when exception is caught.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws InterruptedException will not throw in this test
     * @throws TimeoutException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testtExceptionCaught()
            throws InterruptedException, TimeoutException, IllegalArgumentException, IllegalAccessException, SmtpAsyncClientException {
        final SmtpFuture<SmtpAsyncCreateSessionResponse> smtpFuture = new SmtpFuture<>();
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

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

        Mockito.verify(logger, Mockito.times(1)).error(Mockito.eq(
                "[{},{}] Starttls Connection failed due to encountering exception:{}."),
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
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

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
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

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

        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] Starttls Connection failed due to taking longer than configured allowed time."), Mockito.eq(SESSION_ID),
                Mockito.eq("myCtx"));

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
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

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
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

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
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 465, true).setSessionContext("myCtx")
                .build();
        final Logger logger = Mockito.mock(Logger.class);
        final StarttlsHandler handler = new StarttlsHandler(smtpFuture, logger, DebugMode.DEBUG_ON, SESSION_ID, sessionData);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(false);
        Mockito.when(ctx.channel()).thenReturn(channel);

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
