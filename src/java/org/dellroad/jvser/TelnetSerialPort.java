
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.TooManyListenersException;

import javax.comm.SerialPort;
import javax.comm.SerialPortEvent;
import javax.comm.SerialPortEventListener;
import javax.comm.UnsupportedCommOperationException;

import org.apache.log4j.Logger;
import org.dellroad.jvser.telnet.EchoOptionHandler;
import org.dellroad.jvser.telnet.InvalidTelnetOptionException;
import org.dellroad.jvser.telnet.SuppressGAOptionHandler;
import org.dellroad.jvser.telnet.TelnetClient;
import org.dellroad.jvser.telnet.TelnetInputListener;
import org.dellroad.jvser.telnet.TerminalTypeOptionHandler;
import static org.dellroad.jvser.RFC2217.*;

/**
 * Implementation of the client side of the <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 * serial-over-Telnet protocol.
 *
 * <p>
 * This class extends the {@link SerialPort} class and functions in the same way, however, setup is
 * slightly different. First, instantiate an instance of this class directly, and then "open" it
 * using some variant of {@link TelnetClient#connect(java.net.InetAddress, int) connect()}.
 * After this point, it should act just like a "real" serial port.
 * </p>
 *
 * <p>
 * If the connection get disconnected during normal operation, a {@link DisconnectedException} is thrown
 * when any method is invoked. This is a {@link RuntimeException} so it will have to be deliberately caught.
 * </p>
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class TelnetSerialPort extends SerialPort {

    private static final String DEFAULT_TERMINAL_TYPE = "VT100";

    // Modem state bits we always want the server to report to us regardless of what listener wants
    private static final int MODEMSTATE_ALWAYS_MONITOR
      = MODEMSTATE_CARRIER_DETECT | MODEMSTATE_RING_INDICATOR | MODEMSTATE_DSR | MODEMSTATE_CTS;

    // Line state bits we never want the server to report to us regardless of what listener wants
    private static final int LINESTATE_NEVER_MONITOR = LINESTATE_DATA_READY;

    // States
    private enum State {
        INITIAL     (false, false, false, "not yet opened"),
        CONNECTED   (true, false, false, "already open"),
        OPEN        (true, false, true, "already open"),
        CLOSED      (false, true, false, "closed");

        private final boolean open;
        private final boolean closed;
        private boolean comPortEstablished;
        private final String description;

        private State(boolean open, boolean closed, boolean comPortEstablished, String description) {
            this.open = open;
            this.closed = closed;
            this.comPortEstablished = comPortEstablished;
            this.description = description;
        }

        public void checkAllowOpen() {
            if (open || closed)
                throw new IllegalStateException("port is " + this.description);
        }

        public void checkAllowNormalOperation() {
            if (!open || closed)
                throw new IllegalStateException("port is " + this.description);
        }

        public boolean comPortEstablished() {
            return this.comPortEstablished;
        }
    }

    private final Logger log = Logger.getLogger(getClass());
    private final TelnetClient telnetClient;

    private String name;
    private String signature = getClass().getName();
    private State state;
    private SerialPortEventListener listener;

    private int baudRate = 9600;
    private int dataSize = DATASIZE_8;
    private int flowControlInbound = CONTROL_INBOUND_FLOW_NONE;
    private int flowControlOutbound = CONTROL_OUTBOUND_FLOW_NONE;
    private int parity = PARITY_NONE;
    private int stopSize = STOPSIZE_1;

    private boolean cd;
    private boolean cts;
    private boolean dsr;
    private boolean dtr;
    private boolean ri;
    private boolean rts;

    private int lineStateNotify;                                // which line state changes we notify listener about
    private int lineStateMask;                                  // which line state changes access server notifies us about
    private int lineStateLast;                                  // most recent line state rec'd from access server
    private int modemStateNotify;                               // which modem state changes we notify listener about
    private int modemStateMask = MODEMSTATE_ALWAYS_MONITOR;     // which modem state changes access server notifies us about
    private int modemStateLast;                                 // most recent modem state rec'd from access server

    /**
     * Constructor. This initializes the telnet client and creates the local socket but does not connect it.
     *
     * @param host host to connect to
     * @param port TCP port
     * @throws IllegalArgumentException if port is not between 1 and 65535
     */
    public TelnetSerialPort(InetAddress host, int port) throws IOException {
        this.state = State.INITIAL;
        this.name = getClass().getSimpleName() + "[" + host.getHostAddress() + ":" + port + "]";
        this.telnetClient = createTelnetClient();
    }

    /**
     * Get the descriptive name of this client (used for debug logging).
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the descriptive name of this client (used for debug logging).
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the signature sent to the remote server at connection time.
     * By default, the signature is the name of this class.
     */
    public String getSignature() {
        return this.signature;
    }

    /**
     * Set the signature sent to the remote server at connection time.
     *
     * @param signature signature string, or {@code null} to not send a signature
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Get the {@link TelnetClient} associated with this instance.
     */
    public TelnetClient getTelnetClient() {
        return this.telnetClient;
    }

    /**
     * Construct and configure the {@link TelnetClient} to be used for this instance.
     */
    protected TelnetClient createTelnetClient() throws IOException {
        TelnetClient tc = new TelnetClient(DEFAULT_TERMINAL_TYPE);
        tc.setReaderThread(true);                                   // allows immediate option negotiation
        tc.setTcpNoDelay(true);
        try {
            tc.addOptionHandler(new TerminalTypeOptionHandler(DEFAULT_TERMINAL_TYPE, false, false, true, false));
            tc.addOptionHandler(new EchoOptionHandler(true, false, true, false));
            tc.addOptionHandler(new SuppressGAOptionHandler(true, true, true, true));
            tc.addOptionHandler(new ComPortOptionHandler(this));
        } catch (InvalidTelnetOptionException e) {
            throw new RuntimeException("unexpected exception", e);
        }
        tc.registerInputListener(new TelnetInputListener() {

            @Override
            public void telnetInputAvailable() {
                if ((TelnetSerialPort.this.lineStateNotify & LINESTATE_DATA_READY) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.DATA_AVAILABLE);
            }
        });
        return tc;
    }

    // We wrap the telnet port's InputStream in a NotifyInputStream so we can detect when there
    // is new data available to be read. It would be nice if the TelnetClient provided a way to
    // notify us directly, but it doesn't, so we have to use this hack.
    @Override
    public synchronized InputStream getInputStream() throws IOException {
        this.state.checkAllowNormalOperation();
        return this.telnetClient.getInputStream();
    }

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        this.state.checkAllowNormalOperation();
        return this.telnetClient.getOutputStream();
    }

    @Override
    public synchronized void close() {
        if (this.state == State.CLOSED)
            return;
        this.state.checkAllowNormalOperation();
        log.debug(this.name + ": closing connection");
        this.state = State.CLOSED;
        try {
            this.telnetClient.disconnect();
        } catch (IOException e) {
            log.debug(this.name + ": exception closing TelnetClient (ignoring)", e);
        }
    }

    @Override
    public synchronized int getBaudRate() {
        this.state.checkAllowNormalOperation();
        return this.baudRate;
    }

    @Override
    public synchronized int getDataBits() {
        this.state.checkAllowNormalOperation();
        switch (this.dataSize) {
        case DATASIZE_5:
            return SerialPort.DATABITS_5;
        case DATASIZE_6:
            return SerialPort.DATABITS_6;
        case DATASIZE_7:
            return SerialPort.DATABITS_7;
        case DATASIZE_8:
            return SerialPort.DATABITS_8;
        default:
            throw new RuntimeException("impossible case");
        }
    }

    @Override
    public synchronized int getStopBits() {
        this.state.checkAllowNormalOperation();
        switch (this.stopSize) {
        case STOPSIZE_1:
            return SerialPort.STOPBITS_1;
        case STOPSIZE_2:
            return SerialPort.STOPBITS_2;
        case STOPSIZE_1_5:
            return SerialPort.STOPBITS_1_5;
        default:
            throw new RuntimeException("impossible case");
        }
    }

    @Override
    public synchronized int getParity() {
        this.state.checkAllowNormalOperation();
        switch (this.parity) {
        case PARITY_NONE:
            return SerialPort.PARITY_NONE;
        case PARITY_ODD:
            return SerialPort.PARITY_ODD;
        case PARITY_EVEN:
            return SerialPort.PARITY_EVEN;
        case PARITY_MARK:
            return SerialPort.PARITY_MARK;
        case PARITY_SPACE:
            return SerialPort.PARITY_SPACE;
        default:
            throw new RuntimeException("impossible case");
        }
    }

    @Override
    public synchronized void sendBreak(int millis) {
        this.state.checkAllowNormalOperation();
        this.sendSubnegotiation(SET_CONTROL, CONTROL_BREAK_ON);
        try {
            Thread.currentThread().sleep(millis);
        } catch (InterruptedException e) {
            // ignore
        }
        this.sendSubnegotiation(SET_CONTROL, CONTROL_BREAK_OFF);
    }

    @Override
    public synchronized void setFlowControlMode(int flowControl) throws UnsupportedCommOperationException {
        this.state.checkAllowNormalOperation();

        // Validate bit combination
        if ((flowControl & (SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_XONXOFF_IN))
           == (SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_XONXOFF_IN)
          || (flowControl & (SerialPort.FLOWCONTROL_RTSCTS_OUT | SerialPort.FLOWCONTROL_XONXOFF_OUT))
           == (SerialPort.FLOWCONTROL_RTSCTS_OUT | SerialPort.FLOWCONTROL_XONXOFF_OUT))
            throw new UnsupportedCommOperationException("invalid flow control value " + flowControl);

        // Convert to RFC 2217 values
        int previousFlowControlOutbound = this.flowControlOutbound;
        int previousFlowControlInbound = this.flowControlInbound;
        this.flowControlOutbound = (flowControl & SerialPort.FLOWCONTROL_RTSCTS_OUT) != 0 ? CONTROL_OUTBOUND_FLOW_HARDWARE :
          (flowControl & SerialPort.FLOWCONTROL_XONXOFF_OUT) != 0 ? CONTROL_OUTBOUND_FLOW_XON_XOFF : CONTROL_OUTBOUND_FLOW_NONE;
        this.flowControlInbound = (flowControl & SerialPort.FLOWCONTROL_RTSCTS_IN) != 0 ? CONTROL_INBOUND_FLOW_HARDWARE :
          (flowControl & SerialPort.FLOWCONTROL_XONXOFF_IN) != 0 ? CONTROL_INBOUND_FLOW_XON_XOFF : CONTROL_INBOUND_FLOW_NONE;

        // Update server (outbound first per RFC 2217)
        if (this.flowControlOutbound != previousFlowControlOutbound && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_CONTROL, this.flowControlOutbound);
        if (this.flowControlInbound != previousFlowControlInbound && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_CONTROL, this.flowControlInbound);
    }

    @Override
    public synchronized int getFlowControlMode() {
        this.state.checkAllowNormalOperation();
        int value = SerialPort.FLOWCONTROL_NONE;
        switch (this.flowControlOutbound) {
        case CONTROL_OUTBOUND_FLOW_HARDWARE:
            value |= FLOWCONTROL_RTSCTS_OUT;
            break;
        case CONTROL_OUTBOUND_FLOW_XON_XOFF:
            value |= FLOWCONTROL_XONXOFF_OUT;
            break;
        default:
            break;
        }
        switch (this.flowControlInbound) {
        case CONTROL_INBOUND_FLOW_HARDWARE:
            value |= FLOWCONTROL_RTSCTS_IN;
            break;
        case CONTROL_INBOUND_FLOW_XON_XOFF:
            value |= FLOWCONTROL_XONXOFF_IN;
            break;
        default:
            break;
        }
        return value;
    }

    @Override
    public synchronized void setSerialPortParams(int baudRate, int dataBits, int stopBits, int parity)
      throws UnsupportedCommOperationException {
        this.state.checkAllowNormalOperation();

        // Validate parameters and convert to RFC 2217 values
        if (baudRate <= 0)
            throw new UnsupportedCommOperationException("invalid baud rate " + baudRate);
        switch (dataBits) {
        case SerialPort.DATABITS_5:
            dataBits = DATASIZE_5;
            break;
        case SerialPort.DATABITS_6:
            dataBits = DATASIZE_6;
            break;
        case SerialPort.DATABITS_7:
            dataBits = DATASIZE_7;
            break;
        case SerialPort.DATABITS_8:
            dataBits = DATASIZE_8;
            break;
        default:
            throw new UnsupportedCommOperationException("invalid data bits " + dataBits);
        }
        switch (stopBits) {
        case SerialPort.STOPBITS_1:
            stopBits = STOPSIZE_1;
            break;
        case SerialPort.STOPBITS_2:
            stopBits = STOPSIZE_2;
            break;
        case SerialPort.STOPBITS_1_5:
            stopBits = STOPSIZE_1_5;
            break;
        default:
            throw new UnsupportedCommOperationException("invalid stop bits " + stopBits);
        }
        switch (parity) {
        case SerialPort.PARITY_NONE:
            parity = PARITY_NONE;
            break;
        case SerialPort.PARITY_ODD:
            parity = PARITY_ODD;
            break;
        case SerialPort.PARITY_EVEN:
            parity = PARITY_EVEN;
            break;
        case SerialPort.PARITY_MARK:
            parity = PARITY_MARK;
            break;
        case SerialPort.PARITY_SPACE:
            parity = PARITY_SPACE;
            break;
        default:
            throw new UnsupportedCommOperationException("invalid parity " + parity);
        }

        // Update my state
        boolean changed = false;
        if (this.baudRate != baudRate) {
            this.baudRate = baudRate;
            changed = true;
        }
        if (this.dataSize != dataBits) {
            this.dataSize = dataBits;
            changed = true;
        }
        if (this.stopSize != stopBits) {
            this.stopSize = stopBits;
            changed = true;
        }
        if (this.parity != parity) {
            this.parity = parity;
            changed = true;
        }

        // Update access server if there was a change
        if (changed && this.state.comPortEstablished())
            this.sendSerialPortGeometry();
    }

    @Override
    public synchronized void setDTR(boolean value) {
        this.state.checkAllowNormalOperation();
        if (this.dtr != value) {
            this.dtr = value;
            if (this.state.comPortEstablished())
                this.sendSubnegotiation(SET_CONTROL, this.dtr ? CONTROL_DTR_ON : CONTROL_DTR_OFF);
        }
    }

    @Override
    public synchronized boolean isDTR() {
        this.state.checkAllowNormalOperation();
        return this.dtr;
    }

    @Override
    public synchronized void setRTS(boolean value) {
        this.state.checkAllowNormalOperation();
        if (this.rts != value) {
            this.rts = value;
            if (this.state.comPortEstablished())
                this.sendSubnegotiation(SET_CONTROL, this.rts ? CONTROL_RTS_ON : CONTROL_RTS_OFF);
        }
    }

    @Override
    public synchronized boolean isRTS() {
        this.state.checkAllowNormalOperation();
        return this.rts;
    }

    @Override
    public synchronized boolean isCTS() {
        this.state.checkAllowNormalOperation();
        return this.cts;
    }

    @Override
    public synchronized boolean isDSR() {
        this.state.checkAllowNormalOperation();
        return this.dsr;
    }

    @Override
    public synchronized boolean isRI() {
        this.state.checkAllowNormalOperation();
        return this.ri;
    }

    @Override
    public synchronized boolean isCD() {
        this.state.checkAllowNormalOperation();
        return this.cd;
    }

    // This is invoked by the ComPortOptionHandler once the server has agreed to accept COM-PORT-OPTION subnegotiation commands

    void startSubnegotiation() {

        // Log
        log.debug(this.name + ": server accepted COM-PORT-OPTION, sending serial configuration");

        // Sanity check
        switch (this.state) {
            case CONNECTED:
            case OPEN:
                break;
            default:
                return;
        }

        // Send signature if desired
        if (this.signature != null) {
            try {
                this.sendSubnegotiation(SIGNATURE, this.signature.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                log.error(this.name + ": can't encode signature to UTF-8", e);
            }
        }

        // Send all configuration information
        this.sendSerialPortGeometry();
        this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
        this.sendSubnegotiation(SET_MODEMSTATE_MASK, this.modemStateMask);
        this.sendSubnegotiation(SET_CONTROL, this.flowControlInbound);
        this.sendSubnegotiation(SET_CONTROL, this.flowControlOutbound);
        this.sendSubnegotiation(SET_CONTROL, this.dtr ? CONTROL_DTR_ON : CONTROL_DTR_OFF);
        this.sendSubnegotiation(SET_CONTROL, this.rts ? CONTROL_RTS_ON : CONTROL_RTS_OFF);
    }

    // Listener management

    @Override
    public synchronized void addEventListener(SerialPortEventListener listener) throws TooManyListenersException {
        this.state.checkAllowNormalOperation();
        if (this.listener != null)
            throw new TooManyListenersException("only one listener allowed");
        this.listener = listener;
    }

    @Override
    public synchronized void removeEventListener() {
        this.listener = null;
    }

    // Notification configuration

    @Override
    public synchronized void notifyOnDataAvailable(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_DATA_READY, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    @Override
    public synchronized void notifyOnOutputEmpty(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_TRANSFER_SHIFT_REGISTER_EMPTY, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    @Override
    public synchronized void notifyOnCTS(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateModemStateMask(MODEMSTATE_CTS, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_MODEMSTATE_MASK, this.modemStateMask);
    }

    @Override
    public synchronized void notifyOnDSR(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateModemStateMask(MODEMSTATE_DSR, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_MODEMSTATE_MASK, this.modemStateMask);
    }

    @Override
    public synchronized void notifyOnRingIndicator(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateModemStateMask(MODEMSTATE_RING_INDICATOR, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_MODEMSTATE_MASK, this.modemStateMask);
    }

    @Override
    public synchronized void notifyOnCarrierDetect(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateModemStateMask(MODEMSTATE_CARRIER_DETECT, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_MODEMSTATE_MASK, this.modemStateMask);
    }

    @Override
    public synchronized void notifyOnOverrunError(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_OVERRUN_ERROR, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    @Override
    public synchronized void notifyOnParityError(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_PARITY_ERROR, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    @Override
    public synchronized void notifyOnFramingError(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_FRAMING_ERROR, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    @Override
    public synchronized void notifyOnBreakInterrupt(boolean value) {
        this.state.checkAllowNormalOperation();
        if (updateLineStateMask(LINESTATE_BREAK_DETECT, value) && this.state.comPortEstablished())
            this.sendSubnegotiation(SET_LINESTATE_MASK, this.lineStateMask);
    }

    // Methods invoked when the remote access server notifies us of line or modem state changes

    synchronized void notifyLineState(int value) {
        if ((this.lineStateNotify & value & LINESTATE_TRANSFER_SHIFT_REGISTER_EMPTY) != 0)
            this.sendEvent(SerialPortEvent.OUTPUT_BUFFER_EMPTY);
        if ((this.lineStateNotify & value & LINESTATE_BREAK_DETECT) != 0)
            this.sendEvent(SerialPortEvent.BI);
        if ((this.lineStateNotify & value & LINESTATE_FRAMING_ERROR) != 0)
            this.sendEvent(SerialPortEvent.FE);
        if ((this.lineStateNotify & value & LINESTATE_PARITY_ERROR) != 0)
            this.sendEvent(SerialPortEvent.PE);
        if ((this.lineStateNotify & value & LINESTATE_OVERRUN_ERROR) != 0)
            this.sendEvent(SerialPortEvent.OE);
        this.lineStateLast = value;
    }

    synchronized void notifyModemState(int value) {
        if (((value & MODEMSTATE_CARRIER_DETECT) ^ modemStateLast) != 0)
            this.sendEvent(SerialPortEvent.CD, (value & MODEMSTATE_CARRIER_DETECT) != 0);
        if (((value & MODEMSTATE_RING_INDICATOR) ^ modemStateLast) != 0)
            this.sendEvent(SerialPortEvent.RI, (value & MODEMSTATE_RING_INDICATOR) != 0);
        if (((value & MODEMSTATE_DSR) ^ modemStateLast) != 0)
            this.sendEvent(SerialPortEvent.DSR, (value & MODEMSTATE_DSR) != 0);
        if (((value & MODEMSTATE_CTS) ^ modemStateLast) != 0)
            this.sendEvent(SerialPortEvent.CTS, (value & MODEMSTATE_CTS) != 0);
        this.modemStateLast = value;
    }

    private void sendEvent(int type) {
        sendEvent(type, true);
    }

    private void sendEvent(int type, boolean newValue) {
        SerialPortEventListener currentListener;
        synchronized (this) {
            currentListener = this.listener;
        }
        if (currentListener == null)
            return;
        SerialPortEvent event = new SerialPortEvent(this, type, !newValue, newValue);
        try {
            currentListener.serialEvent(event);
        } catch (Exception e) {
            log.warn(this.name + ": exception from listener " + listener, e);
        }
    }

    // Send serial port "geometry" in the order recommended by RFC 2217 (section 2)
    private void sendSerialPortGeometry() {
        this.sendSubnegotiation(SET_BAUDRATE, this.baudRate);
        this.sendSubnegotiation(SET_DATASIZE, this.dataSize);
        this.sendSubnegotiation(SET_PARITY, this.parity);
        this.sendSubnegotiation(SET_STOPSIZE, this.stopSize);
    }

    // Internal utility methods

    private void sendSubnegotiation(int suboption, byte value) {
        this.sendSubnegotiation(suboption, new byte[] { value });
    }

    private void sendSubnegotiation(int suboption, int value) {
        byte[] buf = new byte[4];
        buf[0] = (byte)(value >> 24);
        buf[1] = (byte)(value >> 16);
        buf[2] = (byte)(value >>  8);
        buf[3] = (byte)value;
        this.sendSubnegotiation(suboption, buf);
    }

    private void sendSubnegotiation(int suboption, byte[] payload) {
        int nlen = 2 + payload.length;
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] == (byte)0xff)
                nlen++;
        }
        int[] nbuf = new int[nlen];
        int j = 0;
        nbuf[j++] = COM_PORT_OPTION;
        nbuf[j++] = suboption;
        for (int i = 0; i < payload.length; i++) {
            nbuf[j++] = payload[i] & 0xff;
            if (payload[i] == (byte)0xff)
                nbuf[j++] = 0xff;
        }
        try {
            if (log.isDebugEnabled())
                log.debug(this.name + ": send COM-PORT-OPTION " + RFC2217.decodeSubnegotiation(nbuf, 1, nbuf.length - 1));
            this.telnetClient.sendSubnegotiation(nbuf);
        } catch (IOException e) {
            throw new DisconnectedException(e);
        }
    }

    // Update line state notifications; return true if we need to send new mask to access server
    private boolean updateLineStateMask(int bit, boolean value) {
        int previous = this.lineStateMask;
        if (value) {
            this.lineStateNotify |= bit;
            this.lineStateMask |= bit;
        } else {
            this.lineStateNotify &= ~bit;
            this.lineStateMask &= ~bit;
        }
        this.lineStateMask &= ~LINESTATE_NEVER_MONITOR;
        return this.lineStateMask != previous;
    }

    // Update modem state notifications; return true if we need to send new mask to access server
    private boolean updateModemStateMask(int bit, boolean value) {
        int previous = this.modemStateMask;
        if (value) {
            this.modemStateNotify |= bit;
            this.modemStateMask |= bit;
        } else {
            this.modemStateNotify &= ~bit;
            this.modemStateMask &= ~bit;
        }
        this.modemStateMask |= MODEMSTATE_ALWAYS_MONITOR;
        return this.modemStateMask != previous;
    }

    // Unimplemented stuff

    @Override
    public synchronized void enableReceiveThreshold(int threshold) throws UnsupportedCommOperationException {
        this.state.checkAllowNormalOperation();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveThreshold() {
        this.state.checkAllowNormalOperation();
    }

    @Override
    public synchronized boolean isReceiveThresholdEnabled() {
        this.state.checkAllowNormalOperation();
        return false;
    }

    @Override
    public synchronized int getReceiveThreshold() {
        this.state.checkAllowNormalOperation();
        return 0;
    }

    @Override
    public synchronized void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException {
        this.state.checkAllowNormalOperation();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveTimeout() {
        this.state.checkAllowNormalOperation();
    }

    @Override
    public synchronized boolean isReceiveTimeoutEnabled() {
        this.state.checkAllowNormalOperation();
        return false;
    }

    @Override
    public synchronized int getReceiveTimeout() {
        this.state.checkAllowNormalOperation();
        return 0;
    }

    @Override
    public synchronized void enableReceiveFraming(int framingByte) throws UnsupportedCommOperationException {
        this.state.checkAllowNormalOperation();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveFraming() {
        this.state.checkAllowNormalOperation();
    }

    @Override
    public synchronized boolean isReceiveFramingEnabled() {
        this.state.checkAllowNormalOperation();
        return false;
    }

    @Override
    public synchronized int getReceiveFramingByte() {
        this.state.checkAllowNormalOperation();
        return 0;
    }

    @Override
    public synchronized void setInputBufferSize(int size) {
        this.state.checkAllowNormalOperation();
    }

    @Override
    public synchronized int getInputBufferSize() {
        this.state.checkAllowNormalOperation();
        return 0;
    }

    @Override
    public synchronized void setOutputBufferSize(int size) {
        this.state.checkAllowNormalOperation();
    }

    @Override
    public synchronized int getOutputBufferSize() {
        this.state.checkAllowNormalOperation();
        return 0;
    }
}

