
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * Support superclass for command line classes.
 */
public abstract class MainClass {

    protected final Logger log = Logger.getLogger(getClass());

    protected MainClass() {
    }

    /**
     * Subclass main implementation. This method is free to throw exceptions; these will
     * be displayed on standard error and converted into non-zero exit values.
     *
     * @return exit value
     */
    protected abstract int run(String[] args) throws Exception;

    /**
     * Display the usage message to standard error.
     */
    protected abstract void usageMessage();

    /**
     * Print the usage message and exit with exit value 1.
     */
    protected void usageError() {
        usageMessage();
        System.exit(1);
    }

    /**
     * Setup logging.
     */
    protected void setupLogging(Level logLevel) {
        if (logLevel == null)
            logLevel = Level.INFO;
        ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("%p: %m%n"), ConsoleAppender.SYSTEM_ERR);
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(consoleAppender);
        Logger.getRootLogger().setLevel(logLevel);
    }

    /**
     * Emit an error message an exit with exit value 1.
     */
    protected final void errout(String message) {
        System.err.println(getClass().getSimpleName() + ": " + message);
        System.exit(1);
    }

    /**
     * Invokes {@link #run}, catching any exceptions thrown and exiting with a non-zero
     * value if and only if an exception was caught.
     * <p/>
     * <p>
     * The concrete class' {@code main()} method should invoke this method.
     * </p>
     */
    protected void doMain(String[] args) {
        int exitValue = 1;
        try {
            exitValue = run(args);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        } finally {
            System.exit(exitValue);
        }
    }
}

