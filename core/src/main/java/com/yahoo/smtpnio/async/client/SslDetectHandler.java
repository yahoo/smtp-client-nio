/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.netty.PlainGreetingHandler;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * This handler detects whether native SSL is available for this connection. If {@link NotSslRecordException} is caught, SSL is not available, fall
 * over to plain connection and try to use the Starttls flow if enabled.
 *
 * <p>
 * Starttls flow dynamically adds/removes three handlers:
 *
 * <li>{@link PlainGreetingHandler} processes server greeting and send first EHLO.
 *
 * <li>{@link EhloHandler} processes EHLO response and send STARTTLS.
 *
 * <li>{@link StarttlsHandler} processes STARTLS response and upgrade plain connection to ssl connection.
 */
public class SslDetectHandler extends ByteToMessageDecoder {

    /** Handler name for the ssl handler. */
    public static final String SSL_DETECT_HANDLER = "SslDetectHandler";

    /** Debug record string template for Ssl detection. */
    private static final String SSL_DETECT_REC = "[{},{}] finish checking native SSL availability. "
            + "result={}, host={}, port={}, sslEnabled={}, startTlsEnabled={}, sniNames={}";

    /** Debug record string template for reconnection. */
    private static final String RE_CONNECTION_REC = "[{},{}] try starttls. Re-connecting with non-ssl. "
            + "host={}, port={}, sslEnabled={}, startTlsEnabled={}, sniNames={}";

    /** The Netty bootstrap of current client. */
    private Bootstrap bootstrap;

    /** Counter to uniquely identify sessions. */
    private AtomicLong sessionCount;

    /** Future for the created session. */
    private SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture;

    /** SessionData object containing information about the connection. */
    private SmtpAsyncSessionData sessionData;

    /** Configuration to be used for this session/connection. */
    private SmtpAsyncSessionConfig sessionConfig;

    /** Logger for debugging messages, errors and other info. */
    private Logger logger;

    /** Debugging option used. */
    private final SmtpAsyncSession.DebugMode debugOption;

    /**
     * Initializes a {@link SslDetectHandler} to detect if native SSL is available for this connection.
     *
     * @param bootstrap Netty bootstrap of current client
     * @param sessionCount client's session counter
     * @param sessionData connection data for this session
     * @param sessionConfig configurations for this session
     * @param debugOption debugging options
     * @param sessionCreatedFuture SMTP session future
     */
    public SslDetectHandler(@Nonnull final Bootstrap bootstrap, @Nonnull final AtomicLong sessionCount,
            @Nonnull final SmtpAsyncSessionData sessionData, @Nonnull final SmtpAsyncSessionConfig sessionConfig,
            @Nonnull final SmtpAsyncSession.DebugMode debugOption, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        this.logger = LoggerFactory.getLogger(SslDetectHandler.class);
        this.sessionConfig = sessionConfig;
        this.sessionData = sessionData;
        this.debugOption = debugOption;
        this.sessionCreatedFuture = sessionCreatedFuture;
        this.sessionCount = sessionCount;
        this.bootstrap = bootstrap;
    }

