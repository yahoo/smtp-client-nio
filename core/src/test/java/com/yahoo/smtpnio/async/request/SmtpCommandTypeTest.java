/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for {@link SmtpCommandType}.
 *
 * @author czhang03
 */
public class SmtpCommandTypeTest {

    /**
     * Test type.
     */
    @Test
    public void test() {
        final SmtpCommandType type = SmtpRFCSupportedCommandType.EHLO;
        Assert.assertEquals(type.getType(), "EHLO", "type should match");
    }
}
