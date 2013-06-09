package io.conaxe;

public class NanoPacket {

    public NanoPacket(int nano, byte[] data) {
        this.nano = (byte)nano;
        this.data = data;
    }

    public NanoPacket() {
    }

    private int nano;

	private byte[] data;

    public int getNano() {
        return nano;
    }

    public void setNano(byte nano) {
        this.nano = nano;
    }


    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
