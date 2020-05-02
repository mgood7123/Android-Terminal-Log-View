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
import android.util.Log;

import java.util.ArrayList;

public class TerminalControllerService implements ServiceConnection {

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    public boolean mIsBound;

    // available for all threads somehow
    final Object waitOnMe = new Object();

    public void waitForReply() {
        Log.e(Config.APP_LOG_TAG, "CLIENT: waiting for reply");
        synchronized (waitOnMe) {
            try {
                waitOnMe.wait();
            } catch (InterruptedException e) {
                // we should have gotten our answer now.
            }
        }
        Log.e(Config.APP_LOG_TAG, "CLIENT: replied");
    }

    HandlerThread ht = new HandlerThread("threadName");
    Looper looper;
    Handler handler;
    Handler.Callback callback = new Handler.Callback() {

        @SuppressWarnings("DuplicateBranchesInSwitch")
        @Override
        public boolean handleMessage(Message msg) {
            Log.e(Config.APP_LOG_TAG, "CLIENT: received message");
            switch (msg.what) {
                case TerminalService.MSG_NO_REPLY:
                    break;
                case TerminalService.MSG_REGISTERED_CLIENT:
                    Log.e(Config.APP_LOG_TAG, "CLIENT: registered");
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_UNREGISTERED_CLIENT:
                    Log.e(Config.APP_LOG_TAG, "CLIENT: unregistered");
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_IS_SERVER_ALIVE:
                    Log.e(Config.APP_LOG_TAG, "CLIENT: SERVER IS ALIVE");
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_REGISTER_ACTIVITY_FAILED:
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_REGISTERED_ACTIVITY:
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_STARTED_TERMINAL_ACTIVITY:
                    break;
                default:
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
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
        Log.e(Config.APP_LOG_TAG, "onServiceConnected() has been called");
        mService = new Messenger(boundService);
        Log.e(Config.APP_LOG_TAG, "CLIENT: BINDED TO REMOTE SERVICE");
        if (ht.getState() == Thread.State.NEW) {
            ht.start();
            looper = ht.getLooper();
            handler = new Handler(looper, callback);
            mMessenger = new Messenger(handler);
            Log.e(Config.APP_LOG_TAG, "CLIENT: STARTED MESSENGER");
        }

        // We want to monitor the service for as long as we are
        // connected to it.
        Log.e(Config.APP_LOG_TAG, "CLIENT: registering");
        sendMessageToServer(TerminalService.MSG_REGISTER_CLIENT);

        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO
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

    public void registerActivity(final Activity activity, final int pseudoTerminal) {
        runWhenConnectedToService(new Runnable() {
            @Override
            public void run() {
                Log.e(Config.APP_LOG_TAG, "REGISTERING ACTIVITY");
                registerActivity_(activity, pseudoTerminal);
                Log.e(Config.APP_LOG_TAG, "REGISTERED ACTIVITY");
            }
        });
    }

    private void registerActivity_(Activity activity, int pseudoTerminal) {
        TrackedActivity trackedActivity = new TrackedActivity();
        trackedActivity.storeNativeFD(pseudoTerminal);
        trackedActivity.packageName = activity.getPackageName();
        trackedActivity.pid = JNI.getPid();
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
        sendMessageToServer(TerminalService.MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        sendMessageToServer(TerminalService.MSG_START_TERMINAL_ACTIVITY);
    }
}
