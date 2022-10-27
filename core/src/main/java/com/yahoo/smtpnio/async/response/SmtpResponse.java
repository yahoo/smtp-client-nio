/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.response;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

/**
 * This class defines a SMTP response.
 */
public class SmtpResponse {

    /** Length of reply code; it is always a positive, 3-digit number. */
    public static final int CODE_LENGTH = 3;

    /** Char constant for hyphen (-). */
    private static final char HYPHEN = '-';

    /** Char constant for space. */
    private static final char SPACE = ' ';

    /**
     * This defines a collection of common reply codes according to the SMTP RFC standards. The general, the codes follow this format:
     *
     * 2yz Positive Completion reply
     *
     * 3yz Positive Intermediate reply
     *
     * 4yz Transient Negative Completion reply
     *
     * 5yz Permanent Negative Completion reply
     *
     * Refer to RFC5321 section 4.2 for more details.
     */
    public static class Code {

        /** Initial greeting upon establishing a connection with the server. */
        public static final int GREETING = 220;

        /** Session ending from the "QUIT" command. */
        public static final int CLOSING = 221;

        /** Successful response from the "EHLO" command. */
        public static final int EHLO_SUCCESS = 250;

        /** Server is ready to start TLS. */
        public static final int STARTTLS_SUCCESS = 220;

        /** Awaiting input from the "DATA" command. */
        public static final int START_MSG_INPUT = 354;

        /** Awaiting input for the challenge response from the "AUTH" command. */
        public static final int CHALLENGE_RESPONSE = 334;

        /** Help message response resulting from the "HELP" command. */
        public static final int HELP = 214;

        /** Value of the reply code. */
        private final int value;

        /**
         * Constructor for this enum.
         *
         * @param value integer value of the code
         */
        Code(final int value) {
            this.value = value;
        }

        /**
         * @return the integer value of the code
         */
        public int value() {
            return value;
        }
    }

    /**
     * Enum that indicates the reply type according to the code.
     */
    public enum ReplyType {

        /** 2XX. */
        POSITIVE_COMPLETION,

        /** 3XX. */
        POSITIVE_INTERMEDIATE,

        /** 4XX. */
        NEGATIVE_TRANSIENT,

        /** 5XX. */
        NEGATIVE_PERMANENT
    }

    /** The reply code associated with the response. */
    private final Code code;

    /** The raw server response as a String. */
    @Nonnull
    private final String response;

    /**
     * Initializes a {@link SmtpResponse} object.
     *
     * @param response the raw response from the server as a string
     * @throws SmtpAsyncClientException when parsing an invalid SMTP response that does not follow the RFC standards
     */
    public SmtpResponse(@Nonnull final String response) throws SmtpAsyncClientException {
        if (response.length() < CODE_LENGTH) { // responses should at least contain the 3 digit code
            throw new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.INVALID_SERVER_RESPONSE,
                    "Server response does not contain a valid reply code");
        }

        // Reply code is the first three digits of the response
        final char firstDigit = response.charAt(0);
        final char secondDigit = response.charAt(1);
        final char thirdDigit = response.charAt(2);

        if (
                firstDigit < '2' || firstDigit > '5' || // First digit must be between 2-5 (inclusive)
                secondDigit < '0' || secondDigit > '5' || // Second digit must be between 0-5 (inclusive)
                thirdDigit < '0' || thirdDigit > '9' // Third digit must be between 0-9 (inclusive)
        ) {
            throw new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.INVALID_SERVER_RESPONSE,
                    "Server response does not contain a valid reply code");
        }

        // If there's content after the code, the char right after must either be a space, or hyphen
        if (response.length() > CODE_LENGTH && response.charAt(CODE_LENGTH) != SPACE && response.charAt(CODE_LENGTH) != HYPHEN) {
            throw new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.INVALID_SERVER_RESPONSE,
                    "Server response is not in the correct format");
        }

        this.code = new Code(Integer.parseInt(response.substring(0, CODE_LENGTH)));
        this.response = response;
    }

    /**
     * Gets the message portion of the response if it exists. The message portion is content after the reply code.
     *
     * @return the message following the response code, empty string if none
     */
    @Nonnull
    public String getMessage() {
        return response.length() > (CODE_LENGTH + 1) ? response.substring(CODE_LENGTH + 1) : "";
    }

    /**
     * @return the reply code of the response
     */
    public Code getCode() {
        return code;
    }

    /**
     * Gets the type of reply associated with the response.
     *
     * @return {@link ReplyType} enum indicating the reply type of the response
     */
    public ReplyType getReplyType() {
        final char firstDigit = response.charAt(0);
        if (firstDigit == '2') {
            return ReplyType.POSITIVE_COMPLETION;
        }
        if (firstDigit == '3') {
            return ReplyType.POSITIVE_INTERMEDIATE;
        }
        if (firstDigit == '4') {
            return ReplyType.NEGATIVE_TRANSIENT;
        }
        return ReplyType.NEGATIVE_PERMANENT;
    }

    /**
     * Indicates whether if the command requires additional input. The DATA command is an example that has this.
     *
     * @return true if and only if the command sent requires additional input to complete
     */
    public boolean isContinuation() {
        return code.value() == Code.START_MSG_INPUT || code.value() == Code.CHALLENGE_RESPONSE;
    }

    /**
     * Indicates whether if the response is the last line of a response. multiline responses are indicated by a hyphen
     * immediately after the response code. See RFC 5321 section 4.2.1 for details.
     *
     * @return true if and only if the response is a terminating response (no more expected responses after)
     */
    public boolean isLastLineResponse() {
        return response.length() == CODE_LENGTH || response.charAt(CODE_LENGTH) != HYPHEN;
    }

    /**
     * @return the raw response as a string
     */
    @Override
    public String toString() {
        return response;
    }
}
