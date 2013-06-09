package io.droidAxe;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import gnu.io.CommPortIdentifier;
import io.conaxe.*;

import java.util.Enumeration;

public class EmulatorService extends Service {

    final static int REG_CONSOLE = 0;
    final static int START = 1;
    final static int STOP = 2;

    private static boolean running;

    private final static String CONFIG_RESET = "reset";
    private final static String CONFIG_RESET_SIGNAL = "resetSignal";
    private final static String CONFIG_INVERTED_RESET_SIGNAL = "invertedResetSignal";
    private final static String CONFIG_FLOWCONTROL = "flowcontrol";

    private Messenger consoleMessengerRegistrationMessenger = new Messenger(new IncomingHandler());
    private Messenger consoleMessenger;

    private NotificationManager mNM;

    public class LocalBinder extends Binder {

        EmulatorService getService() {
            return EmulatorService.this;
        }

    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return consoleMessengerRegistrationMessenger.getBinder();
    }


    private int NOTIFICATION = R.string.local_service_started;

    private void showNotification() {
        CharSequence text = getText(R.string.local_service_started);

        Notification notification = new Notification(R.drawable.ic_conaxe, text,
                System.currentTimeMillis());

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
                text, contentIntent);

        mNM.notify(NOTIFICATION, notification);
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        showNotification();
    }

    private void runEmu() {

        ConsoleOutput serviceConsoleOutput = new ConsoleOutput() {
            @Override
            public void println(String text) {
                try {
                    Message message = new Message();
                    Bundle messageBundle = new Bundle();
                    messageBundle.putString("msg",text);
                    message.setData(messageBundle);
                    consoleMessenger.send(message);
                }catch (Throwable t) {}

            }

            @Override
            public void print(String text) {
                try {
                    Message message = new Message();
                    Bundle messageBundle = new Bundle();
                    messageBundle.putString("msg",text);
                    message.setData(messageBundle);
                    consoleMessenger.send(message);
                }catch (Throwable t) {}
            }
        };

        Configuration appConfiguration;
        try {
            appConfiguration = new Configuration();
            appConfiguration.init(openFileInput("conaxe.ini"));
        } catch (Throwable t) {
            serviceConsoleOutput.println("[Service] Error opening configuration.");
            return;
        }
        serviceConsoleOutput.println("[Service] loaded:"+getFileStreamPath("conaxe.ini"));
        serviceConsoleOutput.println("[Service] starting up..");


        CommPortIdentifier portId = null;
        Enumeration portIdentifiers = CommPortIdentifier.getPortIdentifiers();

        serviceConsoleOutput.println("[Service] listing available ports..");
        while (portIdentifiers.hasMoreElements())
        {
            CommPortIdentifier pid = (CommPortIdentifier) portIdentifiers.nextElement();

             serviceConsoleOutput.println( pid.getName() );
        }
        serviceConsoleOutput.println("[Service] ports list over");

        serviceConsoleOutput.println("[Service] initializing...");

        ConaxCardInfo info = new ConaxCardInfo(serviceConsoleOutput,appConfiguration);
        serviceConsoleOutput.println("[Service] ... card info");
        ConaxTransport transport = new ConaxTransport(serviceConsoleOutput,appConfiguration);
        serviceConsoleOutput.println("[Service] ... transport");
        Camd35Gateway gateway = new Camd35Gateway(serviceConsoleOutput,appConfiguration);
        serviceConsoleOutput.println("[Service] ... gateway");

        // make reset watcher
    /*    ResetWatcher resetWatcher=null;
        if (appConfiguration.getString(CONFIG_RESET).toLowerCase().equals(CONFIG_FLOWCONTROL)) {
            try {
                resetWatcher = new FlowControlResetWatcher(appConfiguration,transport.getSerialPort());
            } catch (Throwable t) {
                serviceConsoleOutput.println("Unable to initialize reset watcher");
                return;
            }
        } else {
            serviceConsoleOutput.println("Unknown reset watcher option. Supported: flowcontrol");
        }*/

        String resetSignal = appConfiguration.getString(CONFIG_RESET_SIGNAL);
        boolean invertedResetSignal = appConfiguration.getString(CONFIG_INVERTED_RESET_SIGNAL).toLowerCase().equals("true");

        ConaxNanoAssembler assembler = new ConaxNanoAssembler();
        ConaxNanoDissector dissector = new ConaxNanoDissector();

        EventDrivenConaxCard card = new EventDrivenConaxCard(serviceConsoleOutput,transport,info,assembler,dissector,gateway,resetSignal,invertedResetSignal);
        serviceConsoleOutput.println("[Service] ... emu core");

        serviceConsoleOutput.println("[Service] waiting for events ..");

        /*while (true) {
            card.runCard();
            serviceConsoleOutput.println("[Service] service died unexpectedly!");
        } */

    }



    class IncomingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REG_CONSOLE:
                    consoleMessenger = msg.replyTo;
                    try {
                        Message message = new Message();
                        Bundle messageBundle = new Bundle();
                        messageBundle.putString("msg","[Service] console registered");
                        message.setData(messageBundle);
                        consoleMessenger.send(message);
                    } catch (RemoteException e) {
                        consoleMessenger = null;
                    }

                    break;
                case START:
                    if (!running){
                        running=true;
                        runEmu();
                    }
                break;
                case STOP:
                    if (running) {
                        running = false;
                    }

                break;

                default:
                    super.handleMessage(msg);
            }

        }
    }

}