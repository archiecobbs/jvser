
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import org.dellroad.jvser.telnet.TelnetOptionHandler;

/**
 * RFC 856 telnet TRANSMIT-BINARY option.
 *
 * @see <a href="http://tools.ietf.org/html/rfc856">RFC 856</a>
 */
public class TransmitBinaryOptionHandler extends TelnetOptionHandler {

    public static final int TRANSMIT_BINARY_OPTION = 0;

    public TransmitBinaryOptionHandler(boolean initlocal, boolean initremote, boolean acceptlocal, boolean acceptremote) {
        super(TRANSMIT_BINARY_OPTION, initlocal, initremote, acceptlocal, acceptremote);
    }

    @Override
    public int[] answerSubnegotiation(int[] data, int length) {
        return null;
    }

    @Override
    public int[] startSubnegotiationLocal() {
        return null;
    }

    @Override
    public int[] startSubnegotiationRemote() {
        return null;
    }
}

