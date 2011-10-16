
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import org.apache.commons.net.telnet.TelnetOptionHandler;
import org.apache.log4j.Logger;

/**
 * RFC 2217 telnet COM-PORT-OPTION.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class ComPortOptionHandler extends TelnetOptionHandler {

    private final Logger log = Logger.getLogger(getClass());
    private final TelnetSerialPort port;

    protected ComPortOptionHandler(TelnetSerialPort telnetSerialPort) {
        super(RFC2217.COM_PORT_OPTION, true, false, true, false);
        if (telnetSerialPort == null)
            throw new IllegalArgumentException("null telnetSerialPort");
        this.port = telnetSerialPort;
    }

    @Override
    public int[] answerSubnegotiation(int[] data, int length) {

        // Copy data into buffer of the correct size
        if (data.length != length) {
            int[] data2 = new int[length];
            System.arraycopy(data, 0, data2, 0, length);
            data = data2;
        }

        // Decode option
        ComPortCommand command;
        try {
            command = RFC2217.decodeComPortCommand(data);
        } catch (IllegalArgumentException e) {
            log.error(this.port.getName() + ": rec'd invalid COM-PORT-OPTION command: " + e.getMessage());
            return null;
        }

        // Notify port
        this.port.handleCommand(command);
        return null;
    }

    @Override
    public int[] startSubnegotiationLocal() {
        this.port.startSubnegotiation();
        return null;
    }

    @Override
    public int[] startSubnegotiationRemote() {
        return null;
    }
}

