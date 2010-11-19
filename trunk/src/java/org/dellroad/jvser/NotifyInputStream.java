
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper around an {@link InputStream} that notifies a listener when there is data available.
 * Instances do not support mark/reset.
 */
public class NotifyInputStream extends FilterInputStream {

    public interface Listener {

        /**
         * Invoked when data is available on a {@link NotifyInputStream}.
         * Any {@link Exception} thrown by this method will be ignored.
         */
        void notifyDataAvailable(NotifyInputStream in);
    }

    private final Object lock = new Object();
    private final Listener listener;

    private IOException exception;                  // exception caught by reader thread, if any
    private int readerCount;                        // prevents us and them reading at the same time
    private boolean closed;                         // indicates close() was called and reader thread should exit
    private int nextByte = -1;                      // the byte that the reader thread read, if any, else -1

    /**
     * Constructor.
     *
     * @param inner the {@link InputStream} to monitor
     * @param listener the listener who receives data available notifications
     * @throws IllegalArgumentException if {@code listener} is null
     */
    public NotifyInputStream(InputStream inner, Listener listener) {
        super(inner);
        if (this.listener == null)
            throw new IllegalArgumentException("null listener");
        this.listener = listener;
        Thread thread = new Thread(getClass().getSimpleName() + " for " + inner) {

            @Override
            public void run() {
                NotifyInputStream.this.monitor();
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void monitor() {
        for (boolean done = false; !done; ) {
            synchronized (this.lock) {

                // Wait until we have the permission and the need to read
                while (!this.closed && !(this.readerCount == 0 && this.nextByte == -1)) {
                    try {
                        this.lock.wait();
                    } catch (InterruptedException e) {
                        continue;
                    }
                }

                // Are we done?
                if (this.closed)
                    break;

                // Try to read a byte (note: we are holding the lock and this may block indefinitely)
                try {
                    this.nextByte = super.read();
                    if (this.nextByte == -1)
                        done = true;
                } catch (IOException e) {
                    if (!this.closed)
                        this.exception = e;
                    done = true;
                }
            }

            // Notify listener (note: not while holding lock)
            try {
                this.listener.notifyDataAvailable(this);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public synchronized int available() throws IOException {
        return (this.nextByte != -1 ? 1 : 0) + super.available();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public int read() throws IOException {
        synchronized (this.lock) {
            if (this.exception != null)
                throw this.exception;
            if (this.nextByte != -1) {
                int r = this.nextByte;
                this.nextByte = -1;
                this.lock.notify();
                return r;
            }
            this.readerCount++;
        }
        try {
            return super.read();
        } finally {
            synchronized (this.lock) {
                this.readerCount--;
                this.lock.notify();
            }
        }
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (len == 0)
            return 0;
        if (len < 0)
            throw new IndexOutOfBoundsException("len = " + len);
        synchronized (this.lock) {
            if (this.exception != null)
                throw this.exception;
            if (this.nextByte != -1) {
                buf[off] = (byte)this.nextByte;
                this.nextByte = -1;
                this.lock.notify();
                return 1;
            }
            this.readerCount++;
        }
        try {
            return super.read(buf, off, len);
        } finally {
            synchronized (this.lock) {
                this.readerCount--;
                this.lock.notify();
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this.lock) {
            this.closed = true;
            this.lock.notify();
        }
        super.close();
    }
}

