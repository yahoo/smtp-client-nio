/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

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

    /**
     * Initializes a DATA command object that contains the message to be sent.
     *
     * @param message a {@link SMTPMessage} containing the message
     * @return the corresponding DATA command
     */
    public static DataCommand fromSmtpMessage(final SMTPMessage message) {
        return new DataCommand(new Supplier<byte[]>() {
            @Override
            public byte[] get() {
                final ByteArrayOutputStream output = new ByteArrayOutputStream();
                try {
                    message.writeTo(output);
                    return output.toByteArray();
                } catch (IOException | MessagingException e) {
                    return null;
                }
            }
        });
    }

    /** String literal for "DATA". */
    private static final String DATA = "DATA";

    /** The message to be sent. */
    private Supplier<byte[]> message;

    /**
     * Initializes a DATA command object that contains the message to be sent.
     *
     * @param message a {@link Supplier} representing the message (Supplier of byte array)
     */
    public DataCommand(@Nonnull final Supplier<byte[]> message) {
        super(DATA);
        this.message = message;
    }

    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) {
        final byte[] bytes = message.get();
        if (bytes == null) {
            return null;
        }
        return Unpooled.buffer(bytes.length + SmtpClientConstants.PADDING_LEN)
            .writeBytes(bytes)
            .writeBytes(AbstractSmtpCommand.CRLF_B)
            .writeByte(SmtpClientConstants.PERIOD)
            .writeBytes(AbstractSmtpCommand.CRLF_B);
    }

    /**
     * @return the {@link SMTPMessage} object
     */
    public byte[] getMessage() {
        return message.get();
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.DATA;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        message = null;
    }
}
