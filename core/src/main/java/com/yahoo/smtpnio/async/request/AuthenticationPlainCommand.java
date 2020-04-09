/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;

import io.netty.buffer.ByteBuf;

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
     * Initializes an AUTH command to authenticate via plaintext.
     *
     * @param secret base64 encoded authentication secret as a byte array
     */
    private AuthenticationPlainCommand(@Nonnull final byte[] secret) {
        super(PLAIN);
        this.secret = secret;
    }

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will encode the username and password into base64.
     *
     * @param username username of the intended sender, usually an email address, in clear text
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(@Nonnull final String username, @Nonnull final String password) {
        this(Base64.encodeBase64((SmtpClientConstants.NULL + username + SmtpClientConstants.NULL + password).getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will not encode the input for the client.
     *
     * @param secret authentication secret already encoded as a base64 string
     */
    public AuthenticationPlainCommand(@Nonnull final String secret) {
        this(secret.getBytes(StandardCharsets.US_ASCII));
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        return super.getCommandLineBytes()
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
        return super.getDebugData() + SmtpClientConstants.SPACE + LOG_SECRET_PLACEHOLDER + SmtpClientConstants.CRLF;
    }
}
