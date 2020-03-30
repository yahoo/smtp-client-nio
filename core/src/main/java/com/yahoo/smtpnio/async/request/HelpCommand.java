/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a Help (HELP) command.
 */
public class HelpCommand extends AbstractSmtpCommand {

    /** String literal for "HELP". */
    private static final String HELP = "HELP";

    /**
     * Initializes a HELP command object.
     */
    public HelpCommand() {
        super(HELP);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.HELP;
    }
}