    @Override
    protected void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final ByteBuf in, @Nonnull final List<Object> out) {
        // ssl succeeds
        if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
            logger.debug(SSL_DETECT_REC, sessionCount.get(), sessionData.getSessionContext(), "Available", sessionData.getHost(),
                    sessionData.getPort(), true, sessionData.isStarttlsEnabled(), sessionData.getSniNames());
        }
        ctx.pipeline().replace(this, "new", new SmtpClientChannelInitializer(sessionConfig.getReadTimeout(), TimeUnit.MILLISECONDS));
        ctx.pipeline().addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(
                sessionCreatedFuture,
                LoggerFactory.getLogger(SmtpAsyncSessionImpl.class), debugOption, sessionCount.get(), sessionData.getSessionContext()));
        cleanup();
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) throws Exception {

        final String host = sessionData.getHost();
        final int port = sessionData.getPort();
        final InetSocketAddress localAddress = sessionData.getLocalAddress();
        final Collection<String> sniNames = sessionData.getSniNames();
        final long sessionId = sessionCount.get();
        final Object sessionCtx = sessionData.getSessionContext();
        final boolean enableStarttls = sessionData.isStarttlsEnabled();

        // ssl failed, re-connect with plain connection
        if (cause.getCause() instanceof NotSslRecordException) {

            if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                logger.debug(SSL_DETECT_REC, sessionId, sessionCtx, "Not available", host, port, true, enableStarttls, sniNames);
            }

            ctx.close();

            // if starttls is enbaled, try to create a new connection without ssl
            if (enableStarttls) {
                if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                    logger.debug(RE_CONNECTION_REC, sessionId, sessionCtx, host, port, false, enableStarttls, sniNames);
                }
                bootstrap.handler(new SmtpClientChannelInitializer(sessionConfig.getReadTimeout(), TimeUnit.MILLISECONDS));
                final ChannelFuture nettyConnectFuture = localAddress == null ? bootstrap.connect(host, port)
                        : bootstrap.connect(new InetSocketAddress(host, port), localAddress);

                // setup listener to handle re-connection done event
                nettyConnectFuture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
                    @Override
                    public void operationComplete(final io.netty.util.concurrent.Future<? super Void> future) {
                        if (!future.isSuccess()) {
                            final SmtpAsyncClientException ex = new SmtpAsyncClientException(
                                    SmtpAsyncClientException.FailureType.WRITE_TO_SERVER_FAILED, future.cause());
                            logger.error(SmtpAsyncClient.CONNECT_RESULT_REC, "N/A", sessionCtx, "failure", host, port, false, sniNames, ex);
                            cleanup();
                            close(ctx);
                            sessionCreatedFuture.done(ex);
                            return;
                        }

                        // add the session specific handlers
                        final Channel ch = nettyConnectFuture.channel();
                        final ChannelPipeline pipeline = ch.pipeline();

                        final long sessionId = sessionCount.getAndUpdate(new LongUnaryOperator() { // atomic update
                            @Override
                            public long applyAsLong(final long counter) { // increment by 1 unless it overflows
                                return counter + 1 < 0 ? 1 : counter + 1;
                            }
                        });

                        if (logger.isTraceEnabled() || debugOption == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                            logger.debug(SmtpAsyncClient.CONNECT_RESULT_REC, sessionId, sessionCtx, "success", host, port, false, sniNames);
                        }
                        pipeline.addLast(PlainGreetingHandler.HANDLER_NAME, new PlainGreetingHandler(
                                sessionCreatedFuture,
                                LoggerFactory.getLogger(SmtpAsyncSessionImpl.class), debugOption, sessionId, sessionCtx, sessionData));
                        cleanup();
                    }
                });
            } else {
                // if starttls is not enabled, finish the future with exception
                final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.NOT_SSL_RECORD,
                        cause.getCause());
                logger.error(SmtpAsyncClient.CONNECT_RESULT_REC, sessionId, sessionCtx, "failure", host, port, false, sniNames, ex);
                sessionCreatedFuture.done(ex);
                cleanup();
                close(ctx);

            }
        } else {
            logger.error("[{},{}] Connection failed due to encountering exception:{}.", sessionId, sessionCtx, cause);
            sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, cause, sessionId, sessionCtx));
            cleanup();
            close(ctx); // closing the connection

        }
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
                final long sessionId = sessionCount.get();
                final Object sessionCtx = sessionData.getSessionContext();
                logger.error("[{},{}] Connection failed due to taking longer than configured allowed time.", sessionId, sessionCtx);
                sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEED_IDLE_MAX, sessionId, sessionCtx));
                // closing the channel if server is not responding for max read timeout limit
                close(ctx);
                cleanup();
            }
        }
    }

    /**
     * Closes the connection.
     *
     * @param ctx the ChannelHandlerContext
     */
    private void close(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            // closing the channel if server is still active
            ctx.close();
        }
    }

    /**
     * Avoids loitering.
     */
    private void cleanup() {
        sessionCreatedFuture = null;
        logger = null;
        bootstrap = null;
        sessionData = null;
        sessionConfig = null;
    }

}
