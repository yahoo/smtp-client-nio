/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.List;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLException;

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
import com.yahoo.smtpnio.async.request.StarttlsCommand;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

/**
 * This handler is used to implement starttls flow.
 *
 * <p>
 * Starttls flow consists of three steps:
 *
 * <li>1. process server connection greeting and send EHLO.
 *
 * <li>2. process EHLO response and send STARTTLS.
 *
 * <li>3. process STARTLS response and upgrade plain connection to ssl connection.
 */
public class StarttlsHandler extends MessageToMessageDecoder<SmtpResponse> {

    /** This enum indicates different states in the starttls flow. */
    private enum StarttlsState {
        /** Receives greeting after re-connection, handler will send EHLO. */
        PRE_EHLO,

        /** EHLO has been sent, handler is checking server responses and will send STARTTLS. */
        PRE_STARTTLS,

        /** STARTTLS has been sent, handler will remove itself and add SslHandler. */
        POST_STARTTLS,

        /** Starttls fails. */
        STARTTLS_FAILURE;
    }

    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "StarttlsHandler";

    /** Literal for the STARTTLS capability. */
    public static final String STARTTLS = "STARTTLS";

    /** Client name used for server to say greeting to. */
    public static final String EHLO_CLIENT_NAME = "Reconnection";

    /** Handler name for idle sate handler. */
    private static final String IDLE_STATE_HANDLER_NAME = "idleStateHandler";

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

    /** Starttls state that the handler is in. */
    private StarttlsState starttlsState;

    /** Flag recording whether server has replied STARTTLS capability. */
    private boolean receivedStarttlsCapability;

    /**
     * Initializes a {@link StarttlsHandler} to process greeting after plain re-connection.
     *
     * @param sessionFuture SMTP session future
     * @param logger the {@link Logger} instance for {@link SmtpAsyncSessionImpl}
     * @param logOpt logging option for the session to be created
     * @param sessionId the session id
     * @param sessionData sessionData object containing information about the connection.
     */
    public StarttlsHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture, @Nonnull final Logger logger,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData) {
        this.sessionCreatedFuture = sessionFuture;
        this.logger = logger;
        this.logOpt = logOpt;
        this.sessionId = sessionId;
        this.sessionCtx = sessionData.getSessionContext();
        this.sessionData = sessionData;
        this.starttlsState = StarttlsState.PRE_EHLO;
        this.receivedStarttlsCapability = false;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse serverResponse, @Nonnull final List<Object> out) {
        final Channel channel = ctx.channel();
        final ChannelPipeline pipeline = ctx.pipeline();

        // all server responses should be 2xx
        if (serverResponse.getReplyType() != SmtpResponse.ReplyType.POSITIVE_COMPLETION) {
            // receive a bad response,
            logger.error("[{},{}] Receive bad response from server: {}", sessionId, sessionCtx, serverResponse.toString());
            starttlsState = StarttlsState.STARTTLS_FAILURE;
        }

        switch (starttlsState) {
        case PRE_EHLO:
            // receive greeting and send EHLO
            if (serverResponse.getCode().value() == SmtpResponse.Code.GREETING) {
                channel.writeAndFlush(new ExtendedHelloCommand(EHLO_CLIENT_NAME).getCommandLineBytes());
                starttlsState = StarttlsState.PRE_STARTTLS;
            } else {
                logger.error("[{},{}] Server greeting response of reconnection was not successful:{}", sessionId, sessionCtx,
                        serverResponse.toString());
                starttlsState = StarttlsState.STARTTLS_FAILURE;
            }
            break;

        case PRE_STARTTLS:
            // receive EHLO responses and send STARTTLS
            final String message = serverResponse.getMessage();
            // check STARTTLS capability
            if (message.length() >= STARTTLS.length() && message.substring(0, STARTTLS.length()).equalsIgnoreCase(STARTTLS)) {
                receivedStarttlsCapability = true;
            }

            // decide next state when we receive last response
            if (serverResponse.isLastLineResponse()) {
                if (receivedStarttlsCapability) {
                    channel.writeAndFlush(new StarttlsCommand().getCommandLineBytes());
                    starttlsState = StarttlsState.POST_STARTTLS;
                } else {
                    // server doesn't reply STARTTLS capability
                    logger.error("[{},{}] Server doesn't support STARTTLS: host:{}, port:{}", sessionId, sessionCtx, sessionData.getHost(),
                            sessionData.getPort());
                    starttlsState = StarttlsState.STARTTLS_FAILURE;
                }
            }
            break;

        case POST_STARTTLS:
            // receive starttls response, remove this handler and add SslHandler
            pipeline.remove(this);

            if (serverResponse.getCode().value() == SmtpResponse.Code.GREETING) { // successful response
                // add the command response handler
                final SmtpAsyncSessionImpl session = new SmtpAsyncSessionImpl(ctx.channel(), logger, logOpt, sessionId, pipeline, sessionCtx);
                final SmtpAsyncCreateSessionResponse response = new SmtpAsyncCreateSessionResponse(session, serverResponse);
                try {
                    // create sslHandler
                    final SslContext sslContext = SslContextBuilder.forClient().build();
                    final SslHandler sslHandler = SslHandlerBuilder
                            .newBuilder(sslContext, ctx.alloc(), sessionData.getHost(), sessionData.getPort(), sessionData.getSniNames()).build();
                    final Promise<Channel> sslConnectionFture = (Promise<Channel>) sslHandler.handshakeFuture();
                    final SmtpFuture<SmtpAsyncCreateSessionResponse> sesssionFuture = this.sessionCreatedFuture;
                    // check if ssl connection succeeds
                    sslConnectionFture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Channel>>() {
                        @Override
                        public void operationComplete(final io.netty.util.concurrent.Future<? super Channel> future) throws Exception {
                            if (future.isSuccess()) {
                                sesssionFuture.done(response);
                                if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                                    logger.debug("[{},{}] Starttls succeeds. Connection is now encrypted.", sessionId, sessionCtx);
                                }
                            } else {
                                logger.error("[{},{}] SslConnection failed after adding SslHandler.", sessionId, sessionCtx);
                                sesssionFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, sessionId, sessionCtx));
                                close(ctx);
                            }
                        }
                    });
                    ctx.pipeline().addAfter(IDLE_STATE_HANDLER_NAME, SslHandlerBuilder.SSL_HANDLER, sslHandler);
                } catch (final SSLException e) {
                    logger.error("[{},{}] Failed to create SslContext: {}", sessionId, sessionCtx, e);
                    starttlsState = StarttlsState.STARTTLS_FAILURE;
                }
            } else {
                logger.error("[{},{}] STARTTLS response was not successful:{}", sessionId, sessionCtx, serverResponse.toString());
                starttlsState = StarttlsState.STARTTLS_FAILURE;
            }
            break;

        default:
            break;
        }

        // close channel and clean up if starttls failed
        if (starttlsState == StarttlsState.STARTTLS_FAILURE) {
            sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.STARTTLS_FALIED, sessionId, sessionCtx, serverResponse.toString()));
            close(ctx);
            cleanup();
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
