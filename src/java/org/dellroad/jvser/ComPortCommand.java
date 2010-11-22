
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

    final String name;
    final int command;
    final int[] bytes;

    /**
     * Constructor.
     *
     * @param name human readable name of this command
     * @param command required COM-PORT-OPTION option command (client version)
     * @param bytes encoded option starting with the {@code COM-PORT-OPTION} byte
     * @throws NullPointerException if {@code bytes} is null
     * @throws IllegalArgumentException if {@code bytes} has length that is too short or too long
     * @throws IllegalArgumentException if {@code bytes[0]} is not {@link RFC2217#COM_PORT_OPTION}
     * @throws IllegalArgumentException if {@code bytes[1]} is not {@code command} (either client or server version)
     */
    protected ComPortCommand(String name, int command, int[] bytes) {
        this.name = name;
        int minLength = 2 + this.getMinPayloadLength();
        int maxLength = 2 + this.getMaxPayloadLength();
        if (bytes.length < minLength || bytes.length > maxLength)
            throw new IllegalArgumentException("length = " + bytes.length + " is not in the range " + minLength + ".." + maxLength);
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
    public String getName() {
        return this.name + (this.isServerCommand() ? "[S]" : "[C]");
    }

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
    abstract int getMinPayloadLength();

    /**
     * Get maximum required length of the payload of this option.
     */
    abstract int getMaxPayloadLength();
}

