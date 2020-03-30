/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

import javax.annotation.Nonnull;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Implementation for the SMTP NIO client.
 */
public class SmtpAsyncClient {

    /** Handler name for the ssl handler. */
    public static final String SSL_HANDLER = "sslHandler";

    /** Debug record string template. */
    private static final String CONNECT_RESULT_REC = "[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}";

    /** The SSL context. */
    private final SslContext sslContext;

    /** Logger for debugging messages, errors and other info. */
    @Nonnull
    private final Logger logger;

    /** Counter to uniquely identify sessions. */
    private final AtomicLong sessionCount = new AtomicLong(1);

    /** The Netty bootstrap. */
    private final Bootstrap bootstrap;

    /** Event loop group that will serve all channels for the SMTP client. */
    private final EventLoopGroup group;

    /**
     * Constructs a NIO-based SMTP client.
     *
     * @param numThreads number of threads to be used by the SMTP client
     * @throws SSLException when encountering an error to create a {@link SslContext} for this client
     */
    public SmtpAsyncClient(final int numThreads) throws SSLException {
        this(new Bootstrap(), new NioEventLoopGroup(numThreads), LoggerFactory.getLogger(SmtpAsyncClient.class));
    }

    /**
     * Constructs a NIO-based SMTP client.
     *
     * @param bootstrap a {@link Bootstrap} instance that makes it easy to bootstrap a {@link Channel} to use for clients
     * @param group an {@link EventLoopGroup} instance allowing registering {@link Channel}s for processing later selection during the event loop
     * @param logger {@link Logger} instance
     * @throws SSLException when encountering an error to create a {@link SslContext} for this client
     */
    SmtpAsyncClient(@Nonnull final Bootstrap bootstrap, @Nonnull final EventLoopGroup group, @Nonnull final Logger logger) throws SSLException {
        this.sslContext = SslContextBuilder.forClient().build();
        this.logger = logger;
        this.bootstrap = bootstrap;
        this.group = group;
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);
    }

    /**
     * Connects to the remote server asynchronously and returns a {@link SmtpAsyncCreateSessionResponse} future.
     *
     * @param sessionData a {@link SmtpAsyncSessionData} object containing information about the connection
     * @param config configuration to be used for this session/connection
     * @param debugOption the debugging option used
     * @return the future containing the result of the request
     */
    public Future<SmtpAsyncCreateSessionResponse> createSession(@Nonnull final SmtpAsyncSessionData sessionData,
                                                                @Nonnull final SmtpAsyncSessionConfig config,
                                                                @Nonnull final SmtpAsyncSession.DebugMode debugOption) {
        bootstrap.handler(new SmtpClientChannelInitializer(config.getReadTimeout(), TimeUnit.MILLISECONDS));
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout());

        final String host = sessionData.getHost();

        if (host == null) {
            throw new IllegalArgumentException("host cannot be null");
        }

        final int port = sessionData.getPort();
        final boolean enableSsl = sessionData.isSslEnabled();
        final InetSocketAddress localAddress = sessionData.getLocalAddress();
        final Collection<String> sniNames = sessionData.getSniNames();
        final Object sessionCtx = sessionData.getSessionContext();

        final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture = new SmtpFuture<>();
        final ChannelFuture nettyConnectFuture =
                localAddress == null ? bootstrap.connect(host, port) : bootstrap.connect(new InetSocketAddress(host, port), localAddress);

        // setup listener to handle connection done event
        nettyConnectFuture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
            @Override
            public void operationComplete(final io.netty.util.concurrent.Future<? super Void> future) {
                if (!future.isSuccess()) {
                    final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.WRITE_TO_SERVER_FAILED,
                            future.cause());
                    sessionFuture.done(ex);
                    logger.error(CONNECT_RESULT_REC, "N/A", sessionCtx, "failure", host, port, enableSsl, sniNames, ex);
                    return;
                }
                // add the session specific handlers
                final Channel ch = nettyConnectFuture.channel();
                final ChannelPipeline pipeline = ch.pipeline();

                // SSL/TLS is required in many SMTP servers
                if (enableSsl) {
                    if (sniNames != null && !sniNames.isEmpty()) { // SNI support
                        final List<SNIServerName> serverNames = new ArrayList<>();
                        for (final String sni : sniNames) {
                            serverNames.add(new SNIHostName(sni));
                        }
                        final SSLParameters params = new SSLParameters();
                        params.setServerNames(serverNames);
                        final SSLEngine engine = sslContext.newEngine(ch.alloc(), host, port);
                        engine.setSSLParameters(params);
                        pipeline.addFirst(SSL_HANDLER, new SslHandler(engine)); // in / outbound
                    } else {
                        pipeline.addFirst(SSL_HANDLER, sslContext.newHandler(ch.alloc(), host, port));
                    }
                }

                final long sessionId = sessionCount.getAndUpdate(new LongUnaryOperator() { // atomic update
                    @Override
                    public long applyAsLong(final long counter) { // increment by 1 unless it overflows
                        return counter + 1 < 0 ? 1 : counter + 1;
                    }
                });
                pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME,
                        new SmtpClientConnectHandler(sessionFuture, LoggerFactory.getLogger(SmtpAsyncSessionImpl.class),
                                debugOption, sessionId, sessionCtx));
                if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                    logger.debug(CONNECT_RESULT_REC, sessionId, sessionCtx, "success",  host, port, enableSsl, sniNames);
                }
            }
        });
        return sessionFuture;
    }

    /**
     * Shuts down the client gracefully.
     */
    public void shutdown() {
        group.shutdownGracefully();
    }
}
