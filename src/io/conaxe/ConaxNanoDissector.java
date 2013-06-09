package io.conaxe;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ConaxNanoDissector {
	
	public List<NanoPacket> dissect(byte[] packet) throws ProtocolException {

        if (packet.length <3)
            throw new ProtocolException();

        LinkedList<NanoPacket> result = new LinkedList<NanoPacket>();

        int locPointer = 0;
        while (locPointer<packet.length-1)
        {
            NanoPacket nano = new NanoPacket();
            nano.setNano(packet[locPointer]);
            locPointer++;

            int nanoLen = packet[locPointer] & 0xFF;

            if (nanoLen > packet.length-2)
                throw new ProtocolException();
            locPointer++;

            nano.setData(Arrays.copyOfRange(packet, locPointer, locPointer + nanoLen));
            locPointer+=nanoLen;

            result.add(nano);

        }

		return result;
	}

    public NanoPacket fetch(int nano, List<NanoPacket> list)
    {
        for (NanoPacket packet: list)
        {
            if (packet.getNano() == nano)
                return packet;
        }
        return null;
    }

}
