package io.conaxe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

public class ConaxNanoAssembler {


    public byte[] assemble(List<NanoPacket> nanos) {
        ByteArrayOutputStream bufferOs = new ByteArrayOutputStream();
        DataOutputStream bufferWriter = new DataOutputStream(bufferOs);

        try {
            for (NanoPacket nanoPacket : nanos) {
                bufferWriter.writeByte(nanoPacket.getNano());
                bufferWriter.writeByte(nanoPacket.getData().length);
                bufferWriter.write(nanoPacket.getData());
            }
        } catch (Throwable t) {
          System.out.println("bufferwriter error");
          System.exit(-1);
        }

        return bufferOs.toByteArray();

    }

    public byte[] assemble(int nanoId, byte[] packet) {
        ByteArrayOutputStream bufferOs = new ByteArrayOutputStream();
        DataOutputStream bufferWriter = new DataOutputStream(bufferOs);
        try {
            bufferWriter.writeByte(nanoId);
            bufferWriter.writeByte(packet.length);
            bufferWriter.write(packet);
        } catch (Throwable t) {
            System.out.println("bufferwriter error");
            System.exit(-1);

        }
        return bufferOs.toByteArray();
    }

}
