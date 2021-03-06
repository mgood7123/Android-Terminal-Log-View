package com.example.libclient_service;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** an embedded copy of my LogUtils library */

public class LibService_LogUtils {
    public final String TAG;
    public final String ERRORMESSAGE;

    public LibService_LogUtils(String tag) {
        TAG = tag;
        ERRORMESSAGE = "An error has occured";
    }

    public LibService_LogUtils(String tag, String errorMessage) {
        TAG = tag;
        ERRORMESSAGE = errorMessage;
    }

    public int log_Debug(String message) {
        return Log.d(TAG, message);
    }

    public int log_Verbose(String message) {
        return Log.v(TAG, message);
    }

    public int log_Info(String message) {
        getParentMethodName();
        return Log.i(TAG, message);
    }

    public int log_Warning(String message) {
        return Log.w(TAG, message);
    }

    public int log_Error(String message) {
        return Log.e(TAG, message);
    }

    public int log_What_A_Terrible_Failure(String message) {
        return Log.wtf(TAG, message);
    }

    public int log_Debug(String message, Throwable throwable) {
        return Log.d(TAG, message, throwable);
    }

    public int log_Verbose(String message, Throwable throwable) {
        return Log.v(TAG, message, throwable);
    }

    public int log_Info(String message, Throwable throwable) {
        return Log.i(TAG, message, throwable);
    }

    public int log_Warning(String message, Throwable throwable) {
        return Log.w(TAG, message, throwable);
    }

    public int log_Error(String message, Throwable throwable) {
        return Log.e(TAG, message, throwable);
    }

    public int log_What_A_Terrible_Failure(String message, Throwable throwable) {
        return Log.wtf(TAG, message, throwable);
    }

    /**
     * Fails a test with the given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     * okay)
     * @see AssertionError
     */
    public void fail(String message) {
        if (message == null) {
            throw new AssertionError();
        }
        throw new AssertionError(message);
    }

    /**
     * Asserts that a condition is true. If it isn't it throws an
     * {@link AssertionError} with the given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     * okay)
     * @param condition condition to be checked
     */
    public void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    /**
     * Asserts that a condition is true. If it isn't it throws an
     * {@link AssertionError} with the given message.
     *
     * @param condition condition to be checked
     */
    public void assertTrue(boolean condition) {
        if (!condition) {
            fail(ERRORMESSAGE);
        }
    }

    /**
     * Asserts that an object isn't null. If it is an {@link AssertionError} is
     * thrown with the given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     * okay)
     * @param object Object to check or <code>null</code>
     */
    public void assertNotNull(String message, Object object) {
        log_Info("checking if object is null...");
        assertTrue(message, object != null);
        log_Info("object is not null");
    }

    public final Throwable error() {
        return error(ERRORMESSAGE);
    }

    public final AssertionError error(String message) {
        AssertionError t = new AssertionError(message);
        log_Error(Log.getStackTraceString(t));
        return t;
    }

    public final void errorNoStackTrace() {
        errorNoStackTrace(ERRORMESSAGE);
    }

    /**
     * alias for log_Error(String message)
     * @param message
     */
    public final void errorNoStackTrace(String message) {
        log_Error(message);
    }

    @Nullable
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorIfNull(@Nullable T object) {
        return errorIfNull(object, ERRORMESSAGE);
    }

    @Nullable
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorIfNull(@Nullable T object, String message) {
        if (object == null) error(message);
        return object;
    }

    @Nullable
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorIfNullNoStackTrace(@Nullable T object) {
        return errorIfNullNoStackTrace(object, ERRORMESSAGE);
    }

    @Nullable
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorIfNullNoStackTrace(@Nullable T object, String message) {
        if (object == null) errorNoStackTrace(message);
        return object;
    }

    @NonNull
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorAndThrowIfNull(@Nullable T object) {
        return errorAndThrowIfNull(object, ERRORMESSAGE);
    }

    @NonNull
    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final <T> T errorAndThrowIfNull(@Nullable T object, String message) {
        assertNotNull(message, object);
        return object;
    }

    public final void errorAndThrowIfNull(@Nullable Object ... objects) {
        assertNotNull(ERRORMESSAGE, objects);
        for (int i = 0, objectsLength = objects.length; i < objectsLength; i++) {
            Object object = objects[i];
            assertNotNull(ERRORMESSAGE, object);
        }
    }

    public final void errorAndThrowIfNull(String message, @Nullable Object ... objects) {
        if (message == null) assertNotNull(ERRORMESSAGE, objects);
        else assertNotNull(message, objects);
        for (int i = 0, objectsLength = objects.length; i < objectsLength; i++) {
            Object object = objects[i];
            if (message == null) assertNotNull(ERRORMESSAGE, object);
            else assertNotNull(message, object);
        }
    }

    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final void errorAndThrow(String message) {
        throw new AssertionError(message);

    }

    @SuppressWarnings("ConstantOnRightSideOfComparison")
    public final void errorAndThrow(String message, Throwable throwable) {
        throw new AssertionError(message, throwable);
    }

    /**
     * alias for logMethodName_Verbose()
     */
    public void logMethodName() {
        log_Verbose(getMethod(1) + " called");
    }

    public void logMethodName_Verbose() {
        log_Verbose(getMethod(1) + " called");
    }

    public void logMethodName_Debug() {
        log_Debug(getMethod(1) + " called");
    }

    public void logMethodName_Info() {
        log_Info(getMethod(1) + " called");
    }

    public void logMethodName_Warning() {
        log_Warning(getMethod(1) + " called");
    }

    public void logMethodName_Error() {
        log_Error(getMethod(1) + " called");
    }

    public void logMethodName_What_A_Terrible_Failure() {
        log_What_A_Terrible_Failure(getMethod(1) + " called");
    }

    /**
     * alias for logParentMethodName_Verbose()
     */
    public void logParentMethodName() {
        log_Verbose(getParentMethod(1) + " called");
    }

    public void logParentMethodName_Verbose() {
        log_Verbose(getParentMethod(1) + " called");
    }

    public void logParentMethodName_Debug() {
        log_Debug(getParentMethod(1) + " called");
    }

    public void logParentMethodName_Info() {
        log_Info(getParentMethod(1) + " called");
    }

    public void logParentMethodName_Warning() {
        log_Warning(getParentMethod(1) + " called");
    }

    public void logParentMethodName_Error() {
        log_Error(getParentMethod(1) + " called");
    }

    public void logParentMethodName_What_A_Terrible_Failure() {
        log_What_A_Terrible_Failure(getParentMethod(1) + " called");
    }

    public String getMethodName() {
        return getMethodName(1);
    }

    public String getMethodName(int methodDepthOffset) {
        return Thread.currentThread().getStackTrace()[3+methodDepthOffset].getMethodName();
    }

    public StackTraceElement getMethod() {
        return getMethod(1);
    }

    public StackTraceElement getMethod(int methodDepthOffset) {
        return Thread.currentThread().getStackTrace()[3+methodDepthOffset];
    }

    public StackTraceElement getParentMethod() {
        return getParentMethod(1);
    }

    public StackTraceElement getParentMethod(int methodDepthOffset) {
        return Thread.currentThread().getStackTrace()[4+methodDepthOffset];
    }

    public String getParentMethodName() {
        return getParentMethodName(1);
    }

    public String getParentMethodName(int methodDepthOffset) {
        return Thread.currentThread().getStackTrace()[4+methodDepthOffset].getMethodName();
    }
}
