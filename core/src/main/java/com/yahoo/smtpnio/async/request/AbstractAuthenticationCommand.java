/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This is the base class of the Authentication (AUTH) command.
 */
public abstract class AbstractAuthenticationCommand extends AbstractSmtpCommand {

    /** String literal for "AUTH". */
    private static final String AUTH = "AUTH";

    /** The mechanism used for the authentication. */
    private String mechanism;

    /** The initial secret to be sent. */
    @Nullable
    private String secret;

    /**
     * Initializes an AUTH command object used to authenticate via a specified SASL mechanism that can be completed in one command.
     *
     * @param mechanism authentication mechanism
     * @param secret authentication secret
     */
    protected AbstractAuthenticationCommand(@Nonnull final String mechanism, @Nonnull final String secret) {
        super(AUTH);
        this.mechanism = mechanism;
        this.secret = secret;
    }

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
        final ByteBuf result = Unpooled.buffer(command.length() + mechanism.length() + CRLF_B.length + SmtpClientConstants.PADDING_LEN)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(mechanism.getBytes(StandardCharsets.US_ASCII));
        if (secret != null) {
            result.writeByte(SmtpClientConstants.SPACE).writeBytes(secret.getBytes(StandardCharsets.US_ASCII));
        }
        return result.writeBytes(CRLF_B);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        mechanism = null;
        secret = null;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.AUTH;
    }

    @Override
    public boolean isCommandLineDataSensitive() {
        return true;
    }
}
