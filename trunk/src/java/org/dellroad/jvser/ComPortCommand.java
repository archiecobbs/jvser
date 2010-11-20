
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import static org.dellroad.jvser.RFC2217.*;

/**
 * Superclass for RFC 2217 command classes.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public abstract class ComPortCommand {

    final int[] bytes;
    final int command;

    /**
     * Constructor.
     *
     * @param bytes encoded option starting with the {@code COM-PORT-OPTION} byte
     * @param command required COM-PORT-OPTION option command (client version)
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} has length that is too short or too long
     * @throws IllegalArgumentException if {@code bytes[0]} is not {@link RFC2217#COM_PORT_OPTION}
     * @throws IllegalArgumentException if {@code bytes[1]} is not {@code command} (either client or server version)
     */
    protected ComPortCommand(int[] bytes, int command) {
        if (bytes.length < this.getMinLength() || bytes.length > this.getMaxLength()) {
            throw new IllegalArgumentException("length = " + bytes.length + " is not in the range "
              + this.getMinLength() + ".." + this.getMaxLength());
        }
        this.bytes = bytes;
        if (this.bytes[0] != COM_PORT_OPTION)
            throw new IllegalArgumentException("not a COM-PORT-OPTION");
        this.command = bytes[1];
        if (this.command != command && this.command != command + SERVER_OFFSET)
            throw new IllegalArgumentException("not a " + getName() + " option");
    }

    /**
     * Determine if this option is client-to-server or server-to-client.
     */
    public final boolean isServerCommand() {
        return this.command >= SERVER_OFFSET;
    }

    /**
     * Get the encoding of this instance.
     *
     * @return encoding starting with {@code COM-PORT-OPTION}
     */
    public final int[] getBytes() {
        return this.bytes.clone();
    }

    /**
     * Get the option command byte.
     *
     * @return option command byte for this option type
     */
    public final int getCommand() {
        return this.command;
    }

    /**
     * Get the human-readable name of this option.
     */
    public abstract String getName();

    /**
     * Get the human-readable description of this option.
     */
    @Override
    public abstract String toString();

    /**
     * Apply visitor pattern.
     *
     * @param sw visitor switch handler
     */
    public abstract void visit(ComPortCommandSwitch sw);

    /**
     * Get the option payload as bytes.
     */
    byte[] getPayload() {
        byte[] buf = new byte[this.bytes.length - 2];
        for (int i = 2; i < bytes.length; i++)
            buf[i - 2] = (byte)this.bytes[i];
        return buf;
    }

    /**
     * Get minimum required length of the payload of this option.
     */
    abstract int getMinLength();

    /**
     * Get maximum required length of the payload of this option.
     */
    abstract int getMaxLength();
}

