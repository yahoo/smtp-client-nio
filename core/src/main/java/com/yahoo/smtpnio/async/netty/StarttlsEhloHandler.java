/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.request.HelloCommand;
import com.yahoo.smtpnio.async.request.StarttlsCommand;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This handler receives EHLO response sent by {@link PlainReconnectGreetingHandler}. If the response code is 2xx, it will send STARTTLS command to
 * notify server for starting TLS connection. If EHLO response fails, try HELO as fallback.
 */
public class StarttlsEhloHandler extends MessageToMessageDecoder<SmtpResponse> {

    /** This enum indicates different state regarding of STARTTLS capability. */
    private enum StartTlsCapabilityState {
        /** EHLO has been sent, no STARTTLS capability received. */
        EHLO_SENT,

        /** STARTTLS capability received. */
        RECEIVE_STARTTLS,

        /** HELO has been sent, no STARTTLS capability received. */
        HELO_SENT;
    }

    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "StarttlsEhloHandler";

    /** Literal for the STARTTLS capability. */
    public static final String STARTTLS = "STARTTLS\r\n";

    /** Future for the created session. */
    private SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture;

    /** Logger instance. */
    private Logger logger;

    /** Logging option for this session. */
    private DebugMode logOpt;

    /** Session Id. */
    private final long sessionId;

    /** Context for session information. */
    private Object sessionCtx;

    /** SessionData object containing information about the connection. */
    private SmtpAsyncSessionData sessionData;

    /** State of checking STARTTLS capability. */
    private StartTlsCapabilityState startTlsCapabilityState;

    /**
     * Initialize an StarttlsEhloHandler for receiving EHLO response and send STARTTLS command.
     *
     * @param sessionFuture SMTP session future
     * @param logger logger instance
     * @param logOpt logging option for this session.
     * @param sessionId session id
     * @param sessionData sessionData object containing information about the connection.
     */
    public StarttlsEhloHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture, @Nonnull final Logger logger,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData) {
        this.sessionCreatedFuture = sessionFuture;
        this.logger = logger;
        this.logOpt = logOpt;
        this.sessionCtx = sessionData.getSessionContext();
        this.sessionId = sessionId;
        this.sessionData = sessionData;
        this.startTlsCapabilityState = StartTlsCapabilityState.EHLO_SENT;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse serverResponse, @Nonnull final List<Object> out) {
        final String message = serverResponse.getMessage();
        // check STARTTLS capability
        if (message.equalsIgnoreCase(STARTTLS)) {
            this.startTlsCapabilityState = StartTlsCapabilityState.RECEIVE_STARTTLS;
        }

        if (serverResponse.isLastLineResponse()) {
            switch (startTlsCapabilityState) {
            case RECEIVE_STARTTLS:
                ctx.channel().writeAndFlush(new StarttlsCommand().getCommandLineBytes());
                ctx.pipeline().replace(this, StarttlsSessionHandler.HANDLER_NAME,
                        new StarttlsSessionHandler(sessionCreatedFuture, logger, logOpt, sessionId, sessionData));
                cleanup();
                break;

            case EHLO_SENT:
                // try HELO as fallback
                ctx.channel().writeAndFlush(new HelloCommand(sessionCtx.toString()).getCommandLineBytes());
                this.startTlsCapabilityState = StartTlsCapabilityState.HELO_SENT;
                break;

            case HELO_SENT:
                // server doesn't reply STARTTLS capability
                logger.error("[{},{}] Server doesn't support starttls: host:{}, port:{}", sessionId, sessionCtx, sessionData.getHost(),
                        sessionData.getPort());
                sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, sessionId, sessionCtx));
                close(ctx);
                cleanup();
                break;

            default:
                break;
            }
        }
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {
        logger.error("[{},{}] Re-connection failed due to encountering exception:{}.", sessionId, sessionCtx, cause);
        sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, cause, sessionId, sessionCtx));
        close(ctx);
        cleanup();
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
                logger.error("[{},{}] Re-connection failed due to taking longer than configured allowed time.", sessionId, sessionCtx);
                sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEED_IDLE_MAX, sessionId, sessionCtx));
                // closing the channel if server is not responding for max read timeout limit
                close(ctx);
                cleanup();
            }
        }
    }

    @Override
    public void channelInactive(@Nonnull final ChannelHandlerContext ctx) {
        if (sessionCreatedFuture == null) {
            return; // cleanup() has been called, leave
        }
        sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_INACTIVE));
        cleanup();
        ctx.close();
    }

    /**
     * Closes the connection.
     *
     * @param ctx the ChannelHandlerContext
     */
    private void close(@Nonnull final ChannelHandlerContext ctx) {
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
        sessionCtx = null;
    }

}
