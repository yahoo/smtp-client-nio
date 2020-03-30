/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a Reset (RSET) command.
 */
public class ResetCommand extends AbstractSmtpCommand {

    /** String literal for "RSET". */
    private static final String RSET = "RSET";

    /**
     * Initializes a RSET command object.
     */
    public ResetCommand() {
        super(RSET);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.RSET;
    }
}
