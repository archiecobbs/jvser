
/*
 * Copyright (C) 2010 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.jvser;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;


/**
 * RFC 2217 constants and utility methods.
 *
 * @see <a href="http://tools.ietf.org/html/rfc2217">RFC 2217</a>
 */
public final class RFC2217 {

    public static final int COM_PORT_OPTION = 44;

    public static final int SIGNATURE = 0;
    public static final int SET_BAUDRATE = 1;
    public static final int SET_DATASIZE = 2;
    public static final int SET_PARITY = 3;
    public static final int SET_STOPSIZE = 4;
    public static final int SET_CONTROL = 5;
    public static final int NOTIFY_LINESTATE = 6;
    public static final int NOTIFY_MODEMSTATE = 7;
    public static final int FLOWCONTROL_SUSPEND = 8;
    public static final int FLOWCONTROL_RESUME = 9;
    public static final int SET_LINESTATE_MASK = 10;
    public static final int SET_MODEMSTATE_MASK = 11;
    public static final int PURGE_DATA = 12;
    public static final int SERVER_OFFSET = 100;

    // SET_DATASIZE values
    public static final int DATASIZE_REQUEST = 0;
    public static final int DATASIZE_5 = 5;
    public static final int DATASIZE_6 = 6;
    public static final int DATASIZE_7 = 7;
    public static final int DATASIZE_8 = 8;

    // SET_PARITY values
    public static final int PARITY_REQUEST = 0;
    public static final int PARITY_NONE = 1;
    public static final int PARITY_ODD = 2;
    public static final int PARITY_EVEN = 3;
    public static final int PARITY_MARK = 4;
    public static final int PARITY_SPACE = 5;

    // SET_STOPSIZE values
    public static final int STOPSIZE_1 = 1;
    public static final int STOPSIZE_2 = 2;
    public static final int STOPSIZE_1_5 = 3;

    // SET_CONTROL values
    public static final int CONTROL_OUTBOUND_FLOW_REQUEST = 0;
    public static final int CONTROL_OUTBOUND_FLOW_NONE = 1;
    public static final int CONTROL_OUTBOUND_FLOW_XON_XOFF = 2;
    public static final int CONTROL_OUTBOUND_FLOW_HARDWARE = 3;
    public static final int CONTROL_BREAK_REQUEST = 4;
    public static final int CONTROL_BREAK_ON = 5;
    public static final int CONTROL_BREAK_OFF = 6;
    public static final int CONTROL_DTR_REQUEST = 7;
    public static final int CONTROL_DTR_ON = 8;
    public static final int CONTROL_DTR_OFF = 9;
    public static final int CONTROL_RTS_REQUEST = 10;
    public static final int CONTROL_RTS_ON = 11;
    public static final int CONTROL_RTS_OFF = 12;
    public static final int CONTROL_INBOUND_FLOW_REQUEST = 13;
    public static final int CONTROL_INBOUND_FLOW_NONE = 14;
    public static final int CONTROL_INBOUND_FLOW_XON_XOFF = 15;
    public static final int CONTROL_INBOUND_FLOW_HARDWARE = 16;
    public static final int CONTROL_OUTBOUND_FLOW_DCD = 17;
    public static final int CONTROL_INBOUND_FLOW_DTR = 18;
    public static final int CONTROL_OUTBOUND_FLOW_DSR = 19;

    // SET_LINESTATE_MASK bit values
    public static final int LINESTATE_TIME_OUT = 0x80;
    public static final int LINESTATE_TRANSFER_SHIFT_REGISTER_EMPTY = 0x40;
    public static final int LINESTATE_TRANSFER_HOLDING_REGISTER_EMPTY = 0x20;
    public static final int LINESTATE_BREAK_DETECT = 0x10;
    public static final int LINESTATE_FRAMING_ERROR = 0x08;
    public static final int LINESTATE_PARITY_ERROR = 0x04;
    public static final int LINESTATE_OVERRUN_ERROR = 0x02;
    public static final int LINESTATE_DATA_READY = 0x01;

    // SET_MODEMSTATE_MASK bit values
    public static final int MODEMSTATE_CARRIER_DETECT = 0x80;
    public static final int MODEMSTATE_RING_INDICATOR = 0x40;
    public static final int MODEMSTATE_DSR = 0x20;
    public static final int MODEMSTATE_CTS = 0x10;
    public static final int MODEMSTATE_DELTA_CARRIER_DETECT = 0x08;
    public static final int MODEMSTATE_TRAILING_EDGE_RING_DETECTOR = 0x04;
    public static final int MODEMSTATE_DELTA_DSR = 0x02;
    public static final int MODEMSTATE_DELTA_CTS = 0x01;

