/*
*************************************************************************
Alpine Term - a VM-based terminal emulator.
Copyright (C) 2019-2020  Leonid Plyushch <leonid.plyushch@gmail.com>

Originally was part of Termux.
Copyright (C) 2019  Fredrik Fornwall <fredrik@fornwall.net>

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
package alpine.term.emulator;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 * <p>
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * {@link #updateSize(int, int)} terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the main thread.
 * <p>
 * The child process may be exited forcefully by using the {@link #finishIfRunning()} method.
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public final class TerminalSession extends TerminalOutput {

    /** Callback to be invoked when a {@link TerminalSession} changes. */
    public interface SessionChangedCallback {
        void onTextChanged(TerminalSession changedSession);

        void onTitleChanged(TerminalSession changedSession);

        void onSessionFinished(TerminalSession finishedSession);

        void onClipboardText(TerminalSession session, String text);

        void onBell(TerminalSession session);

        void onColorsChanged(TerminalSession session);

    }

    private static FileDescriptor wrapFileDescriptor(int fileDescriptor) {
        FileDescriptor result = new FileDescriptor();
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (NoSuchFieldException e) {
                // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            descriptorField.setAccessible(true);
            descriptorField.set(result, fileDescriptor);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Log.wtf(EmulatorDebug.LOG_TAG, "error accessing FileDescriptor#descriptor private field", e);
            System.exit(1);
        }
        return result;
    }

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_PROCESS_EXITED = 4;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /**
     * A queue written to from a separate thread when the process outputs, and read by main thread to process by
     * terminal emulator.
     */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(4096);
    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards by
     * writing to the {@link #mTerminalFileDescriptor}.
     */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
    /** Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Callback which gets notified when a session finishes or changes title. */
    final SessionChangedCallback mChangeCallback;

    /** The pid of the shell process. 0 if not started and -1 if finished running. */
    int mShellPid;

    /** The exit status of the shell process. Only valid if ${@link #mShellPid} is -1. */
    int mShellExitStatus;

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * {@link JNI#createSubprocess(String, String, String[], String[], int[], int, int)}.
     */
    private int mTerminalFileDescriptor;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    @SuppressLint("HandlerLeak")
    final Handler mMainThreadHandler = new Handler() {
        final byte[] mReceiveBuffer = new byte[4 * 1024];

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_NEW_INPUT && isRunning()) {
                int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
                if (bytesRead > 0) {
                    mEmulator.append(mReceiveBuffer, bytesRead);
                    notifyScreenUpdate();
                }
            } else if (msg.what == MSG_PROCESS_EXITED) {
                int exitCode = (Integer) msg.obj;
                cleanupResources(exitCode);
                mChangeCallback.onSessionFinished(TerminalSession.this);

                String exitDescription = "\r\n[Process completed";
                if (exitCode > 0) {
                    // Non-zero process exit.
                    exitDescription += " (code " + exitCode + ")";
                } else if (exitCode < 0) {
                    // Negated signal.
                    exitDescription += " (signal " + (-exitCode) + ")";
                }
                exitDescription += "]";

                byte[] bytesToWrite = exitDescription.getBytes(StandardCharsets.UTF_8);
                mEmulator.append(bytesToWrite, bytesToWrite.length);
                notifyScreenUpdate();
            }
        }
    };

    private final String mShellPath;
    private final String[] mArgs;
    private final String[] mEnv;
    private final String mCwd;
    private final boolean isLogView;

    public TerminalSession(boolean isLogView, String shellPath, String[] args, String[] env, String cwd, SessionChangedCallback changeCallback) {
        mChangeCallback = changeCallback;

        this.isLogView = isLogView;
        this.mShellPath = shellPath;
        this.mArgs = args;
        this.mEnv = env;
        this.mCwd = cwd;
    }

    /** Inform the attached pty of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows);
        } else {
            JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns);
            mEmulator.resize(columns, rows);
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    public void createShellSession(int columns, int rows) {
        Log.w(EmulatorDebug.LOG_TAG, "creating shell");
        int[] processId = new int[1];
        mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns);
        mShellPid = processId[0];
        Log.w(EmulatorDebug.LOG_TAG, "created shell");

        final FileDescriptor terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor);

        new Thread("TermSessionInputReader[pid=" + mShellPid + "]") {
            @Override
            public void run() {
                {
                    String fmt = "starting stdout/stderr reader";
                    JNI.printf(fmt);
                    Log.w(EmulatorDebug.LOG_TAG, fmt);
                }
                try (InputStream termIn = new FileInputStream(terminalFileDescriptorWrapped)) {
                    final byte[] buffer = new byte[4096];
                    while (true) {
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: reading");
                        int read = termIn.read(buffer);
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: read");
                        if (read == -1) {
                            Log.wtf(EmulatorDebug.LOG_TAG, "stdout/stderr reader return -1");
                            return;
                        }
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: writing");
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) {
                            Log.wtf(EmulatorDebug.LOG_TAG, "stdout/stderr reader [write] returned false (closed)");
                            return;
                        }
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: wrote");
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    }
                } catch (Exception e) {
                    // Ignore, just shutting down.
                }
            }
        }.start();

        new Thread("TermSessionOutputWriter[pid=" + mShellPid + "]") {
            @Override
            public void run() {
                {
                    String fmt = "starting stdin writer";
                    JNI.printf(fmt);
                    Log.w(EmulatorDebug.LOG_TAG, fmt);
                }
                final byte[] buffer = new byte[4096];
                try (FileOutputStream termOut = new FileOutputStream(terminalFileDescriptorWrapped)) {
                    while (true) {
                        Log.w(EmulatorDebug.LOG_TAG, "stdin writer: reading");
                        int bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true);
                        Log.w(EmulatorDebug.LOG_TAG, "stdin writer: read");
                        if (bytesToWrite == -1) {
                            Log.wtf(EmulatorDebug.LOG_TAG, "stdin writer return -1");
                            return;
                        }
                        Log.w(EmulatorDebug.LOG_TAG, "stdin writer: writing");
                        termOut.write(buffer, 0, bytesToWrite);
                        Log.w(EmulatorDebug.LOG_TAG, "stdin writer: wrote");
                    }
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }.start();
    }

    public void createLogSession(int columns, int rows) {
        Log.w(EmulatorDebug.LOG_TAG, "creating log");
        mTerminalFileDescriptor = JNI.createLog(mShellPath, mCwd, mArgs, mEnv, rows, columns);
        JNI.printf("created log");
        Log.w(EmulatorDebug.LOG_TAG, "created log");

        final FileDescriptor terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor);

        new Thread("TermSessionInputReader[log]") {
            @Override
            public void run() {
                {
                    String fmt = "starting stdout/stderr reader";
                    JNI.printf(fmt);
                    Log.w(EmulatorDebug.LOG_TAG, fmt);
                }
                try (InputStream termIn = new FileInputStream(terminalFileDescriptorWrapped)) {
                    final byte[] buffer = new byte[4096];
                    while (true) {
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: reading");
                        int read = termIn.read(buffer);
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: read");
                        if (read == -1) {
                            Log.wtf(EmulatorDebug.LOG_TAG, "stdout/stderr reader return -1");
                            return;
                        }
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: writing");
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) {
                            Log.wtf(EmulatorDebug.LOG_TAG, "stdout/stderr reader [write] returned false (closed)");
                            return;
                        }
                        Log.w(EmulatorDebug.LOG_TAG, "stdout/stderr reader: wrote");
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    }
                } catch (Exception e) {
                    // Ignore, just shutting down.
                }
            }
        }.start();
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows) {
        mEmulator = new TerminalEmulator(this, columns, rows, /* transcript= */5000);

        if (isLogView) createLogSession(columns, rows);
        else new Thread("TermSession") {
            @Override
            public void run() {
                while(true) {
                    createShellSession(columns, rows);
                    int processExitCode = JNI.waitFor(mShellPid);
                    byte[] bytesToWrite = String.format(Locale.ENGLISH, "shell returned %d\n\rrestarting...\n\r", processExitCode).getBytes(StandardCharsets.UTF_8);
                    mEmulator.append(bytesToWrite, bytesToWrite.length);
                    JNI.close(mTerminalFileDescriptor);
                }
            }
        }.start();

        {
            String fmt = "emulator initialized";
            JNI.printf(fmt);
            Log.w(EmulatorDebug.LOG_TAG, fmt);
        }
    }

    /** Write data to the shell process. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (!isLogView) if (mShellPid > 0) mTerminalToProcessIOQueue.write(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw new IllegalArgumentException("invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
			/* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
			/* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
			/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /** Notify the {@link #mChangeCallback} that the screen has changed. */
    protected void notifyScreenUpdate() {
        mChangeCallback.onTextChanged(this);
    }

    /** Reset state for terminal emulator state. */
    public void reset(boolean erase) {
        mEmulator.reset(erase);
        notifyScreenUpdate();
    }

    /** Finish this terminal session by sending SIGKILL to the shell. */
    public void finishIfRunning() {
        if (isRunning()) {
            if (!isLogView) {
                try {
                    Os.kill(mShellPid, OsConstants.SIGKILL);
                } catch (ErrnoException e) {
                    Log.w(EmulatorDebug.LOG_TAG, "failed sending SIGKILL: " + e.getMessage());
                }
            }
        }
    }

    /** Cleanup resources when the process exits. */
    void cleanupResources(int exitStatus) {
        synchronized (this) {
            if (!isLogView) {
                mShellPid = -1;
                mShellExitStatus = exitStatus;
            }
        }

        // Stop the reader and writer threads, and close the I/O streams
        mTerminalToProcessIOQueue.close();
        mProcessToTerminalIOQueue.close();
        JNI.close(mTerminalFileDescriptor);
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mChangeCallback.onTitleChanged(this);
    }

    public synchronized boolean isRunning() {
        return mShellPid != -1;
    }

    /** Only valid if not {@link #isRunning()}. */
    public synchronized int getExitStatus() {
        return mShellExitStatus;
    }

    @Override
    public void clipboardText(String text) {
        mChangeCallback.onClipboardText(this, text);
    }

    @Override
    public void onBell() {
        mChangeCallback.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mChangeCallback.onColorsChanged(this);
    }

    public int getPid() {
        return mShellPid;
    }

}
