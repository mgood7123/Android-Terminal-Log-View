package alpine.term;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import alpine.term.emulator.TerminalSession;

public class TerminalControllerService implements ServiceConnection {

    TerminalController terminalController = null;

    /**
     * Initialized in {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    ArrayAdapter<TerminalSession> mListViewAdapter;

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        terminalController.mTermService = ((TerminalService.LocalBinder) service).service;

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
                terminalController.currentFontSize = -1;

                if (terminalController.mTermService.mWantsToStop) {
                    // The service wants to stop as soon as possible.
                    terminalController.activity.finish();
                    return;
                }

                terminalController.mTermService.terminateService();
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

        if (terminalController.mTermService.getSessions().isEmpty()) {
            if (terminalController.mIsVisible) {
                if (terminalController.mTermService == null) return; // Activity might have been destroyed.
                try {
                    TerminalSession session;

                    session = terminalController.mTermService.createShellSession();
                    session.mSessionName = "SHELL";
                    terminalController.mTerminalView.attachSession(session);
                    terminalController.switchToSession(terminalController.mTermService.getSessions().get(0));
                } catch (WindowManager.BadTokenException e) {
                    // Activity finished - ignore.
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
}
