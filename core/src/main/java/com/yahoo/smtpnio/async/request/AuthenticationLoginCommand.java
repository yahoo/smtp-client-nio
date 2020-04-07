/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This class defines the Authentication (AUTH) command using the "LOGIN" mechanism.
 */
public class AuthenticationLoginCommand extends AbstractAuthenticationCommand {

    /** String literal for "LOGIN". */
    private static final String LOGIN = "LOGIN";

    /** String in place of the actual username in the debugging data. */
    private static final String USERNAME_PLACEHOLDER = "<username>";

    /** String in place of the actual password in the debugging data. */
    private static final String PASSWORD_PLACEHOLDER = "<password>";

    /** Username for the login (base64 encoded string). */
    private String username;

    /** Password for the login (base64 encoded string). */
    private String password;

    /** Variable used to indicate which input to be sent next to the server. */
    private InputState nextInputState;

    /**
     * Enum used to keep track of what input to send to the server next.
     */
    private enum InputState {

        /** Username should be sent next. */
        USERNAME,

        /** Password should be sent next. */
        PASSWORD,

        /** All expected input has been sent and completed. */
        COMPLETED
    }

    /**
     * Initializes an AUTH command to authenticate via the "LOGIN" mechanism.
     *
     * @param username username as a base64 encoded string
     * @param password password as a base64 encoded string
     */
    public AuthenticationLoginCommand(@Nonnull final String username, @Nonnull final String password) {
        super(LOGIN);
        this.username = username;
        this.password = password;
        nextInputState = InputState.USERNAME;
    }

    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) {
        final String input;
        if (nextInputState == InputState.USERNAME) {
            input = username;
            nextInputState = InputState.PASSWORD;
        } else if (nextInputState == InputState.PASSWORD) {
            input = password;
            nextInputState = InputState.COMPLETED;
        } else { // COMPLETED state, normal execution should not reach here
            input = ""; // Fail-safe: In case server asks for more input after username and password, send empty string
            nextInputState = null;
        }
        return Unpooled.buffer(input.length() + CRLF_B.length)
                .writeBytes(input.getBytes(StandardCharsets.US_ASCII))
                .writeBytes(CRLF_B);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        username = null;
        password = null;
        nextInputState = null;
    }

    @Nonnull
    @Override
    public String getDebugData() {
        if (this.nextInputState == InputState.USERNAME) {
            return super.getDebugData();
        }
        if (this.nextInputState == InputState.PASSWORD) {
            return USERNAME_PLACEHOLDER + SmtpClientConstants.CRLF;
        }
        if (this.nextInputState == InputState.COMPLETED) {
            return PASSWORD_PLACEHOLDER + SmtpClientConstants.CRLF;
        }
        return SmtpClientConstants.CRLF;
    }
}
