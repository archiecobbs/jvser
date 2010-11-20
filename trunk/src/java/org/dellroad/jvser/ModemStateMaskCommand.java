
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import static org.dellroad.jvser.RFC2217.*;

/**
 * RFC 2217 {@code SET-MODEMSTATE-MASK} command.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class ModemStateMaskCommand extends ComPortCommand {

    private int modemStateMask;

    /**
     * Decoding constructor.
     *
     * @param bytes encoded option starting with the {@code COM-PORT-OPTION} byte
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} has length != 3
     * @throws IllegalArgumentException if {@code bytes[0]} is not {@link RFC2217#COM_PORT_OPTION}
     * @throws IllegalArgumentException if {@code bytes[1]} is not {@link RFC2217#SET_MODEMSTATE_MASK} (client or server)
     */
    public ModemStateMaskCommand(int[] bytes) {
        super(bytes, SET_MODEMSTATE_MASK);
        this.modemStateMask = bytes[2];
    }

    /**
     * Encoding constructor.
     *
     * @param modemStateMask modem state mask value
     * @param client true for the client command, false for the server command
     */
    public ModemStateMaskCommand(boolean client, int modemStateMask) {
        this(new int[] {
            COM_PORT_OPTION,
            client ? SET_MODEMSTATE_MASK : SET_MODEMSTATE_MASK + SERVER_OFFSET,
            modemStateMask
        });
    }

    @Override
    public String getName() {
        return "SET-MODEMSTATE-MASK";
    }

    @Override
    public String toString() {
        return this.getName() + " " + Util.decodeBits(this.modemStateMask, Util.MODEM_STATE_BITS);
    }

    @Override
    public void visit(ComPortCommandSwitch sw) {
        sw.caseModemStateMask(this);
    }

    public int getModemStateMask() {
        return this.modemStateMask;
    }

    @Override
    int getMinLength() {
        return 1;
    }

    @Override
    int getMaxLength() {
        return 1;
    }
}

