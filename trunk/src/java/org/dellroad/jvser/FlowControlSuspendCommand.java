
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import static org.dellroad.jvser.RFC2217.*;

/**
 * RFC 2217 {@code FLOWCONTROL-SUSPEND} command.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class FlowControlSuspendCommand extends ComPortCommand {

    /**
     * Decoding constructor.
     *
     * @param bytes encoded option starting with the {@code COM-PORT-OPTION} byte
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} has length != 3
     * @throws IllegalArgumentException if {@code bytes[0]} is not {@link RFC2217#COM_PORT_OPTION}
     * @throws IllegalArgumentException if {@code bytes[1]} is not {@link RFC2217#FLOWCONTROL_SUSPEND} (client or server)
     */
    public FlowControlSuspendCommand(int[] bytes) {
        super("FLOWCONTROL-SUSPEND", FLOWCONTROL_SUSPEND, bytes);
    }

    /**
     * Encoding constructor.
     *
     * @param client true for the client command, false for the server command
     */
    public FlowControlSuspendCommand(boolean client) {
        this(new int[] {
            COM_PORT_OPTION,
            client ? FLOWCONTROL_SUSPEND : FLOWCONTROL_SUSPEND + SERVER_OFFSET,
        });
    }

    @Override
    public String toString() {
        return this.getName();
    }

    @Override
    public void visit(ComPortCommandSwitch sw) {
        sw.caseFlowControlSuspend(this);
    }

    @Override
    int getMinPayloadLength() {
        return 0;
    }

    @Override
    int getMaxPayloadLength() {
        return 0;
    }
}

