/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLException;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;
import com.yahoo.smtpnio.client.SmtpClientRespReader;
import com.yahoo.smtpnio.command.SmtpClientRespDecoder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Unit test for {@link SmtpAsyncClient}.
 */
public class SmtpAsyncClientTest {

    /**
     * Tests the public constructor of {@link SmtpAsyncClient}.
     */
    @Test
    public void testConstructor() {
        SmtpAsyncClient client = null;
        client = new SmtpAsyncClient(1);
        Assert.assertNotNull(client, "Client was not successfully created");
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        Whitebox.setInternalState(client, "group", group);
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} when given bad input for the session data.
     *
     * @throws Exception expected to throw when encountering bad input
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateSessionBadData() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);

        client.createSession(SmtpAsyncSessionData.newBuilder(null, 465, true).build(), new SmtpAsyncSessionConfig(),
                SmtpAsyncSession.DebugMode.DEBUG_OFF);
    }

    /**
     * Tests the behavior of {@code createSession} on a successful SSL connection.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionSuccessSSl() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, true).setSessionContext(
                "myCtx")
                .build();

        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(sessionData, new SmtpAsyncSessionConfig(),
                SmtpAsyncSession.DebugMode.DEBUG_ON);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq("myCtx"), Mockito.eq("success"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(993),
                Mockito.eq(true), Mockito.eq(null));

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} on a successful SSL connection with startTls disabled.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionSuccessSSlWithoutStartTls() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, true).setSessionContext("myCtx")
                .build();

        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(sessionData, new SmtpAsyncSessionConfig().setEnableStarttls(
                false),
                SmtpAsyncSession.DebugMode.DEBUG_ON);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq("myCtx"), Mockito.eq("success"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(993),
                Mockito.eq(true), Mockito.eq(null));

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} on a successful connection without SSL or StartTls.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionSuccessNoSSL() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, false).build(), new SmtpAsyncSessionConfig().setEnableStarttls(false),
                SmtpAsyncSession.DebugMode.DEBUG_ON);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq(null), Mockito.eq("success"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(993),
                Mockito.eq(false), Mockito.eq(null));

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createStarttlsSession} on a successful plain connection.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateStarttlsSessionSuccess() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final SmtpFuture<SmtpAsyncCreateSessionResponse> future = new SmtpFuture<SmtpAsyncCreateSessionResponse>();
        client.createStarttlsSession(SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, false).build(),
                new SmtpAsyncSessionConfig().setEnableStarttls(true), SmtpAsyncSession.DebugMode.DEBUG_ON, future);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), StarttlsHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq(null), Mockito.eq("success"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(993),
                Mockito.eq(false), Mockito.eq(null));

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} on a successful connection.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionSuccessDebugOn() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, true).setSessionContext("myCtx").build(), new SmtpAsyncSessionConfig(),
                SmtpAsyncSession.DebugMode.DEBUG_OFF);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq("myCtx"), Mockito.eq("success"), Mockito.eq("smtp.one.two.three.com"), Mockito.eq(993),
                Mockito.eq(true), Mockito.eq(null));

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} on a successful connection, without debugging messages.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionSuccessDebugOff() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.one.two.three.com", 993, true).setSessionContext("myCtx").build(), new SmtpAsyncSessionConfig(),
                SmtpAsyncSession.DebugMode.DEBUG_OFF);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");

        // verify to make sure logger isn't called
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyBoolean());

        // Tests proper shutdown
        client.shutdown();
        Mockito.verify(group, Mockito.times(1)).shutdownGracefully();
    }

    /**
     * Tests the behavior of {@code createSession} on a failed connection with SSL enabled.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionFailedWithSSL() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(false);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(nettyChannel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.test.example.com", 123, true).setSessionContext("myCtxFail").build(),
                new SmtpAsyncSessionConfig(), SmtpAsyncSession.DebugMode.DEBUG_OFF);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 0, "Unexpected count of ChannelHandler added.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq("N/A"),
                Mockito.eq("myCtxFail"), Mockito.eq("failure"), Mockito.eq("smtp.test.example.com"), Mockito.eq(123), Mockito.eq(true),
                Mockito.eq(null), Mockito.isA(SmtpAsyncClientException.class));
        Mockito.verify(nettyChannel, Mockito.times(1)).isActive();
        Mockito.verify(nettyChannel, Mockito.times(1)).close();
    }

    /**
     * Tests the behavior of {@code createSession} on a failed connection with SSL enabled but startTls disabled.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionFailedSSLWithoutStartTls() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(false);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(nettyChannel.isActive()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.test.example.com", 123, true).setSessionContext("myCtxFail").build(),
                new SmtpAsyncSessionConfig().setEnableStarttls(false), SmtpAsyncSession.DebugMode.DEBUG_OFF);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 0, "Unexpected count of ChannelHandler added.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq("N/A"),
                Mockito.eq("myCtxFail"), Mockito.eq("failure"), Mockito.eq("smtp.test.example.com"), Mockito.eq(123), Mockito.eq(true),
                Mockito.eq(null), Mockito.isA(SmtpAsyncClientException.class));
        Mockito.verify(nettyChannel, Mockito.times(1)).isActive();
        Mockito.verify(nettyChannel, Mockito.times(1)).close();
    }

    /**
     * Tests the behavior of {@code createSession} on a failed connection with SSL and StartTls disabled.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateSessionFailedNoSSL() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(false);
        Mockito.when(nettyChannel.isActive()).thenReturn(false);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.test.example.com", 123, false).setSessionContext("myCtxFail")
                        .build(),
                new SmtpAsyncSessionConfig().setEnableStarttls(false), SmtpAsyncSession.DebugMode.DEBUG_OFF);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 0, "Unexpected count of ChannelHandler added.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq("N/A"),
                Mockito.eq("myCtxFail"), Mockito.eq("failure"), Mockito.eq("smtp.test.example.com"), Mockito.eq(123), Mockito.eq(false),
                Mockito.eq(null), Mockito.isA(SmtpAsyncClientException.class));
        Mockito.verify(nettyChannel, Mockito.times(1)).isActive();
        Mockito.verify(nettyChannel, Mockito.times(0)).close(); // since channel is not active
    }

    /**
     * Tests the behavior of {@code createStarttlsSession} on a failed connection.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testCreateStarttlsSessionFailed() throws Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(false);
        Mockito.when(nettyChannel.isActive()).thenReturn(false);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final SmtpFuture<SmtpAsyncCreateSessionResponse> future = new SmtpFuture<SmtpAsyncCreateSessionResponse>();
        client.createStarttlsSession(SmtpAsyncSessionData.newBuilder("smtp.test.example.com", 123, true).setSessionContext("myCtxFail").build(),
                new SmtpAsyncSessionConfig().setEnableStarttls(true), SmtpAsyncSession.DebugMode.DEBUG_OFF, future);

        Assert.assertNotNull(future, "The response future should not be null");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 0, "Unexpected count of ChannelHandler added.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq("N/A"),
                Mockito.eq("myCtxFail"), Mockito.eq("failure"), Mockito.eq("smtp.test.example.com"), Mockito.eq(123), Mockito.eq(false),
                Mockito.eq(null), Mockito.isA(SmtpAsyncClientException.class));
        Mockito.verify(nettyChannel, Mockito.times(1)).isActive();
        Mockito.verify(nettyChannel, Mockito.times(0)).close(); // since channel is not active
    }

    /**
     * Tests {@code createSession} method when successful.
     *
     * @throws SSLException will not throw
     * @throws URISyntaxException will not throw
     * @throws Exception when calling operationComplete() at {@link GenericFutureListener}
     */
    @Test
    public void testCreateSessionNoLocalAddressSNIEmptySuccessful() throws SSLException, URISyntaxException, Exception {

        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);

        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        Whitebox.setInternalState(client, "sessionCount", new AtomicLong(Long.MAX_VALUE - 1)); // test edge case when ID is near max

        final SmtpAsyncSessionConfig config = new SmtpAsyncSessionConfig();
        config.setConnectionTimeout(5000);
        config.setReadTimeout(6000);

        // test create session
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.foo.com", 993, true).setSessionContext("N/A").setSniNames(Collections.emptyList()).build(),
                new SmtpAsyncSessionConfig(), SmtpAsyncSession.DebugMode.DEBUG_ON);

        // verify session creation
        Assert.assertNotNull(future, "Future for SmtpAsyncSession should not be null.");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");
        // verify if session level is on, whether debug call will be called
        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(Long.MAX_VALUE - 1)), Mockito.eq("N/A"), Mockito.eq("success"), Mockito.eq("smtp.foo.com"), Mockito.eq(993),
                Mockito.eq(true), Mockito.eq(Collections.emptyList()));

        // Next ID should be the max value of Long
        Assert.assertEquals(((AtomicLong) Whitebox.getInternalState(client, "sessionCount")).get(), Long.MAX_VALUE,
                "The session ID did not wrap around properly");
    }

    /**
     * Tests createSession method with failure when channel is null.
     *
     * @throws SSLException will not throw
     * @throws URISyntaxException will not throw
     * @throws Exception when calling operationComplete() at GenericFutureListener
     */
    @Test
    public void testCreateSessionConnectionFailedChannelIsNull() throws SSLException, URISyntaxException, Exception {
        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(false);

        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);

        Mockito.when(nettyConnectFuture.cause()).thenReturn(new ConnectTimeoutException("connection timed out"));
        Mockito.when(bootstrap.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(nettyConnectFuture);

        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        final SmtpAsyncClient client = new SmtpAsyncClient(bootstrap, group, logger);
        final SmtpAsyncSessionConfig config = new SmtpAsyncSessionConfig();
        config.setConnectionTimeout(5000);
        config.setReadTimeout(6000);

        // test create session
        final Future<SmtpAsyncCreateSessionResponse> future = client.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.foo.com", 993, true).setSessionContext("N/A").setSniNames(Collections.emptyList()).build(),
                new SmtpAsyncSessionConfig(), SmtpAsyncSession.DebugMode.DEBUG_ON); // verify session creation

        Assert.assertNotNull(future, "Future for SmtpAsyncSession should not be null.");
        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);

        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));
        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.anyString(), Mockito.anyInt());

        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);

        Mockito.verify(nettyPipeline, Mockito.times(0)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 0, "number of handlers mismatched.");
        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(0)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 0, "Unexpected count of ChannelHandler added.");

        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).error(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"), Mockito.eq("N/A"),
                Mockito.eq("N/A"), Mockito.eq("failure"), Mockito.eq("smtp.foo.com"), Mockito.eq(993), Mockito.eq(true),
                Mockito.eq(Collections.emptyList()), Mockito.isA(SmtpAsyncClientException.class));
        Assert.assertTrue(future.isDone(), "Future should be done.");
        SmtpAsyncClientException actual = null;
        try {
            future.get(5, TimeUnit.SECONDS);
            Assert.fail("Should throw connect timeout exception");
        } catch (final ExecutionException | InterruptedException ex) {
            Assert.assertNotNull(ex, "Expect exception to be thrown.");
            Assert.assertNotNull(ex.getCause(), "Expect cause.");
            Assert.assertEquals(ex.getClass(), ExecutionException.class, "Class type mismatch.");
            final Exception exception = (Exception) ex.getCause();
            Assert.assertEquals(exception.getClass(), SmtpAsyncClientException.class, "exception type mismatch." + ex);
            actual = (SmtpAsyncClientException) exception;
        }

        Assert.assertNotNull(actual.getCause(), "Cause should not be null");
        Assert.assertEquals(actual.getCause().getClass(), ConnectTimeoutException.class, "Cause should be connection timeout exception");
        Assert.assertSame(actual.getCause(), nettyConnectFuture.cause(), "Cause should be same object");
        Assert.assertEquals(actual.getFailureType(), FailureType.WRITE_TO_SERVER_FAILED, "Exception type should be CONNECTION_TIMEOUT_EXCEPTION");
    }

    /**
     * Tests createSession method when successful with class level debug on and session level debug off.
     *
     * @throws SSLException will not throw
     * @throws URISyntaxException will not throw
     * @throws Exception when calling operationComplete() at {@link GenericFutureListener}
     */
    @Test
    public void testCreateSessionWithLocalAddressSniSuccessfulSessionDebugOn() throws SSLException, URISyntaxException, Exception {

        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class))).thenReturn(nettyConnectFuture);

        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        final SmtpAsyncClient aclient = new SmtpAsyncClient(bootstrap, group, logger);

        final SmtpAsyncSessionConfig config = new SmtpAsyncSessionConfig();
        config.setConnectionTimeout(5000);
        config.setReadTimeout(6000);
        final List<String> sniNames = Collections.singletonList("sni.domain.name.org");
        // test create session
        final InetSocketAddress localAddress = new InetSocketAddress("10.10.10.10", 23112);
        final Future<SmtpAsyncCreateSessionResponse> future = aclient.createSession(
                SmtpAsyncSessionData.newBuilder("smtp.foo.com", 993, true).setSessionContext("someUserId").setSniNames(sniNames)
                        .setLocalAddress(localAddress).build(),
                config,
                SmtpAsyncSession.DebugMode.DEBUG_ON);

        // verify session creation
        Assert.assertNotNull(future, "Future for SmtpAsyncSession should not be null.");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");

        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(1)), Mockito.eq("someUserId"), Mockito.eq("success"), Mockito.eq("smtp.foo.com"), Mockito.eq(993),
                Mockito.eq(true), Mockito.eq(Collections.singletonList("sni.domain.name.org")));

        // Next ID should be one more
        Assert.assertEquals(((AtomicLong) Whitebox.getInternalState(aclient, "sessionCount")).get(), 2,
                "The session ID did not wrap around properly");
    }

    /**
     * Tests the correctness of the session ID wrap around when overflowing to the negatives.
     *
     * @throws Exception will not throw in this test
     */
    @Test
    public void testIdWrapAround() throws Exception {

        final Bootstrap bootstrap = Mockito.mock(Bootstrap.class);
        final ChannelFuture nettyConnectFuture = Mockito.mock(ChannelFuture.class);
        Mockito.when(nettyConnectFuture.isSuccess()).thenReturn(true);
        final Channel nettyChannel = Mockito.mock(Channel.class);
        final ChannelPipeline nettyPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(nettyChannel.pipeline()).thenReturn(nettyPipeline);
        Mockito.when(nettyConnectFuture.channel()).thenReturn(nettyChannel);
        Mockito.when(bootstrap.connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class))).thenReturn(nettyConnectFuture);

        final EventLoopGroup group = Mockito.mock(EventLoopGroup.class);
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        final SmtpAsyncClient aclient = new SmtpAsyncClient(bootstrap, group, logger);

        Whitebox.setInternalState(aclient, "sessionCount", new AtomicLong(Long.MAX_VALUE));

        final SmtpAsyncSessionConfig config = new SmtpAsyncSessionConfig();
        config.setConnectionTimeout(5000);
        config.setReadTimeout(6000);
        final List<String> sniNames = Collections.singletonList("sni.domain.name.org");
        // test create session
        final InetSocketAddress localAddress = new InetSocketAddress("10.10.10.10", 23112);
        final Future<SmtpAsyncCreateSessionResponse> future = aclient.createSession(SmtpAsyncSessionData.newBuilder("smtp.foo.com", 993, true)
                .setSessionContext("user1").setSniNames(sniNames).setLocalAddress(localAddress).build(), config, SmtpAsyncSession.DebugMode.DEBUG_ON);

        // verify session creation
        Assert.assertNotNull(future, "Future for SmtpAsyncSession should not be null.");

        final ArgumentCaptor<SmtpClientChannelInitializer> initializerCaptor = ArgumentCaptor.forClass(SmtpClientChannelInitializer.class);
        Mockito.verify(bootstrap, Mockito.times(1)).handler(initializerCaptor.capture());
        Assert.assertEquals(initializerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");
        final SmtpClientChannelInitializer initializer = initializerCaptor.getAllValues().get(0);

        // should not call this connect
        Mockito.verify(bootstrap, Mockito.times(1)).connect(Mockito.any(SocketAddress.class), Mockito.any(SocketAddress.class));

        // should call following connect
        Mockito.verify(bootstrap, Mockito.times(0)).connect(Mockito.anyString(), Mockito.anyInt());
        final ArgumentCaptor<GenericFutureListener> listenerCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        Mockito.verify(nettyConnectFuture, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpClientChannelInitializer.");

        // test connection established and channel initialized new
        final SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
        final ChannelPipeline socketPipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(socketChannel.pipeline()).thenReturn(socketPipeline);
        initializer.initChannel(socketChannel);

        // verify initChannel
        final ArgumentCaptor<ChannelHandler> handlerCaptor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(socketPipeline, Mockito.times(5)).addLast(Mockito.anyString(), handlerCaptor.capture());
        Assert.assertEquals(handlerCaptor.getAllValues().size(), 5, "Unexpected count of ChannelHandler added.");
        // following order should be preserved
        Assert.assertEquals(handlerCaptor.getAllValues().get(0).getClass(), IdleStateHandler.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(1).getClass(), SmtpClientRespReader.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(2).getClass(), StringDecoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(3).getClass(), StringEncoder.class, "expected class mismatched.");
        Assert.assertEquals(handlerCaptor.getAllValues().get(4).getClass(), SmtpClientRespDecoder.class, "expected class mismatched.");

        // verify GenericFutureListener.operationComplete()
        final GenericFutureListener listener = listenerCaptor.getAllValues().get(0);
        listener.operationComplete(nettyConnectFuture);
        final ArgumentCaptor<ChannelHandler> handlerCaptorFirst = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addFirst(Mockito.anyString(), handlerCaptorFirst.capture());
        Assert.assertEquals(handlerCaptorFirst.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorFirst.getAllValues().get(0).getClass(), SslHandler.class, "expected class mismatched.");

        final ArgumentCaptor<ChannelHandler> handlerCaptorAfter = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addAfter(Mockito.anyString(), Mockito.anyString(), handlerCaptorAfter.capture());
        Assert.assertEquals(handlerCaptorAfter.getAllValues().size(), 1, "number of handlers mismatched.");
        Assert.assertEquals(handlerCaptorAfter.getAllValues().get(0).getClass(), SslDetectHandler.class, "expected class mismatched.");


        final ArgumentCaptor<ChannelHandler> handlerCaptorLast = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(nettyPipeline, Mockito.times(1)).addLast(Mockito.anyString(), handlerCaptorLast.capture());
        Assert.assertEquals(handlerCaptorLast.getAllValues().size(), 1, "Unexpected count of ChannelHandler added.");
        Assert.assertEquals(handlerCaptorLast.getAllValues().get(0).getClass(), SmtpClientConnectHandler.class, "expected class mismatched.");

        // verify logging messages
        Mockito.verify(logger, Mockito.times(1)).debug(
                Mockito.eq("[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}"),
                Mockito.eq(Long.valueOf(Long.MAX_VALUE)), // biggest possible ID
                Mockito.eq("user1"), Mockito.eq("success"), Mockito.eq("smtp.foo.com"), Mockito.eq(993), Mockito.eq(
                        true),
                Mockito.eq(Collections.singletonList("sni.domain.name.org")));

        // Check to make sure the next ID cycled back to 1 to avoid negative IDs
        Assert.assertEquals(((AtomicLong) Whitebox.getInternalState(aclient, "sessionCount")).get(), 1,
                "The session ID did not wrap around properly");
    }
}
