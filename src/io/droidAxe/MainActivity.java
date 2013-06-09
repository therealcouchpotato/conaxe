package io.droidAxe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import io.conaxe.ConsoleOutput;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity
{
    private ServiceConnection emulatorServiceConnection;
    private Messenger emulatorMessenger;
    private Intent serviceIntent;

    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (EmulatorService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updateRunningState(TextView status) {
        if (isMyServiceRunning()) {
            if (null != status)
                status.setText("Service RUNNING");
        } else {
            if (null != status)
                status.setText("Service STOPPED");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Button startButton = (Button)findViewById(R.id.startbutton);
        final Button stopButton = (Button)findViewById(R.id.stopbutton);
        final TextView status = (TextView)findViewById(R.id.status);
        final TextView console = (TextView)findViewById(R.id.console);
        console.setMovementMethod(new ScrollingMovementMethod());

        updateRunningState(status);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(serviceIntent);
                bindService(serviceIntent);
                updateRunningState(status);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try{
                    emulatorMessenger.send(Message.obtain(null,EmulatorService.STOP,null));
                }catch (Throwable t) {
                    console.append("[Main] error sending stop message to service\n");
                }

                stopService(serviceIntent);
                updateRunningState(status);
            }
        });


        ConsoleOutput androidOutput = new ConsoleOutput() {
            @Override
            public void println(String text) {
                console.append(text+"\n");
            }

            @Override
            public void print(String text) {
                console.append(text);
            }
        };

        androidOutput.println(
                        "                    ___            \n" +
                        "  _________  ____  /   |  _  _____ \n" +
                        " / ___/ __ \\/ __ \\/ /| | | |/_/ _ \\\n" +
                        "/ /__/ /_/ / / / / ___ |_>  </  __/\n" +
                        "\\___/\\____/_/ /_/_/  |_/_/|_|\\___/ \n" +
                        " v 0.1 (Android launcher v0.1)         ");


        final Messenger mMessenger = new Messenger(new ServiceConsoleHandler(androidOutput));

        emulatorServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

                emulatorMessenger = new Messenger(iBinder);
                try {
                    Message consoleRegistrationMessage = Message.obtain(null,EmulatorService.REG_CONSOLE);
                    consoleRegistrationMessage.replyTo = mMessenger;
                    emulatorMessenger.send(consoleRegistrationMessage);

                } catch (RemoteException e){
                    console.append("[Main] service console registration failed\n");
                    return;
                }
                console.append("[Main] service connected\n");

                try{
                    emulatorMessenger.send(Message.obtain(null,EmulatorService.START,null));
                }catch (Throwable t) {
                    console.append("[Main] error sending start message to service\n");
                }


            }


            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                console.append("[Main] service died.\n");
            }
        };

        // init config

        File configFile = getFileStreamPath("conaxe.ini");
        if (!configFile.exists()){

            InputStream defaultConfig;
            try {
                defaultConfig = getAssets().open("default.ini");
            } catch (Throwable t) {
                console.append("[Main] Error opening default config asset\n");
                return;
            }
            FileOutputStream configWriter;
            try {
                configWriter = openFileOutput("conaxe.ini",Context.MODE_PRIVATE);

                byte[] buffer = new byte[1024];
                int len;
                while ((len = defaultConfig.read(buffer)) != -1) {
                    configWriter.write(buffer, 0, len);
                }

                configWriter.close();
                defaultConfig.close();

            } catch (Throwable t) {
                console.append("[Main] Error deploying default configuration\n");
                return;
            }

            console.append("[Main] Default configuration deployed. Edit it manually because I'm too lazy to add a config editor\n");
        }
        // - init config

        console.append("[Main] connecting service\n");

        serviceIntent =new Intent(this, EmulatorService.class);
        startService(serviceIntent);
        bindService(serviceIntent);

        updateRunningState(status);

    }



    private void bindService(Intent serviceIntent)
    {
        bindService(new Intent(this,
                EmulatorService.class), emulatorServiceConnection, 0);
    }

    class ServiceConsoleHandler extends Handler {

        private ConsoleOutput consoleOutput;

        public ServiceConsoleHandler(ConsoleOutput consoleOutput) {
            this.consoleOutput = consoleOutput;
        }
        @Override
        public void handleMessage(Message msg) {
            consoleOutput.println(msg.getData().getString("msg"));
        }
    }
}
