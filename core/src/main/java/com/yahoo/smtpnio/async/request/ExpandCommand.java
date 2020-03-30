/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines an Expand (EXPN) command.
 */
public class ExpandCommand extends AbstractSmtpCommand {

    /** String literal for "EXPN". */
    private static final String EXPN = "EXPN";

    /**
     * Initializes a EXPN command object used to log out of the server. Note that not all SMTP servers have support for this command.
     */
    public ExpandCommand() {
        super(EXPN);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.EXPN;
    }
}
