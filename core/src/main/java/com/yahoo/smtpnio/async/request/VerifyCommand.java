/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a Verify (VRFY) command.
 */
public class VerifyCommand extends AbstractSmtpCommand {

    /** String literal for "VRFY". */
    private static final String VRFY = "VRFY";

    /**
     * Initializes a VRFY command object.
     */
    public VerifyCommand() {
        super(VRFY);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.VRFY;
    }
}
