package io.conaxe;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;

// implements the UDP Camd35 protocol

public class Camd35Gateway implements CardServerGateway {

    private final static String CONFIG_ECM_TIMEOUT = "camd35.ecmTimeout";
    public final static String CONFIG_HOST = "camd35.host";
    public final static String CONFIG_PORT = "camd35.port";
    public final static String CONFIG_USER = "camd35.user";
    public final static String CONFIG_PASSWORD = "camd35.password";
    public final static String CONFIG_PRID = "camd35.prid";
    public final static String CONFIG_SRVID = "camd35.srvid";
    public final static String CONFIG_CAID = "camd35.caid";
    public final static String CONFIG_SEND_EMM = "camd35.sendEMM";

    private byte[] srvid;
    private byte[] prid;
    private byte[] caid;

    private final static int REQ_SIZE = 512+20+0x34; // as defined in OSCAM

    private final static int COMMAND_ECM = 0x00;
    private final static int RESPONSE_ECM = 0x01;
    private final static int COMMAND_EMM = 0x06;
    private final static int RESPONSE_STOP = 0x08; // stop sending for this provider/card/service because I don't
                                                   // have a card for it
    private final static int RESPONSE_EMM_REQUEST = 0x05; // card server is specifying what unique and group serials
                                                          // it is expecting EMMs for

    private final String host;
    private final String port;

    private final String username;
    private final String password;

    private boolean sendEMM;

    private int ecmTimeout;
    private int token;
    Cipher packetEncrypter;
    Cipher responseDecrypter;

    private InetAddress cardServerAddress;
    private DatagramSocket cardServerSocket;

    private final ConsoleOutput console;


    public Camd35Gateway(ConsoleOutput console, Configuration configuration) {
        this.console = console;
        this.host = configuration.getString(CONFIG_HOST);
        this.port = configuration.getString(CONFIG_PORT);
        this.username = configuration.getString(CONFIG_USER);
        this.password = configuration.getString(CONFIG_PASSWORD);
        this.caid = configuration.getByteArray(CONFIG_CAID);
        this.prid = configuration.getByteArray(CONFIG_PRID);
        this.srvid = configuration.getByteArray(CONFIG_SRVID);
        this.sendEMM = configuration.getByte(CONFIG_SEND_EMM)>0;
        this.ecmTimeout = configuration.getInteger(CONFIG_ECM_TIMEOUT);

        token = makeToken(username);

        packetEncrypter = initEncrypter(password);
        responseDecrypter = initDecrypter(password);

        try {
            cardServerSocket = new DatagramSocket();
            cardServerSocket.setSoTimeout(ecmTimeout);

            cardServerAddress = InetAddress.getByName(host);
        } catch (Throwable t){
            console.println("[Camd35Gateway] Error initializing DatagramSocket");
            System.exit(-1);
        }



    }

