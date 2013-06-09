package io.conaxe;

public interface CardServerGateway {

    /**
     * Send an EMM packet off to the card server.
     * There is no need to return anything
     * @param emmPacket (format: the raw EMM within the 0x12 nano without leading null byte (pairing flag IIRC))
     */
    void doEMM(byte[] emmPacket);

    /**
     * Decode an ECM with a card server
     * @param ecmPacket (format: the raw ECM within the 0x14 nano)
     * @return The raw ControlWord as a 16byte byte array properly concatenated (CW Index 0 followed by CW Index 1)
     *         NULL if ECM decoding has failed
     */
    byte[] decodeECM(byte[] ecmPacket);

}
