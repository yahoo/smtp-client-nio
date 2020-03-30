/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.List;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This class handles the business logic of how to process messages and handle events.
 */
public class SmtpClientCommandRespHandler extends MessageToMessageDecoder<SmtpResponse> {

    /** Literal for the name registered in the pipeline. */
    public static final String HANDLER_NAME = "SmtpClientCommandRespHandler";

    /** The SMTP channel event processor. */
    private SmtpCommandChannelEventProcessor processor;

    /**
     * Initializes a handler to process pipeline response and events. This handler should include client business logic.
     *
     * @param processor SMTP channel processor that handles the SMTP events
     */
    public SmtpClientCommandRespHandler(@Nonnull final SmtpCommandChannelEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void decode(@Nonnull final ChannelHandlerContext ctx, @Nonnull final SmtpResponse msg, @Nonnull final List<Object> out) {
        processor.handleChannelResponse(msg);
    }

    @Override
    public void exceptionCaught(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Throwable cause) {
        processor.handleChannelException(cause);
    }

    /**
     * Receives an idle state event when {@code READER_IDLE} (no data was received for a while) or {@code WRITER_IDLE} (no data was sent for a while).
     *
     * @param ctx channel handler ctx
     * @param msg idle state event generated on idle connections by IdleStateHandler
     */
    @Override
    public void userEventTriggered(@Nonnull final ChannelHandlerContext ctx, @Nonnull final Object msg) {
        if (msg instanceof IdleStateEvent) { // Handle the IdleState if needed
            final IdleStateEvent event = (IdleStateEvent) msg;
            if (event.state() == IdleState.ALL_IDLE) {
                processor.handleIdleEvent(event);
            }
        }
    }

    /**
     * Handles the event when a channel is closed (disconnected) either by the server or client.
     *
     * @param ctx channel handler ctx
     */
    @Override
    public void channelInactive(@Nonnull final ChannelHandlerContext ctx) {
        if (processor == null) {
            return; // cleanup() has been called, leave
        }
        processor.handleChannelClosed();
        cleanup();
    }

    /**
     * Avoids loitering.
     */
    private void cleanup() {
        processor = null;
    }
}
