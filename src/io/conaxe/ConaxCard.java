package io.conaxe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static io.conaxe.ByteUtil.*;

//
// the emulated card class
//
public class ConaxCard {

    private boolean DEBUG=false;

    private boolean initial = true;
    private ByteArrayQueue answerQueue;

    private final static int MATURITY_OVER_18 = 0x4;


    private final static int CC_CARD_RECORD_READ = 0x26;       // read records from the card
    private final static int CC_CARD_IDENTIFY = 0x82;   // read identity IDs from the card (this is probably more like a handshake than a read command)
    private final static int CC_ECM = 0xA2;             // ECM message
    private final static int CC_EMM = 0x84;             // EMM message
    private final static int CS_CARD_READ_ENTITLEMENTS = 0xC6; // read subscription records from card
    private final static int CC_CARD_READ_SERIAL = 0xC2;  // gets the card serial number
    private final static int CC_GET_ANSWER = 0xCA; // drain answer buffer
    private final static int CC_MANAGE_MATURITY_RESTRICTION = 0xC8; // maturity restriction controls

    private final static int CC_RESPONSE_ACK_EMPTY = 0x90;
    private final static int CC_RESPONSE_ACK_DATA = 0x98;

    private final static int NANO_RESTRICTION_LEVEL = 0x30;
    private final static int NANO_SESSIONS = 0x23;

    private final static int NANO_EMM = 0x12;
    private final static int NANO_ECM = 0x14;
    private final static int NANO_ECM_RESPONSE_KEY = 0x25;
    private final static int NANO_ECM_RESPONSE_STATE = 0x31;

    private final static byte[] NANOPACKET_ECM_OK = new byte[]{0x40,0x00};
    private final static byte[] NANOPACKET_ECM_NO_ACCESS = new byte[]{0x00,00};
    private final static int ERROR_ECM_NO_ACCESS = 0x12;
    private final static byte[] NANOPACKET_ECM_RESTRICTED_AUTHENTICATE = new byte[]{0x02,0x00,0x09};

    private final static int NANO_CARD_OPEN_RESTRICTED_CHANNEL = 0x1D;
    private final static int NANO_CARD_SET_RESTRICTION = 0x1F;
    private final static int NANO_CARD_CHANGE_PIN = 0x1E;
    private final static int ERROR_WRONG_PIN = 0x17;

    private final static int NANO_CAM_IDENTITY_RECORD = 0x11;
    private final static int NANO_CARD_IDENTITY_RECORD = 0x22;
    private final static int NANO_CAM_ID = 0x9;
    private final static int NANO_SERIAL = 0x23;
    private final static int NANO_CARD_SERIAL = 0x74;

    private final static int NANO_INTERFACE_VERSION= 0x20;
    private final static int NANO_SYSTEM_ID = 0x28;
    private final static int NANO_LANGUAGE_ID= 0x2F;

    private final static int NANO_ENTITLEMENT_BANK = 0x1C;
    private final static int NANO_ENTITLEMENT = 0x32;
    private final static int NANO_ENTITLEMENT_SUBRECORD = 0x10;

    private final static int NANO_ENTITLEMENT_PROVIDER_NAME = 0x1;
    private final static int NANO_ENTITLEMENT_PROVIDER_CLASS = 0x20;
    private final static int NANO_ENTITLEMENT_DATE =0x30;


    private final ConaxTransport transport;
    private final ConaxCardInfo cardInfo;
    private final ConaxNanoAssembler nanoAssembler;
    private final ConaxNanoDissector nanoDissector;
    private final ResetWatcher resetWatcher;

    private long ecmCounter= 0;
    private long emmCounter= 0;
    private long commandCounter = 0;

    private CardServerGateway cardServerGateway;
    
    private final ConsoleOutput console;
    


    public ConaxCard(ConsoleOutput console, ConaxTransport transport, ConaxCardInfo cardInfo, ConaxNanoAssembler assembler, ConaxNanoDissector dissector,
                     CardServerGateway cardServerGateway, ResetWatcher resetWatcher) {
        this.console = console;
        this.transport = transport;
        this.cardInfo = cardInfo;
        this.cardServerGateway = cardServerGateway;
        this.resetWatcher = resetWatcher;
        answerQueue = new ByteArrayQueue(254);


        nanoAssembler = assembler;
        nanoDissector = dissector;

    }


    public void runCard()
    {
	byte packet[] = null;

        while (true) {

            try {Thread.sleep(10);}catch (Throwable t){}

            if (resetWatcher.isReset()) {

                try {
                    answerQueue.clear();
                    transport.reset();
                    try {Thread.sleep(10);} catch (Throwable t){}
                    transport.send(cardInfo.getATR());
                    console.println("RESET! ATR Sent..");
                } catch (Throwable t) {
                    console.println("[ConaxCard] Failed to send reset");
                }

            }
            packet = null;

            try {
                packet = transport.readPacket();
            } catch (Throwable t){
                transport.reset();
                console.println("[ConaxCard] packet read failure");
            }

            if (null != packet)
            {

                ConaxCommand command = ConaxCommandDissector.dissect(packet);
                handleCommand(command);

            }

        }
    }

