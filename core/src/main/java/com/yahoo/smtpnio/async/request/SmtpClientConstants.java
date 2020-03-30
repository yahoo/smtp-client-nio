/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/** Class for commonly used constants in the SMTP asynchronous library. */
final class SmtpClientConstants {

    /** Space character. */
    static final char SPACE = ' ';

    /** Length of a char. */
    static final int CHAR_LEN = "a".length();

    /** A set padding length. */
    static final int PADDING_LEN = 40;

    /** String for CR and LF. */
    static final String CRLF = "\r\n";

    /** NULL character. */
    static final char NULL = '\0';

    /** Period character. */
    static final char PERIOD = '.';

    /** Equal character. */
    static final char EQUAL = '=';

    /** Left angle bracket. */
    static final char L_ANGLE_BRACKET = '<';

    /** Right angle bracket. */
    static final char R_ANGLE_BRACKET = '>';

    /** Literal for colon. */
    static final char COLON = ':';

    /** Private constructor to avoid constructing instance of this class. */
    private SmtpClientConstants() {
    }
}
