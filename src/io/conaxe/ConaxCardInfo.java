package io.conaxe;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class ConaxCardInfo {
	
	private final static String CONFIG_ATR = "atr";
	private final static String CONFIG_CARD_SERIAL="cardSerial";
    private final static String CONFIG_GROUP_SERIAL="groupSerial";
	private final static String CONFIG_LANGUAGE_ID="languageId";
	private final static String CONFIG_SYSTEM_ID="systemId";
	private final static String CONFIG_INTERFACE_VERSION="interfaceVersion";
	private final static String CONFIG_RESTRICTION_LEVEL ="restrictionLevel";
	private final static String CONFIG_CARD_SESSIONS ="sessions";
    private final static String CONFIG_CARD_PIN = "cardPin";

	private byte[] atr;
	private byte[] cardSerial;
    private byte[] groupSerial;
	private byte[] languageId;
    private byte[] systemId;
	private byte interfaceVersion;
	private byte restrictionLevel;
	private byte sessions;
    private String cardPin;
    private List<Subscription> subscriptions;
    private final ConsoleOutput console;
			
	public ConaxCardInfo(ConsoleOutput console, Configuration config)
	{
        this.console = console;
		this.atr = config.getByteArray(CONFIG_ATR);
		this.cardSerial = config.getByteArray(CONFIG_CARD_SERIAL);
        this.groupSerial = config.getByteArray(CONFIG_GROUP_SERIAL);
		this.languageId = config.getByteArray(CONFIG_LANGUAGE_ID);
        this.systemId = config.getByteArray(CONFIG_SYSTEM_ID);
		this.sessions = config.getByte(CONFIG_CARD_SESSIONS);
		this.restrictionLevel = config.getByte(CONFIG_RESTRICTION_LEVEL);
		this.interfaceVersion = config.getByte(CONFIG_INTERFACE_VERSION);
        this.cardPin = config.getString(CONFIG_CARD_PIN);

        this.subscriptions = loadSubscriptions(config);

	}

    private List<Subscription> loadSubscriptions(Configuration config) {
        final String SUB = "subscription";
        List<Subscription> subscriptions = new LinkedList<Subscription>();

        int subscriptionCount = config.getInteger("subscriptions");
        for (int i = 1;i<=subscriptionCount;i++)
        {
            String subIndex = String.valueOf(i);
            Subscription subscription = new Subscription();
            subscription.setName(config.getString(SUB+subIndex+".name"));
            if (null == subscription.getName())
            {
                console.println("[Config] Error parsing name for subscription"+String.valueOf(i));
                System.exit(-1);

            }
            int recordCount = config.getInteger(SUB + subIndex + ".records");
            for (int j = 1;j<=recordCount;j++)
            {
                SubscriptionRecord record = new SubscriptionRecord();
                String recIndex = String.valueOf(j);

                record.setId(config.getByteArray(SUB+subIndex+"."+recIndex+".id"));
                record.setValidity(config.getString(SUB+subIndex+"."+recIndex+".validity").toLowerCase());
                if (record.getValidity().equals("static")) {
                    DateFormat validityDateFormat = new SimpleDateFormat("yyyy.MM.dd");
                    String validFrom = config.getString(SUB+subIndex+"."+recIndex+".validFrom");
                    String validTo = config.getString(SUB+subIndex+"."+recIndex+".validTo");
                    try {
                        record.setValidFrom(validityDateFormat.parse(validFrom));
                        record.setValidTo(validityDateFormat.parse(validTo));
                    } catch (Throwable t) {
                        console.println("[Config] Error parsing validity date. Format should be yyyy.mm.dd, got:"+validFrom+" and "+validTo);
                        System.exit(-1);
                    }

                } else if (record.getValidity().equals("rolling")) {
                    // TODO: implement, though turns out this actually isn't used for anything
		    // so this would merely be a cosmetic feature
                } else {
                    console.println("[Config] Validity types accepted: static, rolling. Found:"+record.getValidity());
                    System.exit(-1);
                }
                subscription.getRecords().add(record);
            }
            subscriptions.add(subscription);
        }
        return subscriptions;
    }


    public byte[] getATR()
	{
		return atr;
	}
	
	public byte[] getCardSerial()
	{
		return cardSerial; 
	}
	
	public byte[] getLanguageId()
	{
		return languageId;
	}

    public byte[] getSystemId()
    {
        return systemId;
    }
	
	public byte getSessions()
	{
		return sessions;
	}
	
	public byte getRestrictionLevel()
	{
		return restrictionLevel;
	}
	
	public byte getInterfaceVersion()
	{
		return interfaceVersion;
	}

    public byte[] getGroupSerial() {
        return groupSerial;
    }


    public String getCardPin() {
        return cardPin;
    }

    public static class Subscription {

        private String name;
        List<SubscriptionRecord> records = new LinkedList<SubscriptionRecord>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<SubscriptionRecord> getRecords() {
            return records;
        }

        public void setRecords(List<SubscriptionRecord> records) {
            this.records = records;
        }
    }

    public List<Subscription> getSubscriptions()
    {
        return subscriptions;
    }

    public byte[] conaxEncodeDate(Date date)
    {
        /*

           Conax date encoding is 2 bytes. Let's name them 1 and 2
           Day is the lower 4 bits of byte 2
           Month is the lower 5 bits of byte 1
           Year is the number of years since 1990 encoded on:
            the upper 3 bits of byte 1 (A) followed by
            the upper 4 bits of byte 2 (B) for a total of 7 bits
           Year is A*10 + B

           2013.03.01
           0x4133

           0x41     0x33
           01000001 00110011
           AAAmmmmm BBBBdddd
           A: b010 = 2 * 10 = 20
           B: b0011 = 3
           y = 20+3 = 23 .. 1990+23 = 2013

        */

        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH)+1;
        int day = calendar.get(calendar.DAY_OF_MONTH);

        int A = ((year-1990)/10) & 7;
        int B = (year-1990)-A*10;
        int conaxDate = 0;
        conaxDate = month;
        conaxDate |= day << 8;
        conaxDate |= A << 13;
        conaxDate |= B << 4;

        return ByteBuffer.allocate(2).putShort((short)conaxDate).array();

    }



    public static class SubscriptionRecord
    {
        private byte[] id;
        private String validity;
        private Date validFrom;
        private Date validTo;

        public byte[] getId() {
            return id;
        }

        public void setId(byte[] id) {
            this.id = id;
        }

        public String getValidity() {
            return validity;
        }

        public void setValidity(String validity) {
            this.validity = validity;
        }

        public Date getValidFrom() {
            return validFrom;
        }

        public void setValidFrom(Date validFrom) {
            this.validFrom = validFrom;
        }

        public Date getValidTo() {
            return validTo;
        }

        public void setValidTo(Date validTo) {
            this.validTo = validTo;
        }
    }
}
