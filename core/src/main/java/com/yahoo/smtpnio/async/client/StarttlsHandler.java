/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.util.List;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.request.ExtendedHelloCommand;
import com.yahoo.smtpnio.async.request.StarttlsCommand;
import com.yahoo.smtpnio.async.response.SmtpResponse;
import com.yahoo.smtpnio.async.response.SmtpResponse.Code;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * This handler is used to implement startTls flow.
 *
 * Starttls flow consists of four steps:
 * <ul>
 * <li>1. process server greeting and send EHLO.</li>
 *
 * <li>2. process EHLO response and send STARTTLS.</li>
 *
 * <li>3. process STARTLS response and upgrade plain connection to ssl connection.</li>
 *
 * <li>4. wait for ssl connection completed, finish the session future if ssl succeeds, otherwise fails.</li>
 * </ul>
 */
public class StarttlsHandler extends MessageToMessageDecoder<SmtpResponse> {

    /**
     * This listener is used to check if ssl connection succeeds.
     */
    class SSLHandShakeCompleteListener implements GenericFutureListener<io.netty.util.concurrent.Future<? super Channel>> {

        /** Server greeting response, response code should be 220. */
        @Nonnull
        private final SmtpResponse serverResponse;
        /** Handler context. */
        @Nonnull
        private final ChannelHandlerContext ctx;

        /**
         * Initialize a SSLHandShakeCompleteListener instance used to check ssl connection.
         *
         * @param serverResponse server greeting response
         * @param ctx handler context
         */
        public SSLHandShakeCompleteListener(@Nonnull final SmtpResponse serverResponse, @Nonnull final ChannelHandlerContext ctx) {
            this.serverResponse = serverResponse;
            this.ctx = ctx;
        }

