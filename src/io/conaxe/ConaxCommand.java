package io.conaxe;

public class ConaxCommand {

	private int instruction;
	private int P1;
	private int P2;
	private byte[] packet;

    public int getInstruction() {
        return instruction;
    }

    public void setInstruction(int instruction) {
        this.instruction = instruction;
    }

    public int getP1() {
        return P1;
    }

    public void setP1(int p1) {
        P1 = p1;
    }

    public int getP2() {
        return P2;
    }

    public void setP2(int p2) {
        P2 = p2;
    }

    public byte[] getPacket() {
        return packet;
    }

    public void setPacket(byte[] packet) {
        this.packet = packet;
    }
}
