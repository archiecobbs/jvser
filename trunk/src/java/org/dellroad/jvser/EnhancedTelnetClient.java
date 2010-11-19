
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.net.telnet.TelnetClient;

/**
 * Subclass of Apache commons-net's {@link org.apache.commons.net.telnet.TelnetClient TelnetClient} that
 * provides some missing functionality.
 */
public class EnhancedTelnetClient extends TelnetClient {

    private Method sendSubMethod;

    public EnhancedTelnetClient() {
    }

    public EnhancedTelnetClient(String termtype) {
        super(termtype);
    }

    /**
     * Send spurious sub-negotiation data to the peer. This functionality was seemingly left out of {@link TelnetClient},
     * and we have to perform an introspection hack to provide it here.
     *
     * @param data
     */
    public void sendSubnegotiation(int[] data) throws IOException {

        // Find method
        if (this.sendSubMethod == null) {
            for (Class<?> cl = this.getClass().getSuperclass(); cl != null; cl = cl.getSuperclass()) {
                try {
                    this.sendSubMethod = cl.getDeclaredMethod("_sendSubnegotiation", int[].class);
                } catch (NoSuchMethodException e) {
                    continue;
                }
                break;
            }
            if (this.sendSubMethod == null)
                throw new RuntimeException("can't find Telnet._sendSubnegotiation(int[])... incompatible commons-net?");
        }

        // Invoke method introspectively
        try {
            try {
                this.sendSubMethod.invoke(this, data);
            } catch (InvocationTargetException e) {
                if (e.getTargetException() instanceof Exception)
                    throw (Exception)e.getTargetException();
                if (e.getTargetException() instanceof Error)
                    throw (Error)e.getTargetException();
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException)e;
            throw new RuntimeException("unable to use introspection hack to implement sendSubnegotiation()", e);
        }
    }
}

