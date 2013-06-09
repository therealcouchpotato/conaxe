package io.conaxe;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class Configuration {
    private Properties configFile;


    public Configuration() {
        this.configFile = new Properties();
    }

    public void init(InputStream stream) throws IOException{
        this.configFile.load(stream);
    }

    public void init(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        this.configFile.load(fis);
    }

    public String getString(String key)
    {
        return this.configFile.getProperty(key);
    }

    public int getInteger(String key)
    {
        try {
            return Integer.parseInt(this.configFile.getProperty(key));
        } catch (Throwable t)
        {
            return 0;
        }
    }
    
    public byte getByte(String key)
    {
        String s = this.configFile.getProperty(key);

        if (s.startsWith("0x"))
        try{
            s = s.substring(2);
            return Byte.parseByte(s,16);
        } catch (Throwable t)
        {
            return 0;
        }
        else return Byte.parseByte(s);
    }
    
    public long getLong(String key)
    {
        try {
            return Long.parseLong(this.configFile.getProperty(key));
        } catch (Throwable t)
        {
            return 0;
        }
    }
    
    
    public byte[] getByteArray(String key) {
    	String s = this.configFile.getProperty(key);

        if (s.startsWith("0x"))
            s = s.substring(2);

	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}

}
