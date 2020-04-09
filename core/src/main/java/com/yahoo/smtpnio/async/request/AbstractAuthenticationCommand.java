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
 * This is the base class for all Authentication (AUTH) commands.
 */
public abstract class AbstractAuthenticationCommand extends AbstractSmtpCommand {

    /** String literal for "AUTH". */
    private static final String AUTH = "AUTH";

    /** Mechanism used for authentication. */
    private String mechanism;

    /**
     * Initializes an AUTH command object used to authenticate via a specified SASL mechanism that cannot be completed in one command
     * and requires additional challenge responses from the server.
     *
     * @param mechanism authentication mechanism
     */
    protected AbstractAuthenticationCommand(@Nonnull final String mechanism) {
        super(AUTH);
        this.mechanism = mechanism;
    }

    /**
     * @return the mechanism as a string.
     */
    public String getMechanism() {
        return mechanism;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        return Unpooled.buffer(command.length() + mechanism.length() + CRLF_B.length + SmtpClientConstants.PADDING_LEN)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(mechanism.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void cleanup() {
        super.cleanup();
        mechanism = null;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.AUTH;
    }

    @Override
    public boolean isCommandLineDataSensitive() {
        return true;
    }

    @Nonnull
    @Override
    public String getDebugData() {
        return AUTH + SmtpClientConstants.SPACE + mechanism;
    }
}
