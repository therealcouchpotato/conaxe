package io.conaxe;

import gnu.io.*;

import java.io.*;
import java.util.Date;

import static io.conaxe.ByteUtil.bytesToHex;
import static io.conaxe.ByteUtil.unsign;

public class ConaxTransport {

    private final static int CONAX_CLASS = 0xDD;
    private final static int CC_GET_ANSWER = 0xCA;
    private final static String CONFIG_PORT = "port";
    private final static String CONFIG_BAUD = "baud";

    private DataInputStream serialInput;
    private DataOutputStream serialOutput;
    private Configuration configuration;
    private String port;
    private int baud;
    private final ConsoleOutput console;


    private SerialPort serialPort;


    public ConaxTransport(ConsoleOutput console, DataInputStream inputStream)
    {
        this.console = console;
        this.serialInput = inputStream;
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        this.serialOutput = new DataOutputStream(bas);
        this.serialPort = null;
    }

    public ConaxTransport(ConsoleOutput console, Configuration configuration) {
        this.console = console;
        this.configuration = configuration;
        this.port = configuration.getString(CONFIG_PORT);
        this.baud = configuration.getInteger(CONFIG_BAUD);
        this.serialPort = init();

    }

    private SerialPort init()
    {
        CommPortIdentifier portIdentifier = null;
        CommPort commPort = null;
        try {
            portIdentifier = CommPortIdentifier.getPortIdentifier(port);
            commPort = portIdentifier.open(this.getClass().getName(),2000);
        } catch (NoSuchPortException e){
            console.println("[ConaxTransport] unable to open port: "+port);
            System.exit(-1);
        } catch (PortInUseException e) {
            console.println("[ConaxTransport] Port is currently in use");
            System.exit(-1);
        }

        if ( commPort instanceof SerialPort )
        {
            SerialPort serialPort = (SerialPort) commPort;
            try {

                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                serialPort.setSerialPortParams(baud,SerialPort.DATABITS_8,SerialPort.STOPBITS_2,SerialPort.PARITY_EVEN);

            } catch (UnsupportedCommOperationException e)
            {
                console.println("[ConaxTransport] Unable to configure port to requested settings");
                System.exit(-1);
            }

            try {
                InputStream in = serialPort.getInputStream();
                OutputStream out = serialPort.getOutputStream();
                DataInputStream dataInput = new DataInputStream(in);
                DataOutputStream dataOutput = new DataOutputStream(out);

                this.serialInput = dataInput;
                this.serialOutput = dataOutput;

            } catch (IOException e)
            {
                console.println("[ConaxTransport] Unable to fetch streams");
                System.exit(-1);
            }

            return (SerialPort)commPort;
        }

        return null;
    }

    public void sendByte(int buffer) throws Exception
    {
        serialOutput.writeByte(buffer);
        serialInput.readByte(); // since we're a 1 wire setup we have to read back what we're written
    }

    public void send(byte[] buffer) throws Exception
    {
        serialOutput.write(buffer);
        int timeoutCounter = 0;
        while (serialInput.available() < buffer.length) {
            timeoutCounter++;
            Thread.sleep(10);
            if (timeoutCounter == 100) {
                reset();
                return;
            }
        }
        serialInput.read(buffer); // since we're a 1 wire setup we have to read back what we're written
    }

    public void reset()
    {
        try {
            if (serialInput.available() >0)
            {
                byte[] discard = new byte[serialInput.available()];
                serialInput.read(discard);
            }
        }catch (Throwable t)
        {
            console.println("[ConaxTransport] Unable to read interface");
            return;
        }
    }

    public void reinitialize() {

        this.serialPort.removeEventListener();
        this.serialPort.close();

        this.serialPort = init();
        console.println("[ConaxTransport] Serial interface reinitialized");
    }

    public byte[] readPacket() throws Exception
    {

        byte[] header = new byte[5];

        if (serialInput.available() < 5)
        {
            return null;
        }

        if (serialInput.read(header) != 5)
        {
            return null;
        }

        if (unsign(header[0]) != CONAX_CLASS)
        {
            console.println("[ConaxTransport] Beginning of a malformed Conax APDU received: "+header[0]);
            reset();
            return null;
        }

        // we have successfully read 5 bytes. now we must ACK the instruction byte
        sendByte(header[1]);


        // we are now ready to read the command or answer to a get-answer command

        int length = unsign(header[4]); //last byte in the 5-byte header is data length

        if (unsign(header[1]) == CC_GET_ANSWER) // CA command is special, the length field is for the data requested from the card
                                                // not for the amount of the data the cam sends
        {
            // let's adjust the protocol
            // this bypasses the need for priority treatment of this special case throughout the app
            byte[] resultPacket = new byte[header.length+1];
            System.arraycopy(header,0,resultPacket,0,header.length);
            resultPacket[resultPacket.length-1] = (byte)length;
            resultPacket[resultPacket.length-2] = 1;

            return resultPacket;

        }

        byte[] data = new byte[length];


        int timeoutCounter = 0;
        while( serialInput.available() < data.length )
        {
            Thread.sleep(10);
            timeoutCounter++;
            if (timeoutCounter == 100)
            {
                console.println("[ConaxTransport] Timed out waiting for command..");
                console.println("[ConaxTransport] header:"+bytesToHex(header));
                console.println("[ConaxTransport] data:"+bytesToHex(data));
                reset();
                return null;
            }
        }
        serialInput.read(data);

        byte[] resultPacket = new byte[header.length+data.length];
        System.arraycopy(header,0,resultPacket,0,header.length);
        System.arraycopy(data,0,resultPacket,header.length,data.length);

        return resultPacket;
    }

    public SerialPort getSerialPort() {
        return serialPort;
    }


}
