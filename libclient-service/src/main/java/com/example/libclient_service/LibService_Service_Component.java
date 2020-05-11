package com.example.libclient_service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class LibService_Service_Component extends Service {

    Runnable onCreateCallback = null;

    public LibService_LogUtils log = new LibService_LogUtils("LibService - Service - Component");

    /** Keeps track of all current registered clients. */
    public ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    public final LibService_Messenger messenger = new LibService_Messenger("Service");

    public LibService_Service_Connection manager = null;

    public AtomicBoolean onServiceConnectedCallbackCalled = new AtomicBoolean(false);

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    public class Local extends Binder {
        public final LibService_Service_Component service = LibService_Service_Component.this;
    }

    public abstract void onMessengerBindLocal();
    public abstract void onMessengerBindRemote();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        // can probs be fixed by connection/binding re-ordering

        if (intent.hasExtra("BINDING_TYPE")) {
            if (intent.getStringExtra("BINDING_TYPE").contentEquals("BINDING_LOCAL")) {
                log.log_Info("onBind: binded to local service");
                onMessengerBindLocal();
                return new Local();
            }
        }
        log.log_Info("onBind: binded to remote service");
        onMessengerBindRemote();
        messenger.start();
        return messenger.getBinder();
    }

    @Override
    public void onCreate() {
        if (onCreateCallback != null) onCreateCallback.run();
    }
}