    private Cipher initDecrypter(String password) {
        MessageDigest md5er = null;
        try {
            md5er = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            console.println("[Camd35Gateway] Java environment doesn't supply MD5 hasher");
            System.exit(-1);
        }

        Cipher c = null;
        try {
            c = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (Exception e) {
            console.println("[Camd35Gateway] Java environment doesn't supply AES/ECB/NoPadding");
            System.exit(-1);
        }

        SecretKeySpec k = new SecretKeySpec(md5er.digest(password.getBytes()), "AES");

        try {
            c.init(Cipher.DECRYPT_MODE,k);
        } catch (InvalidKeyException e) {
            System.exit(-1);
        }

        return c;

    }

    private DatagramPacket newDatagram(byte[] buf)
    {
        return new DatagramPacket(buf,buf.length,cardServerAddress, Integer.parseInt(port));
    }

    private Cipher initEncrypter(String password) {
        MessageDigest md5er = null;
        try {
            md5er = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            console.println("[Camd35Gateway] Java environment doesn't supply MD5 hasher");
            System.exit(-1);
        }

        Cipher c = null;
        try {
            c = Cipher.getInstance("AES");
        } catch (Exception e) {
            console.println("[Camd35Gateway] Java environment doesn't supply AES");
            System.exit(-1);
        }

        SecretKeySpec k = new SecretKeySpec(md5er.digest(password.getBytes()), "AES");

        try {
            c.init(Cipher.ENCRYPT_MODE,k);
        } catch (InvalidKeyException e) {
            System.exit(-1);
        }

        return c;
    }

    private int makeToken(String username) {
        MessageDigest md5er = null;
        try {
            md5er = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            console.println("[Camd35Gateway] Java environment doesn't supply MD5 hasher");
            System.exit(-1);
        }

        byte[] userHash = md5er.digest(username.getBytes());
        CRC32 crc32 = new CRC32();
        crc32.update(userHash);

        return (int)crc32.getValue()&0xFFFFFFFF;
    }

    public void doEMM(byte[] emmPacket)
    {
        if (!sendEMM) return;

        CRC32 crc32 = new CRC32();
        crc32.update(emmPacket);

        byte[] emmRequest = new byte[REQ_SIZE];
        ByteBuffer emmRequestBuffer = ByteBuffer.wrap(emmRequest);

        emmRequestBuffer.put((byte)COMMAND_EMM);
        emmRequestBuffer.put((byte)emmPacket.length); // packet length
        emmRequestBuffer.put((byte)0xFF);
        emmRequestBuffer.put((byte) 0xFF);
        emmRequestBuffer.putInt((int) crc32.getValue() & 0xFFFFFFFF);
        emmRequestBuffer.put(srvid);  //2 bytes
        emmRequestBuffer.put(caid);   //2 bytes
        emmRequestBuffer.put(prid);   //4 bytes

        emmRequestBuffer.put((byte)0x00);
        emmRequestBuffer.put((byte)0x00);
        emmRequestBuffer.put((byte)0x00);
        emmRequestBuffer.put((byte)0x00);

        emmRequestBuffer.put(emmPacket);


        try {
            byte[] encryptedCommand = packetEncrypter.doFinal(emmRequestBuffer.array());
            byte[] encryptedCommandPacket = new byte[4+encryptedCommand.length];
            System.arraycopy(ByteBuffer.allocate(4).putInt(token).array(), 0, encryptedCommandPacket, 0, 4);
            System.arraycopy(encryptedCommand, 0, encryptedCommandPacket, 4, encryptedCommand.length);

            cardServerSocket.send(newDatagram(encryptedCommandPacket));
        } catch (IOException e) {
            console.println("[Camd35Gateway] Error sending EMM");

        } catch (IllegalBlockSizeException e) {
            console.println("[Camd35Gateway] Error encrypting EMM command packet");

        } catch (BadPaddingException e) {
            console.println("[Camd35Gateway] Error encrypting EMM command packet");

        }


    }

    public byte[] decodeECM(byte[] ecmPacket)
    {

        CRC32 crc32 = new CRC32();
        crc32.update(ecmPacket);

        byte[] ecmRequest = new byte[REQ_SIZE];

        ByteBuffer ecmRequestBuffer = ByteBuffer.wrap(ecmRequest);
        ecmRequestBuffer.put((byte) COMMAND_ECM);
        ecmRequestBuffer.put((byte) 0xFF);  // ignored now because of the size optimization
        ecmRequestBuffer.put((byte) 0xFF);
        ecmRequestBuffer.put((byte) 0xFF);
        ecmRequestBuffer.putInt((int) crc32.getValue() & 0xFFFFFFFF);

        ecmRequestBuffer.put(srvid);  //2 bytes
        ecmRequestBuffer.put(caid);   //2 bytes
        ecmRequestBuffer.put(prid);   //4 bytes

        ecmRequestBuffer.put((byte)0x00);
        ecmRequestBuffer.put((byte)0x00);
        ecmRequestBuffer.put((byte)0x00);
        ecmRequestBuffer.put((byte)0x00);

        ecmRequestBuffer.put(ecmPacket);

        try {
            byte[] encryptedCommand = packetEncrypter.doFinal(ecmRequestBuffer.array());
            byte[] encryptedCommandPacket = new byte[4+encryptedCommand.length];
            System.arraycopy(ByteBuffer.allocate(4).putInt(token).array(), 0, encryptedCommandPacket, 0, 4);
            System.arraycopy(encryptedCommand, 0, encryptedCommandPacket, 4, encryptedCommand.length);

            cardServerSocket.send(newDatagram(encryptedCommandPacket));

        } catch (IOException e) {
            console.println("[Camd35Gateway] Error sending ECM");
            return null;
        } catch (IllegalBlockSizeException e) {
            console.println("[Camd35Gateway] Error encrypting ECM command packet");
            return null;
        } catch (BadPaddingException e) {
            console.println("[Camd35Gateway] Error encrypting ECM command packet");
            return null;
        }

        byte[] controlWord = new byte[16];

        byte[] decrypted = receiveResponse();

        if (decrypted == null)
        {
            return controlWord; // we've timed out or something. return a fake in hopes of getting it right next time
        }

        int badResponseCount=0;

        while (decrypted[0] != RESPONSE_ECM) {
            badResponseCount++;
            if (badResponseCount >2) {  // at most before ECM response we should only encounter 2 EMM_REQUESTs
                return controlWord;           // if we're still not getting the ECM response we have a problem.
            }

            decrypted = receiveResponse();
            if (decrypted == null)
            {
                return controlWord; // we've timed out or something. return a fake in hopes of getting it right next time
            }
        }

        System.arraycopy(decrypted,20,controlWord,0,16);
        return controlWord;

    }

    private byte[] receiveResponse() {
        byte[] response = new byte[REQ_SIZE];
        DatagramPacket responsePacket = new DatagramPacket(response,response.length);
        byte[] decrypted = null;

        try {

            cardServerSocket.receive(responsePacket);
            byte[] responseBody = new byte[responsePacket.getLength()-4];
            System.arraycopy(responsePacket.getData(),4,responseBody,0,responsePacket.getLength()-4);

            try {
                decrypted = responseDecrypter.doFinal(responseBody);
            } catch (IllegalBlockSizeException e) {
                console.println("[Camd35Gateway] Error decrypting response");

            } catch (BadPaddingException e) {
                console.println("[Camd35Gateway] Error decrypting response");

            }

        } catch (SocketTimeoutException e) {
            console.println("[Camd35Gateway] Server took too long to respond");


        } catch (IOException e) {
            console.println("[Camd35Gateway] Could not initiate UDP receive for response");

        }

        return decrypted;
    }

}
