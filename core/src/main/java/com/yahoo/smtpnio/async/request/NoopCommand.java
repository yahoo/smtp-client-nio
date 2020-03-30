/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a Noop (NOOP) command.
 */
public class NoopCommand extends AbstractSmtpCommand {

    /** String literal for "NOOP". */
    private static final String NOOP = "NOOP";

    /**
     * Initializes a NOOP command object.
     */
    public NoopCommand() {
        super(NOOP);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.NOOP;
    }
}
