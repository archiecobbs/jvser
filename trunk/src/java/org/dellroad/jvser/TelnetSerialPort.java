
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * This class extends the {@link SerialPort} class and functions in the same way, however, there are
 * a couple of differences to be aware of.
 * </p>
 *
 * <p>
 * First, to create and "open" a serial port, instantiate an instance of this class, configure it as
 * required, and then "open" it by getting the {@link TelnetClient} via {@link #getTelnetClient} and
 * invoking some variant of {@link TelnetClient#connect(java.net.InetAddress, int) TelnetClient.connect()}.
 * This will initiate the actual telnet connection to the access server.
 * </p>
 *
 * <p>
 * Once connected, if the underlying telnet connection is broken, then an {@link IOException} will be
 * thrown when attempting to access the serial port input or output streams. In addition, a
 * {@link SerialPortEvent#DATA_AVAILABLE DATA_AVAILABLE} event will be immediately generated (assuming
 * a listener is registered and {@link #notifyOnDataAvailable notifyOnDataAvailable(true)} invoked).
 * </p>
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public class TelnetSerialPort extends SerialPort {

    /**
     * The default baud rate (specified in the {@link SerialPort} API documentation).
     */
    public static final int DEFAULT_BAUD_RATE = 9600;

    private static final String DEFAULT_TERMINAL_TYPE = "VT100";

    // Modem state bits we always want the server to report to us regardless of what listener wants.
    // This is so we can always stay up-to-date with their values in case isCD(), etc. is invoked.
    private static final int MODEMSTATE_ALWAYS_MONITOR
      = MODEMSTATE_CARRIER_DETECT | MODEMSTATE_RING_INDICATOR | MODEMSTATE_DSR | MODEMSTATE_CTS;

    // Line state bits we never want the server to report to us regardless of what listener wants; internally,
    // we use the LINESTATE_DATA_READY bit only to indicate the listener wants DATA_AVAILABLE notifications.
    private static final int LINESTATE_NEVER_MONITOR = LINESTATE_DATA_READY;

    // States
    private enum State {
        INITIAL(false, false),
        ESTABLISHED(true, false),
        CLOSED(false, true);

        private final boolean established;
        private final boolean closed;

        private State(boolean established, boolean closed) {
            this.established = established;
            this.closed = closed;
        }

        public void checkNotClosed() {
            if (this.closed)
                throw new IllegalStateException("port is closed");
        }

        public boolean isEstablished() {
            return this.established;
        }
    }

    private final Logger log = Logger.getLogger(getClass());
    private final TelnetClient telnetClient;

    private String name;
    private String signature;
    private State state;
    private SerialPortEventListener listener;

    private int baudRate = DEFAULT_BAUD_RATE;
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
        this.signature = "jvser v" + Version.JVSER_VERSION;
        this.telnetClient = this.createTelnetClient();
        this.telnetClient.registerInputListener(new TelnetInputListener() {

            @Override
            public void telnetInputAvailable() {
                boolean notify;
                synchronized (TelnetSerialPort.this) {
                    notify = (TelnetSerialPort.this.lineStateNotify & LINESTATE_DATA_READY) != 0;
                }
                if (notify)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.DATA_AVAILABLE);
            }
        });
    }

    /**
     * Get the descriptive name of this client (used for logging purposes).
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the descriptive name of this client (used for logging purposes).
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
     * @param signature signature string, or {@code null} (or empty string) to not send a signature
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
        return tc;
    }

    // We wrap the telnet port's InputStream in a NotifyInputStream so we can detect when there
    // is new data available to be read. It would be nice if the TelnetClient provided a way to
    // notify us directly, but it doesn't, so we have to use this hack.
    @Override
    public synchronized InputStream getInputStream() throws IOException {
        this.state.checkNotClosed();
        return this.telnetClient.getInputStream();
    }

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        this.state.checkNotClosed();
        return this.telnetClient.getOutputStream();
    }

    @Override
    public synchronized void close() {
        if (this.state == State.CLOSED)
            return;
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
        this.state.checkNotClosed();
        return this.baudRate;
    }

    @Override
    public synchronized int getDataBits() {
        this.state.checkNotClosed();
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
        this.state.checkNotClosed();
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
        this.state.checkNotClosed();
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
    public void sendBreak(int millis) {
        synchronized (this) {
            this.state.checkNotClosed();
            if (this.state != State.ESTABLISHED)
                return;
            this.sendSubnegotiation(new ControlCommand(true, CONTROL_BREAK_ON));
        }
        try {
            Thread.currentThread().sleep(millis);
        } catch (InterruptedException e) {
            // ignore
        }
        synchronized (this) {
            if (this.state != State.ESTABLISHED)
                return;
            this.sendSubnegotiation(new ControlCommand(true, CONTROL_BREAK_OFF));
        }
    }

    @Override
    public synchronized void setFlowControlMode(int flowControl) throws UnsupportedCommOperationException {
        this.state.checkNotClosed();

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
        if (this.flowControlOutbound != previousFlowControlOutbound && this.state.isEstablished())
            this.sendSubnegotiation(new ControlCommand(true, this.flowControlOutbound));
        if (this.flowControlInbound != previousFlowControlInbound && this.state.isEstablished())
            this.sendSubnegotiation(new ControlCommand(true, this.flowControlInbound));
    }

    @Override
    public synchronized int getFlowControlMode() {
        this.state.checkNotClosed();
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
        this.state.checkNotClosed();

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
        if (changed && this.state.isEstablished())
            this.sendSerialPortGeometry();
    }

    @Override
    public synchronized void setDTR(boolean value) {
        this.state.checkNotClosed();
        if (this.dtr != value) {
            this.dtr = value;
            if (this.state.isEstablished())
                this.sendSubnegotiation(new ControlCommand(true, this.dtr ? CONTROL_DTR_ON : CONTROL_DTR_OFF));
        }
    }

    @Override
    public synchronized boolean isDTR() {
        this.state.checkNotClosed();
        return this.dtr;
    }

    @Override
    public synchronized void setRTS(boolean value) {
        this.state.checkNotClosed();
        if (this.rts != value) {
            this.rts = value;
            if (this.state.isEstablished())
                this.sendSubnegotiation(new ControlCommand(true, this.rts ? CONTROL_RTS_ON : CONTROL_RTS_OFF));
        }
    }

    @Override
    public synchronized boolean isRTS() {
        this.state.checkNotClosed();
        return this.rts;
    }

    @Override
    public synchronized boolean isCTS() {
        this.state.checkNotClosed();
        return this.cts;
    }

    @Override
    public synchronized boolean isDSR() {
        this.state.checkNotClosed();
        return this.dsr;
    }

    @Override
    public synchronized boolean isRI() {
        this.state.checkNotClosed();
        return this.ri;
    }

    @Override
    public synchronized boolean isCD() {
        this.state.checkNotClosed();
        return this.cd;
    }

    // This is invoked by the ComPortOptionHandler once the server has agreed to accept COM-PORT-OPTION subnegotiation commands

    synchronized void startSubnegotiation() {

        // Log
        log.debug(this.name + ": server accepted COM-PORT-OPTION, sending serial configuration to peer");

        // Update state
        this.state.checkNotClosed();
        this.state = State.ESTABLISHED;

        // Request signature from peer
        this.sendSubnegotiation(new SignatureCommand(true));

        // Send signature if desired
        if (this.signature != null && this.signature.length() > 0)
            this.sendSubnegotiation(new SignatureCommand(true, this.signature));

        // Send all configuration information
        this.sendSerialPortGeometry();
        this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
        this.sendSubnegotiation(new ModemStateMaskCommand(true, this.modemStateMask));
        this.sendSubnegotiation(new ControlCommand(true, this.flowControlInbound));
        this.sendSubnegotiation(new ControlCommand(true, this.flowControlOutbound));
        this.sendSubnegotiation(new ControlCommand(true, this.dtr ? CONTROL_DTR_ON : CONTROL_DTR_OFF));
        this.sendSubnegotiation(new ControlCommand(true, this.rts ? CONTROL_RTS_ON : CONTROL_RTS_OFF));
    }

    // Method to send serial port "geometry" in the order recommended by RFC 2217 (section 2)

    private void sendSerialPortGeometry() {
        this.sendSubnegotiation(new BaudRateCommand(true, this.baudRate));
        this.sendSubnegotiation(new DataSizeCommand(true, this.dataSize));
        this.sendSubnegotiation(new ParityCommand(true, this.parity));
        this.sendSubnegotiation(new StopSizeCommand(true, this.stopSize));
    }

    // This is invoked by the ComPortOptionHandler when we receive a command from the server

    void handleCommand(ComPortCommand command) {
        // Question: should we verify command.isServerCommand() ?
        command.visit(new AbstractComPortCommandSwitch() {

            @Override
            public void caseBaudRate(BaudRateCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                synchronized (TelnetSerialPort.this) {
                    TelnetSerialPort.this.baudRate = command.getBaudRate();
                }
            }

            @Override
            public void caseDataSize(DataSizeCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                synchronized (TelnetSerialPort.this) {
                    TelnetSerialPort.this.dataSize = command.getDataSize();
                }
            }

            @Override
            public void caseParity(ParityCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                synchronized (TelnetSerialPort.this) {
                    TelnetSerialPort.this.parity = command.getParity();
                }
            }

            @Override
            public void caseStopSize(StopSizeCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                synchronized (TelnetSerialPort.this) {
                    TelnetSerialPort.this.stopSize = command.getStopSize();
                }
            }

            @Override
            public void caseControl(ControlCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                synchronized (TelnetSerialPort.this) {
                    switch (command.getControl()) {
                    case CONTROL_OUTBOUND_FLOW_NONE:
                    case CONTROL_OUTBOUND_FLOW_XON_XOFF:
                    case CONTROL_OUTBOUND_FLOW_HARDWARE:
                        TelnetSerialPort.this.flowControlOutbound = command.getControl();
                        break;
                    case CONTROL_INBOUND_FLOW_NONE:
                    case CONTROL_INBOUND_FLOW_XON_XOFF:
                    case CONTROL_INBOUND_FLOW_HARDWARE:
                        TelnetSerialPort.this.flowControlInbound = command.getControl();
                        break;
                    case CONTROL_DTR_ON:
                        TelnetSerialPort.this.dtr = true;
                        break;
                    case CONTROL_DTR_OFF:
                        TelnetSerialPort.this.dtr = false;
                        break;
                    case CONTROL_RTS_ON:
                        TelnetSerialPort.this.rts = true;
                        break;
                    case CONTROL_RTS_OFF:
                        TelnetSerialPort.this.rts = false;
                        break;
                    default:
                        log.info(TelnetSerialPort.this.name + ": rec'd " + command + " (ignoring)");
                        break;
                    }
                }
            }

            @Override
            public void caseNotifyLineState(NotifyLineStateCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                int lineState = command.getLineState();
                int notify;
                synchronized (TelnetSerialPort.this) {
                    notify = TelnetSerialPort.this.lineStateNotify;
                    TelnetSerialPort.this.lineStateLast = lineState;
                }
                notify &= lineState;                                    // notify only if bit is equal to 1
                if ((notify & LINESTATE_TRANSFER_SHIFT_REGISTER_EMPTY) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.OUTPUT_BUFFER_EMPTY);
                if ((notify & LINESTATE_BREAK_DETECT) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.BI);
                if ((notify & LINESTATE_FRAMING_ERROR) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.FE);
                if ((notify & LINESTATE_PARITY_ERROR) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.PE);
                if ((notify & LINESTATE_OVERRUN_ERROR) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.OE);
            }

            @Override
            public void caseNotifyModemState(NotifyModemStateCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command);
                int modemState = command.getModemState();
                int notify;
                synchronized (TelnetSerialPort.this) {
                    notify = TelnetSerialPort.this.modemStateNotify;
                    TelnetSerialPort.this.modemStateLast = modemState;
                }
                notify &= modemState ^ modemStateLast;                  // notify only if bit has changed
                if ((notify & MODEMSTATE_CARRIER_DETECT) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.CD, (modemState & MODEMSTATE_CARRIER_DETECT) != 0);
                if ((notify & MODEMSTATE_RING_INDICATOR) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.RI, (modemState & MODEMSTATE_RING_INDICATOR) != 0);
                if ((notify & MODEMSTATE_DSR) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.DSR, (modemState & MODEMSTATE_DSR) != 0);
                if ((notify & MODEMSTATE_CTS) != 0)
                    TelnetSerialPort.this.sendEvent(SerialPortEvent.CTS, (modemState & MODEMSTATE_CTS) != 0);
            }

            @Override
            protected void caseDefault(ComPortCommand command) {
                log.info(TelnetSerialPort.this.name + ": rec'd " + command + " (ignoring)");
            }
        });
    }

    // Listener management

    @Override
    public synchronized void addEventListener(SerialPortEventListener listener) throws TooManyListenersException {
        this.state.checkNotClosed();
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
        this.state.checkNotClosed();
        updateLineStateMask(LINESTATE_DATA_READY, value);
    }

    @Override
    public synchronized void notifyOnOutputEmpty(boolean value) {
        this.state.checkNotClosed();
        if (updateLineStateMask(LINESTATE_TRANSFER_SHIFT_REGISTER_EMPTY, value) && this.state.isEstablished())
            this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
    }

    @Override
    public synchronized void notifyOnCTS(boolean value) {
        this.state.checkNotClosed();
        if (updateModemStateMask(MODEMSTATE_CTS, value) && this.state.isEstablished())
            this.sendSubnegotiation(new ModemStateMaskCommand(true, this.modemStateMask));
    }

    @Override
    public synchronized void notifyOnDSR(boolean value) {
        this.state.checkNotClosed();
        if (updateModemStateMask(MODEMSTATE_DSR, value) && this.state.isEstablished())
            this.sendSubnegotiation(new ModemStateMaskCommand(true, this.modemStateMask));
    }

    @Override
    public synchronized void notifyOnRingIndicator(boolean value) {
        this.state.checkNotClosed();
        if (updateModemStateMask(MODEMSTATE_RING_INDICATOR, value) && this.state.isEstablished())
            this.sendSubnegotiation(new ModemStateMaskCommand(true, this.modemStateMask));
    }

    @Override
    public synchronized void notifyOnCarrierDetect(boolean value) {
        this.state.checkNotClosed();
        if (updateModemStateMask(MODEMSTATE_CARRIER_DETECT, value) && this.state.isEstablished())
            this.sendSubnegotiation(new ModemStateMaskCommand(true, this.modemStateMask));
    }

    @Override
    public synchronized void notifyOnOverrunError(boolean value) {
        this.state.checkNotClosed();
        if (updateLineStateMask(LINESTATE_OVERRUN_ERROR, value) && this.state.isEstablished())
            this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
    }

    @Override
    public synchronized void notifyOnParityError(boolean value) {
        this.state.checkNotClosed();
        if (updateLineStateMask(LINESTATE_PARITY_ERROR, value) && this.state.isEstablished())
            this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
    }

    @Override
    public synchronized void notifyOnFramingError(boolean value) {
        this.state.checkNotClosed();
        if (updateLineStateMask(LINESTATE_FRAMING_ERROR, value) && this.state.isEstablished())
            this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
    }

    @Override
    public synchronized void notifyOnBreakInterrupt(boolean value) {
        this.state.checkNotClosed();
        if (updateLineStateMask(LINESTATE_BREAK_DETECT, value) && this.state.isEstablished())
            this.sendSubnegotiation(new LineStateMaskCommand(true, this.lineStateMask));
    }

    // Methods for sending event notifications

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

    // Internal utility methods

    // Send a subnegotiation to the peer
    private void sendSubnegotiation(ComPortCommand command) {
        try {
            if (log.isDebugEnabled())
                log.debug(this.name + ": send " + command);
            this.telnetClient.sendSubnegotiation(command.getBytes());
        } catch (IOException e) {
            log.warn(this.name + ": exception sending subcommand", e);
        }
    }

    // Update line state notifications; return true if we need to send new mask to access server
    private synchronized boolean updateLineStateMask(int bit, boolean value) {
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
    private synchronized boolean updateModemStateMask(int bit, boolean value) {
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

    // Unimplemented methods

    @Override
    public synchronized void enableReceiveThreshold(int threshold) throws UnsupportedCommOperationException {
        this.state.checkNotClosed();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveThreshold() {
        this.state.checkNotClosed();
    }

    @Override
    public synchronized boolean isReceiveThresholdEnabled() {
        this.state.checkNotClosed();
        return false;
    }

    @Override
    public synchronized int getReceiveThreshold() {
        this.state.checkNotClosed();
        return 0;
    }

    @Override
    public synchronized void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException {
        this.state.checkNotClosed();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveTimeout() {
        this.state.checkNotClosed();
    }

    @Override
    public synchronized boolean isReceiveTimeoutEnabled() {
        this.state.checkNotClosed();
        return false;
    }

    @Override
    public synchronized int getReceiveTimeout() {
        this.state.checkNotClosed();
        return 0;
    }

    @Override
    public synchronized void enableReceiveFraming(int framingByte) throws UnsupportedCommOperationException {
        this.state.checkNotClosed();
        throw new UnsupportedCommOperationException();
    }

    @Override
    public synchronized void disableReceiveFraming() {
        this.state.checkNotClosed();
    }

    @Override
    public synchronized boolean isReceiveFramingEnabled() {
        this.state.checkNotClosed();
        return false;
    }

    @Override
    public synchronized int getReceiveFramingByte() {
        this.state.checkNotClosed();
        return 0;
    }

    @Override
    public synchronized void setInputBufferSize(int size) {
        this.state.checkNotClosed();
    }

    @Override
    public synchronized int getInputBufferSize() {
        this.state.checkNotClosed();
        return 0;
    }

    @Override
    public synchronized void setOutputBufferSize(int size) {
        this.state.checkNotClosed();
    }

    @Override
    public synchronized int getOutputBufferSize() {
        this.state.checkNotClosed();
        return 0;
    }
}

