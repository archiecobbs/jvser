
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

/**
 * Throw by {@link TelnetSerialPort} methods if the telnet connection has dropped.
 */
@SuppressWarnings("serial")
public class DisconnectedException extends RuntimeException {

    public DisconnectedException() {
    }

    public DisconnectedException(String message) {
        super(message);
    }

    public DisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public DisconnectedException(Throwable cause) {
        super(cause);
    }
}

