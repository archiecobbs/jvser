**jvser** (Java Virtual Serial Port) is a Java library implementing the client side of the [RFC 2217](http://tools.ietf.org/html/rfc2217) protocol.

RFC 2217 defines a TCP protocol for virtualizing traditional serial ports over a telnet stream. This allows clients to connect to remote serial lines using access servers that support this protocol.

**jvser** provides the [TelnetSerialPort](http://archiecobbs.github.io/jvser/publish/reports/javadoc/index.html?org/dellroad/jvser/TelnetSerialPort.html) class which implements the Java standard [javax.comm.SerialPort](http://download.oracle.com/docs/cd/E17802_01/products/products/javacomm/reference/api/javax/comm/SerialPort.html) API, and so is backward compatible with existing Java software that works with serial ports.

**License**

**jvser** is licensed under either [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) or [LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html).
