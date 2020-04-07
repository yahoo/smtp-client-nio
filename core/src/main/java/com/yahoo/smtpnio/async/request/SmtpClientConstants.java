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

    /** String for CRLF. */
    static final String CRLF = "\r\n";

    /** NULL character. */
    static final char NULL = '\0';

    /** Start of Header char (aka. CTRL-A or \001). **/
    static final char SOH = 0x1;

    /** Private constructor to avoid constructing instance of this class. */
    private SmtpClientConstants() {
    }
}
