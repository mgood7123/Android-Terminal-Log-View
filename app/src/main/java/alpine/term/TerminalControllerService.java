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
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.libclient_service.LibService_Messenger;

import java.util.ArrayList;
import java.util.Locale;

import alpine.term.emulator.JNI;
import alpine.term.emulator.TerminalSession;

public class TerminalControllerService implements ServiceConnection {

    LogUtils logUtils = new LogUtils("Terminal Controller Service");

    TerminalController terminalController = null;

    /**
     * Initialized in {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    ArrayAdapter<TerminalSession> mListViewAdapter;

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    public boolean mIsBound;

    private ArrayList<Runnable> runnableArrayList = new ArrayList<>();

    boolean isLocalService = false;

    Context context = null;

    LibService_Messenger libService_messenger = new LibService_Messenger();

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder boundService) {
        logUtils.logMethodName_Info();
        if (boundService instanceof TerminalService.LocalBinder) {
            TerminalService.LocalBinder b = (TerminalService.LocalBinder) boundService;
            terminalController.mTermService = b.service;
            context = b.service.getApplicationContext();
            terminalController.mTermService.terminalControllerService = this;
            logUtils.log_Info("binded to local service");
            isLocalService = true;
        } else {
            libService_messenger
                .addResponse(TerminalService.MSG_NO_REPLY)
                .addResponse(TerminalService.MSG_REGISTERED_CLIENT, () -> {
                    libService_messenger.log.log_Info("registered");
                    libService_messenger.sendMessageToServerNonBlocking(
                        TerminalService.MSG_CALLBACK_INVOKED
                    );
                })
                .addResponse(TerminalService.MSG_UNREGISTERED_CLIENT, () -> {
                    libService_messenger.log.log_Info("unregistered");
                    libService_messenger.sendMessageToServerNonBlocking(
                        TerminalService.MSG_CALLBACK_INVOKED
                    );
                })
                .addResponse(TerminalService.MSG_UNREGISTERED_CLIENT, () -> {
                    libService_messenger.log.log_Info("SERVER IS ALIVE");
                    libService_messenger.sendMessageToServerNonBlocking(
                        TerminalService.MSG_CALLBACK_INVOKED
                    );
                })
                .addResponse(TerminalService.MSG_REGISTER_ACTIVITY_FAILED, () -> {
                    libService_messenger.sendMessageToServerNonBlocking(
                        TerminalService.MSG_CALLBACK_INVOKED
                    );
                })
                .addResponse(TerminalService.MSG_REGISTERED_ACTIVITY, () -> {
                    libService_messenger.sendMessageToServerNonBlocking(
                        TerminalService.MSG_CALLBACK_INVOKED
                    );
                })
                .addResponse(TerminalService.MSG_STARTED_TERMINAL_ACTIVITY);
            libService_messenger.start(boundService);
            // We want to monitor the service for as long as we are
            // connected to it.
            logUtils.log_Info("registering");
            libService_messenger.sendMessageToServer(TerminalService.MSG_REGISTER_CLIENT);
            if (terminalController != null) {
                if (terminalController.activity == null) {
                    logUtils.log_Error("activity has not been started");
                    return;
                }
            }
        }
        for (Runnable action : runnableArrayList) {
            action.run();
        }
        runnableArrayList.clear();
        // if this is a remote service, then return
        if (!isLocalService) return;

        terminalController.mTermService.mSessionChangeCallback = new TerminalSession.SessionChangedCallback() {
            @Override
            public void onTextChanged(TerminalSession changedSession) {
                if (!terminalController.mIsVisible) return;
                if (terminalController.mTerminalView.getCurrentSession() == changedSession) {
                    terminalController.activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            terminalController.mTerminalView.onScreenUpdated();
                        }
                    });
                }
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
                terminalController.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mListViewAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onSessionFinished(final TerminalSession finishedSession) {
                if (terminalController.mTermService.getSessions().isEmpty()) {
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
                TerminalSession log = createLog(true, context);
                createLogcat(context);
                terminalController.switchToSession(log);
                // create a log and logcat for each registered activity
                for (TrackedActivity trackedActivity : terminalController.mTermService.mTrackedActivities) {
                    log = createLog(trackedActivity, false, context);
                    createLogcat(trackedActivity, context);
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

    public TerminalSession createLog(boolean printWelcomeMessage, Context context) {
        return createLog(null, printWelcomeMessage, context);
    }

    public TerminalSession createLog(TrackedActivity trackedActivity, boolean printWelcomeMessage, Context context) {
        if (terminalController.mTermService == null) {
            logUtils.log_Error("error: terminalController.mTermService is null");
            return null;
        }
        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createShellSession(true, trackedActivity, printWelcomeMessage, context);
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        if (session.isTrackedActivity) {
            session.mSessionName = "LOG [CONNECTED: pid=" + logPid + ", " + trackedActivity.packageName + "]";
        } else {
            session.mSessionName = "LOG [LOCAL: pid=" + logPid + "]";
        }
        JNI.puts(String.format(Locale.ENGLISH, "log has started, pid is %d", logPid));

        terminalController.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                terminalController.switchToSession(currentSession, session);
                mListViewAdapter.notifyDataSetChanged();
            }
        });
        return session;
    }

    private TerminalSession createLogcat(Context context) {
        return createLogcat(null, false, context);
    }

    public TerminalSession createLogcat(TrackedActivity trackedActivity, Context context) {
        return createLogcat(null, false, context);
    }

    public TerminalSession createLogcat(TrackedActivity trackedActivity, boolean useRoot, Context context) {
        if (terminalController.mTermService == null) {
            logUtils.log_Error("error: terminalController.mTermService is null");
            return null;
        }

        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createLogcatSession(trackedActivity, useRoot, context);
        terminalController.mTerminalView.attachSession(session);

        int logPid;
        logPid = session.getPid();
        if (session.isTrackedActivity) {
            session.mSessionName =
                "logcat -C --pid=" + session.trackedActivityPid +
                    " [CONNECTED: " + trackedActivity.packageName + "]";
        } else {
            session.mSessionName = "logcat -C --pid=" + JNI.getPid() + " [LOCAL: pid=" + logPid + "]";
        }
        JNI.puts(String.format(Locale.ENGLISH, "logcat has started, pid is %d", logPid));

        terminalController.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                terminalController.switchToSession(currentSession, session);
                mListViewAdapter.notifyDataSetChanged();
            }
        });
        return session;
    }

    public TerminalSession createShell(Context context) {
        return createShell(null, context);
    }

    public TerminalSession createShell(TrackedActivity trackedActivity, Context context) {
        if (terminalController.mTermService == null) {
            logUtils.log_Error("error: terminalController.mTermService is null");
            return null;
        }

        TerminalSession session;
        TerminalSession currentSession = terminalController.mTerminalView.getCurrentSession();

        session = terminalController.mTermService.createShellSession(false, trackedActivity, false, context);
        terminalController.mTerminalView.attachSession(session);

        int shellPid;
        shellPid = session.getPid();
        session.mSessionName = "SHELL [LOCAL: pid=" + shellPid + "]";
        JNI.puts(String.format(Locale.ENGLISH, "shell has started, pid is %d", shellPid));

        terminalController.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                terminalController.switchToSession(currentSession, session);
                mListViewAdapter.notifyDataSetChanged();
            }
        });
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

    public void runWhenConnectedToService(Runnable runnable) {
        runnableArrayList.add(runnable);
    }

    public void bindToTerminalService(Activity activity) {
        Intent serviceIntent = new Intent("alpine.term.TerminalControllerService");
        serviceIntent.setPackage("alpine.term");

        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!activity.bindService(serviceIntent, this, Context.BIND_EXTERNAL_SERVICE)) {
            throw new RuntimeException("bindService() failed");
        }
        mIsBound = true;
    }

    public void registerActivity(final Activity activity, final int[] pseudoTerminal) {
        runWhenConnectedToService(() -> {
            logUtils.log_Error("REGISTERING ACTIVITY");
            registerActivity_(activity, pseudoTerminal);
            logUtils.log_Error("REGISTERED ACTIVITY");
        });
    }

    private void registerActivity_(Activity activity, int[] pseudoTerminal) {
        TrackedActivity trackedActivity = new TrackedActivity();
        trackedActivity.storePseudoTerminal(pseudoTerminal);
        trackedActivity.packageName = activity.getPackageName();
        trackedActivity.pid = JNI.getPid();
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
        libService_messenger.sendMessageToServer(TerminalService.MSG_REGISTER_ACTIVITY, bundle);
    }

    public void startTerminalActivity() {
        runWhenConnectedToService(
            () -> {
                libService_messenger.sendMessageToServer(
                    TerminalService.MSG_START_TERMINAL_ACTIVITY
                );
            }
        );
    }
}
