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

import com.example.libclient_service.LibService_Messenger;

import java.util.ArrayList;

public class TerminalClientAPI implements ServiceConnection {

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

    LibService_Messenger libService_messenger = new LibService_Messenger();

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder boundService) {
        logUtils.logMethodName_Info();
        libService_messenger
            .addResponse(MSG_NO_REPLY)
            .addResponse(MSG_REGISTERED_CLIENT, (message) -> {
                libService_messenger.log.log_Info("registered");
                libService_messenger.sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
            })
            .addResponse(MSG_UNREGISTERED_CLIENT, (message) -> {
                libService_messenger.log.log_Info("unregistered");
                libService_messenger.sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
            })
            .addResponse(MSG_UNREGISTERED_CLIENT, (message) -> {
                libService_messenger.log.log_Info("SERVER IS ALIVE");
                libService_messenger.sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
            })
            .addResponse(MSG_REGISTER_ACTIVITY_FAILED, (message) -> {
                libService_messenger.sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
            })
            .addResponse(MSG_REGISTERED_ACTIVITY, (message) -> {
                libService_messenger.sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
            })
            .addResponse(MSG_STARTED_TERMINAL_ACTIVITY)
            .bind(boundService)
            .start();

        // We want to monitor the service for as long as we are
        // connected to it.
        logUtils.log_Info("registering");
        libService_messenger.sendMessageToServer(MSG_REGISTER_CLIENT);

        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        logUtils.logMethodName_Info();
    }

    public void runWhenConnectedToService(Runnable runnable) {
        runnableArrayList.add(runnable);
    }

    public void bindToTerminalService(Activity activity) {
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName("alpine.term", "alpine.term.TerminalService");
        serviceIntent.setPackage("alpine.term");

        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!activity.bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }
        mIsBound = true;
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
        libService_messenger.sendMessageToServer(MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        libService_messenger.sendMessageToServer(MSG_START_TERMINAL_ACTIVITY);
    }

    public void connectToService(Activity activity) {
        bindToTerminalService(activity);
        registerActivity(activity, createPseudoTerminal());
    }
}
