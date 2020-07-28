/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;
import com.yahoo.smtpnio.async.netty.SslDetectHandler;
import com.yahoo.smtpnio.async.netty.SslHandlerBuilder;
import com.yahoo.smtpnio.async.netty.StarttlsHandler;

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
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Implementation for the SMTP NIO client.
 */
public class SmtpAsyncClient {

    /** Enum used to specify different session mode. */
    private enum SessionMode {

        /** Session will be built via SSL connection and uses startTls as fallback. */
        SSL_WITH_STARTTLS,

        /** Session will be built via SSL connection, don't use startTls as fallback. */
        SSL_WITHOUT_STARTTLS,

        /** Session will be built using startTls via plain connection. */
        PLAIN_STARTTLS,

        /** Session will be built using plain connection. */
        NON_SSL;
    }

    /** Debug record string template. */
    public static final String CONNECT_RESULT_REC = "[{},{}] connect operation complete. result={}, host={}, port={}, sslEnabled={}, sniNames={}";

    /** Debug record string template for reconnection. */
    public static final String RE_CONNECTION_REC = "[{},{}] try starttls. Re-connecting with non-ssl. "
            + "host={}, port={}, sslEnabled={}, startTlsEnabled={}, sniNames={}";

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
            @Nonnull final SmtpAsyncSessionConfig config, @Nonnull final SmtpAsyncSession.DebugMode debugOption) {
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout());
        final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture = new SmtpFuture<>();
        createSession(sessionData, config, debugOption, sessionCreatedFuture, sessionData.isSslEnabled());
        return sessionCreatedFuture;
    }

    /**
     * Re-connect to the remote server using plain connection and start the Starttls flow.
     *
     * @param sessionData a {@link SmtpAsyncSessionData} object containing information about the connection
     * @param config configuration to be used for this session/connection
     * @param debugOption the debugging option used
     * @param sessionCreatedFuture future containing the result of the request
     */
    public void createStarttlsSession(@Nonnull final SmtpAsyncSessionData sessionData, @Nonnull final SmtpAsyncSessionConfig config,
            @Nonnull final SmtpAsyncSession.DebugMode debugOption, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        createSession(sessionData, config, debugOption, sessionCreatedFuture, false);
    }

    /**
     * Connects to the remote server asynchronously according to session state.
     *
     * @param sessionData a {@link SmtpAsyncSessionData} object containing information about the connection
     * @param config configuration to be used for this session/connection
     * @param debugOption the debugging option used
     * @param sessionCreatedFuture future containing the result of the request
     * @param isSsl whether this session uses SSL for connection
     */
    private void createSession(@Nonnull final SmtpAsyncSessionData sessionData, @Nonnull final SmtpAsyncSessionConfig config,
            @Nonnull final SmtpAsyncSession.DebugMode debugOption, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture,
            final boolean isSsl) {
        final String host = sessionData.getHost();

        if (host == null) {
            throw new IllegalArgumentException("host cannot be null");
        }

        final int port = sessionData.getPort();
        final boolean isStarttls = config.getEnableStarttls();
        final InetSocketAddress localAddress = sessionData.getLocalAddress();
        final Collection<String> sniNames = sessionData.getSniNames();
        final Object sessionCtx = sessionData.getSessionContext();
        final SmtpAsyncClient smtpAsyncClient = this;

        bootstrap.handler(new SmtpClientChannelInitializer(config.getReadTimeout(), TimeUnit.MILLISECONDS));

        final ChannelFuture nettyConnectFuture = localAddress == null ? bootstrap.connect(host, port)
                : bootstrap.connect(new InetSocketAddress(host, port), localAddress);

        // setup listener to handle connection done event
        nettyConnectFuture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
            @Override
            public void operationComplete(final io.netty.util.concurrent.Future<? super Void> future) throws SmtpAsyncClientException {
                if (!future.isSuccess()) {
                    final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.WRITE_TO_SERVER_FAILED,
                            future.cause());
                    sessionCreatedFuture.done(ex);
                    logger.error(CONNECT_RESULT_REC, "N/A", sessionCtx, "failure", host, port, isSsl, sniNames, ex);
                    closeChannel(nettyConnectFuture.channel());
                    return;
                }

                // avoid negative ids
                final long sessionId = sessionCount.getAndUpdate(new LongUnaryOperator() { // atomic update
                    @Override
                    public long applyAsLong(final long counter) { // increment by 1 unless it overflows
                        return counter + 1 < 0 ? 1 : counter + 1;
                    }
                });

                final Channel ch = nettyConnectFuture.channel();
                final ChannelPipeline pipeline = ch.pipeline();

                // decide session state
                final SessionMode sessionMode;
                if (isSsl && isStarttls) {
                    sessionMode = SessionMode.SSL_WITH_STARTTLS;
                } else if (isSsl) {
                    sessionMode = SessionMode.SSL_WITHOUT_STARTTLS;
                } else if (isStarttls) {
                    sessionMode = SessionMode.PLAIN_STARTTLS;
                } else {
                    sessionMode = SessionMode.NON_SSL;
                }

                // add session specific handlers
                switch (sessionMode) {
                case SSL_WITH_STARTTLS:
                    pipeline.addFirst(SslDetectHandler.HANDLER_NAME, new SslDetectHandler(sessionCount.get(), sessionData, config,
                            LoggerFactory.getLogger(SslDetectHandler.class), debugOption, smtpAsyncClient, sessionCreatedFuture));
                    pipeline.addFirst(SslHandlerBuilder.SSL_HANDLER,
                            SslHandlerBuilder.newBuilder(sslContext, ch.alloc(), host, port, sniNames).build());
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(sessionCreatedFuture,
                            LoggerFactory.getLogger(SmtpClientConnectHandler.class), debugOption, sessionId, sessionCtx));
                    break;

                case SSL_WITHOUT_STARTTLS:
                    // don't add SslDetectHandler if startTls is disabled
                    pipeline.addFirst(SslHandlerBuilder.SSL_HANDLER,
                            SslHandlerBuilder.newBuilder(sslContext, ch.alloc(), host, port, sniNames).build());
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(sessionCreatedFuture,
                            LoggerFactory.getLogger(SmtpClientConnectHandler.class), debugOption, sessionId, sessionCtx));
                    break;

                case PLAIN_STARTTLS:
                    // add StarttlsHandler to start starttls flow during re-connection
                    pipeline.addLast(StarttlsHandler.HANDLER_NAME, new StarttlsHandler(sessionCreatedFuture,
                            LoggerFactory.getLogger(StarttlsHandler.class), debugOption, sessionId, sessionData));
                    break;

                case NON_SSL:
                    // add SmtpClientConnectHandler for initial non-ssl connection
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(sessionCreatedFuture,
                            LoggerFactory.getLogger(SmtpClientConnectHandler.class), debugOption, sessionId, sessionCtx));
                    break;

                default:
                    // shouldn't reach here
                    throw new SmtpAsyncClientException(FailureType.INVALID_INPUT, "Invalid state");
                }

                if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                    logger.debug(CONNECT_RESULT_REC, sessionId, sessionCtx, "success", host, port, isSsl, sniNames);
                }
            }
        });
    }

    /**
     * Closes channel.
     *
     * @param channel the channel
     */
    private void closeChannel(@Nullable final Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    /**
     * Shuts down the client gracefully.
     */
    public void shutdown() {
        group.shutdownGracefully();
    }
}
