/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.exception;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is the exception class for any kind of SMTP async error.
 */
public class SmtpAsyncClientException extends Exception {

    /**
     * An enum that specifies different types of failures for an unsuccessful operation.
     */
    public enum FailureType {

        /** Server greeting is absent upon connection. */
        CONNECTION_FAILED_INVALID_GREETING_CODE("Unexpected Server greeting code"),

        /** Connection failed with an exception. */
        CONNECTION_FAILED_EXCEPTION("Connection failed with an exception."),

        /** Timeout on server connection. */
        CONNECTION_FAILED_EXCEED_IDLE_MAX("Timeout on server connection."),

        /** Connection inactive. */
        CONNECTION_INACTIVE("Connection inactive."),

        /** Operation on an already closed channel. */
        OPERATION_PROHIBITED_ON_CLOSED_CHANNEL("Operation on a closed channel is prohibited."),

        /** Command is not allowed to be executed. */
        COMMAND_NOT_ALLOWED("Command is not allowed to be executed."),

        /** Write to SMTP server failed. */
        WRITE_TO_SERVER_FAILED("Write to SMTP server failed."),

        /** Failed in closing connection. */
        CLOSING_CONNECTION_FAILED("Failed in closing connection"),

        /** Encountering exception during communication to remote. */
        CHANNEL_EXCEPTION("Encountering exception during communication to remote."),

        /** Channel disconnected. */
        CHANNEL_DISCONNECTED("Channel was closed already."),

        /** This operation is not supported for this command. */
        OPERATION_NOT_SUPPORTED_FOR_COMMAND("This operation is not supported for this command."),

        /** Timeout from server. */
        CHANNEL_TIMEOUT("Timeout from server after command is sent."),

        /** Invalid input. */
        INVALID_INPUT("Input is invalid."),

        /** Invalid Server Response. */
        INVALID_SERVER_RESPONSE("The server response is invalid."),

        /** Server asks for more input. An example is the extra prompt after username and password in AUTH LOGIN */
        MORE_INPUT_THAN_EXPECTED("The server ask for more input than expected"),

        /** Server replies non-ssl during an ssl enabled connection. */
        NOT_SSL_RECORD("Server replied non-ssl response during an ssl enabled connection."),

        /** Stattls fails. */
        STARTTLS_FALIED("Failed on starttls");

        /**
         * Constructor to add an error message for failure type belonging to this enum.
         *
         * @param message the error message associated with this failure type
         */
        FailureType(@Nonnull final String message) {
        }
    }

    /** Required. */
    private static final long serialVersionUID = 1L;

    /** The failure type. */
    @Nonnull
    private final FailureType failureType;

    /** The session id. */
    @Nullable
    private final Long sessionId;

    /** The information about this session that client wants to be printed when exception is displayed. */
    @Nullable
    private final String userInfo;

    /** Additional message that can be carried with the exception. */
    @Nullable
    private final String message;

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type. It is used when session is not created.
     *
     * @param failureType the failure type associated with the error
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType) {
        this(failureType, null, null, null, null);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type and a custom message.
     *
     * @param failureType the failure type associated with the error
     * @param message the message associated with the exception
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, @Nullable final String message) {
        this(failureType, null, null, null, message);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type and cause. It is used when session is not created.
     *
     * @param failureType the failure type associated with the error
     * @param cause the exception underneath
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, final Throwable cause) {
        this(failureType, cause, null, null, null);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type and session id.
     *
     * @param failureType the failure type associated with the error
     * @param sessionId the session id
     * @param sessionCtx additional user information by caller to identify this session
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, @Nonnull final Long sessionId, @Nullable final Object sessionCtx) {
        this(failureType, null, sessionId, sessionCtx, null);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type and session id.
     *
     * @param failureType the failure type associated with the error
     * @param sessionId the session id
     * @param sessionCtx additional user information by caller to identify this session
     * @param message the message associated with the exception
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, @Nonnull final Long sessionId, @Nullable final Object sessionCtx,
                                    @Nonnull final String message) {
        this(failureType, null, sessionId, sessionCtx, message);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type and cause.
     *
     * @param failureType the failure type associated with the error
     * @param cause the exception underneath
     * @param sessionId the session id
     * @param sessionCtx additional user information by caller to identify this session
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, @Nullable final Throwable cause, @Nullable final Long sessionId,
                                    @Nullable final Object sessionCtx) {
        this(failureType, cause, sessionId, sessionCtx, null);
    }

    /**
     * Initializes a {@link SmtpAsyncClientException} with the failure type, cause, session id, context and message.
     *
     * @param failureType the failure type associated with the error
     * @param cause the exception underneath
     * @param sessionId the session id
     * @param sessionCtx additional user information by caller to identify this session
     * @param message the message associated with the exception
     */
    public SmtpAsyncClientException(@Nonnull final FailureType failureType, @Nullable final Throwable cause, @Nullable final Long sessionId,
                                    @Nullable final Object sessionCtx, @Nullable final String message) {
        super(cause);
        this.sessionId = sessionId;
        this.failureType = failureType;
        this.userInfo = (sessionCtx != null) ? sessionCtx.toString() : null;
        this.message = message;
    }

    /**
     * @return the failure type
     */
    @Nonnull
    public FailureType getFailureType() {
        return failureType;
    }

    @Override
    public String getMessage() {
        final StringBuilder sb = new StringBuilder("failureType=").append(failureType.name());
        if (sessionId != null) {
            sb.append(",sId=").append(sessionId);
        }
        if (userInfo != null) {
            sb.append(",uId=").append(userInfo);
        }
        if (message != null) {
            sb.append(",message=").append(message);
        }
        return sb.toString();
    }
}
