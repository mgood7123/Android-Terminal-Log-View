package alpine.term;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import alpine.term.emulator.JNI;
import alpine.term.emulator.TerminalColors;
import alpine.term.emulator.TerminalSession;
import alpine.term.emulator.TextStyle;
import alpine.term.terminal_view.TerminalView;

public class TerminalController {
    /**
     * The main view of the activity showing the terminal. Initialized in onCreate().
     */
    TerminalView mTerminalView;

    ExtraKeysView mExtraKeysView;

    TerminalPreferences mSettings;

    Activity activity;

    LayoutInflater inflater;

    AssetManager assetManager;

    TerminalService mTermService;
    TerminalControllerService terminalControllerService;


    final int MAX_FONTSIZE = 256;
    int MIN_FONTSIZE;
    int currentFontSize = -1;

    public void onCreate(
        TerminalActivity terminalActivity,
        TerminalView viewById,
        boolean isLogView
    ) {
        inflater = LayoutInflater.from(terminalActivity);
        activity = terminalActivity;
        assetManager = terminalActivity.getAssets();
        mTerminalView = viewById;
        mTerminalView.isLogView = isLogView;
        mTerminalView.setOnKeyListener(new InputDispatcher(terminalActivity, this));

        // is this needed?
        mSettings = new TerminalPreferences(terminalActivity);

        // set text size
        if (currentFontSize == -1) currentFontSize = 24;

        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum
        // font size to prevent invisible text due to zoom by mistake:
        MIN_FONTSIZE = 12;

        mTerminalView.setTextSize(currentFontSize);

        // reload terminal style
        reloadTerminalStyling();

        if (!mTerminalView.isLogView) {
            final ViewPager viewPager = terminalActivity.findViewById(R.id.viewpager);
            if (mSettings.isExtraKeysEnabled()) viewPager.setVisibility(View.VISIBLE);

            TerminalActivity finalTerminalActivity = terminalActivity;
            viewPager.setAdapter(new PagerAdapter() {
                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                    return view == object;
                }

                @NonNull
                @Override
                public Object instantiateItem(@NonNull ViewGroup collection, int position) {
                    View layout;

                    if (position == 0) {
                        layout = mExtraKeysView = (ExtraKeysView) inflater.inflate(R.layout.extra_keys_main, collection, false);
                    } else {
                        layout = inflater.inflate(R.layout.extra_keys_right, collection, false);
                        final EditText editText = layout.findViewById(R.id.text_input);

                        editText.setOnEditorActionListener((v, actionId, event) -> {
                            TerminalSession session = mTerminalView.getCurrentSession();

                            if (session != null) {
                                if (session.isRunning()) {
                                    String textToSend = editText.getText().toString();

                                    if (textToSend.length() == 0) {
                                        textToSend = "\r";
                                    }

                                    session.write(textToSend);
                                }

                                editText.setText("");
                            }

                            return true;
                        });
                    }

                    collection.addView(layout);

                    return layout;
                }

                @Override
                public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
                    collection.removeView((View) view);
                }
            });

            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    if (position == 0) {
                        mTerminalView.requestFocus();
                    } else {
                        final EditText editText = viewPager.findViewById(R.id.text_input);

                        if (editText != null) {
                            editText.requestFocus();
                        }
                    }
                }
            });
        }

        terminalActivity.registerForContextMenu(mTerminalView);

        terminalControllerService = new TerminalControllerService();
        terminalControllerService.terminalController = this;

        Intent serviceIntent = new Intent(terminalActivity, TerminalService.class);
        // Start the service and make it run regardless of who is bound to it:
        terminalActivity.startService(serviceIntent);
        if (!terminalActivity.bindService(serviceIntent, terminalControllerService, 0)) {
            throw new RuntimeException("bindService() failed");
        }
    }

    public void updateBackgroundColor() {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            mTerminalView.setBackgroundColor(session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }

    /**
     * Reload terminal color scheme and background.
     */
    public void reloadTerminalStyling() {
        String fileName = mSettings.getColorScheme();

        Properties props = new Properties();

        if (!fileName.equals("Default")) {
            try (InputStream in = assetManager.open("color_schemes/" + fileName)) {
                props.load(in);
            } catch (IOException e) {
                Log.e(Config.APP_LOG_TAG, "failed to load color scheme file '" + fileName + "' from assets", e);
            }
        }

        try {
            TerminalColors.COLOR_SCHEME.updateWith(props);
        } catch (Exception e) {
            Log.e(Config.APP_LOG_TAG, "failed to update terminal color scheme", e);
        }

        if (mTermService != null) {
            for (TerminalSession session : mTermService.getSessions()) {
                if (session != null && session.getEmulator() != null) {
                    session.getEmulator().mColors.reset();
                }
            }
        }

        updateBackgroundColor();
        mTerminalView.invalidate();
    }

    /**
     * Open dialog with spinner for selecting terminal color scheme. The file name of a picked
     * color scheme will be saved in shared preferences and loaded from assets by {@link #reloadTerminalStyling()}.
     */
    public void terminalStylingDialog() {
        ArrayAdapter<String> colorsAdapter = new ArrayAdapter<>(activity, R.layout.styling_dialog_item);

        List<String> fileNames = new ArrayList<>();
        fileNames.add("Default");

        try {
            fileNames.addAll(Arrays.asList(Objects.requireNonNull(assetManager.list("color_schemes"))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> colorSchemes = new ArrayList<>();

        for (String s : fileNames) {
            String name = s.replace('-', ' ');
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex != -1) name = name.substring(0, dotIndex);

            boolean lastWhitespace = true;
            char[] chars = name.toCharArray();

            for (int i = 0; i < chars.length; i++) {
                if (Character.isLetter(chars[i])) {
                    if (lastWhitespace) {
                        chars[i] = Character.toUpperCase(chars[i]);
                    }

                    lastWhitespace = false;
                } else {
                    lastWhitespace = Character.isWhitespace(chars[i]);
                }
            }

            colorSchemes.add(new String(chars));
        }

        colorsAdapter.addAll(colorSchemes);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setSingleChoiceItems(colorsAdapter, fileNames.indexOf(mSettings.getColorScheme()), (dialogInterface, i) -> {
            String current = fileNames.get(i);

            if (current != null) {
                showToast(activity.getResources().getString(R.string.style_toast_theme_applied) + "\n" + colorSchemes.get(i), true);
                mSettings.setColorScheme(activity, current);
            }

            reloadTerminalStyling();
            dialogInterface.dismiss();
        });

        builder.create().show();
    }

    /**
     * The current session as stored or the last one if that does not exist.
     */
    public TerminalSession getStoredCurrentSessionOrLast() {
        TerminalSession stored = TerminalPreferences.getCurrentSession(activity, mTermService);

        if (stored != null) {
            return stored;
        }

        List<TerminalSession> sessions = mTermService.getSessions();

        return sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
    }

    /**
     * Try switching to session and note about it, but do nothing if already displaying the session.
     */
    public void switchToSession(TerminalSession session) {
        if (mTerminalView.attachSession(session)) {
            if (mIsVisible) {
                final int indexOfSession = mTermService.getSessions().indexOf(session);
                terminalControllerService.mListViewAdapter.notifyDataSetChanged();
                showToast(toToastTitle(session), false);
            }

            updateBackgroundColor();
        }
    }

    /**
     * Switch to next or previous session,
     */
    public void switchToSession(boolean forward) {
        TerminalSession currentSession = mTerminalView.getCurrentSession();
        int index = mTermService.getSessions().indexOf(currentSession);
        if (forward) {
            if (++index >= mTermService.getSessions().size()) index = 0;
        } else {
            if (--index < 0) index = mTermService.getSessions().size() - 1;
        }
        switchToSession(mTermService.getSessions().get(index));
    }


    public void onStart() {
        mIsVisible = true;
        if (mTermService != null) {
            // The service has connected, but data may have changed since we were last in the foreground.
            switchToSession(getStoredCurrentSessionOrLast());
            terminalControllerService.mListViewAdapter.notifyDataSetChanged();
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal:
        mTerminalView.onScreenUpdated();
    }

    public void onStop() {
        mIsVisible = false;
        TerminalSession currentSession = mTerminalView.getCurrentSession();
        if (currentSession != null) TerminalPreferences.storeCurrentSession(activity, currentSession);
    }

    public void onDestroy() {
        if (mTermService != null) {
            // Do not leave service with references to activity.
            mTermService.mSessionChangeCallback = null;
            mTermService = null;
        }
        activity.unbindService(terminalControllerService);
    }

    private static final int CONTEXTMENU_VNC_VIEWER = 0;
    private static final int CONTEXTMENU_SHOW_HELP = 1;
    private static final int CONTEXTMENU_SELECT_URL_ID = 2;
    private static final int CONTEXTMENU_SHARE_TRANSCRIPT_ID = 3;
    private static final int CONTEXTMENU_PASTE_ID = 4;
    private static final int CONTEXTMENU_RESET_TERMINAL_ID = 5;
    private static final int CONTEXTMENU_CONSOLE_STYLE = 6;
    private static final int CONTEXTMENU_TOGGLE_IGNORE_BELL = 7;

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        TerminalSession currentSession = mTerminalView.getCurrentSession();

        if (currentSession == null) {
            return;
        }

        menu.add(Menu.NONE, 99, Menu.NONE, "printf something to the terminal");
        menu.add(Menu.NONE, CONTEXTMENU_SHOW_HELP, Menu.NONE, R.string.menu_show_help);
        menu.add(Menu.NONE, CONTEXTMENU_SELECT_URL_ID, Menu.NONE, R.string.menu_select_url);
        menu.add(Menu.NONE, CONTEXTMENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.menu_share_transcript);
        menu.add(Menu.NONE, CONTEXTMENU_RESET_TERMINAL_ID, Menu.NONE, R.string.menu_reset_terminal);
        menu.add(Menu.NONE, CONTEXTMENU_CONSOLE_STYLE, Menu.NONE, R.string.menu_console_style);
    }

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    private Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    public boolean mIsVisible;

    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = mTerminalView.getCurrentSession();

        switch (item.getItemId()) {
            case 99: {
                JNI.printf("TERMINAL: invoking printf");
                JNI.test_printf();
                return true;
            }
            case CONTEXTMENU_SHOW_HELP:
                activity.startActivity(new Intent(activity, HelpActivity.class));
                return true;
            case CONTEXTMENU_CONSOLE_STYLE:
                terminalStylingDialog();
                return true;
            case CONTEXTMENU_SELECT_URL_ID:
                showUrlSelection();
                return true;
            case CONTEXTMENU_SHARE_TRANSCRIPT_ID:
                if (session != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    String transcriptText = session.getEmulator().getScreen().getTranscriptTextWithoutJoinedLines().trim();
                    // See https://github.com/termux/termux-app/issues/1166.
                    final int MAX_LENGTH = 100_000;
                    if (transcriptText.length() > MAX_LENGTH) {
                        int cutOffIndex = transcriptText.length() - MAX_LENGTH;
                        int nextNewlineIndex = transcriptText.indexOf('\n', cutOffIndex);
                        if (nextNewlineIndex != -1 && nextNewlineIndex != transcriptText.length() - 1) {
                            cutOffIndex = nextNewlineIndex + 1;
                        }
                        transcriptText = transcriptText.substring(cutOffIndex).trim();
                    }
                    intent.putExtra(Intent.EXTRA_TEXT, transcriptText);
                    intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.share_transcript_file_name));
                    activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_transcript_chooser_title)));
                }
                return true;
            case CONTEXTMENU_PASTE_ID:
                doPaste();
                return true;
            case CONTEXTMENU_RESET_TERMINAL_ID: {
                if (session != null) {
                    session.reset(true);
                    showToast(activity.getResources().getString(R.string.reset_toast_notification), true);
                }
                return true;
            }

            default:
                return false;
        }
    }

    public void onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
    }

    /**
     * Change terminal font size.
     */
    public void changeFontSize(boolean increase) {
        currentFontSize += (increase ? 1 : -1) * 2;
        currentFontSize = Math.max(MIN_FONTSIZE, Math.min(currentFontSize, MAX_FONTSIZE));
        mTerminalView.setTextSize(currentFontSize);
    }

    /**
     * Toggle extra keys layout.
     */
    public void toggleShowExtraKeys() {
        final ViewPager viewPager = activity.findViewById(R.id.viewpager);
        final boolean showNow = mSettings.toggleShowExtraKeys(activity);

        viewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);

        if (showNow && viewPager.getCurrentItem() == 1) {
            // Focus the text input view if just revealed.
            activity.findViewById(R.id.text_input).requestFocus();
        }
    }

    /**
     * Paste text from clipboard.
     */
    public void doPaste() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            ClipData clipData = clipboard.getPrimaryClip();

            if (clipData == null) {
                return;
            }

            CharSequence paste = clipData.getItemAt(0).coerceToText(activity);
            if (!TextUtils.isEmpty(paste)) {
                TerminalSession currentSession = mTerminalView.getCurrentSession();

                if (currentSession != null) {
                    currentSession.getEmulator().paste(paste.toString());
                }
            }
        }
    }

    /**
     * Extract URLs from the current transcript and show them in dialog.
     */
    public void showUrlSelection() {
        TerminalSession currentSession = mTerminalView.getCurrentSession();

        if (currentSession == null) {
            return;
        }

        String text = currentSession.getEmulator().getScreen().getTranscriptText();
        LinkedHashSet<CharSequence> urlSet = extractUrls(text);

        if (urlSet.isEmpty()) {
            showToast(activity.getResources().getString(R.string.select_url_toast_no_found), true);
            return;
        }

        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        Collections.reverse(Arrays.asList(urls)); // Latest first.

        // Click to copy url to clipboard:
        final AlertDialog dialog = new AlertDialog.Builder(activity).setItems(urls, (di, which) -> {
            String url = (String) urls[which];
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(url)));
                showToast(activity.getResources().getString(R.string.select_url_toast_copied_to_clipboard), true);
            }
        }).setTitle(R.string.select_url_dialog_title).create();

        // Long press to open URL:
        dialog.setOnShowListener(di -> {
            ListView lv = dialog.getListView(); // this is a ListView with your "buds" in it
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];

                // Disable handling of 'file://' urls since this may
                // produce android.os.FileUriExposedException.
                if (!url.startsWith("file://")) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try {
                        activity.startActivity(i, null);
                    } catch (ActivityNotFoundException e) {
                        // If no applications match, Android displays a system message.
                        activity.startActivity(Intent.createChooser(i, null));
                    }
                } else {
                    showToast(activity.getResources().getString(R.string.select_url_toast_cannot_open), false);
                }

                return true;
            });
        });

        dialog.show();
    }

    /**
     * Extract URLs from the given text.
     */
    @SuppressWarnings("StringBufferReplaceableByString")
    private static LinkedHashSet<CharSequence> extractUrls(String text) {
        StringBuilder regex_sb = new StringBuilder();

        regex_sb.append("(");                       // Begin first matching group.
        regex_sb.append("(?:");                     // Begin scheme group.
        regex_sb.append("dav|");                    // The DAV proto.
        regex_sb.append("dict|");                   // The DICT proto.
        regex_sb.append("dns|");                    // The DNS proto.
        regex_sb.append("file|");                   // File path.
        regex_sb.append("finger|");                 // The Finger proto.
        regex_sb.append("ftp(?:s?)|");              // The FTP proto.
        regex_sb.append("git|");                    // The Git proto.
        regex_sb.append("gopher|");                 // The Gopher proto.
        regex_sb.append("http(?:s?)|");             // The HTTP proto.
        regex_sb.append("imap(?:s?)|");             // The IMAP proto.
        regex_sb.append("irc(?:[6s]?)|");           // The IRC proto.
        regex_sb.append("ip[fn]s|");                // The IPFS proto.
        regex_sb.append("ldap(?:s?)|");             // The LDAP proto.
        regex_sb.append("pop3(?:s?)|");             // The POP3 proto.
        regex_sb.append("redis(?:s?)|");            // The Redis proto.
        regex_sb.append("rsync|");                  // The Rsync proto.
        regex_sb.append("rtsp(?:[su]?)|");          // The RTSP proto.
        regex_sb.append("sftp|");                   // The SFTP proto.
        regex_sb.append("smb(?:s?)|");              // The SAMBA proto.
        regex_sb.append("smtp(?:s?)|");             // The SMTP proto.
        regex_sb.append("svn(?:(?:\\+ssh)?)|");     // The Subversion proto.
        regex_sb.append("tcp|");                    // The TCP proto.
        regex_sb.append("telnet|");                 // The Telnet proto.
        regex_sb.append("tftp|");                   // The TFTP proto.
        regex_sb.append("udp|");                    // The UDP proto.
        regex_sb.append("vnc|");                    // The VNC proto.
        regex_sb.append("ws(?:s?)");                // The Websocket proto.
        regex_sb.append(")://");                    // End scheme group.
        regex_sb.append(")");                       // End first matching group.

        // Begin second matching group.
        regex_sb.append("(");

        // User name and/or password in format 'user:pass@'.
        regex_sb.append("(?:\\S+(?::\\S*)?@)?");

        // Begin host group.
        regex_sb.append("(?:");

        // IP address (from http://www.regular-expressions.info/examples.html).
        regex_sb.append("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|");

        // Host name or domain.
        regex_sb.append("(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,})))?|");

        // Just path. Used in case of 'file://' scheme.
        regex_sb.append("/(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)");

        // End host group.
        regex_sb.append(")");

        // Port number.
        regex_sb.append("(?::\\d{1,5})?");

        // Resource path with optional query string.
        regex_sb.append("(?:/[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");

        // Fragment.
        regex_sb.append("(?:#[a-zA-Z0-9:@%\\-._~!$&()*+,;=?/]*)?");

        // End second matching group.
        regex_sb.append(")");

        final Pattern urlPattern = Pattern.compile(
            regex_sb.toString(),
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

        LinkedHashSet<CharSequence> urlSet = new LinkedHashSet<>();
        Matcher matcher = urlPattern.matcher(text);

        while (matcher.find()) {
            int matchStart = matcher.start(1);
            int matchEnd = matcher.end();
            String url = text.substring(matchStart, matchEnd);
            urlSet.add(url);
        }

        return urlSet;
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    public void showToast(String text, boolean longDuration) {
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(activity, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);

        TextView toastText = mLastToast.getView().findViewById(android.R.id.message);
        if (toastText != null) {
            toastText.setGravity(Gravity.CENTER);
        }

        mLastToast.show();
    }

    /**
     * Create a message with session name ready for showing in toast.
     */
    public String toToastTitle(TerminalSession session) {
        final int indexOfSession = mTermService.getSessions().indexOf(session);

        StringBuilder toastTitle = new StringBuilder("[" + (indexOfSession + 1) + "]");

        if (!TextUtils.isEmpty(session.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName);
        }

        String title = session.getTitle();

        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(session.mSessionName == null ? " " : "\n");
            toastTitle.append(title);
        }

        return toastTitle.toString();
    }
}
