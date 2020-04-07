/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines the Authentication (AUTH) command using the "PLAIN" mechanism.
 */
public class AuthenticationPlainCommand extends AbstractAuthenticationCommand {

    /** String in place of the actual secret in the debugging data. */
    private static final String LOG_SECRET_PLACEHOLDER = "<secret>";

    /** Authentication secret. */
    private byte[] secret;

    /** Number of spaces used. */
    private static final int NUM_SPACE = 4;


    /** String in place of the actual secret in the debugging data. */
    private static final String LOG_SECRET_PLACEHOLDER = "<secret>";

    /** Authentication secret. */
    private byte[] secret;

    /**
     * Initializes an AUTH command to authenticate via plaintext.
     *
     * @param secret base64 encoded authentication secret as a byte array
     */
    private AuthenticationPlainCommand(@Nonnull final byte[] secret) {
        super(PLAIN);
        this.secret = secret;
    }

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will encode the authzid, username and password into base64.
     *
     * @param authorizationIdentity The authorization identity (aka. authzid)
     * @param username username/authentication identity of the intended sender, usually an email address, in clear text (aka. authcid)
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(final String authorizationIdentity, @Nonnull final String username, @Nonnull final String password) {
        super(Mechanism.PLAIN);
        final String authzid = authorizationIdentity == null ? "" : authorizationIdentity;
        this.secret = Base64.encodeBase64((authzid + SmtpClientConstants.NULL + username + SmtpClientConstants.NULL + password)
                .getBytes(StandardCharsets.US_ASCII));
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
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
        this.secret = null;
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
