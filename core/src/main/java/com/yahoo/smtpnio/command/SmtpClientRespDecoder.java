/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.command;

import java.util.List;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * A simple SMTP response decoder that outputs {@link SmtpResponse} objects.
 */
public class SmtpClientRespDecoder extends MessageToMessageDecoder<String> {

    @Override
    protected void decode(final ChannelHandlerContext channelHandlerContext, @Nonnull final String message, @Nonnull final List<Object> out)
            throws SmtpAsyncClientException {
        out.add(new SmtpResponse(message));
    }
}
