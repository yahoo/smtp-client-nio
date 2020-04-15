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

    /** String literal for "PLAIN". */
    private static final String PLAIN = "PLAIN";

    /** String in place of the actual secret in the debugging data. */
    private static final String LOG_SECRET_PLACEHOLDER = "<secret>";

    /** Authentication secret. */
    private byte[] secret;

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will encode the username and password into base64.
     *
     * @param username username of the intended sender, usually an email address, in clear text
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(@Nonnull final String username, @Nonnull final String password) {
        super(PLAIN);
        this.secret = Base64.encodeBase64((SmtpClientConstants.NULL + username + SmtpClientConstants.NULL + password)
                .getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will encode the authzid, username and password into base64.
     *
     * @param authorizationIdentity The authorization identity (aka. authzid)
     * @param username username/authentication identity of the intended sender, usually an email address, in clear text (aka. authcid)
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(@Nonnull final String authorizationIdentity, @Nonnull final String username, @Nonnull final String password) {
        super(PLAIN);
        this.secret = Base64.encodeBase64((authorizationIdentity + SmtpClientConstants.NULL + username + SmtpClientConstants.NULL + password)
                .getBytes(StandardCharsets.US_ASCII));
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        final int numSpaces = 4;
        return Unpooled.buffer(command.length() + mechanism.length() + secret.length + numSpaces * SmtpClientConstants.CHAR_LEN)
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
