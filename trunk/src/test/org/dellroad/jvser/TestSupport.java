
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.util.Random;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;

/**
 * Base class for unit tests providing initialized logging and source of randomness.
 */
public abstract class TestSupport {

    private static boolean reportedSeed;

    protected final Logger log = Logger.getLogger(getClass());

    protected Random random;

    @BeforeClass
    @Parameters({ "randomSeed" })
    public void seedRandom(String randomSeed) {
        this.random = getRandom(randomSeed);
    }

    @BeforeClass
    @Parameters({ "logLevel", "logPattern" })
    public void setupLogging(String logLevel, String logPattern) {
        ConsoleAppender consoleAppender = new ConsoleAppender(
          new PatternLayout(logPattern), ConsoleAppender.SYSTEM_ERR);
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(consoleAppender);
        Level level = Level.toLevel(logLevel, Level.DEBUG);
        Logger.getRootLogger().setLevel(level);
        this.log.debug("logging configured for level " + level);
    }

    public static Random getRandom(String randomSeed) {
        long seed;
        try {
            seed = Long.parseLong(randomSeed);
        } catch (NumberFormatException e) {
            seed = System.currentTimeMillis();
        }
        if (!reportedSeed) {
            reportedSeed = true;
            Logger.getLogger(TestSupport.class).info("test seed = " + seed);
        }
        return new Random(seed);
    }
}

