
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import static org.dellroad.jvser.RFC2217.*;

/**
 * RFC 2217 {@code NOTIFY-LINESTATE} command.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class NotifyLineStateCommand extends ComPortCommand {

    private int lineState;

    /**
     * Decoding constructor.
     *
     * @param bytes encoded option starting with the {@code COM-PORT-OPTION} byte
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} has length != 3
     * @throws IllegalArgumentException if {@code bytes[0]} is not {@link RFC2217#COM_PORT_OPTION}
     * @throws IllegalArgumentException if {@code bytes[1]} is not {@link RFC2217#NOTIFY_LINESTATE} (client or server)
     */
    public NotifyLineStateCommand(int[] bytes) {
        super(bytes, NOTIFY_LINESTATE);
        this.lineState = bytes[2];
    }

    /**
     * Encoding constructor.
     *
     * @param lineState line state value
     * @param client true for the client command, false for the server command
     */
    public NotifyLineStateCommand(boolean client, int lineState) {
        this(new int[] {
            COM_PORT_OPTION,
            client ? NOTIFY_LINESTATE : NOTIFY_LINESTATE + SERVER_OFFSET,
            lineState
        });
    }

    @Override
    public String getName() {
        return "NOTIFY-LINESTATE";
    }

    @Override
    public String toString() {
        return this.getName() + " " + Util.decodeBits(this.lineState, Util.LINE_STATE_BITS);
    }

    @Override
    public void visit(ComPortCommandSwitch sw) {
        sw.caseNotifyLineState(this);
    }

    public int getLineState() {
        return this.lineState;
    }

    @Override
    int getMinPayloadLength() {
        return 1;
    }

    @Override
    int getMaxPayloadLength() {
        return 1;
    }
}

