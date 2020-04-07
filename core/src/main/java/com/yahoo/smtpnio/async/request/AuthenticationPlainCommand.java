/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.annotation.Nonnull;

/**
 * This class defines the Authentication (AUTH) command using the "PLAIN" mechanism.
 */
public class AuthenticationPlainCommand extends AbstractAuthenticationCommand {

    /** String literal for "PLAIN". */
    private static final String PLAIN = "PLAIN";

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will encode the username and password into base64.
     *
     * @param username username of the intended sender, usually an email address, in clear text
     * @param password password associated with the above username, in clear text
     */
    public AuthenticationPlainCommand(@Nonnull final String username, @Nonnull final String password) {
        super(PLAIN, Base64.getEncoder().encodeToString((SmtpClientConstants.NULL + username + SmtpClientConstants.NULL + password)
                .getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Initializes an AUTH command to authenticate via plaintext. This constructor will not encode the input for the client.
     *
     * @param secret authentication secret already encoded as a base64 string
     */
    public AuthenticationPlainCommand(@Nonnull final String secret) {
        super(PLAIN, secret);
    }
}
