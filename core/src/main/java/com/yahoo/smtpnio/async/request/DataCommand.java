/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.mail.MessagingException;

import com.sun.mail.smtp.SMTPMessage;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines a Data (DATA) command.
 */
public class DataCommand extends AbstractSmtpCommand {

    /** String literal for "DATA". */
    private static final String DATA = "DATA";

    /** The message to be sent. */
    private SMTPMessage message;

    /**
     * Initializes a DATA command object that contains the message to be sent.
     *
     * @param message a {@link SMTPMessage} containing the message
     */
    public DataCommand(@Nonnull final SMTPMessage message) {
        super(DATA);
        this.message = message;
    }

    /**
     * @return the {@link SMTPMessage} object
     */
    public SMTPMessage getMessage() {
        return message;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.DATA;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        message = null;
    }

    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            return Unpooled.buffer(output.size() + SmtpClientConstants.PADDING_LEN)
                    .writeBytes(output.toByteArray())
                    .writeBytes(SmtpClientConstants.CRLF.getBytes(StandardCharsets.US_ASCII))
                    .writeByte(SmtpClientConstants.PERIOD)
                    .writeBytes(SmtpClientConstants.CRLF.getBytes(StandardCharsets.US_ASCII));
        } catch (final MessagingException | IOException e) {
            return null;
        }
    }
}
