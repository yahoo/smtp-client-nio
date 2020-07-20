/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a STARTTLS (STARTTLS) command.
 */
public class StarttlsCommand extends AbstractSmtpCommand {

    /** String literal for "STARTTLS". */
    private static final String STARTTLS = "STARTTLS";

    /**
     * Initializes a STARTTLS command object used to upgrade plain connection to tls connection.
     */
    public StarttlsCommand() {
        super(STARTTLS);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.STARTTLS;
    }
}