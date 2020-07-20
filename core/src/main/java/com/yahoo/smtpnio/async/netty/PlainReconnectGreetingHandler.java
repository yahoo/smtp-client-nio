/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.request.ExtendedHelloCommand;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This handler receives greeting message from server after plain re-connection. If the response code is 250, it will start startTLS flow by sending
 * EHLO command and replace itself by {@link StarttlsEhloHandler}
 */
public class PlainReconnectGreetingHandler extends MessageToMessageDecoder<SmtpResponse> {

    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "PlainReconnectGreetingHandler";

    /** Client name used for server to say greeting to. */
    public static final String EHLO_CLIENT_NAME = "Reconnection";

    /** Future for the created session. */
    private SmtpFuture<SmtpAsyncCreateSessionResponse> sessionCreatedFuture;

    /** SessionData object containing information about the connection. */
    private SmtpAsyncSessionData sessionData;

    /** Logger instance. */
    private Logger logger;

    /** Logging option for this session. */
    private DebugMode logOpt;

    /** Session Id. */
    private final long sessionId;

    /** Context for session information. */
    private Object sessionCtx;

    /**
     * Initializes a {@link PlainReconnectGreetingHandler} to process greeting after plain re-connection.
     *
     * @param sessionFuture SMTP session future
     * @param logger the {@link Logger} instance for {@link SmtpAsyncSessionImpl}
     * @param logOpt logging option for the session to be created
     * @param sessionId the session id
     * @param sessionCtx context for the session information; it is used used for logging
     * @param sessionData sessionData object containing information about the connection.
     */
    public PlainReconnectGreetingHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture, @Nonnull final Logger logger,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nullable final Object sessionCtx,
            @Nonnull final SmtpAsyncSessionData sessionData) {
        this.sessionCreatedFuture = sessionFuture;
        this.logger = logger;
        this.logOpt = logOpt;
        this.sessionId = sessionId;
        this.sessionCtx = sessionCtx;
        this.sessionData = sessionData;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse serverResponse, @Nonnull final List<Object> out) {
        if (serverResponse.getCode().value() == SmtpResponse.Code.GREETING) { // successful response
            Channel channel = ctx.channel();
            channel.writeAndFlush(new ExtendedHelloCommand(EHLO_CLIENT_NAME).getCommandLineBytes());
            ctx.pipeline().replace(this, StarttlsEhloHandler.HANDLER_NAME,
                    new StarttlsEhloHandler(sessionCreatedFuture, logger, logOpt, sessionId, sessionCtx, sessionData));
            if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                logger.debug("[{},{}] Server greeting response of reconnection was successful. Starttls flow begins. Sending EHLO.", sessionId,
                        sessionCtx);
            }
        } else {
            logger.error("[{},{}] Server greeting response of reconnection was not successful:{}", sessionId, sessionCtx, serverResponse.toString());
            sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_INVALID_GREETING_CODE, sessionId, sessionCtx,
                    serverResponse.toString()));
            close(ctx); // closing the channel if we are not getting a ok greeting
        }
        cleanup();
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
}
