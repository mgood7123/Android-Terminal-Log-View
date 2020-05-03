package alpine.term.demo;


import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import alpine.term.LogUtils;
import alpine.term.TerminalClientAPI;

public class MainActivity extends Activity {

    LogUtils logUtils = new LogUtils("Main Activity");

    TerminalClientAPI clientAPI = new TerminalClientAPI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clientAPI.connectToService(this);
        logUtils.logMethodName();
    }

    @Override
    protected void onStart() {
        super.onStart();
        logUtils.logMethodName();
    }

    @Override
    protected void onResume() {
        super.onResume();
        logUtils.logMethodName();
    }

    @Override
    protected void onPause() {
        super.onPause();
        logUtils.logMethodName();
    }

    @Override
    protected void onStop() {
        super.onStop();
        logUtils.logMethodName();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logUtils.logMethodName();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        logUtils.logMethodName();
    }
}
