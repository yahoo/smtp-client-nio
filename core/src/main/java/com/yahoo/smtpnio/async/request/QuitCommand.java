/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This class defines a Quit (QUIT) command.
 */
public class QuitCommand extends AbstractSmtpCommand {

    /** String literal for "QUIT". */
    private static final String QUIT = "QUIT";

    /**
     * Initializes a QUIT command object used to log out of the server.
     */
    public QuitCommand() {
        super(QUIT);
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.QUIT;
    }
}
