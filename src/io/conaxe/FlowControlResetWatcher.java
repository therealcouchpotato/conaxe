package io.conaxe;

import gnu.io.*;


public class FlowControlResetWatcher implements ResetWatcher {

    private final static int CTS = 0;
    private final static int DSR = 1;
    private final static int DCD = 2;
    private final static int RI = 3;

    private final static String CONFIG_SIGNAL = "flowcontrolreset.signal";
    private final static String CONFIG_INVERTED = "flowcontrolreset.inverted";

    private SerialPort port;
    private int signal;
    private boolean idleState;
    private boolean lastState;

    public FlowControlResetWatcher(Configuration configuration, SerialPort port) throws Exception{

        this.port = port;
        this.idleState = configuration.getByte(CONFIG_INVERTED)==0;
        String sSignal = configuration.getString(CONFIG_SIGNAL);
        if (sSignal.toLowerCase().equals("cts")) signal = CTS; else
        if (sSignal.toLowerCase().equals("dsr")) signal = DSR; else
        if (sSignal.toLowerCase().equals("dcd")) signal = DCD; else
        if (sSignal.toLowerCase().equals("ri")) signal = RI;



    }

    private boolean getState()
    {
        switch (signal) {
            case CTS:
                return port.isCTS();
            case DSR:
                return port.isDSR();
            case DCD:
                return port.isCD();
            case RI:
                return port.isRI();
            default:
                return false;
        }
    }

    @Override
    public boolean isReset() {

        boolean newState = getState();

        if ((lastState == idleState) && (newState != idleState)) {
            lastState = newState;
            return true;
        }

        lastState = newState;
        return false;

    }
}
