package alpine.term;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.example.libclient_service.LibService_Client;

import java.util.ArrayList;

public class TerminalClientAPI extends LibService_Client {

    LogUtils logUtils = new LogUtils("Terminal Client Api");

    static {
        System.loadLibrary("terminal_jni");
    }
    public static native int getPid();
    public static native int[] createPseudoTerminal();

    public static final int MSG_REGISTER_ACTIVITY = 5;
    static final int MSG_REGISTERED_ACTIVITY = 6;
    static final int MSG_REGISTER_ACTIVITY_FAILED = 7;
    public static final int MSG_START_TERMINAL_ACTIVITY = 8;
    static final int MSG_STARTED_TERMINAL_ACTIVITY = 9;

    static final int MSG_CALLBACK_INVOKED = 100;

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    public boolean mIsBound;

    private ArrayList<Runnable> runnableArrayList = new ArrayList<>();

    @Override
    public void onMessengerAddResponses() {
        messenger
            .addResponse(MSG_REGISTER_ACTIVITY_FAILED, (message) -> {
                messenger.log.log_Info("failed to register activity");
            })
            .addResponse(MSG_REGISTERED_ACTIVITY, (message) -> {
                messenger.log.log_Info("registered activity");
            })
            .addResponse(MSG_STARTED_TERMINAL_ACTIVITY, (messsage) -> {
                messenger.log.log_Info("started terminal activity");
            });
    }

    @Override
    public void onServiceConnectedCallback(IBinder boundService) {
        log.logParentMethodName_Info();
        log.logMethodName_Info();
        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
    }

    @Override
    public void onServiceDisconnectedCallback(ComponentName name) {
        // reconnect to service when service dies
        connectToService(activity);
    }

    public void runWhenConnectedToService(Runnable runnable) {
        runnableArrayList.add(runnable);
    }

    // caches
    Activity activity = null;
    int[] pseudoTerminal = null;
    Bundle bundle = null;

    public void connectToService(Activity activity) {

        // cache activity if it does not exist, and then use it
        if (this.activity == null) this.activity = activity;

        connectToService(activity, "alpine.term", "alpine.term.TerminalService");

        // cache psuedoTerminal if it does not exist, and then use it
        if (pseudoTerminal == null) pseudoTerminal = createPseudoTerminal();

        registerActivity(activity, pseudoTerminal);
    }

    public void registerActivity(final Activity activity, final int[] pseudoTerminal) {
        runWhenConnectedToService(new Runnable() {
            @Override
            public void run() {
                logUtils.log_Info("REGISTERING ACTIVITY");
                registerActivity_(activity, pseudoTerminal);
                logUtils.log_Info("REGISTERED ACTIVITY");
            }
        });
    }

    private void storeBundle(Activity activity, int[] pseudoTerminal) {
        bundle = new Bundle();
        TrackedActivity trackedActivity = new TrackedActivity();
        if (!trackedActivity.storePseudoTerminal(pseudoTerminal)) {
            logUtils.errorAndThrow("failed to store Pseudo-Terminal");
        };
        trackedActivity.packageName = activity.getPackageName();
        trackedActivity.pid = getPid();
        trackedActivity.pidAsString = Integer.toString(trackedActivity.pid);
        PackageManager pm = activity.getPackageManager();
        ComponentName componentName = activity.getComponentName();
        ActivityInfo activityInfo;
        try {
            activityInfo = pm.getActivityInfo(componentName, 0);
            trackedActivity.activityInfo = activityInfo;
            Resources resources = activity.getResources();
            int labelRes = activityInfo.labelRes;
            if (labelRes != 0)
                trackedActivity.label = resources.getString(labelRes);
            int descriptionRes = activityInfo.descriptionRes;
            if (descriptionRes != 0)
                trackedActivity.description = resources.getString(descriptionRes);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        bundle.putParcelable("ACTIVITY", trackedActivity);
    }

    private void registerActivity_(Activity activity, int[] pseudoTerminal) {
        // a bundle cannot be cached if it contains a file descriptor, so always create new a bundle
        storeBundle(activity, pseudoTerminal);
        messenger.sendMessageToServer(MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        messenger.sendMessageToServer(MSG_START_TERMINAL_ACTIVITY);
    }
}
