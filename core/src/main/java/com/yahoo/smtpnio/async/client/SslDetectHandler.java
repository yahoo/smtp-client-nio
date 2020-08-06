/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.NotSslRecordException;

/**
 * This handler detects whether native SSL is available for this connection. If {@link NotSslRecordException} is caught, SSL is not available, fall
 * back to plain connection and try to use the Starttls flow if enabled.
 */
public class SslDetectHandler extends ByteToMessageDecoder {
    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "SslDetectHandler";

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

    /** Flag for whether it is during re-connection. */
    private boolean isReconnecting;

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
            @Nonnull final SmtpAsyncSessionConfig sessionConfig, @Nonnull final DebugMode logOpt,
            @Nonnull final SmtpAsyncClient smtpAsyncClient, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        this(sessionId, sessionData, sessionConfig, LoggerFactory.getLogger(SslDetectHandler.class), logOpt,
                smtpAsyncClient, sessionCreatedFuture);
    }

    /**
     * Initializes a {@link SslDetectHandler} to detect if native SSL is available for this connection.
     *
     * @param sessionId client's session id
     * @param sessionData connection data for this session
     * @param sessionConfig configurations for this session
     * @param logger logger for debugging messages
     * @param logOpt debugging options
     * @param smtpAsyncClient Client that holds this connection
     * @param sessionCreatedFuture SMTP session future
     */
    SslDetectHandler(final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData, @Nonnull final SmtpAsyncSessionConfig sessionConfig,
            @Nonnull final Logger logger, @Nonnull final DebugMode logOpt, @Nonnull final SmtpAsyncClient smtpAsyncClient,
            @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        this.logger = logger;
        this.sessionConfig = sessionConfig;
        this.sessionData = sessionData;
        this.logOpt = logOpt;
        this.sessionCreatedFuture = sessionCreatedFuture;
        this.sessionId = sessionId;
        this.smtpAsyncClient = smtpAsyncClient;
        this.isReconnecting = false;
    }

    @Override
    protected void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final ByteBuf in, @Nonnull final List<Object> out) {
        // ssl succeeds
        if (this.logger.isTraceEnabled() || this.logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
            this.logger.debug("[{},{}] finish checking native SSL availability. result={}, host={}, port={}, sslEnabled={}, sniNames={}",
                    this.sessionId, this.sessionData.getSessionContext(), "Available", this.sessionData.getHost(), this.sessionData.getPort(),
                    this.sessionData.isSslEnabled(), this.sessionData.getSniNames());
        }
        ctx.pipeline().remove(this);
        cleanup();
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {
        // ssl failed, re-connect with plain connection
        if (cause.getCause() instanceof NotSslRecordException) {
            this.isReconnecting = true;
            close(ctx);
            // if startTls is enabled, try to create a new connection without ssl
            this.smtpAsyncClient.createStartTlsSession(this.sessionData, this.sessionConfig, this.logOpt, this.sessionCreatedFuture);
            if (this.logger.isTraceEnabled() || this.logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                this.logger.debug("[{},{}] finish checking native SSL availability. result={}, host={}, port={}, sslEnabled={}, sniNames={}",
                        this.sessionId, this.sessionData.getSessionContext(), "Not available", this.sessionData.getHost(), this.sessionData.getPort(),
                        this.sessionData.isSslEnabled(), this.sessionData.getSniNames());
            }
            cleanup();
        } else {
            ctx.fireExceptionCaught(cause);
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

    @Override
    public void channelInactive(@Nonnull final ChannelHandlerContext ctx) {
        // only finish the future with exception when it's not re-connecting
        if (!this.isReconnecting) {
            if (sessionCreatedFuture == null) {
                return; // cleanup() has been called, leave
            }
            sessionCreatedFuture
                    .done(new SmtpAsyncClientException(FailureType.CHANNEL_INACTIVE, this.sessionId, this.sessionData.getSessionContext()));
        }
        cleanup();
    }

    /**
     * Avoids loitering.
     */
    private void cleanup() {
        this.sessionCreatedFuture = null;
        this.logger = null;
        this.logOpt = null;
        this.sessionData = null;
        this.sessionConfig = null;
        this.smtpAsyncClient = null;
    }

}
