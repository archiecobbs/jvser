
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;
import org.dellroad.jvser.telnet.TelnetOptionHandler;
import static org.dellroad.jvser.RFC2217.*;

/**
 * RFC 2217 telnet COM-PORT-OPTION.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class ComPortOptionHandler extends TelnetOptionHandler {

    private final Logger log = Logger.getLogger(getClass());
    private final TelnetSerialPort port;

    protected ComPortOptionHandler(TelnetSerialPort telnetSerialPort) {
        super(COM_PORT_OPTION, true, true, true, true);
        if (telnetSerialPort == null)
            throw new IllegalArgumentException("null telnetSerialPort");
        this.port = telnetSerialPort;
    }

    @Override
    public int[] answerSubnegotiation(int[] data, int length) {
        if (length < 2) {
            log.warn(this.port.getName() + ": rec'd COM-PORT-OPTION subnegotiation with bogus length " + length);
            return null;
        }
        log.debug(this.port.getName() + ": rec'd COM-PORT-OPTION " + RFC2217.decodeSubnegotiation(data, 1, length - 1));
        switch (data[1]) {
        case SERVER_OFFSET + SIGNATURE:
            byte[] buf = new byte[length - 2];
            for (int i = 2; i < length; i++)
                buf[i - 2] = (byte)data[i];
            String signature;
            try {
                signature = new String(buf, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                signature = "" + e;
            }
            log.info(this.port.getName() + ": rec'd remote signature: " + signature);
            break;
        case SERVER_OFFSET + NOTIFY_LINESTATE:
            if (length != 3) {
                log.warn(this.port.getName() + ": rec'd COM-PORT-OPTION NOTIFY-LINESTATE with bogus length " + length);
                break;
            }
            this.port.notifyLineState(data[2]);
            break;
        case SERVER_OFFSET + NOTIFY_MODEMSTATE:
            if (length != 3) {
                log.warn(this.port.getName() + ": rec'd COM-PORT-OPTION NOTIFY-MODEMSTATE with bogus length " + length);
                break;
            }
            this.port.notifyModemState(data[2]);
            break;
        default:
            log.warn(this.port.getName() + ": rec'd unrecognized COM-PORT-OPTION subnegotiation code " + data[1]);
            break;
        }
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

