/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.SmtpClientConnectHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This handler detects whether native SSL is available for this connection. If {@link NotSslRecordException} is caught, SSL is not available, fall
 * over to plain connection and try to use the Starttls flow if enabled.
 *
 * <p>
 * Starttls flow dynamically adds/removes three handlers:
 *
 * <li>{@link PlainReconnectGreetingHandler} processes server greeting and send EHLO.
 *
 * <li>{@link StarttlsEhloHandler} processes EHLO response and send STARTTLS.
 *
 * <li>{@link StarttlsSessionHandler} processes STARTLS response and upgrade plain connection to ssl connection.
 */
public class SslDetectHandler extends ByteToMessageDecoder {

    /** Debug record string template for Ssl detection. */
    private static final String SSL_DETECT_REC = "[{},{}] finish checking native SSL availability. "
            + "result={}, host={}, port={}, sslEnabled={}, startTlsEnabled={}, sniNames={}";

    /** Session Id. */
    private final long sessionId;

    /** Future for the created session. */
    private SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture;

    /** SessionData object containing information about the connection. */
    private SmtpAsyncSessionData sessionData;

    /** Configuration to be used for this session/connection. */
    private SmtpAsyncSessionConfig sessionConfig;

    /** Client that holds this connection. */
    private SmtpAsyncClient smtpAsyncClient;

    /** Logger for debugging messages, errors and other info. */
    private Logger logger;

    /** Debugging option used. */
    private DebugMode logOpt;

    /**
     * Initializes a {@link SslDetectHandler} to detect if native SSL is available for this connection.
     *
     * @param sessionId client's session id
     * @param sessionData connection data for this session
     * @param sessionConfig configurations for this session
     * @param logOpt debugging options
     * @param smtpAsyncClient Client that holds this connection
     * @param sessionCreatedFuture SMTP session future
     */
    public SslDetectHandler(final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData,
            @Nonnull final SmtpAsyncSessionConfig sessionConfig, @Nonnull final DebugMode logOpt, @Nonnull final SmtpAsyncClient smtpAsyncClient,
            @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        this.logger = LoggerFactory.getLogger(SslDetectHandler.class);
        this.sessionConfig = sessionConfig;
        this.sessionData = sessionData;
        this.logOpt = logOpt;
        this.sessionCreatedFuture = sessionCreatedFuture;
        this.sessionId = sessionId;
        this.smtpAsyncClient = smtpAsyncClient;
    }

    @Override
    protected void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final ByteBuf in, @Nonnull final List<Object> out) {
        // ssl succeeds
        if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
            logger.debug(SSL_DETECT_REC, sessionId, sessionData.getSessionContext(), "Available", sessionData.getHost(), sessionData.getPort(), true,
                    sessionData.isStarttlsEnabled(), sessionData.getSniNames());
        }
        ctx.pipeline().replace(this, SmtpClientChannelInitializer.INITIALIZER_NAME,
                new SmtpClientChannelInitializer(sessionConfig.getReadTimeout(), TimeUnit.MILLISECONDS));
        ctx.pipeline().addLast(SmtpClientConnectHandler.HANDLER_NAME, new SmtpClientConnectHandler(sessionCreatedFuture,
                LoggerFactory.getLogger(SmtpClientConnectHandler.class), logOpt, sessionId, sessionData.getSessionContext()));
        cleanup();
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) throws Exception {

        final String host = sessionData.getHost();
        final int port = sessionData.getPort();
        final Collection<String> sniNames = sessionData.getSniNames();
        final Object sessionCtx = sessionData.getSessionContext();
        final boolean enableStarttls = sessionData.isStarttlsEnabled();

        close(ctx); // closing the connection

        // ssl failed, re-connect with plain connection
        if (cause.getCause() instanceof NotSslRecordException) {

            if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                logger.debug(SSL_DETECT_REC, sessionId, sessionCtx, "Not available", host, port, true, enableStarttls, sniNames);
            }

            // if starttls is enbaled, try to create a new connection without ssl
            if (enableStarttls) {
                smtpAsyncClient.createStarttlsSession(sessionData, sessionConfig, logOpt, sessionCreatedFuture);
            } else {
                // if starttls is not enabled, finish the future with exception
                final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.NOT_SSL_RECORD,
                        cause.getCause());
                logger.error(SmtpAsyncClient.CONNECT_RESULT_REC, sessionId, sessionCtx, "failure", host, port, false, sniNames, ex);
                sessionCreatedFuture.done(ex);
                cleanup();
            }
        } else {
            logger.error("[{},{}] Connection failed due to encountering exception:{}.", sessionId, sessionCtx, cause);
            sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, cause, sessionId, sessionCtx));

        }
        cleanup();
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
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
        logOpt = null;
        sessionData = null;
        sessionConfig = null;
        smtpAsyncClient = null;
    }

}
