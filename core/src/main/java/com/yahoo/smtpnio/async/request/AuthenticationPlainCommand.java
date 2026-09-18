/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Base64;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines the Authentication (AUTH) command using the "PLAIN" mechanism.
 */
public class AuthenticationPlainCommand extends AbstractAuthenticationCommand {

    /** String in place of the actual secret in the debugging data. */
    private static final String LOG_SECRET_PLACEHOLDER = "<secret>";

    /** The authorization identity (aka. authzid), optional. */
    @Nullable
    private String authorizationIdentity;

    /** Username/authentication identity of the intended sender, in clear text (aka. authcid). */
    private String username;

    /** Password associated with the username, in clear text. */
    private String password;

    /** Number of spaces used. */
    private static final int NUM_SPACE = 4;

    /**
     * Initializes an AUTH command to authenticate via plaintext. The authzid, username and password are encoded into base64 when the
     * command line is built.
     *
     * @param authorizationIdentity The authorization identity (aka. authzid)
     * @param username username/authentication identity of the intended sender, usually an email address, in clear text (aka. authcid)
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(@Nullable final String authorizationIdentity, @Nonnull final String username, @Nonnull final String password) {
        super(Mechanism.PLAIN);
        this.authorizationIdentity = authorizationIdentity;
        this.username = username;
        this.password = password;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() throws SmtpAsyncClientException {
        // PLAIN format: "{authzid}\0{username}\0{password}", NULL separates the fields so no argument may carry one
        final StringBuilder secretBuilder = new StringBuilder();
        if (authorizationIdentity != null) {
            ARGUMENT_FORMATTER.formatArgument(authorizationIdentity, secretBuilder, "authorization identity");
        }
        secretBuilder.append(SmtpClientConstants.NULL);
        ARGUMENT_FORMATTER.formatArgument(username, secretBuilder, "username");
        secretBuilder.append(SmtpClientConstants.NULL);
        ARGUMENT_FORMATTER.formatArgument(password, secretBuilder, "password");
        final byte[] secret = Base64.encodeBase64(secretBuilder.toString().getBytes(StandardCharsets.US_ASCII));
        return Unpooled.buffer(command.length() + mechanism.length() + secret.length + NUM_SPACE * SmtpClientConstants.CHAR_LEN)
                .writeBytes(AUTH_B)
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(mechanism.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(secret)
                .writeBytes(CRLF_B);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.authorizationIdentity = null;
        this.username = null;
        this.password = null;
    }

    @Nonnull
    @Override
    public String getDebugData() {
        return new StringBuilder(AUTH)
                .append(SmtpClientConstants.SPACE)
                .append(mechanism)
                .append(SmtpClientConstants.SPACE)
                .append(LOG_SECRET_PLACEHOLDER)
                .append(SmtpClientConstants.CRLF).toString();
    }
}
