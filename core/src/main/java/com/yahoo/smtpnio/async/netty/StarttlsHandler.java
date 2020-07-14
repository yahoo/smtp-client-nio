/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import org.slf4j.Logger;

import com.yahoo.smtpnio.async.client.SmtpAsyncCreateSessionResponse;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpAsyncSessionData;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This handler receives STARTTLS response sent by {@link EhloHandler}. If the response code if 250, add SslHandler to upgrade current plain
 * connection to encrypted connection. starting encryption connection.
 */
public class StarttlsHandler extends MessageToMessageDecoder<SmtpResponse> {

    /** Literal for the name registered in pipeline. */
    public static final String HANDLER_NAME = "SmtpClientConnectHandler";

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

    /**
     * Initialize an StarttlsHandler for receiving STARTTLS response and upgrading to encrypted connection.
     *
     * @param sessionFuture SMTP session future
     * @param logger logger instance
     * @param logOpt logging option for this session.
     * @param sessionId session id
     * @param sessionCtx context for session information
     * @param sessionData sessionData object containing information about the connection.
     */
    public StarttlsHandler(@Nonnull final SmtpFuture<SmtpAsyncCreateSessionResponse> sessionFuture,
            @Nonnull final Logger logger,
            @Nonnull final DebugMode logOpt, final long sessionId, @Nullable final Object sessionCtx,
            @Nullable final SmtpAsyncSessionData sessionData) {
        this.sessionCreatedFuture = sessionFuture;
        this.logger = logger;
        this.logOpt = logOpt;
        this.sessionCtx = sessionCtx;
        this.sessionId = sessionId;
        this.sessionData = sessionData;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse serverResponse, @Nonnull final List<Object> out) {
        final ChannelPipeline pipeline = ctx.pipeline();
        pipeline.remove(this);

        if (serverResponse.getCode().value() == SmtpResponse.Code.GREETING) { // successful response
            // add the command response handler
            final SmtpAsyncSessionImpl session = new SmtpAsyncSessionImpl(ctx.channel(), logger, logOpt, sessionId, pipeline, sessionCtx);
            final SmtpAsyncCreateSessionResponse response = new SmtpAsyncCreateSessionResponse(session, serverResponse);
            sessionCreatedFuture.done(response);
            try {
                // add sslHandler
                ctx.pipeline().addFirst(newSslHandler(ctx.alloc(), sessionData.getHost(), sessionData.getPort(), sessionData.getSniNames()));
            } catch (SSLException e) {
                logger.error("[{},{}] Failed to create SslContext after receving STARTTLS response: {}", sessionId, sessionCtx, e);
                sessionCreatedFuture
                        .done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, e, sessionId, sessionCtx));
                close(ctx);
            }
            if (logger.isTraceEnabled() || logOpt == SmtpAsyncSession.DebugMode.DEBUG_ON) {
                logger.debug("[{},{}] Starttls was successful. Connection is now encrypted. ", sessionId, sessionCtx);
            }
        } else {
            logger.error("[{},{}] STARTTLS response was not successful:{}", sessionId, sessionCtx, serverResponse.toString());
            sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_INVALID_GREETING_CODE, sessionId, sessionCtx,
                    serverResponse.toString()));
            close(ctx); // closing the channel if we r not getting a ok greeting
        }
        cleanup();

    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {
        logger.error("[{},{}] Re-connection failed due to encountering exception:{}.", sessionId, sessionCtx, cause);
        sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEPTION, cause, sessionId, sessionCtx));
        close(ctx); // closing the connection
    }

    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.READER_IDLE) {
                logger.error("[{},{}] Re-connection failed due to taking longer than configured allowed time.", sessionId, sessionCtx);
                sessionCreatedFuture.done(new SmtpAsyncClientException(FailureType.CONNECTION_FAILED_EXCEED_IDLE_MAX, sessionId, sessionCtx));
                // closing the channel if server is not responding for max read timeout limit
                cleanup();
                close(ctx);
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
        sessionCtx = null;
        sessionData = null;
    }

    /**
     * Create a new SslHanlder for decryption/encryption.
     *
     * @param alloc Allocator for ByteBuf objects
     * @param host host name of server
     * @param port port of server
     * @param sniNames collection of SNI names
     * @return an SslHandler to process ssl connection
     * @throws SSLException when failed to create {@link SslContext}}
     */
    private SslHandler newSslHandler(@Nonnull final ByteBufAllocator alloc, @Nullable final String host, final int port,
            @Nullable final Collection<String> sniNames) throws SSLException {
        final SslContext sslContext = SslContextBuilder.forClient().build();
        final SSLEngine engine = sslContext.newEngine(alloc, host, port);
        if (sniNames != null && !sniNames.isEmpty()) { // SNI support
            final List<SNIServerName> serverNames = new ArrayList<>();
            for (final String sni : sniNames) {
                serverNames.add(new SNIHostName(sni));
            }
            final SSLParameters params = new SSLParameters();
            params.setServerNames(serverNames);
            engine.setSSLParameters(params);
        }
        return new SslHandler(engine);
    }

}