        @Override
        public void operationComplete(@Nonnull final Future<? super Channel> sslConnectionFuture) throws Exception {
            if (sslConnectionFuture.isSuccess()) {
                // add the command response handler
                final SmtpAsyncSessionImpl session = new SmtpAsyncSessionImpl(ctx.channel(), logger, logOpt, sessionId, ctx.pipeline(), sessionCtx);
                final SmtpAsyncCreateSessionResponse createSessionResponse = new SmtpAsyncCreateSessionResponse(session, this.serverResponse);
                sessionCreatedFuture.done(createSessionResponse);
                if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                    logger.debug("[{},{}] Starttls succeeds. Connection is now encrypted.", sessionId, sessionCtx);
                }
            } else {
                handleSessionFailed(this.ctx, new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, sessionId, sessionCtx));
            }
        }
    }

    /** This enum indicates different states in the startTls flow. */
    private enum StartTlsState {
        /** Waiting for server greeting message after re-connection, send EHLO if greeting code is 220, otherwise fail the session future. */
        GET_SERVER_GREETING,

        /**
         * Waiting for EHLO response, send STARTTLS when all responses are good and server has startTls capability. If server replies bad response or
         * no startTls capability is received, it fails the session future.
         */
        GET_EHLO_RESP,

        /** Waiting for STARTTLS response, add SslHandler if response code is 220, otherwise fails. */
        GET_STARTTLS_RESP;
    }

    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "StarttlsHandler";

    /** Handler name for the ssl handler. */
    private static final String SSL_HANDLER_NAME = "sslHandler";

    /** Literal for the STARTTLS capability. */
    public static final String STARTTLS = "STARTTLS\r\n";

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

    /** Starttls state that the handler is in. */
    private StartTlsState startTlsState;

    /** Flag recording whether server has replied STARTTLS capability. */
    private boolean receivedStarttlsCapability;

    /**
     * Initializes a {@link StarttlsHandler} to process greeting after plain re-connection.
     *
     * @param sessionFuture SMTP session future
     * @param logOpt logging option for the session to be created
     * @param sessionId the session id
     * @param sessionData sessionData object containing information about the connection.
     */
    public StarttlsHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData) {
        this(sessionFuture, LoggerFactory.getLogger(StarttlsHandler.class), logOpt, sessionId, sessionData);
    }

    /**
     * Initializes a {@link StarttlsHandler} to process greeting after plain re-connection.
     *
     * @param sessionFuture SMTP session future
     * @param logger the {@link Logger} instance for {@link StarttlsHandler}
     * @param logOpt logging option for the session to be created
     * @param sessionId the session id
     * @param sessionData sessionData object containing information about the connection.
     */
    StarttlsHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture,
            @Nonnull final Logger logger,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nonnull final SmtpAsyncSessionData sessionData) {
        this.sessionCreatedFuture = sessionFuture;
        this.logger = logger;
        this.logOpt = logOpt;
        this.sessionId = sessionId;
        this.sessionCtx = sessionData.getSessionContext();
        this.sessionData = sessionData;
        this.startTlsState = StartTlsState.GET_SERVER_GREETING;
        this.receivedStarttlsCapability = false;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse serverResponse, @Nonnull final List<Object> out) {
        final Channel channel = ctx.channel();
        final ChannelPipeline pipeline = ctx.pipeline();
        SmtpAsyncClientException failedCause = null; // exception to be set to session on failure

        switch (this.startTlsState) {
        case GET_SERVER_GREETING:
            // check server greeting
            if (serverResponse.getCode().value() == SmtpResponse.Code.GREETING) {
                channel.writeAndFlush(new ExtendedHelloCommand(EHLO_CLIENT_NAME).getCommandLineBytes());
                this.startTlsState = StartTlsState.GET_EHLO_RESP;
            } else {
                failedCause = new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_INVALID_GREETING_CODE, sessionId, sessionCtx,
                        serverResponse.toString());
            }
            break;

        case GET_EHLO_RESP:
            // check EHLO responses
            if (STARTTLS.equalsIgnoreCase(serverResponse.getMessage())) {
                this.receivedStarttlsCapability = true;
            }

            // decide next state when we receive last response
            if (serverResponse.isLastLineResponse()) {
                if (serverResponse.getCode().value() != Code.EHLO_SUCCESS) {
                    // receive bad responses for EHLO
                    failedCause = new SmtpAsyncClientException(FailureType.BAD_EHLO_RESPONSE, sessionId, sessionCtx, serverResponse.toString());
                } else if (!this.receivedStarttlsCapability) {
                    // server doesn't reply STARTTLS capability
                    failedCause = new SmtpAsyncClientException(FailureType.NO_STARTTLS_CAPABILITY, sessionId, sessionCtx, serverResponse.toString());
                } else {
                    // confirmed STARTTLS capability from server, send STARTTLS command
                    channel.writeAndFlush(new StarttlsCommand().getCommandLineBytes());
                    this.startTlsState = StartTlsState.GET_STARTTLS_RESP;
                }
            }
            break;

        case GET_STARTTLS_RESP:
            // receive startTls response, remove this handler and add SslHandler
            pipeline.remove(this);

            if (serverResponse.isLastLineResponse() && serverResponse.getCode().value() == SmtpResponse.Code.STARTTLS_SUCCESS) {
                // successful response
                try {
                    // create sslHandler
                    final SslHandler sslHandler = SmtpAsyncClient.createSSLHandler(ctx.alloc(), this.sessionData.getHost(),
                            this.sessionData.getPort(), this.sessionData.getSniNames(), this.sessionData.getSSLContext());
                    // add listener to check if ssl connection succeeds
                    sslHandler.handshakeFuture().addListener(new SSLHandShakeCompleteListener(serverResponse, ctx));
                    ctx.pipeline().addFirst(SSL_HANDLER_NAME, sslHandler);
                } catch (final SSLException e) {
                    failedCause = new SmtpAsyncClientException(FailureType.SSL_CONTEXT_EXCEPTION, this.sessionId, this.sessionCtx);
                }
            } else {
                failedCause = new SmtpAsyncClientException(FailureType.BAD_STARTTLS_RESPONSE, this.sessionId, this.sessionCtx,
                        serverResponse.toString());
            }
            break;

        default:
            // shouldn't reach here
            failedCause = new SmtpAsyncClientException(FailureType.ILLEGAL_STATE, sessionId, sessionCtx, serverResponse.toString());
        }

        // close channel and clean up if startTls failed, all handlers will be removed through handleSessionFailed's close.
        if (failedCause != null) {
            handleSessionFailed(ctx, failedCause);
        }
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {
        handleSessionFailed(ctx, new SmtpAsyncClientException(FailureType.CHANNEL_EXCEPTION, cause, this.sessionId, this.sessionCtx));
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
                handleSessionFailed(ctx,
                        new SmtpAsyncClientException(FailureType.CHANNEL_TIMEOUT, this.sessionId, this.sessionCtx));
            }
        }
    }

    @Override
    public void channelInactive(@Nonnull final ChannelHandlerContext ctx) {
        if (this.sessionCreatedFuture == null) {
            return; // cleanup() has been called, leave
        }
        handleSessionFailed(ctx, new SmtpAsyncClientException(FailureType.CHANNEL_INACTIVE, this.sessionId, this.sessionCtx));

    }

    /**
     * Fails the session future on error, close the channel and cleanup.
     *
     * @param ctx handler context
     * @param cause exception to set with future done
     */
    private void handleSessionFailed(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpAsyncClientException cause) {
        this.logger.error("[{},{}] startTls failed.", this.sessionId, this.sessionCtx, cause);
        this.sessionCreatedFuture.done(cause);
        closeAndcleanup(ctx);
    }

    /**
     * Closes the connection and clean up class members to avoids loitering.
     *
     * @param ctx the ChannelHandlerContext
     */
    private void closeAndcleanup(@Nonnull final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            // closing the channel if server is still active
            ctx.close();
        }
        this.sessionCreatedFuture = null;
        this.logger = null;
        this.logOpt = null;
        this.sessionData = null;
        this.sessionCtx = null;
        this.startTlsState = null;
    }
}
