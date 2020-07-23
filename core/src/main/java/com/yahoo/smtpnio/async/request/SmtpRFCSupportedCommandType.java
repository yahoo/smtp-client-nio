/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;


/***
 * Enum used to identify RFC SMTP command.
 */
enum SmtpRFCSupportedCommandType implements SmtpCommandType {
    /** The Extended Hello command (EHLO). */
    EHLO,

    /** The Hello command (HELO). */
    HELO,

    /** The Quit command (QUIT). */
    QUIT,

    /** The Authentication command (AUTH). */
    AUTH,

    /** The Starttls command (Starttls). */
    STARTTLS;

    @Override
    public String getType() {
        return this.name();
    }
}

