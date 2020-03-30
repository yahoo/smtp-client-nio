/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/***
 * Enum used to identify the type of SMTP command that is sent.
 */
public enum SmtpCommandType {

    /** The Extended Hello command (EHLO). */
    EHLO,

    /** The Hello command. */
    HELO,

    /** The Mail command. */
    MAIL,

    /** The Recipient command. */
    RCPT,

    /** The Data command. */
    DATA,

    /** The Reset command. */
    RSET,

    /** The Noop command. */
    NOOP,

    /** The Quit command (QUIT). */
    QUIT,

    /** The Verify command. */
    VRFY,

    /** The Authentication command. */
    AUTH,

    /** The Help command. */
    HELP,

    /** The Expand command. */
    EXPN
}
