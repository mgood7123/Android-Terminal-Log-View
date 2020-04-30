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
        terminalControllerService.bindToTerminalService(this);
        terminalControllerService.runOnServiceConnect(new Runnable() {
            @Override
            public void run() {
                terminalControllerService.startTerminalActivity();
            }
        });
    }
}
