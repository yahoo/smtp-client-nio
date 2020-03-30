/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines a Recipient (RCPT) command.
 */
public class RecipientCommand extends AbstractSmtpCommand {

    /** String literal for "RCPT". */
    private static final String RCPT = "RCPT";

    /** String literal for "TO". */
    private static final String TO = "TO";

    /** The recipient argument for the command. */
    private String recipient;

    /**
     * Initializes a RCPT command object.
     *
     * @param recipient the address of the recipient.
     */
    public RecipientCommand(@Nonnull final String recipient) {
        super(RCPT);
        this.recipient = recipient;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.RCPT;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        // space, colon, left and right angle brackets make up 4 of the extra chars
        final int len = command.length() + TO.length() + recipient.length() + CRLF_B.length + SmtpClientConstants.PADDING_LEN;
        return Unpooled.buffer(len)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(TO.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.COLON)
                .writeByte(SmtpClientConstants.L_ANGLE_BRACKET)
                .writeBytes(recipient.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.R_ANGLE_BRACKET)
                .writeBytes(CRLF_B);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        recipient = null;
    }
}
