package io.conaxe;

import java.util.Arrays;

import static io.conaxe.ByteUtil.unsign;

public class ConaxCommandDissector {


    public static ConaxCommand dissect(byte[] packet) throws ProtocolException
    {

        ConaxCommand command = new ConaxCommand();
        command.setInstruction(unsign(packet[1]));
        command.setP1(unsign(packet[2]));
        command.setP2(unsign(packet[3]));
        int length = unsign(packet[4]);
        command.setPacket(Arrays.copyOfRange(packet,5,5+length));

        return command;
    }

}
