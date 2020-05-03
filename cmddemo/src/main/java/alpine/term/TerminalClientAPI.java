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

    // available for all threads somehow
    final Object waitOnMe = new Object();

    public void waitForReply() {
        logUtils.log_Info("CLIENT: waiting for reply");
        synchronized (waitOnMe) {
            try {
                waitOnMe.wait();
            } catch (InterruptedException e) {
                // we should have gotten our answer now.
            }
        }
        logUtils.log_Info("CLIENT: replied");
    }

    HandlerThread ht = new HandlerThread("Terminal Client");
    Looper looper;
    Handler handler;
    Handler.Callback callback = new Handler.Callback() {

        @SuppressWarnings("DuplicateBranchesInSwitch")
        @Override
        public boolean handleMessage(Message msg) {
            logUtils.log_Info("CLIENT: received message");
            switch (msg.what) {
                case MSG_NO_REPLY:
                    break;
                case MSG_REGISTERED_CLIENT:
                    logUtils.log_Info("CLIENT: registered");
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_UNREGISTERED_CLIENT:
                    logUtils.log_Info("CLIENT: unregistered");
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_IS_SERVER_ALIVE:
                    logUtils.log_Info("CLIENT: SERVER IS ALIVE");
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_REGISTER_ACTIVITY_FAILED:
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_REGISTERED_ACTIVITY:
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_STARTED_TERMINAL_ACTIVITY:
                    break;
                default:
                    sendMessageToServerNonBlocking(MSG_CALLBACK_INVOKED);
                    break;
            }
            // handled messages are handled in background thread
            // then notify about finished message.
            synchronized (waitOnMe) {
                waitOnMe.notifyAll();
            }
            return true;
        }
    };

    public boolean sendMessageToServer(int what) {
        return sendMessageToServer(null, what, 0, 0, null, null);
    }

    private boolean sendMessageToServer(int what, int arg1) {
        return sendMessageToServer(null, what, arg1, 0, null, null);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2) {
        return sendMessageToServer(null, what, arg1, arg2, null, null);
    }

    public boolean sendMessageToServer(int what, Object obj) {
        return sendMessageToServer(null, what, 0, 0, obj, null);
    }

    private boolean sendMessageToServer(int what, int arg1, Object obj) {
        return sendMessageToServer(null, what, arg1, 0, obj, null);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2, Object obj) {
        return sendMessageToServer(null, what, arg1, arg2, obj, null);
    }

    public boolean sendMessageToServer(int what, Bundle bundle) {
        return sendMessageToServer(null, what, 0, 0, null, bundle);
    }

    private boolean sendMessageToServer(int what, int arg1, Bundle bundle) {
        return sendMessageToServer(null, what, arg1, 0, null, bundle);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2, Bundle bundle) {
        return sendMessageToServer(null, what, arg1, arg2, null, bundle);
    }

    public boolean sendMessageToServer(int what, Object obj, Bundle bundle) {
        return sendMessageToServer(null, what, 0, 0, obj, bundle);
    }

    private boolean sendMessageToServer(int what, int arg1, Object obj, Bundle bundle) {
        return sendMessageToServer(null, what, arg1, 0, obj, bundle);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2, Object obj, Bundle bundle) {
        return sendMessageToServer(null, what, arg1, arg2, obj, bundle);
    }

    public boolean sendMessageToServer(Handler handler, int what, int arg1, int arg2, Object obj, Bundle bundle) {
        try {
            Message msg = Message.obtain(handler, what, arg1, arg2, obj);
            if (bundle != null) msg.setData(bundle);
            msg.replyTo = mMessenger;
            mService.send(msg);
            waitForReply();
            return true;
        } catch (RemoteException e) {
            // In this case the service has crashed before we could even
            // do anything with it
            return false;
        }
    }

    public boolean sendMessageToServerNonBlocking(int what) {
        return sendMessageToServerNonBlocking(null, what, 0, 0, null);
    }

    private boolean sendMessageToServerNonBlocking(int what, int arg1) {
        return sendMessageToServerNonBlocking(null, what, arg1, 0, null);
    }

    private boolean sendMessageToServerNonBlocking(int what, int arg1, int arg2) {
        return sendMessageToServerNonBlocking(null, what, arg1, arg2, null);
    }

    public boolean sendMessageToServerNonBlocking(int what, Object obj) {
        return sendMessageToServerNonBlocking(null, what, 0, 0, obj);
    }

    private boolean sendMessageToServerNonBlocking(int what, int arg1, Object obj) {
        return sendMessageToServerNonBlocking(null, what, arg1, 0, obj);
    }

    private boolean sendMessageToServerNonBlocking(int what, int arg1, int arg2, Object obj) {
        return sendMessageToServerNonBlocking(null, what, arg1, arg2, obj);
    }

    public boolean sendMessageToServerNonBlocking(Handler handler, int what, int arg1, int arg2, Object obj) {
        try {
            Message msg = Message.obtain(handler, what, arg1, arg2, obj);
            msg.replyTo = mMessenger;
            mService.send(msg);
            return true;
        } catch (RemoteException e) {
            // In this case the service has crashed before we could even
            // do anything with it
            return false;
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger mMessenger;

    private ArrayList<Runnable> runnableArrayList = new ArrayList<>();

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder boundService) {
        logUtils.logMethodName_Info();
        mService = new Messenger(boundService);
        logUtils.log_Info("BINDED TO REMOTE SERVICE");
        if (ht.getState() == Thread.State.NEW) {
            ht.start();
            looper = ht.getLooper();
            handler = new Handler(looper, callback);
            mMessenger = new Messenger(handler);
            logUtils.log_Info("STARTED MESSENGER");
        }

        // We want to monitor the service for as long as we are
        // connected to it.
        logUtils.log_Info("registering");
        sendMessageToServer(MSG_REGISTER_CLIENT);

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
        sendMessageToServer(MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        sendMessageToServer(MSG_START_TERMINAL_ACTIVITY);
    }

    public void connectToService(Activity activity) {
        bindToTerminalService(activity);
        registerActivity(activity, createPseudoTerminal());
    }
}