    private void handleCommand(ConaxCommand command)
    {
        int errorCode = 0;

        if (DEBUG) console.println("[ConaxCard] instruction:"+Integer.toHexString(command.getInstruction()));
        if (DEBUG) console.println("[ConaxCard] packet: " + bytesToHex(command.getPacket()));
        switch (command.getInstruction())
        {
            case CC_GET_ANSWER:

                if (DEBUG) console.println("CC_REQ_ANSWER");

                int length = unsign(command.getPacket()[0]);

                byte[] answer = new byte[length];
                answerQueue.remove(answer);

                if (DEBUG) console.println("[ConaxCard] sending answer:" + bytesToHex(answer));

                try {
                    transport.send(answer);
                } catch (Throwable t) {

                    console.println("[ConaxCard] error sending response.");
                    return;
                }

                break;

            case CC_CARD_IDENTIFY:
                if (DEBUG) console.println("CC_CARD_IDENTIFY");

                List<NanoPacket> camIdentity = null;
                try {
                    camIdentity = nanoDissector.dissect(command.getPacket());
                } catch (Exception e)
                {
                    console.println("[ConaxCard] Unable to process CAM identity record.");
                    return;
                }
                if ((null == camIdentity) | (camIdentity.size() >1))
                {
                    console.println("[ConaxCard] Unknown cam identity record format detected. More records");
                    return;
                }
                NanoPacket camIdentityNano =  camIdentity.get(0);
                if (camIdentityNano.getNano() != NANO_CAM_IDENTITY_RECORD)
                {
                    console.println("[ConaxCard] Unknown cam identity record format detected. Unknown id");
                    return;
                }

                byte[] camIdentityNanoData = camIdentityNano.getData();
                byte[] camSerial = Arrays.copyOfRange(camIdentityNanoData,0,7);
                byte[] camIdSubNanoHeader = Arrays.copyOfRange(camIdentityNanoData,8,10);
                byte[] camId = null;
                if ((unsign(camIdSubNanoHeader[0]) == 9) && (unsign(camIdSubNanoHeader[1]) == 4))
                {
                    camId = Arrays.copyOfRange(camIdentityNanoData,10,14);
                } else {
                    console.println("[ConaxCard] Unknown cam identity record format. Could not extract Cam ID");
                    return;
                }

                List<NanoPacket> identity = new LinkedList<NanoPacket>();

                identity.add(new NanoPacket(NANO_CAM_ID, camId));
                identity.add(new NanoPacket(NANO_SERIAL, cardInfo.getCardSerial()));
                identity.add(new NanoPacket(NANO_SERIAL, cardInfo.getGroupSerial()));

                answerQueue.add(nanoAssembler.assemble(NANO_CARD_IDENTITY_RECORD, nanoAssembler.assemble(identity)));

            break;

            case CC_CARD_RECORD_READ:

                if (DEBUG) console.println("CC_CARD_RECORD_READ");
                byte[] readParameter = command.getPacket();

                if (readParameter.length <=4)
                {
                    if (DEBUG) console.println("CC_CARD_RECORD_READ: param len ok");
                    int readParamInt = bytesToInt(readParameter);

                    if (DEBUG) console.println("CC_CARD_RECORD_READ: param 0x"+Integer.toHexString(readParamInt));
                    switch (readParamInt)
                    {
                        case 0x100140:      // this might be something like Class 10 / Record 40
                            if (DEBUG) console.println("CC_CARD_RECORD_READ: handle 0x100140");
                            List<NanoPacket> cardInfos = new LinkedList<NanoPacket>();

                            cardInfos.add(new NanoPacket(NANO_INTERFACE_VERSION, new byte[]{cardInfo.getInterfaceVersion()}));
                            cardInfos.add(new NanoPacket(NANO_SYSTEM_ID, cardInfo.getSystemId()));
                            cardInfos.add(new NanoPacket(NANO_LANGUAGE_ID, cardInfo.getLanguageId()));
                            cardInfos.add(new NanoPacket(NANO_RESTRICTION_LEVEL, new byte[]{cardInfo.getRestrictionLevel()}));
                            cardInfos.add(new NanoPacket(NANO_SESSIONS, new byte[]{cardInfo.getSessions()}));

                            answerQueue.add(nanoAssembler.assemble(cardInfos));


                        break;

                        // pairing mode requests..
                        case 0x6C021000:
                        case 0x6C021001:
                            console.println("[ConaxCard] WARNING! Box is requesting \"Type B\" pairing. Not supported!");
                            console.println("[ConaxCard] !! CHANNELS WILL NOT WORK !!");
                        break;
                        case 0x690100:

                        break;
                    }

                } else {
                    console.println("[ConaxCard] protocol panic. CC_CARD_READ_RECORD received unknown parameter");
                    return;
                }

            break;

            case CC_CARD_READ_SERIAL:
                if (DEBUG) console.println("CC_CARD_READ_SERIAL");

                byte[] serialReadParameter = command.getPacket();
                if (serialReadParameter.length != 2)
                {
                    console.println("[ConaxCard] protocol panic. CC_CARD_READ_SERIAL received unknown parameter");
                    return;
                }

                int serialReadParamInt = bytesToInt(serialReadParameter);

                switch (serialReadParamInt)
                {
                    case 0x6600:
                        List<NanoPacket> serialRead = new LinkedList<NanoPacket>();
                        byte[] cardSerial = cardInfo.getCardSerial();
                        byte[] shortCardSerial = Arrays.copyOfRange(cardSerial,cardSerial.length-4,cardSerial.length);
                        serialRead.add(new NanoPacket(NANO_CARD_SERIAL,shortCardSerial));
                        answerQueue.add(nanoAssembler.assemble(serialRead));
                    break;
                    default:
                        console.println("[ConaxCard] protocol panic. CC_CARD_READ_SERIAL received unknown parameter");
                        return;
                }

                break;

            case CS_CARD_READ_ENTITLEMENTS:

                if (DEBUG) console.println("CS_CARD_READ_ENTITLEMENTS");

                // we only emulate a single entitlement bank. It's only for show anyway

                List<NanoPacket> entitlementRequest = null;
                try {
                    entitlementRequest =  nanoDissector.dissect(command.getPacket());
                    if (entitlementRequest.size() > 1)
                    {
                        console.println("[ConaxCard] Too many nanos in entitlements request");
                    } else if (entitlementRequest.size() < 1 ){
                        console.println("[ConaxCard] No nanos in entitlements request");
                        break;
                    }

                } catch (Throwable t)
                {
                    console.println("[ConaxCard] Error dissecting entitlements request");
                    return;
                }

                NanoPacket bankRequest = nanoDissector.fetch(NANO_ENTITLEMENT_BANK,entitlementRequest);
                if (!(bankRequest != null && unsign(bankRequest.getData()[0]) == 0))
                {
                    // we only handle bank 0
                    // not sure if the bank paramater is optional or not. needs to be changed if so
                    break;
                }

                // let's see..
                List<NanoPacket> entitlementNanos = new LinkedList<NanoPacket>();

                int subRecordId = 0x10;         // might be an offset instead of an index? oh well
                for (ConaxCardInfo.Subscription subscription : cardInfo.getSubscriptions())
                {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    DataOutputStream entitlement = new DataOutputStream(out);

                    try {
                        entitlement.writeByte(NANO_ENTITLEMENT_SUBRECORD);
                        entitlement.writeByte(subRecordId);
                        entitlement.writeByte(NANO_ENTITLEMENT_PROVIDER_NAME);
                        entitlement.writeByte(0xF);
                        entitlement.write(String.format("%1$-" + 15 + "s", subscription.getName()).getBytes());


                        for (ConaxCardInfo.SubscriptionRecord record: subscription.getRecords())
                        {
                            entitlement.writeByte(NANO_ENTITLEMENT_DATE);
                            entitlement.writeByte(2);
                            entitlement.write(cardInfo.conaxEncodeDate(record.getValidFrom()));
                            entitlement.writeByte(NANO_ENTITLEMENT_DATE);
                            entitlement.writeByte(2);
                            entitlement.write(cardInfo.conaxEncodeDate(record.getValidTo()));
                            entitlement.writeByte(NANO_ENTITLEMENT_PROVIDER_CLASS);
                            entitlement.writeByte(4);
                            entitlement.write(record.getId());
                        }


                    } catch (Throwable t) {

                    }
                    entitlementNanos.add(new NanoPacket(NANO_ENTITLEMENT,out.toByteArray()));

                    subRecordId+=0x10;
                }

                answerQueue.add(nanoAssembler.assemble(entitlementNanos));


            break;
            case CC_ECM:

                if (DEBUG) console.println("CC_ECM");

                ecmCounter++;
                List<NanoPacket> ecmRequestNanos = null;
                try {
                    ecmRequestNanos = nanoDissector.dissect(command.getPacket());
                } catch (Exception e) {
                    console.println("[ConaxCard] Error dissecting ECM packet");
                    return;
                }
                NanoPacket ecm = nanoDissector.fetch(NANO_ECM,ecmRequestNanos);
                if (ecm != null && ecm.getData()[0] != 0)
                {
                    console.println("[ConaxCard] Box is paired(?) Bailing");
                    return;
                }

                byte[] ecmPacket = new byte[ecm.getData().length-1];             // remove leading null
                System.arraycopy(ecm.getData(),1,ecmPacket,0,ecmPacket.length);

                byte[] controlWord = cardServerGateway.decodeECM(ecmPacket);

                if (null == controlWord)
                {

                    answerQueue.add(nanoAssembler.assemble(NANO_ECM_RESPONSE_STATE,NANOPACKET_ECM_NO_ACCESS));
                    errorCode = ERROR_ECM_NO_ACCESS;
                    break;

                } else {

                    List<NanoPacket> ecmReply = new LinkedList<NanoPacket>();
                    byte[] cw1 = new byte[0xd];
                    byte[] cw2 = new byte[0xd];
                    System.arraycopy(controlWord,8,cw1,5,8);
                    System.arraycopy(controlWord,0,cw2,5,8);
                    cw1[2]= 1;      // key index

                    ecmReply.add(new NanoPacket(NANO_ECM_RESPONSE_KEY,cw1));
                    ecmReply.add(new NanoPacket(NANO_ECM_RESPONSE_KEY,cw2));
                    ecmReply.add(new NanoPacket(NANO_ECM_RESPONSE_STATE,NANOPACKET_ECM_OK));

                    answerQueue.add(nanoAssembler.assemble(ecmReply));

                }

            break;

            case CC_EMM:
                if (DEBUG) console.println("CC_EMM");

                List<NanoPacket> emmRequestNanos = nanoDissector.dissect(command.getPacket());

                if (emmRequestNanos.size() != 1)
                {
                    console.println("[ConaxCard] Received EMM with multiple nano params. Unknown!");
                    return;
                }

                NanoPacket emm = nanoDissector.fetch(NANO_EMM,emmRequestNanos);

                if (emm!=null)
                {
                    cardServerGateway.doEMM(emm.getData());
                }

                emmCounter++;
            break;

            case CC_MANAGE_MATURITY_RESTRICTION:

                List<NanoPacket> manageRestriction;

                try {
                    manageRestriction = nanoDissector.dissect(command.getPacket());
                } catch (Exception e) {
                    console.println("[ConaxCard] Error dissecting manage restriction packet");
                    break;
                }
                if (manageRestriction.size() != 1) {
                    console.println("[ConaxCard] Unknown format for manage restriction packet");
                    break;
                }

                NanoPacket manageRestrictionNano = manageRestriction.get(0);

                switch (manageRestrictionNano.getNano())
                {
                    case NANO_CARD_SET_RESTRICTION:

                        byte[] setData= manageRestrictionNano.getData();

                        int newLevel = setData[4];

                        String setPin = bytesRangeToString(setData,0,3);

                        if (!setPin.toString().equals("4444")) errorCode = ERROR_WRONG_PIN;

                        if (DEBUG) console.println("[ConaxCard] Set restriction with PIN:"+setPin+ " to level "+newLevel);

                    break;

                    case NANO_CARD_OPEN_RESTRICTED_CHANNEL:

                        byte[] openData= manageRestrictionNano.getData();

                        int requestedLevel = openData[0];

                        String openPin = bytesRangeToString(openData,1,4);

                        if (!openPin.equals("4444")) errorCode = ERROR_WRONG_PIN;
                        if (DEBUG) console.println("[ConaxCard] Request restricted access with PIN:"+openPin.toString()+ " level:"+requestedLevel);

                    break;
                    case NANO_CARD_CHANGE_PIN:
                        String change = Arrays.toString(manageRestrictionNano.getData());
                        String oldPin = change.substring(0,3);
                        String newPin = change.substring(4,7);
                        if (!oldPin.equals("4444")) errorCode = ERROR_WRONG_PIN;
                        if (DEBUG) console.println("[ConaxCard] Pin change OLD:"+oldPin+" NEW:"+newPin);
                    break;
                    default:
                        console.println("[ConaxCard] Unknown format for manage restriction packet");
                }

            break;

        }
        commandCounter++;

        try {
            if (answerQueue.length() >0)
            {
                if (DEBUG) console.println("Response: "+answerQueue.length());

                transport.sendByte(CC_RESPONSE_ACK_DATA);
                transport.sendByte(answerQueue.length());
            } else {
                if (DEBUG) console.println("Empty response");
                transport.sendByte(CC_RESPONSE_ACK_EMPTY);
                transport.sendByte(errorCode);
            }
        } catch (Throwable t)
        {
            console.println("[ConaxCard] Error sending response SW bytes.");
            return;
        }
        if (commandCounter == 100)
        {
            if (DEBUG) console.println("[ConaxCard] stats: processed "+emmCounter+" EMMs and "+ecmCounter+" ECMs this session");
            commandCounter=0;
        }


    }

}
