package alpine.term;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

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
    int SESSION_ID;

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

    // available for all threads somehow
    final Object waitOnMe = new Object();

    boolean MSG_ARE_SESSIONS_EMPTY_RESULT;
    boolean MSG_DOES_SERVER_WANT_TO_STOP_RESULT;

    HandlerThread ht = new HandlerThread("threadName");
    Looper looper;
    Handler handler;
    Handler.Callback callback = new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case TerminalService.MSG_REGISTERED_CLIENT:
                    Log.e(Config.APP_LOG_TAG, "CLIENT: registered");
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_CALLBACK_INVOKED:
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                case TerminalService.MSG_ARE_SESSIONS_EMPTY:
                    MSG_ARE_SESSIONS_EMPTY_RESULT = TerminalService.toBoolean(msg.arg1);
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_SESSION_CREATED: SESSION_ID = msg.arg1;
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_DOES_SERVER_WANT_TO_STOP:
                    MSG_DOES_SERVER_WANT_TO_STOP_RESULT = TerminalService.toBoolean(msg.arg1);
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_ON_TEXT_CHANGED:
                    if (!terminalController.mIsVisible) break;
                    if (terminalController.mTerminalView.getCurrentSession() == changedSession) terminalController.mTerminalView.onScreenUpdated();
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_ON_TITLE_CHANGED:
                    if (!terminalController.mIsVisible) break;
                    if (updatedSession != terminalController.mTerminalView.getCurrentSession()) {
                        // Only show toast for other sessions than the current one, since the user
                        // probably consciously caused the title change to change in the current session
                        // and don't want an annoying toast for that.
                        terminalController.showToast(terminalController.toToastTitle(updatedSession), false);
                    }
                    mListViewAdapter.notifyDataSetChanged();
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_ON_SESSION_FINISHED:
                    // hopefully this works?
                    new Thread("") {
                        @Override
                        public void run() {
                            waitForReply();
                            sendMessageToServer(TerminalService.MSG_ARE_SESSIONS_EMPTY);
                            // Needed for resetting font size on next application launch
                            // otherwise it will be reset only after force-closing.
                            if (MSG_ARE_SESSIONS_EMPTY_RESULT) {
                                terminalController.currentFontSize = -1;
                                sendMessageToServer(TerminalService.MSG_DOES_SERVER_WANT_TO_STOP);
                                if (MSG_DOES_SERVER_WANT_TO_STOP_RESULT) {
                                    // The service wants to stop as soon as possible.
                                    terminalController.activity.finish();
                                    return;
                                }
                                sendMessageToServer(TerminalService.MSG_TERMINATE);
                            } else {
                                terminalController.switchToPreviousSession();
                                sendMessageToServer(TerminalService.MSG_REMOVE_SESSION, finishedSession);
                            }
                        }
                    }.start();
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_ON_CLIPBOARD_TEXT:
                    if (!terminalController.mIsVisible) break;
                    ClipboardManager clipboard = (ClipboardManager) terminalController.activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(text)));
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;
                case TerminalService.MSG_ON_BELL:
                    if (!terminalController.mIsVisible) break;

                    Bell.getInstance(terminalController.activity).doBell();
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
                    break;

                case TerminalService.MSG_ON_COLORS_CHANGED:
                    if (terminalController.mTerminalView.getCurrentSession() == changedSession)
                        terminalController.updateBackgroundColor();
                    sendMessageToServerNonBlocking(TerminalService.MSG_CALLBACK_INVOKED);
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
        sendMessageToServer(null, what, 0, 0, null);
    }

    private boolean sendMessageToServer(int what, int arg1) {
        sendMessageToServer(null, what, arg1, 0, null);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2) {
        return sendMessageToServer(null, what, arg1, arg2, null);
    }

    public boolean sendMessageToServer(int what, Object obj) {
        return sendMessageToServer(null, what, 0, 0, obj);
    }

    private boolean sendMessageToServer(int what, int arg1, Object obj) {
        return sendMessageToServer(null, what, arg1, 0, obj);
    }

    private boolean sendMessageToServer(int what, int arg1, int arg2, Object obj) {
        return sendMessageToServer(null, what, arg1, arg2, obj);
    }

    public boolean sendMessageToServer(Handler handler, int what, int arg1, int arg2, Object obj) {
        try {
            Message msg = Message.obtain(handler, what, arg1, arg2, obj);
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
        try {
            Message msg = Message.obtain(null,
                what);
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

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder boundService) {
        Log.e(Config.APP_LOG_TAG, "onServiceConnected() has been called");
        mService = new Messenger(boundService);
        if (ht.getState() == Thread.State.NEW) {
            ht.start();
            looper = ht.getLooper();
            handler = new Handler(looper, callback);
            mMessenger = new Messenger(handler);
        }

        // We want to monitor the service for as long as we are
        // connected to it.
        Log.e(Config.APP_LOG_TAG, "CLIENT: registering");
        sendMessageToServer(TerminalService.MSG_REGISTER_CLIENT);

        // TODO: IMPLEMENT IPC CALLBACKS
        //  when server recieves command, do callback associated with command

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

        sendMessageToServer(TerminalService.MSG_ARE_SESSIONS_EMPTY);
        if (MSG_ARE_SESSIONS_EMPTY_RESULT) {
            if (terminalController.mIsVisible) {
                TerminalSession log = createLog();
                createLogcat();
                terminalController.switchToSession(log);
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
            !sendMessageToServer(TerminalService.MSG_IS_SERVER_ALIVE)
                ? null : terminalController.mTerminalView.getCurrentSession();
    }

    public TerminalSession createLog() {
        if (!sendMessageToServer(TerminalService.MSG_IS_SERVER_ALIVE)) return null;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        sendMessageToServer(TerminalService.MSG_CREATE_SHELL_SESSION, TerminalService.toInt(true));

        // this will data race due to multi threading
        int session_id = SESSION_ID;
        sendMessageToServer(TerminalService.MSG_ATTACH_SESSION, SESSION_ID);
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        session.mSessionName = "LOG [pid=" + logPid + "]";
        JNI.puts(String.format(Locale.ENGLISH, "log has started, pid is %d", logPid));

        terminalController.switchToSession(currentSession, session);
        mListViewAdapter.notifyDataSetChanged();
        return session;
    }

    public TerminalSession createLogcat() {
        if (terminalController.mTermService == null) return null;
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createLogcatSession();
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        session.mSessionName = "logcat -C --pid=" + JNI.getPid() + " [pid=" + logPid + "]";
        JNI.puts(String.format(Locale.ENGLISH, "logcat has started, pid is %d", logPid));

        terminalController.switchToSession(currentSession, session);
        mListViewAdapter.notifyDataSetChanged();
        return session;
    }

    public TerminalSession createShell() {
        if (terminalController.mTermService == null) return null;
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createShellSession(false);
        terminalController.mTerminalView.attachSession(session);

        int shellPid;
        shellPid = session.getPid();
        session.mSessionName = "SHELL [pid=" + shellPid + "]";
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
}
