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
 * This class defines an Extended Hello (EHLO) command used to greet the server.
 */
public class ExtendedHelloCommand extends AbstractSmtpCommand {

    /** String literal for "EHLO". */
    private static final String EHLO = "EHLO";

    /** the identify of the client. */
    private String name;

    /**
     * Initializes a EHLO command object.
     *
     * @param name the domain/name that the client greets the server with, a required argument for EHLO
     */
    public ExtendedHelloCommand(@Nonnull final String name) {
        super(EHLO);
        this.name = name;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        return Unpooled.buffer(command.length() + SmtpClientConstants.CHAR_LEN + name.length() + CRLF_B.length)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(name.getBytes(StandardCharsets.US_ASCII))
                .writeBytes(CRLF_B);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.EHLO;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        name = null;
    }
}
