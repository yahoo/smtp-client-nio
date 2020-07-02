/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;

import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines the Authentication (AUTH) command using the "XOAUTH2" mechanism.
 */
public class AuthenticationXoauth2Command extends AbstractAuthenticationCommand {

    /** String literal for "use=", part of required syntax for the command. **/
    private static final String USER_EQUAL = "user=";

    /** String literal for "use=", part of required syntax for the command. **/
    private static final String AUTH_BEARER = "auth=Bearer ";

    /** String in place of the actual secret in the debugging data. */
    private static final String LOG_SECRET_PLACEHOLDER = "<secret>";

    /** User to be authenticated. **/
    private String username;

    /** User's OAuth 2.0 access token. **/
    private String token;

    /**
     * Initializes an AUTH command to authenticate via the "XOAUTH2" mechanism (OAuth 2.0).
     *
     * @param username username of the account holder
     * @param accessToken OAuth 2.0 access token for the account
     */
    public AuthenticationXoauth2Command(@Nonnull final String username, @Nonnull final String accessToken) {
        super(Mechanism.XOAUTH2);
        this.username = username;
        this.token = accessToken;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.username = null;
        this.token = null;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        // XOAUTH2 format: "user={username}^Aauth=Bearer {token}^A^A"
        final String commandStr = new StringBuilder()
                .append(USER_EQUAL).append(username).append(SmtpClientConstants.SOH)
                .append(AUTH_BEARER).append(token).append(SmtpClientConstants.SOH).append(SmtpClientConstants.SOH)
                .toString();
        return Unpooled.buffer(AUTH_B.length + getMechanism().length() + commandStr.length() + SmtpClientConstants.PADDING_LEN).writeBytes(AUTH_B)
                .writeByte(SmtpClientConstants.SPACE).writeBytes(getMechanism().getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE).writeBytes(Base64.encodeBase64(commandStr.getBytes(StandardCharsets.UTF_8))).writeBytes(CRLF_B);
    }

    /**
     * Upon a failed negotiation, the server returns a base64 encoded response indicating the error. The client will then send CRLF
     * as dummy data back to the server to finalize the negotiation. (See RFC 7628, section 3 for details)
     *
     * @param serverResponse contains base64 encoded server response indicating authentication failure
     * @return {@link ByteBuf} containing CRLF bytes
     */
    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) {
        return Unpooled.buffer(CRLF_B.length).writeBytes(CRLF_B);
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
