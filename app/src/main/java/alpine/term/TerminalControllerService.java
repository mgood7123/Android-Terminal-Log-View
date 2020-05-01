package alpine.term;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import alpine.term.emulator.JNI;
import alpine.term.emulator.TerminalSession;

public class TerminalControllerService implements ServiceConnection {

    TerminalController terminalController = null;

    /**
     * Initialized in {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    ArrayAdapter<TerminalSession> mListViewAdapter;

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

    boolean isLocalService = false;

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder boundService) {
        Log.e(Config.APP_LOG_TAG, "onServiceConnected() has been called");
        if (boundService instanceof TerminalService.LocalBinder) {
            TerminalService.LocalBinder b = (TerminalService.LocalBinder) boundService;
            terminalController.mTermService = b.service;
            Log.e(Config.APP_LOG_TAG, "CLIENT: CREATED LOCAL SERVICE");
            isLocalService = true;
        } else {
            mService = new Messenger(boundService);
            Log.e(Config.APP_LOG_TAG, "CLIENT: CREATED REMOTE SERVICE");
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
            Log.e(Config.APP_LOG_TAG, "CLIENT: unregistering");
            sendMessageToServer(TerminalService.MSG_UNREGISTER_CLIENT);
            Log.e(Config.APP_LOG_TAG, "CLIENT: registering");
            sendMessageToServer(TerminalService.MSG_REGISTER_CLIENT);
            if (terminalController != null) {
                if (terminalController.activity == null) {
                    Log.e(Config.APP_LOG_TAG, "ERROR: ACTIVITY HAS NOT BEEN STARTED");
                    return;
                }
            }
        }
        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
        if (!isLocalService) return;

        terminalController.mTermService.mSessionChangeCallback = new TerminalSession.SessionChangedCallback() {
            @Override
            public void onTextChanged(TerminalSession changedSession) {
                if (!terminalController.mIsVisible) return;
                if (terminalController.mTerminalView.getCurrentSession() == changedSession) terminalController.mTerminalView.onScreenUpdated();
            }

            @Override
            public void onTitleChanged(TerminalSession updatedSession) {
                if (!terminalController.mIsVisible) return;
                if (updatedSession != terminalController.mTerminalView.getCurrentSession()) {
                    // Only show toast for other sessions than the current one, since the user
                    // probably consciously caused the title change to change in the current session
                    // and don't want an annoying toast for that.
                    terminalController.showToast(terminalController.toToastTitle(updatedSession), false);
                }
                mListViewAdapter.notifyDataSetChanged();
            }

            @Override
            public void onSessionFinished(final TerminalSession finishedSession) {
                // Needed for resetting font size on next application launch
                // otherwise it will be reset only after force-closing.
                if (terminalController.mTermService.getSessions().isEmpty()) {
                    terminalController.currentFontSize = -1;
                    if (terminalController.mTermService.mWantsToStop) {
                        // The service wants to stop as soon as possible.
                        terminalController.activity.finish();
                        return;
                    }

                    terminalController.mTermService.terminateService();
                } else {
                    terminalController.switchToPreviousSession();
                    terminalController.mTermService.removeSession(finishedSession);
                }
            }

            @Override
            public void onClipboardText(TerminalSession session, String text) {
                if (!terminalController.mIsVisible) return;
                ClipboardManager clipboard = (ClipboardManager) terminalController.activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(text)));
            }

            @Override
            public void onBell(TerminalSession session) {
                if (!terminalController.mIsVisible) {
                    return;
                }

                Bell.getInstance(terminalController.activity).doBell();
            }

            @Override
            public void onColorsChanged(TerminalSession changedSession) {
                if (terminalController.mTerminalView.getCurrentSession() == changedSession)
                    terminalController.updateBackgroundColor();
            }
        };

        mListViewAdapter = new ArrayAdapter<TerminalSession>(terminalController.activity, R.layout.line_in_drawer, terminalController.mTermService.getSessions()) {
            final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
            final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = terminalController.inflater.inflate(R.layout.line_in_drawer, parent, false);
                }

                TerminalSession sessionAtRow = getItem(position);
                boolean sessionRunning = sessionAtRow.isRunning();

                TextView firstLineView = row.findViewById(R.id.row_line);

                String name = sessionAtRow.mSessionName;
                String sessionTitle = sessionAtRow.getTitle();

                String numberPart = "[" + (position + 1) + "] ";
                String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
                String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

                String text = numberPart + sessionNamePart + sessionTitlePart;
                SpannableString styledText = new SpannableString(text);
                styledText.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styledText.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                firstLineView.setText(styledText);

                if (sessionRunning) {
                    firstLineView.setPaintFlags(firstLineView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    firstLineView.setPaintFlags(firstLineView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? Color.BLACK : Color.RED;
                firstLineView.setTextColor(color);
                return row;
            }
        };

        ListView listView = terminalController.activity.findViewById(R.id.left_drawer_list);
        mListViewAdapter = new ArrayAdapter<TerminalSession>(terminalController.activity.getApplicationContext(), R.layout.line_in_drawer, terminalController.mTermService.getSessions()) {
            final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
            final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = terminalController.inflater.inflate(R.layout.line_in_drawer, parent, false);
                }

                TerminalSession sessionAtRow = getItem(position);
                boolean sessionRunning = sessionAtRow.isRunning();

                TextView firstLineView = row.findViewById(R.id.row_line);

                String name = sessionAtRow.mSessionName;
                String sessionTitle = sessionAtRow.getTitle();

                String numberPart = "[" + (position + 1) + "] ";
                String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
                String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

                String text = numberPart + sessionNamePart + sessionTitlePart;
                SpannableString styledText = new SpannableString(text);
                styledText.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styledText.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                firstLineView.setText(styledText);

                if (sessionRunning) {
                    firstLineView.setPaintFlags(firstLineView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    firstLineView.setPaintFlags(firstLineView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? Color.BLACK : Color.RED;
                firstLineView.setTextColor(color);
                return row;
            }
        };

        listView.setAdapter(mListViewAdapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            TerminalSession clickedSession = mListViewAdapter.getItem(position);
            TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();
            terminalController.switchToSession(currentSession, clickedSession);
            terminalController.getDrawer().closeDrawers();
        });

        if (terminalController.mTermService.getSessions().isEmpty()) {
            if (terminalController.mIsVisible) {
                TerminalSession log = createLog(true);
                createLogcat();
                terminalController.switchToSession(log);
                // create a log and logcat for each registered activity
                for (TrackedActivity activity : terminalController.mTermService.mTrackedActivities) {
                    log = createLog(activity, false);
                    createLogcat(activity);
                    terminalController.switchToSession(log);
                }
            } else {
                // The service connected while not in foreground - just bail out.
                terminalController.activity.finish();
            }
        } else {
            terminalController.switchToSession(terminalController.getStoredCurrentSessionOrLast());
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Respect being stopped from the TerminalService notification action.
        terminalController.activity.finish();
    }

    public TerminalSession getCurrentSession() {
        return
            terminalController.mTermService == null
                ? null : terminalController.mTerminalView.getCurrentSession();
    }

    public TerminalSession createLog(boolean printWelcomeMessage) {
        return createLog(null, printWelcomeMessage);
    }

    public TerminalSession createLog(TrackedActivity activity, boolean printWelcomeMessage) {
        if (terminalController.mTermService == null) return null;
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createShellSession(true, activity, printWelcomeMessage);
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        if (activity == null)
            session.mSessionName = "LOG [LOCAL: pid=" + logPid + "]";
        else
            session.mSessionName = "LOG [CONNECTED: pid=" + logPid + ", " + activity.packageName + "]";
        JNI.puts(String.format(Locale.ENGLISH, "log has started, pid is %d", logPid));

        terminalController.switchToSession(currentSession, session);
        mListViewAdapter.notifyDataSetChanged();
        return session;
    }

    public TerminalSession createLogcat() {
        return createLogcat(null);
    }

    public TerminalSession createLogcat(TrackedActivity activity) {
        if (terminalController.mTermService == null) return null;
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createLogcatSession(activity);
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        if (activity == null)
            session.mSessionName = "logcat -C --pid=" + JNI.getPid() + " [LOCAL: pid=" + logPid + "]";
        else
            session.mSessionName =
                "logcat -C --pid=" + activity.pid + " [CONNECTED: " + activity.packageName + "]";
        JNI.puts(String.format(Locale.ENGLISH, "logcat has started, pid is %d", logPid));

        terminalController.switchToSession(currentSession, session);
        mListViewAdapter.notifyDataSetChanged();
        return session;
    }

    public TerminalSession createShell() {
        return createShell(null);
    }

    public TerminalSession createShell(TrackedActivity activity) {
        if (terminalController.mTermService == null) return null;
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createShellSession(false, activity, false);
        terminalController.mTerminalView.attachSession(session);

        int shellPid;
        shellPid = session.getPid();
        session.mSessionName = "SHELL [LOCAL: pid=" + shellPid + "]";
        JNI.puts(String.format(Locale.ENGLISH, "shell has started, pid is %d", shellPid));

        terminalController.switchToSession(currentSession, session);
        mListViewAdapter.notifyDataSetChanged();
        return session;
    }

    public Boolean isCurrentSessionShell() {
        return
            terminalController.mTermService == null
                ? null : terminalController.mTerminalView.getCurrentSession().isShell();
    }

    public Boolean isCurrentSessionLogView() {
        return
            terminalController.mTermService == null
                ? null : terminalController.mTerminalView.getCurrentSession().isLogView();
    }

    public void runOnServiceConnect(Runnable runnable) {
        runnableArrayList.add(runnable);
    }

    public void bindToTerminalService(Activity activity) {
        Intent serviceIntent = new Intent(activity, TerminalService.class);
        serviceIntent.setPackage("alpine.term");

        // Start the service and make it run regardless of who is bound to it:
        activity.startService(serviceIntent);

        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!activity.bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }
        mIsBound = true;
    }

    public void registerActivity(Activity activity, int pseudoTerminal) {
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
