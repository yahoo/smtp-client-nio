/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.yahoo.smtpnio.async.client.SmtpAsyncClient;
import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionConfig;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This handler detects whether native SSL is available for this connection. If {@link NotSslRecordException} is caught, SSL is not available, fall
 * back to plain connection and try to use the Starttls flow if enabled.
 */
public class SslDetectHandler extends ByteToMessageDecoder {
    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "SslDetectHandler";

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
     * @param logger logger for debugging messages
     * @param logOpt debugging options
     * @param smtpAsyncClient Client that holds this connection
     * @param sessionCreatedFuture SMTP session future
     */
    public SslDetectHandler(final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData,
            @Nonnull final SmtpAsyncSessionConfig sessionConfig, @Nonnull final Logger logger, @Nonnull final DebugMode logOpt,
            @Nonnull final SmtpAsyncClient smtpAsyncClient, @Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture) {
        this.logger = logger;
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
        if (this.logger.isTraceEnabled() || this.logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
            this.logger.debug(SSL_DETECT_REC, this.sessionId, this.sessionData.getSessionContext(), "Available", this.sessionData.getHost(),
                    this.sessionData.getPort(), true, sessionConfig.getEnableStarttls(), this.sessionData.getSniNames());
        }
        ctx.pipeline().remove(this);
        cleanup();
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {

        final String host = this.sessionData.getHost();
        final int port = this.sessionData.getPort();
        final Collection<String> sniNames = this.sessionData.getSniNames();
        final Object sessionCtx = this.sessionData.getSessionContext();
        final boolean enableStarttls = this.sessionConfig.getEnableStarttls();

        ctx.close();

        // ssl failed, re-connect with plain connection
        if (cause.getCause() instanceof NotSslRecordException) {

            if (this.logger.isTraceEnabled() || this.logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                this.logger.debug(SSL_DETECT_REC, this.sessionId, sessionCtx, "Not available", host, port, true, enableStarttls, sniNames);
            }

            // if startTls is enbaled, try to create a new connection without ssl
            if (enableStarttls) {
                this.smtpAsyncClient.createStarttlsSession(this.sessionData, this.sessionConfig, this.logOpt, this.sessionCreatedFuture);
            } else {
                // if startTls is not enabled, finish the future with exception
                final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.NOT_SSL_RECORD,
                        cause.getCause());
                this.logger.error(SmtpAsyncClient.CONNECT_RESULT_REC, this.sessionId, sessionCtx, "failure", host, port, false, sniNames, ex);
                this.sessionCreatedFuture.done(ex);
            }
        } else {
            this.logger.error("[{},{}] Connection failed due to encountering exception:{}.", this.sessionId, sessionCtx, cause);
            this.sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, cause, this.sessionId, sessionCtx));

        }
        cleanup();
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
                final Object sessionCtx = sessionData.getSessionContext();
                this.logger.error("[{},{}] Connection failed due to taking longer than configured allowed time.", this.sessionId, sessionCtx);
                this.sessionCreatedFuture
                        .done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEED_IDLE_MAX, this.sessionId, sessionCtx));
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

    @Override
    public void channelInactive(@Nonnull final ChannelHandlerContext ctx) {
        // after close() has been called on SslHandler failure, channelInactive() will also be called
        // this method is used to stop channelInactive chain reaching SmtpClientConnectionHandler
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
