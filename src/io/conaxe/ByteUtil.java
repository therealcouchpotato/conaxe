package io.conaxe;

public class ByteUtil {


    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static int unsign(byte signedByte)
    {
        return (int) signedByte&0xFF;
    }

    public static int bytesToInt(byte[] bytes)
    {
        int outInt = 0;
        for(int i =0; i < bytes.length; i++){
            outInt <<= 8;
            outInt ^= (long)bytes[i] & 0xFF;
        }
        return outInt;
    }

    public static String bytesRangeToString(byte[] bytes, int from, int to)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i=from;i<=to;i++)
            stringBuilder.append((char) bytes[i]);

        return stringBuilder.toString();
    }


}
