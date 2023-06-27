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
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
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

    private static final String CHUNKED_WRITER = "chunked-writer";

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
    public static final String CONNECT_RESULT_REC = "[{},{}] connect operation complete. result={}, host={}, "
            + "port={}, sslEnabled={}, sniNames={}, sessionMode={}";

    /** Handler name for the ssl handler. */
    private static final String SSL_HANDLER = "sslHandler";

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
     */
    public SmtpAsyncClient(final int numThreads) {
        this(new Bootstrap(), new NioEventLoopGroup(numThreads), LoggerFactory.getLogger(SmtpAsyncClient.class));
    }

    /**
     * Constructs a NIO-based SMTP client.
     *
     * @param bootstrap a {@link Bootstrap} instance that makes it easy to bootstrap a {@link Channel} to use for clients
     * @param group an {@link EventLoopGroup} instance allowing registering {@link Channel}s for processing later selection during the event loop
     * @param logger {@link Logger} instance
     */
    SmtpAsyncClient(@Nonnull final Bootstrap bootstrap, @Nonnull final EventLoopGroup group, @Nonnull final Logger logger) {
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
    void createStartTlsSession(
            @Nonnull final SmtpAsyncSessionData sessionData,
            @Nonnull final SmtpAsyncSessionConfig config,
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
            public void operationComplete(final io.netty.util.concurrent.Future<? super Void> future) {
                final Channel ch = nettyConnectFuture.channel();

                if (!future.isSuccess()) {
                    final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.WRITE_TO_SERVER_FAILED,
                            future.cause());
                    handleSessionFailed("N/A", sessionData, ex, sessionCreatedFuture, ch, isSsl);
                    return;
                }

                // avoid negative ids
                final long sessionId = sessionCount.getAndUpdate(new LongUnaryOperator() { // atomic update
                    @Override
                    public long applyAsLong(final long counter) { // increment by 1 unless it overflows
                        return counter + 1 < 0 ? 1 : counter + 1;
                    }
                });

                // create SslHandler for secure connection
                SslHandler sslHandler = null;
                if (isSsl) {
                    try {
                        sslHandler = createSSLHandler(ch.alloc(), host, port, sniNames, sessionData.getSSLContext());
                    } catch (final SSLException e) {
                        final SmtpAsyncClientException ex = new SmtpAsyncClientException(FailureType.SSL_CONTEXT_EXCEPTION, e);
                        handleSessionFailed(sessionId, sessionData, ex, sessionCreatedFuture, ch, isSsl);
                        return;
                    }
                }

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

                final ChannelPipeline pipeline = ch.pipeline();

                // add session specific handlers
                switch (sessionMode) {
                case SSL_WITH_STARTTLS:
                    pipeline.addFirst(SSL_HANDLER, sslHandler);
                    pipeline.addAfter(SSL_HANDLER, SslDetectHandler.HANDLER_NAME, new SslDetectHandler(sessionCount.get(), sessionData, config,
                            debugOption, smtpAsyncClient, sessionCreatedFuture));
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(sessionCreatedFuture,
                            debugOption, sessionId, sessionCtx));
                    break;

                case SSL_WITHOUT_STARTTLS:
                    // don't add SslDetectHandler if startTls is disabled
                    pipeline.addFirst(SSL_HANDLER, sslHandler);
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME,
                            new SmtpClientConnectHandler(sessionCreatedFuture, debugOption, sessionId, sessionCtx));
                    break;

                case PLAIN_STARTTLS:
                    // add StarttlsHandler to start starttls flow during re-connection
                    pipeline.addLast(StarttlsHandler.HANDLER_NAME, new StarttlsHandler(sessionCreatedFuture, debugOption, sessionId, sessionData));
                    break;

                case NON_SSL:
                    // add SmtpClientConnectHandler for initial non-ssl connection
                    pipeline.addLast(SmtpClientConnectHandler.HANDLER_NAME,
                            new SmtpClientConnectHandler(sessionCreatedFuture, debugOption, sessionId, sessionCtx));
                    break;

                default:
                    // shouldn't reach here
                    handleSessionFailed(sessionId, sessionData, new SmtpAsyncClientException(FailureType.ILLEGAL_STATE, "Illegal state"),
                            sessionCreatedFuture, ch, isSsl);
                    return;
                }

                if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                    logger.debug(CONNECT_RESULT_REC, sessionId, sessionCtx, "success", host, port, isSsl, sniNames, sessionMode.name());
                }
            }
        });
    }

    /**
     * Fails the session future on error and close the channel.
     *
     * @param sessionId client's session id
     * @param sessionData connection data for the session
     * @param ex exception that causes the session failure
     * @param sessionCreatedFuture SMTP session future
     * @param channel session channel to close
     * @param isSsl whether session use ssl or not
     */
    private void handleSessionFailed(@Nonnull final Object sessionId, @Nonnull final SmtpAsyncSessionData sessionData,
            @Nonnull final SmtpAsyncClientException ex, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture,
            @Nullable final Channel channel, final boolean isSsl) {
        sessionCreatedFuture.done(ex);
        logger.error(CONNECT_RESULT_REC, sessionId, sessionData.getSessionContext(), "failure", sessionData.getHost(), sessionData.getPort(), isSsl,
                sessionData.getSniNames(), "N/A", ex);
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    /**
     * Creates a new {@link SslHandler} object used to build secure connection.
     *
     * @param alloc allocator for ByteBuf objects
     * @param host host name of server
     * @param port port of server
     * @param sniNames collection of SNI names
     * @param sslCtxt Optional Custom SSLContext
     * @return a SslHandlerBuilder object used to build {@link SslHandler}
     * @throws SSLException on SslContext creation error
     */
    static SslHandler createSSLHandler(@Nonnull final ByteBufAllocator alloc, @Nonnull final String host, final int port,
            @Nullable final Collection<String> sniNames, @Nullable final SSLContext sslCtxt) throws SSLException {
        // Use the passed SslContext only if non-null. Otherwise build a default client SslContext for use.
        final SslContext sslContext = (sslCtxt == null) ? SslContextBuilder.forClient().build() : new JdkSslContext(sslCtxt, true, ClientAuth.NONE);
        if (sniNames != null && !sniNames.isEmpty()) { // SNI support
            final List<SNIServerName> serverNames = new ArrayList<>();
            for (final String sni : sniNames) {
                serverNames.add(new SNIHostName(sni));
            }
            final SSLParameters params = new SSLParameters();
            params.setServerNames(serverNames);
            final SSLEngine engine = sslContext.newEngine(alloc, host, port);
            engine.setSSLParameters(params);
            return new SslHandler(engine);
        } else {
            return sslContext.newHandler(alloc, host, port);
        }
    }

    /**
     * Shuts down the client gracefully.
     */
    public void shutdown() {
        group.shutdownGracefully();
    }
}
