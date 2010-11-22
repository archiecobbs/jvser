
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import java.net.InetAddress;

import org.dellroad.jvser.TelnetSerialPort;

/**
 * Launcher for the command line client.
 *
 * @see Client
 */
public final class Main extends MainClass {

    private InetAddress host;
    private int port;
    private boolean useThread;

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
            if (args[i].equals("-t")) {
                this.useThread = true;
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

        // Start client
        new Client(this.host, this.port, this.useThread).run();

        // Done
        return 0;
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage: java " + Main.class.getName() + " host port");
    }
}

