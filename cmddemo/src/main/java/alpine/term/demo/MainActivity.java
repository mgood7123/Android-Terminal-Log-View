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
        TerminalControllerService TCS = new TerminalControllerService();
        TCS.bindToTerminalService(this);
        TCS.registerActivity(this, TerminalController.createPseudoTerminal());
    }
}
