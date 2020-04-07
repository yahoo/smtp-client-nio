/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

/**
 * This interface identifies the type of SMTP command that is sent.
 */
public interface SmtpCommandType {

    /**
     * @return the type of the command
     */
    String getType();
}

