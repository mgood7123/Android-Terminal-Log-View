package alpine.term;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import alpine.term.emulator.JNI;

public class Main2Activity extends Activity {

    TerminalControllerService terminalControllerService = new TerminalControllerService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        terminalControllerService.runOnServiceConnect(new Runnable() {
            @Override
            public void run() {
                terminalControllerService.registerActivity(Main2Activity.this);
                terminalControllerService.startTerminalActivity();
            }
        });
        terminalControllerService.bindToTerminalService(this);

        new Thread() {
            @Override
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(10000);
                        Log.e(Config.APP_LOG_TAG, "invoking test puts for terminal connection");
                        JNI.test_puts();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }
}