    // PURGE_DATA values
    public static final int PURGE_DATA_RECEIVE_DATA_BUFFER = 0x01;
    public static final int PURGE_DATA_TRANSMIT_DATA_BUFFER = 0x02;
    public static final int PURGE_DATA_BOTH_DATA_BUFFERS = 0x03;

    private RFC2217() {
    }

    public static String decodeComPortCommand(int command) {
        String s = decodeClientComPortCommand(command);
        if (s != null)
            return s + "[C]";
        s = decodeClientComPortCommand(command - SERVER_OFFSET);
        if (s != null)
            return s + "[S]";
        return rawBytes(new int[] { command }, 0, 1);
    }

    /**
     * Decode an RFC 2217 subnegotiation into human-readable form.
     * The first byte should be the byte after the COM-PORT-OPTION byte.
     */
    public static String decodeSubnegotiation(int[] data, int off, int len) {
        if (len <= 0)
            return "(empty)";
        String payload = null;
        switch (data[off]) {
        case SIGNATURE:
        case SIGNATURE + SERVER_OFFSET:
            byte[] buf = new byte[len - 1];
            for (int i = 1; i < len; i++)
                buf[i - 1] = (byte)data[i];
            try {
                payload = "\"" + new String(buf, "UTF-8") + "\"";
            } catch (UnsupportedEncodingException e) {
                break;
            }
            break;
        case SET_BAUDRATE:
        case SET_BAUDRATE + SERVER_OFFSET:
            if (len != 5)
                break;
            int baudRate = ((data[1] & 0xff) << 24) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 8) | (data[4] & 0xff);
            payload = "" + baudRate;
            break;
        case SET_DATASIZE:
        case SET_DATASIZE + SERVER_OFFSET:
            if (len != 2)
                break;
            switch (data[1]) {
            case DATASIZE_REQUEST:
                payload = "REQUEST";
                break;
            case DATASIZE_5:
                payload = "5";
                break;
            case DATASIZE_6:
                payload = "6";
                break;
            case DATASIZE_7:
                payload = "7";
                break;
            case DATASIZE_8:
                payload = "8";
                break;
            default:
                break;
            }
            break;
        case SET_PARITY:
        case SET_PARITY + SERVER_OFFSET:
            if (len != 2)
                break;
            switch (data[1]) {
            case PARITY_REQUEST:
                payload = "REQUEST";
                break;
            case PARITY_NONE:
                payload = "NONE";
                break;
            case PARITY_ODD:
                payload = "ODD";
                break;
            case PARITY_EVEN:
                payload = "EVEN";
                break;
            case PARITY_MARK:
                payload = "MARK";
                break;
            case PARITY_SPACE:
                payload = "SPACE";
                break;
            default:
                break;
            }
            break;
        case SET_STOPSIZE:
        case SET_STOPSIZE + SERVER_OFFSET:
            if (len != 2)
                break;
            switch (data[1]) {
            case STOPSIZE_1:
                payload = "1";
                break;
            case STOPSIZE_2:
                payload = "2";
                break;
            case STOPSIZE_1_5:
                payload = "1.5";
                break;
            default:
                break;
            }
            break;
        case SET_CONTROL:
        case SET_CONTROL + SERVER_OFFSET:
            if (len != 2)
                break;
            switch (data[1]) {
            case CONTROL_OUTBOUND_FLOW_REQUEST:
                payload = "OUTBOUND_FLOW_REQUEST";
                break;
            case CONTROL_OUTBOUND_FLOW_NONE:
                payload = "OUTBOUND_FLOW_NONE";
                break;
            case CONTROL_OUTBOUND_FLOW_XON_XOFF:
                payload = "OUTBOUND_FLOW_XON_XOFF";
                break;
            case CONTROL_OUTBOUND_FLOW_HARDWARE:
                payload = "OUTBOUND_FLOW_HARDWARE";
                break;
            case CONTROL_BREAK_REQUEST:
                payload = "BREAK_REQUEST";
                break;
            case CONTROL_BREAK_ON:
                payload = "BREAK_ON";
                break;
            case CONTROL_BREAK_OFF:
                payload = "BREAK_OFF";
                break;
            case CONTROL_DTR_REQUEST:
                payload = "DTR_REQUEST";
                break;
            case CONTROL_DTR_ON:
                payload = "DTR_ON";
                break;
            case CONTROL_DTR_OFF:
                payload = "DTR_OFF";
                break;
            case CONTROL_RTS_REQUEST:
                payload = "RTS_REQUEST";
                break;
            case CONTROL_RTS_ON:
                payload = "RTS_ON";
                break;
            case CONTROL_RTS_OFF:
                payload = "RTS_OFF";
                break;
            case CONTROL_INBOUND_FLOW_REQUEST:
                payload = "INBOUND_FLOW_REQUEST";
                break;
            case CONTROL_INBOUND_FLOW_NONE:
                payload = "INBOUND_FLOW_NONE";
                break;
            case CONTROL_INBOUND_FLOW_XON_XOFF:
                payload = "INBOUND_FLOW_XON_XOFF";
                break;
            case CONTROL_INBOUND_FLOW_HARDWARE:
                payload = "INBOUND_FLOW_HARDWARE";
                break;
            case CONTROL_OUTBOUND_FLOW_DCD:
                payload = "OUTBOUND_FLOW_DCD";
                break;
            case CONTROL_INBOUND_FLOW_DTR:
                payload = "INBOUND_FLOW_DTR";
                break;
            case CONTROL_OUTBOUND_FLOW_DSR:
                payload = "OUTBOUND_FLOW_DSR";
                break;
            default:
                break;
            }
            break;
        case SET_LINESTATE_MASK:
        case SET_LINESTATE_MASK + SERVER_OFFSET:
        case NOTIFY_LINESTATE:
        case NOTIFY_LINESTATE + SERVER_OFFSET:
            if (len != 2)
                break;
            payload = decodeBits(data[1], new String[] {
                "TIME_OUT",
                "TRANSFER_SHIFT_REGISTER_EMPTY",
                "TRANSFER_HOLDING_REGISTER_EMPTY",
                "BREAK_DETECT",
                "FRAMING_ERROR",
                "PARITY_ERROR",
                "OVERRUN_ERROR",
                "DATA_READY",
            });
            break;
        case SET_MODEMSTATE_MASK:
        case SET_MODEMSTATE_MASK + SERVER_OFFSET:
        case NOTIFY_MODEMSTATE:
        case NOTIFY_MODEMSTATE + SERVER_OFFSET:
            if (len != 2)
                break;
            payload = decodeBits(data[1], new String[] {
                "CARRIER_DETECT",
                "RING_INDICATOR",
                "DSR",
                "CTS",
                "DELTA_CARRIER_DETECT",
                "TRAILING_EDGE_RING_DETECTOR",
                "DELTA_DSR",
                "DELTA_CTS",
            });
            break;
        case FLOWCONTROL_SUSPEND:
        case FLOWCONTROL_SUSPEND + SERVER_OFFSET:
        case FLOWCONTROL_RESUME:
        case FLOWCONTROL_RESUME + SERVER_OFFSET:
            payload = "";
            break;
        case PURGE_DATA:
        case PURGE_DATA + SERVER_OFFSET:
            if (len != 2)
                break;
            switch (data[1]) {
            case PURGE_DATA_RECEIVE_DATA_BUFFER:
                payload = "RECEIVE_DATA_BUFFER";
                break;
            case PURGE_DATA_TRANSMIT_DATA_BUFFER:
                payload = "TRANSMIT_DATA_BUFFER";
                break;
            case PURGE_DATA_BOTH_DATA_BUFFERS:
                payload = "BOTH_DATA_BUFFERS";
                break;
            default:
                break;
            }
            break;
        default:
            break;
        }
        if (payload == null)
            payload = rawBytes(data, off + 1, len - 1);
        String command = decodeComPortCommand(data[off]);
        return payload.length() > 0 ? command + " " + payload : command;
    }

    private static String decodeClientComPortCommand(int command) {
        switch (command) {
        case SIGNATURE:
            return "SIGNATURE";
        case SET_BAUDRATE:
            return "SET_BAUDRATE";
        case SET_DATASIZE:
            return "SET_DATASIZE";
        case SET_PARITY:
            return "SET_PARITY";
        case SET_STOPSIZE:
            return "SET_STOPSIZE";
        case SET_CONTROL:
            return "SET_CONTROL";
        case NOTIFY_LINESTATE:
            return "NOTIFY_LINESTATE";
        case NOTIFY_MODEMSTATE:
            return "NOTIFY_MODEMSTATE";
        case FLOWCONTROL_SUSPEND:
            return "FLOWCONTROL_SUSPEND";
        case FLOWCONTROL_RESUME:
            return "FLOWCONTROL_RESUME";
        case SET_LINESTATE_MASK:
            return "SET_LINESTATE_MASK";
        case SET_MODEMSTATE_MASK:
            return "SET_MODEMSTATE_MASK";
        case PURGE_DATA:
            return "PURGE_DATA";
        default:
            return null;
        }
    }

    private static String decodeBits(int value, String[] names) {
        ArrayList<String> list = new ArrayList<String>(8);
        for (int i = 0; i < 8; i++) {
            if ((value & (1 << (7 - i))) != 0)
                list.add(names[i]);
        }
        if (list.isEmpty())
            return "(none)";
        names = list.toArray(new String[list.size()]);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0)
                buf.append(' ');
            buf.append(names[i]);
        }
        return buf.toString();
    }

    private static String rawBytes(int[] data, int off, int len) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0)
                buf.append(' ');
            buf.append(String.format("0x%02x", data[off + i]));
        }
        return buf.toString();
    }
}

