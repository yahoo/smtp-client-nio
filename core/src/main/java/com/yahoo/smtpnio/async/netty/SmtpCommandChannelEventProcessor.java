/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.handler.timeout.IdleStateEvent;

/**
 * This interface defines events coming from {@link SmtpClientCommandRespHandler}.
 */
public interface SmtpCommandChannelEventProcessor {

    /**
     * Handles when a channel response arrives.
     *
     * @param msg the response
     */
    void handleChannelResponse(SmtpResponse msg);

    /**
     * Handles when a channel has an exception.
     *
     * @param cause the exception
     */
    void handleChannelException(Throwable cause);

    /**
     * Handles when a channel receives an idle-too-long event.
     *
     * @param timeOutEvent the timeout event
     */
    void handleIdleEvent(IdleStateEvent timeOutEvent);

    /**
     * Handles the event when a channel is closed/disconnected either by the server or client.
     */
    void handleChannelClosed();
}
