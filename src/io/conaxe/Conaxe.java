package io.conaxe;

import java.io.IOException;

public class Conaxe {

    private final static String CONFIG_RESET = "reset";
    private final static String CONFIG_FLOWCONTROL = "flowcontrol";

    public static void main(String[] args)
    {
        ConsoleOutput system = new SystemConsoleOutput();

        system.println(
                "                    ___            \n" +
                "  _________  ____  /   |  _  _____ \n" +
                " / ___/ __ \\/ __ \\/ /| | | |/_/ _ \\\n" +
                "/ /__/ /_/ / / / / ___ |_>  </  __/\n" +
                "\\___/\\____/_/ /_/_/  |_/_/|_|\\___/ \n" +
                " v 0.1                                 ");


        ConaxNanoAssembler assembler = new ConaxNanoAssembler();
        ConaxNanoDissector dissector = new ConaxNanoDissector();
        Configuration configuration = new Configuration();

        try {
            configuration.init("conaxe.ini");
        } catch (IOException e)
        {
            system.println("Unable to load configuration!");
            System.exit(-1);
        }

        ConaxCardInfo cardInfo = new ConaxCardInfo(system,configuration);
        ConaxTransport transport = new ConaxTransport(system,configuration);
        CardServerGateway camd35Gateway = new Camd35Gateway(system,configuration);

        ResetWatcher resetWatcher=null;
        if (configuration.getString(CONFIG_RESET).toLowerCase().equals(CONFIG_FLOWCONTROL)) {
            try {
                resetWatcher = new FlowControlResetWatcher(configuration,transport.getSerialPort());
            } catch (Throwable t) {
                system.println("Unable to initialize reset watcher");
                System.exit(-1);
            }
        } else {
            system.println("Unknown reset watcher option. Supported: flowcontrol");
            System.exit(-1);
        }

        ConaxCard card = new ConaxCard(system,transport,cardInfo,assembler,dissector,camd35Gateway,resetWatcher);

        card.runCard();

    }

}
