
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import java.net.InetAddress;

import javax.comm.SerialPort;
import javax.comm.SerialPortEvent;
import javax.comm.SerialPortEventListener;

import org.dellroad.jvser.TelnetSerialPort;

/**
 * Command line client.
 */
public final class Main extends MainClass implements SerialPortEventListener {

    private InetAddress host;
    private int port;
    private boolean verbose;

    private int baudRate = TelnetSerialPort.DEFAULT_BAUD_RATE;
    private int dataBits = SerialPort.DATABITS_8;
    private int stopBits = SerialPort.STOPBITS_1;
    private int parity = SerialPort.PARITY_NONE;
    private int flowControl = SerialPort.FLOWCONTROW_NONE_IN | SerialPort.FLOWCONTROW_NONE_OUT;

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
        port.setSerialPortParams(this.baudRate, this.dataBits, this.stopBits, this.parity);
        port.setFlowControlMode(this.flowControl);
        port.setDTR(true);
        //...

        // Done
        return 0;
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage: java " + Main.class.getName() + " [-b baudRate] host port");
    }
}

