/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

/**
 * This is the base class for all Authentication (AUTH) commands.
 */
public abstract class AbstractAuthenticationCommand extends AbstractSmtpCommand {

    /** String literal for "AUTH". */
    protected static final String AUTH = "AUTH";

    /** Byte array for "AUTH". */
    protected static final byte[] AUTH_B = AUTH.getBytes(StandardCharsets.US_ASCII);

    /** Mechanism used for authentication. */
    protected String mechanism;

    /** Three AUTH mechanisms. */
    protected enum Mechanism {
        /** AUTH LOGIN. */
        LOGIN,

        /** AUTH PLAIN. */
        PLAIN,

        /** AUTH XOAUTH2. */
        XOAUTH2
    }

    /**
     * Initializes an AUTH command object used to authenticate via a specified SASL mechanism that cannot be completed in one command
     * and requires additional challenge responses from the server.
     *
     * @param mechanism authentication mechanism
     */
    protected AbstractAuthenticationCommand(@Nonnull final Mechanism mechanism) {
        super(AUTH);
        this.mechanism = mechanism.name();
    }

    /**
     * @return the mechanism as a string.
     */
    public String getMechanism() {
        return mechanism;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        mechanism = null;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.AUTH;
    }

    @Override
    public boolean isCommandLineDataSensitive() {
        return true;
    }
}
