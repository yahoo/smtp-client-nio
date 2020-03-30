/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;

/**
 * Basic response reader based on a line delimiter.
 */
public class SmtpClientRespReader extends DelimiterBasedFrameDecoder {

    /** SMTP response line delimiter, carriage return - new line. */
    private static final ByteBuf[] DELIMITER = new ByteBuf[] { Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }) };

    /**
     * Initializes a {@link SmtpClientRespReader} object.
     *
     * @param maxLineLength maximum response line length
     */
    public SmtpClientRespReader(final int maxLineLength) {
        super(maxLineLength, false, DELIMITER);
    }
}
