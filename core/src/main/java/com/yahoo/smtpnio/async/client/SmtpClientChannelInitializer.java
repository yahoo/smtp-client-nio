/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.util.concurrent.TimeUnit;

import com.yahoo.smtpnio.client.SmtpClientRespReader;
import com.yahoo.smtpnio.command.SmtpClientRespDecoder;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * This class initializes the pipeline with the correct handlers.
 */
final class SmtpClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    /** Handler name for idle sate handler. */
    private static final String IDLE_STATE_HANDLER_NAME = "idleStateHandler";

    /** Handler name for response reader. */
    private static final String SMTP_LINE_DECODER_HANDLER_NAME = "SmtpClientRespReader";

    /** Handler name for string decoder. */
    private static final String STRING_DECODER_HANDLER_NAME = "decoder";

    /** Handler name for string encoder. */
    private static final String STRING_ENCODER_HANDLER_NAME = "encoder";

    /** Handler name for response decoder. */
    private static final String STRING_SMTP_MSG_RESPONSE_NAME = "SmtpClientRespDecoder";

    /** Read timeout value. */
    private final int readTimeoutValue;

    /** Timeout unit associated with {@code readTimeoutValue}. */
    private final TimeUnit timeUnit;

    /**
     * Initializes {@link SmtpClientChannelInitializer} with the read time out value.
     *
     * @param readTimeoutValue timeout value for server not responding after write command is sent
     * @param unit the unit {@code readTimeoutValue} is measured in
     */
    SmtpClientChannelInitializer(final int readTimeoutValue, final TimeUnit unit) {
        this.readTimeoutValue = readTimeoutValue;
        this.timeUnit = unit;
    }

    @Override
    protected void initChannel(final SocketChannel socketChannel) {
        final ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast(IDLE_STATE_HANDLER_NAME, new IdleStateHandler(0, 0, readTimeoutValue, timeUnit));
        pipeline.addLast(SMTP_LINE_DECODER_HANDLER_NAME, new SmtpClientRespReader(Integer.MAX_VALUE));
        pipeline.addLast(STRING_DECODER_HANDLER_NAME, new StringDecoder());
        pipeline.addLast(STRING_ENCODER_HANDLER_NAME, new StringEncoder());
        pipeline.addLast(STRING_SMTP_MSG_RESPONSE_NAME, new SmtpClientRespDecoder());
    }
}
