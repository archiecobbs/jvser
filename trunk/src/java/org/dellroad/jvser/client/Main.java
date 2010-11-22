
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import java.net.InetAddress;

import org.apache.log4j.Level;

/**
 * Launcher for the command line client.
 *
 * @see Client
 */
public final class Main extends MainClass {

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
        InetAddress host = null;
        int port = -1;
        boolean useThread = true;
        boolean debug = false;
        boolean logInput = false;
        int i;
        for (i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-"))
                break;
            if (args[i].equals("--")) {
                i++;
                break;
            }
            if (args[i].equals("-d")) {
                if (debug)
                    logInput = true;
                debug = true;
                continue;
            }
            if (args[i].equals("-n")) {
                useThread = false;
                continue;
            }
            usageError();
        }
        switch (args.length - i) {
        case 2:
            host = InetAddress.getByName(args[i]);
            port = Integer.parseInt(args[i + 1]);
            break;
        default:
            usageError();
            break;
        }

        // Configure logging
        setLogLevel(debug ? Level.DEBUG : Level.INFO);

        // Start client
        new Client(host, port, useThread, logInput).run();

        // Done
        return 0;
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage: java " + Main.class.getName() + " [-n] [-d] host port");
    }
}

