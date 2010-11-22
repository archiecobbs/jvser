
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

import javax.comm.SerialPort;
import javax.comm.SerialPortEvent;
import javax.comm.SerialPortEventListener;
import javax.comm.UnsupportedCommOperationException;

import org.apache.log4j.Logger;
import org.dellroad.jvser.TelnetSerialPort;

/**
 * A simple command line client.
 */
public class Client implements SerialPortEventListener {

    protected final Logger log = Logger.getLogger(getClass());

    private static final String DATA_ENCODING = "ISO-8859-1";

    private final InetAddress host;
    private final int tcpPort;
    private final boolean useThread;

    private TelnetSerialPort port;
    private boolean done;

    private int baudRate = TelnetSerialPort.DEFAULT_BAUD_RATE;
    private int dataBits = SerialPort.DATABITS_8;
    private int stopBits = SerialPort.STOPBITS_1;
    private int parity = SerialPort.PARITY_NONE;
    private int flowControl = SerialPort.FLOWCONTROL_NONE;
    private boolean dtr;
    private boolean rts;

    public Client(InetAddress host, int tcpPort, boolean useThread) {
        this.host = host;
        this.tcpPort = tcpPort;
        this.useThread = useThread;
    }

    public void run() throws Exception {

        // Sanity check
        if (this.port != null)
            throw new IllegalStateException();

        // Setup port
        this.port = new TelnetSerialPort();
        this.port.setSerialPortParams(this.baudRate, this.dataBits, this.stopBits, this.parity);
        this.port.setFlowControlMode(this.flowControl);
        this.port.addEventListener(this);
        this.port.notifyOnBreakInterrupt(true);
        this.port.notifyOnCarrierDetect(true);
        this.port.notifyOnCTS(true);
        this.port.notifyOnDataAvailable(true);
        this.port.notifyOnDSR(true);
        this.port.notifyOnFramingError(true);
        this.port.notifyOnOutputEmpty(true);
        this.port.notifyOnOverrunError(true);
        this.port.notifyOnParityError(true);
        this.port.notifyOnRingIndicator(true);

        // Connect port
        log.info("connecting to " + this.host + ":" + this.tcpPort);
        this.port.getTelnetClient().connect(this.host, this.tcpPort);

        // Spawn reader thread if needed
        if (this.useThread) {
            Thread reader = new Thread() {

                @Override
                public void run() {
                    while (!Client.this.done)
                        Client.this.readData();
                }
            };
            reader.setDaemon(false);
            reader.start();
        }

        // Main loop
        log.info("available commands: !break !speed !flow !rts !dtr !close");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, DATA_ENCODING));
        while (!this.done) {

            // Read keyboard input
            String line = reader.readLine();
            line += "\r\n";

            // Handle special commands
            if (line.charAt(0) == '!') {
                String[] cmd = line.substring(1).trim().split("\\s");
                if (cmd.length == 0 || cmd[0].length() == 0)
                    continue;
                if (cmd[0].equals("!close")) {
                    this.done = true;
                    continue;
                }
                handleCommand(line.substring(1).trim().split("\\s"));
                continue;
            }

            // Handle plain data
            byte[] data = line.getBytes(DATA_ENCODING);
            this.port.getOutputStream().write(data);
            this.port.getOutputStream().flush();
        }

        // Close port
        this.port.close();
    }

    private void handleCommand(String[] cmd) throws UnsupportedCommOperationException {
        if (cmd[0].equals("break") && cmd.length == 1) {
            this.port.sendBreak(500);
            return;
        }
        if (cmd[0].equals("speed") && cmd.length == 2) {
            try {
                this.baudRate = Integer.parseInt(cmd[1]);
            } catch (NumberFormatException e) {
                log.error("invalid baud rate: " + e);
                return;
            }
            log.info("setting baud rate to " + this.baudRate);
            this.port.setSerialPortParams(this.baudRate, this.dataBits, this.stopBits, this.parity);
            return;
        }
        if (cmd[0].equals("flow") && cmd.length == 2) {
            boolean outbound;
            if (cmd[1].equals("none"))
                this.flowControl = SerialPort.FLOWCONTROL_NONE;
            else if (cmd[1].equals("xonoff"))
                this.flowControl = SerialPort.FLOWCONTROL_XONXOFF_IN | SerialPort.FLOWCONTROL_XONXOFF_OUT;
            else if (cmd[1].equals("hardware") || cmd[2].equals("hw"))
                this.flowControl = SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT;
            else {
                log.error("invalid flow control `" + cmd[1] + "': should be `none', `xonoff', or `hardware'");
                return;
            }
            log.info("setting flow control mode to " + cmd[1]);
            this.port.setFlowControlMode(this.flowControl);
            return;
        }
        if (cmd[0].equals("dtr") && cmd.length == 2) {
            this.dtr = Boolean.parseBoolean(cmd[1]);
            log.info("setting DTR to " + (this.dtr ? "1" : "0"));
            this.port.setDTR(this.dtr);
            return;
        }
        if (cmd[0].equals("rts") && cmd.length == 2) {
            this.rts = Boolean.parseBoolean(cmd[1]);
            log.info("setting RTS to " + (this.rts ? "1" : "0"));
            this.port.setRTS(this.rts);
            return;
        }
        log.error("unknown command `" + cmd[0] + "'");
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE)
            log.info("rec'd serial port event " + decodeSerialEvent(event));
        if (!this.useThread)
            readData();
    }

    private void readData() {
        byte[] buf = new byte[8192];
        int r;
        try {
            r = this.port.getInputStream().read(buf);
        } catch (IOException e) {
            log.error("exception while reading: " + e);
            this.done = true;
            return;
        }
        if (r == -1) {
            log.error("read EOF from stream");
            this.done = true;
            return;
        }
        char[] cbuf = new char[r];
        for (int i = 0; i < r; i++)
            cbuf[i] = (char)(buf[i] & 0xff);
        String s = new String(cbuf);
        s = s.replaceAll("\n", "\\n").replaceAll("\r", "\\r").replaceAll("\t", "\\t");
        log.info("RECV: [" + s + "]");
    }

    public static String decodeSerialEvent(SerialPortEvent event) {
        String name;
        switch (event.getEventType()) {
        case SerialPortEvent.BI:
            name = "BREAK";
            break;
        case SerialPortEvent.CD:
            name = "CARRIER-DETECT";
            break;
        case SerialPortEvent.CTS:
            name = "CTS";
            break;
        case SerialPortEvent.DATA_AVAILABLE:
            name = "DATA_AVAILABLE";
            break;
        case SerialPortEvent.DSR:
            name = "DSR";
            break;
        case SerialPortEvent.FE:
            name = "FRAMING-ERROR";
            break;
        case SerialPortEvent.OE:
            name = "OVERFLOW-ERROR";
            break;
        case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
            name = "OUTPUT-BUFFER-EMPTY";
            break;
        case SerialPortEvent.PE:
            name = "PARITY-ERROR";
            break;
        case SerialPortEvent.RI:
            name = "RING-INDICATION";
            break;
        default:
            name = "?" + event.getEventType() + "?";
            break;
        }
        String oldValue = event.getOldValue() ? "0" : "1";
        String newValue = event.getNewValue() ? "0" : "1";
        return name + " " + oldValue + " -> " + newValue;
    }
}

