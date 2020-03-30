/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** This class defines a Mail (MAIL) command. */
public class MailCommand extends AbstractSmtpCommand {

    /** String literal for "MAIL". */
    private static final String MAIL = "MAIL";

    /** String literal for "FROM". */
    private static final String FROM = "FROM";

    /** Sender of the message. */
    private String sender;

    /** Collection of additional mail parameters that can be sent. */
    @Nullable
    private Collection<MailParameter> mailParameters;

    /**
     * A container class that holds values for additional mail parameters. Parameters are keywords associated with
     * optional values. Refer to RFC 5321 for details.
     */
    public static class MailParameter {

        /** Keyword of the parameter. */
        @Nonnull
        private final String keyword;

        /** Value of the parameter (optional). */
        @Nullable
        private final String value;

        /**
         * Constructor for a mail parameter with only the keyword.
         *
         * @param keyword the keyword
         */
        public MailParameter(@Nonnull final String keyword) {
            this.keyword = keyword;
            this.value = null;
        }

        /**
         * Constructor for a mail parameter with a keyword/value pair.
         *
         * @param keyword the keyword
         * @param value the argument associated with the keyword
         */
        public MailParameter(@Nonnull final String keyword, @Nonnull final String value) {
            this.keyword = keyword;
            this.value = value;
        }
    }

    /**
     * Initializes a MAIL command without the sender.
     */
    public MailCommand() {
        this("");
    }


    /**
     * Initializes a MAIL command with a specified sender, but no additional mail parameters.
     *
     * @param sender the sender
     */
    public MailCommand(@Nonnull final String sender) {
        this(sender, null);
    }


    /**
     * Initializes a MAIL command with a specified collection of additional mail parameters, but no sender.
     *
     * @param mailParameters collection of mail parameters to be sent
     */
    public MailCommand(@Nonnull final Collection<MailParameter> mailParameters) {
        this("", mailParameters);
    }


    /**
     * Initializes a MAIL command with a specified sender and a collection additional mail parameters.
     *
     * @param sender the sender
     * @param mailParameters collection of mail parameters to be sent
     */
    public MailCommand(@Nonnull final String sender, @Nullable final Collection<MailParameter> mailParameters) {
        super(MAIL);
        this.sender = sender;
        this.mailParameters = mailParameters;
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpCommandType.MAIL;
    }

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        // space, colon, left and right angle brackets make up 4 of extra chars; padding is added to account for possible additional arguments
        final int len = command.length() + FROM.length() + sender.length() + CRLF_B.length + SmtpClientConstants.PADDING_LEN;
        final ByteBuf res = Unpooled.buffer(len)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.SPACE)
                .writeBytes(FROM.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.COLON)
                .writeByte(SmtpClientConstants.L_ANGLE_BRACKET)
                .writeBytes(sender.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.R_ANGLE_BRACKET);

        if (mailParameters != null) { // adds the additional mail parameters if available
            for (final MailParameter mailParameter : mailParameters) {
                res.writeByte(SmtpClientConstants.SPACE)
                        .writeBytes(mailParameter.keyword.getBytes(StandardCharsets.US_ASCII));
                if (mailParameter.value != null) {
                    res.writeByte(SmtpClientConstants.EQUAL)
                            .writeBytes(mailParameter.value.getBytes(StandardCharsets.US_ASCII));
                }
            }
        }
        return res.writeBytes(CRLF_B);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        sender = null;
        mailParameters = null;
    }
}
