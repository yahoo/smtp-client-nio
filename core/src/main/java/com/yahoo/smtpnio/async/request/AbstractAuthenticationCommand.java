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

    /** String in place of the actual secret in the debugging data. */
    private static final String SECRET = "<secret>";

    /** Mechanism used for authentication. */
    private String mechanism;

    /** Authentication secret. */
    private String secret;

    /**
     * Initializes an AUTH command object used to authenticate via a specified SASL mechanism.
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
     * @return mechanism as a string.
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
                .writeBytes(mechanism.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(secret.getBytes(StandardCharsets.US_ASCII))
                .writeBytes(CRLF_B);
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

    @Nonnull
    @Override
    public String getDebugData() {
        return String.format("%s %s %s%s", AUTH, mechanism, SECRET, SmtpClientConstants.CRLF);
    }
}
