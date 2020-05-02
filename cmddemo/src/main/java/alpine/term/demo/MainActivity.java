package alpine.term.demo;


import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import alpine.term.TerminalClientAPI;

public class MainActivity extends Activity {

    TerminalClientAPI clientAPI = new TerminalClientAPI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clientAPI.connectToService(this);
        clientAPI.logUtils.logMethodName();
    }

    @Override
    protected void onStart() {
        super.onStart();
        clientAPI.logUtils.logMethodName();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clientAPI.logUtils.logMethodName();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clientAPI.logUtils.logMethodName();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clientAPI.logUtils.logMethodName();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clientAPI.logUtils.logMethodName();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        clientAPI.logUtils.logMethodName();
    }
}
