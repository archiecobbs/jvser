
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import java.net.InetAddress;

import org.dellroad.jvser.TelnetSerialPort;

/**
 * Command line client.
 */
public final class Main extends MainClass {

    private InetAddress host;
    private int port;
    private int baudRate = TelnetSerialPort.DEFAULT_BAUD_RATE;
    private int flowControl = SerialPort.FLOWCONTROW_RTSCTS_IN | SerialPort.FLOWCONTROW_RTSCTS_OUT;
    %%
    private boolean verbose;

    private Main() {
    }

    /**
     * Command line entry point.
     */
    public static void main(String[] args) {
        new Main().doMain(args);
    }

    @Override
    protected int run(String[] args) throws Exception {

        // Parse initial command line flags
        String resource = null;
        String expr = null;
        int i;
        for (i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-"))
                break;
            if (args[i].equals("--")) {
                i++;
                break;
            }
            if (args[i].equals("-b") && ++i < args.length) {
                this.baudRate = Integer.parseInt(args[i]);
                continue;
            }
            if (args[i].equals("-v")) {
                verbose = true;
                continue;
            }
            usageError();
        }
        switch (args.length - i) {
        case 2:
            this.host = InetAddress.getByName(args[i]);
            this.port = Integer.parseInt(args[i + 1]);
            break;
        default:
            usageError();
            break;
        }

        // Load config
        if (verbose)
            log.info("connecting to " + this.host + ":" + this.port);
        TelnetSerialPort port = new TelnetSerialPort(this.host, this.port);
        port.setSignature("jvser client");
        port.setSerialPortParams(this.baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        port.setDTR(true);
        port.setFlowControlMode(SerialPort.FLOWCONTROW_RTSCTS_IN | );
        //...

        // Done
        return 0;
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage: java " + Main.class.getName() + " [-b baudRate] host port");
    }
}

