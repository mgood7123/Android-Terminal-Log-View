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
import com.example.libclient_service.LibService_Messenger;
import com.example.libclient_service.RunnableArgument;

import java.util.ArrayList;

public class TerminalClientAPI extends LibService_Client {

    LogUtils logUtils = new LogUtils("Terminal Client Api");

    static {
        System.loadLibrary("terminal_jni");
    }
    public static native int getPid();
    public static native int[] createPseudoTerminal();

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_REGISTERED_CLIENT = 2;

    /**
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */

    static final int MSG_UNREGISTER_CLIENT = 3;
    static final int MSG_UNREGISTERED_CLIENT = 4;

    public static final int MSG_REGISTER_ACTIVITY = 5;
    static final int MSG_REGISTERED_ACTIVITY = 6;
    static final int MSG_REGISTER_ACTIVITY_FAILED = 7;
    public static final int MSG_START_TERMINAL_ACTIVITY = 8;
    static final int MSG_STARTED_TERMINAL_ACTIVITY = 9;

    static final int MSG_CALLBACK_INVOKED = 100;
    static final int MSG_IS_SERVER_ALIVE = 1300;
    static final int MSG_NO_REPLY = 999;

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    public boolean mIsBound;

    private ArrayList<Runnable> runnableArrayList = new ArrayList<>();

    public int MSG_REGISTRATION_CONFIRMED = 12345;

    @Override
    public void onServiceConnectedCallback(IBinder boundService) {
        log.logParentMethodName_Info();
        log.logMethodName_Info();
        messenger
            .addResponse(LibService_Messenger.PONG)
            .addResponse(MSG_NO_REPLY)
            .addResponse(MSG_REGISTERED_CLIENT, (message) -> {
                messenger.log.log_Info("registered");
                messenger.sendMessageToServer(MSG_REGISTRATION_CONFIRMED);
                messenger.log.log_Info("sent message to server");
            })
            .addResponse(MSG_UNREGISTERED_CLIENT, (message) -> {
                messenger.log.log_Info("unregistered");
            })
            .addResponse(MSG_UNREGISTERED_CLIENT, (message) -> {
                messenger.log.log_Info("SERVER IS ALIVE");
            })
            .addResponse(MSG_REGISTER_ACTIVITY_FAILED, (message) -> {
                messenger.log.log_Info("failed to register activity");
            })
            .addResponse(MSG_REGISTERED_ACTIVITY, (message) -> {
                messenger.log.log_Info("registered activity");
            })
            .addResponse(MSG_STARTED_TERMINAL_ACTIVITY, (messsage) -> {
                messenger.log.log_Info("started terminal activity");
            })
            .bind(boundService)
            .start();

        // We want to monitor the service for as long as we are
        // connected to it.
        messenger.ping();
        logUtils.log_Info("registering");
        messenger.sendMessageToServer(MSG_REGISTER_CLIENT);

        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
    }

    public void runWhenConnectedToService(Runnable runnable) {
        runnableArrayList.add(runnable);
    }

    public void connectToService(Activity activity) {
        connectToService(activity, "alpine.term", "alpine.term.TerminalService");
        registerActivity(activity, createPseudoTerminal());
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

    private void registerActivity_(Activity activity, int[] pseudoTerminal) {
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
        Bundle bundle = new Bundle();
        bundle.putParcelable("ACTIVITY", trackedActivity);
        messenger.sendMessageToServer(MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        messenger.sendMessageToServer(MSG_START_TERMINAL_ACTIVITY);
    }
}
