/*
*************************************************************************
Alpine Term - a VM-based terminal emulator.
Copyright (C) 2019-2020  Leonid Plyushch <leonid.plyushch@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*************************************************************************
*/
package alpine.term;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NotificationCompat;

import alpine.term.emulator.JNI;
import alpine.term.emulator.TerminalSession;
import alpine.term.emulator.TerminalSession.SessionChangedCallback;

/**
 * A service holding a list of terminal sessions, {@link #mTerminalSessions}, showing a foreground notification while
 * running so that it is not terminated. The user interacts with the session through {@link TerminalActivity}, but this
 * service may outlive the activity when the user or the system disposes of the activity. In that case the user may
 * restart {@link TerminalActivity} later to yet again access the sessions.
 * <p/>
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, {@link Service#startForeground(int, Notification)}.
 * <p/>
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * {@link #buildNotification()}.
 */
public class TerminalService extends Service implements SessionChangedCallback, Parcelable {

    private static final String INTENT_ACTION_SERVICE_STOP = "alpine.term.ACTION_STOP_SERVICE";
    private static final String INTENT_ACTION_WAKELOCK_ENABLE = "alpine.term.ACTION_ENABLE_WAKELOCK";
    private static final String INTENT_ACTION_WAKELOCK_DISABLE = "alpine.term.ACTION_DISABLE_WAKELOCK";

    private static final int NOTIFICATION_ID = 1338;
    private static final String NOTIFICATION_CHANNEL_ID = "alpine.term.NOTIFICATION_CHANNEL";
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

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

    // callbacks
    static final int MSG_ON_TITLE_CHANGED = 4;
    static final int MSG_ON_SESSION_FINISHED = 5;
    static final int MSG_ON_TEXT_CHANGED = 6;
    static final int MSG_ON_CLIPBOARD_TEXT = 7;
    static final int MSG_ON_BELL = 8;
    static final int MSG_ON_COLORS_CHANGED = 9;
    static final int MSG_ARE_SESSIONS_EMPTY = 10;
    static final int MSG_DOES_SERVER_WANT_TO_STOP = 11;
    static final int MSG_REMOVE_SESSION = 12;
    static final int MSG_CREATE_SHELL_SESSION = 13;
    static final int MSG_CREATE_LOGCAT_SESSION = 14;
    static final int MSG_SESSION_CREATED = 15;
    static final int MSG_ATTACH_SESSION = 16;

    static final int MSG_CALLBACK_INVOKED = 100;
    static final int MSG_TERMINATE = 500;
    static final int MSG_IS_SERVER_ALIVE = 1300;

    /**
     * The terminal sessions which this service manages.
     * <p/>
     * Note that this list is observed by {@link TerminalActivity#mListViewAdapter}, so any changes must be made on the UI
     * thread and followed by a call to {@link ArrayAdapter#notifyDataSetChanged()} }.
     */
    private final List<TerminalSession> mTerminalSessions = new ArrayList<>();

    private IBinder mBinder = new LocalBinder();

    /**
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    SessionChangedCallback mSessionChangeCallback;

    /**
     * If the user has executed the {@link #INTENT_ACTION_SERVICE_STOP} intent.
     */
    boolean mWantsToStop = false;

    /**
     * The wake lock and wifi lock are always acquired and released together.
     */
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    // the current terminal session object
    private TerminalSession mTermSession;

    // provide zero argument constructor to avoid the following error:

    // java.lang.RuntimeException: Unable to instantiate service
    // alpine.term.TerminalService: java.lang.InstantiationException:
    // java.lang.Class<alpine.term.TerminalService> has no zero argument constructor

    // make it public

    // java.lang.RuntimeException: Unable to instantiate service alpine.term.TerminalService:
    // java.lang.IllegalAccessException: void alpine.term.TerminalService.<init>() is not
    // accessible from java.lang.Class<android.app.AppComponentFactory>

    public TerminalService() {}

    protected TerminalService(Parcel in) {
        mBinder = in.readStrongBinder();
    }

