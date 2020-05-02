package alpine.term.demo;


import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import alpine.term.Config;
import alpine.term.TerminalController;
import alpine.term.TerminalControllerService;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e(Config.APP_LOG_TAG, "AM I PRINTING? 0");
        int pseudoTerminal = TerminalController.createPseudoTerminal();
        TerminalControllerService terminalControllerService = new TerminalControllerService();
        Log.e(Config.APP_LOG_TAG, "AM I PRINTING? 1");
        terminalControllerService.bindToTerminalService(this);
        Log.e(Config.APP_LOG_TAG, "AM I PRINTING? 2");
        terminalControllerService.registerActivity(this, pseudoTerminal);
        Log.e(Config.APP_LOG_TAG, "AM I PRINTING? 3");
        new Thread() {
            @Override
            public void run() {
                while(true) {
                    SystemClock.sleep(1000);
                    Log.e(Config.APP_LOG_TAG, "AM I PRINTING? LOOPED");
                }
            }
        }.start();
    }
}