    public static final Creator<TerminalService> CREATOR = new Creator<TerminalService>() {
        @Override
        public TerminalService createFromParcel(Parcel in) {
            return new TerminalService(in);
        }

        @Override
        public TerminalService[] newArray(int size) {
            return new TerminalService[size];
        }
    };

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.application_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications from " + getString(R.string.application_name));

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();

        stopForeground(true);

        for (TerminalSession mTerminalSession : mTerminalSessions) {
            mTerminalSession.finishIfRunning();
        }
    }

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

    @SuppressWarnings({"PointlessBooleanExpression"})
    public static int toInt(boolean value) {
        if (value == true) return 0;
        return 1;
    }

    @SuppressWarnings("RedundantIfStatement")
    public static boolean toBoolean(int value) {
        if (value == 0) return true;
        return false;
    }

    // available for all threads somehow
    final Object waitOnMe = new Object();

    HandlerThread ht = new HandlerThread("threadName");
    Looper looper;
    Handler handler;
    Handler.Callback callback = new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    Log.e(Config.APP_LOG_TAG, "SERVER: registered client");
                    Log.e(Config.APP_LOG_TAG, "SERVER: informing client of registration");
                    sendMessageToAllClients(MSG_REGISTERED_CLIENT);
                    Log.e(Config.APP_LOG_TAG, "SERVER: informed client of registration");
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_ARE_SESSIONS_EMPTY:
                    sendMessageToAllClients(MSG_ARE_SESSIONS_EMPTY, toInt(getSessions().isEmpty()));
                    break;
                case MSG_DOES_SERVER_WANT_TO_STOP:
                    sendMessageToAllClients(MSG_DOES_SERVER_WANT_TO_STOP, toInt(mWantsToStop));
                    break;
                case MSG_REMOVE_SESSION:
                    removeSession(session);
                    sendMessageToAllClients(MSG_CALLBACK_INVOKED);
                    break;
                case MSG_TERMINATE:
                    sendMessageToAllClients(MSG_CALLBACK_INVOKED);
                    terminateService();
                    break;
                case MSG_IS_SERVER_ALIVE:
                    break;
                case MSG_ATTACH_SESSION:
                    mTermSession = mTerminalSessions.get(msg.arg1);
                case MSG_CREATE_SHELL_SESSION:
                    sendMessageToAllClients(MSG_SESSION_CREATED, createShellSession(toBoolean(msg.arg1));
                default:
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

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger mMessenger;

    @SuppressLint({"Wakelock", "WakelockTimeout"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(Config.APP_LOG_TAG, "onStartCommand() has been called");
        String action = intent.getAction();
        if (INTENT_ACTION_SERVICE_STOP.equals(action)) {
            Log.e(Config.APP_LOG_TAG, "terminating");
            terminateService();
        } else if (INTENT_ACTION_WAKELOCK_ENABLE.equals(action)) {
            Log.e(Config.APP_LOG_TAG, "received wakelock");
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.WAKELOCK_LOG_TAG);
                mWakeLock.acquire();

                // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Config.WAKELOCK_LOG_TAG);
                mWifiLock.acquire();

                updateNotification();
            }
        } else if (INTENT_ACTION_WAKELOCK_DISABLE.equals(action)) {
            Log.e(Config.APP_LOG_TAG, "received disable wakelock");
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;

                mWifiLock.release();
                mWifiLock = null;

                updateNotification();
            }
        } else if (action != null) {
            Log.e(Config.APP_LOG_TAG, "received an unknown action for TerminalService: " + action);
        }

        if (ht.getState() == Thread.State.NEW) {
            ht.start();
            looper = ht.getLooper();
            handler = new Handler(looper, callback);
            mMessenger = new Messenger(handler);
        }

        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
        Log.e(Config.APP_LOG_TAG, "returning START_NOT_STICKY");
        return Service.START_NOT_STICKY;
    }

    public void sendMessageToAllClients(int what) {
        sendMessageToAllClients(null, what, 0, 0, null);
    }

    private void sendMessageToAllClients(int what, int arg1) {
        sendMessageToAllClients(null, what, arg1, 0, null);
    }

    private void sendMessageToAllClients(int what, int arg1, int arg2) {
        sendMessageToAllClients(null, what, arg1, arg2, null);
    }

    public void sendMessageToAllClients(int what, Object obj) {
        sendMessageToAllClients(null, what, 0, 0, obj);
    }

    private void sendMessageToAllClients(int what, int arg1, Object obj) {
        sendMessageToAllClients(null, what, arg1, 0, obj);
    }

    private void sendMessageToAllClients(int what, int arg1, int arg2, Object obj) {
        sendMessageToAllClients(null, what, arg1, arg2, obj);
    }

    public void sendMessageToAllClients(Handler handler, int what, int arg1, int arg2, Object obj) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                mClients.get(i).send(Message.obtain(handler, what, arg1, arg2, obj));
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                Log.e(Config.APP_LOG_TAG, "SERVER: client " + i + " is dead, removing...");
                mClients.remove(i);
                Log.e(Config.APP_LOG_TAG, "SERVER: removed client " + i + "");
            }
        }
        waitForReply();
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        sendMessageToAllClients(MSG_ON_TITLE_CHANGED);
    }

    @Override
    public void onSessionFinished(final TerminalSession finishedSession) {
        sendMessageToAllClients(MSG_ON_SESSION_FINISHED);
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        sendMessageToAllClients(MSG_ON_TEXT_CHANGED);
    }

    @Override
    public void onClipboardText(TerminalSession session, String text) {
        sendMessageToAllClients(MSG_ON_CLIPBOARD_TEXT);
    }

    @Override
    public void onBell(TerminalSession session) {
        sendMessageToAllClients(MSG_ON_BELL);
    }

    @Override
    public void onColorsChanged(TerminalSession session) {
        sendMessageToAllClients(MSG_ON_COLORS_CHANGED);
    }

    public boolean removeSession(TerminalSession session) {
        return mTerminalSessions.remove(session);
    }

    public List<TerminalSession> getSessions() {
        Log.e(Config.APP_LOG_TAG, "getSessions() has been called");
        return mTerminalSessions;
    }

    public void terminateService() {
        Log.e(Config.APP_LOG_TAG, "terminateService() has been called");
        mWantsToStop = true;

        if (!mTerminalSessions.isEmpty()) {
            for (TerminalSession mTerminalSession : mTerminalSessions) {
                mTerminalSession.finishIfRunning();
            }
        }

        stopSelf();
    }

    /**
     * Creates terminal instance with running 'sh'.
     * @param isLogView     if this is true then this is converted into a LogView instead of a Shell
     * @return              a created terminal session that can be attached to TerminalView.
     */
    public int createShellSession(boolean isLogView) {
        Log.e(Config.APP_LOG_TAG, "createShellSession() has been called");
        ArrayList<String> environment = new ArrayList<>();
        Context appContext = getApplicationContext();

        String execPath = appContext.getApplicationInfo().nativeLibraryDir;
        String runtimeDataPath = Config.getDataDirectory(appContext);

        environment.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        environment.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        environment.add("PREFIX=" + runtimeDataPath);
        environment.add("LANG=en_US.UTF-8");
        environment.add("HOME=" + runtimeDataPath);
        environment.add("PATH=" + System.getenv("PATH"));
        environment.add("TMPDIR=" + Config.getTemporaryDirectory(appContext));

        ArrayList<String> processArgs = new ArrayList<>();
        processArgs.add("/bin/sh");

        Log.i(Config.APP_LOG_TAG, "initiating sh session with following arguments: " + processArgs.toString());

        TerminalSession session = new TerminalSession(isLogView, "/bin/sh", processArgs.toArray(new String[0]), environment.toArray(new String[0]), runtimeDataPath, this);
        mTerminalSessions.add(session);
        updateNotification();
        return mTerminalSessions.indexOf(session);
    }

    /**
     * Creates terminal instance with running 'Logcat'.
     * @return              a created terminal session that can be attached to TerminalView.
     */
    public int createLogcatSession() {
        ArrayList<String> environment = new ArrayList<>();
        Context appContext = getApplicationContext();

        String execPath = appContext.getApplicationInfo().nativeLibraryDir;
        String runtimeDataPath = Config.getDataDirectory(appContext);

        environment.add("ANDROID_ROOT=" + System.getenv("ANDROID_ROOT"));
        environment.add("ANDROID_DATA=" + System.getenv("ANDROID_DATA"));
        environment.add("PREFIX=" + runtimeDataPath);
        environment.add("LANG=en_US.UTF-8");
        environment.add("HOME=" + runtimeDataPath);
        environment.add("PATH=" + System.getenv("PATH"));
        environment.add("TMPDIR=" + Config.getTemporaryDirectory(appContext));

        ArrayList<String> processArgs = new ArrayList<>();
        processArgs.add("/bin/logcat");
        processArgs.add("-C");
        processArgs.add("--pid=" + JNI.getPid());

        Log.i(Config.APP_LOG_TAG, "initiating sh session with following arguments: " + processArgs.toString());

        TerminalSession session = new TerminalSession(true, "/bin/logcat", processArgs.toArray(new String[0]), environment.toArray(new String[0]), runtimeDataPath, this);
        mTerminalSessions.add(session);
        updateNotification();
        return mTerminalSessions.indexOf(session);
    }

    private Notification buildNotification() {
        Intent notifyIntent = new Intent(this, TerminalActivity.class);
        // PendingIntent#getActivity(): "Note that the activity will be started outside of the context of an existing
        // activity, so you must use the Intent.FLAG_ACTIVITY_NEW_TASK launch flag in the Intent":
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);

        StringBuilder contentText = new StringBuilder();

        if (!mTerminalSessions.isEmpty()) {
            contentText.append("Virtual machine is running.");
        } else {
            contentText.append("Virtual machine is not initialized.");
        }

        final boolean wakeLockHeld = mWakeLock != null;

        if (wakeLockHeld) {
            contentText.append(" Wake lock held.");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        builder.setContentIntent(pendingIntent);
        builder.setContentTitle(getText(R.string.application_name));
        builder.setContentText(contentText.toString());
        builder.setSmallIcon(R.drawable.ic_service_notification);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOngoing(true);

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Background color for small notification icon:
        builder.setColor(0xFF000000);

        String newWakeAction = wakeLockHeld ? INTENT_ACTION_WAKELOCK_DISABLE : INTENT_ACTION_WAKELOCK_ENABLE;
        Intent toggleWakeLockIntent = new Intent(this, TerminalService.class).setAction(newWakeAction);
        String actionTitle = getResources().getString(wakeLockHeld ?
            R.string.notification_action_wake_unlock :
            R.string.notification_action_wake_lock);
        int actionIcon = wakeLockHeld ? android.R.drawable.ic_lock_idle_lock : android.R.drawable.ic_lock_lock;
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, 0));

        return builder.build();
    }

    /**
     * Update the shown foreground service notification after making any changes that affect it.
     */
    private void updateNotification() {
        if (mTerminalSessions.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            stopSelf();
        } else {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /**
     * Describe the kinds of special objects contained in this Parcelable
     * instance's marshaled representation. For example, if the object will
     * include a file descriptor in the output of {@link #writeToParcel(Parcel, int)},
     * the return value of this method must include the
     * {@link #CONTENTS_FILE_DESCRIPTOR} bit.
     *
     * @return a bitmask indicating the set of special object types marshaled
     * by this Parcelable object instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest  The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *              May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mBinder);
        dest.writeByte((byte) (mWantsToStop ? 1 : 0));
    }

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    public class LocalBinder extends Binder {
        public final TerminalService service = TerminalService.this;
    }
}
