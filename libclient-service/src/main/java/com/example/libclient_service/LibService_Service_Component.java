package com.example.libclient_service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class LibService_Service_Component extends Service {

    Runnable onCreateCallback = null;

    private LibService_LogUtils log = new LibService_LogUtils("libService - Service - Component");

    /** Keeps track of all current registered clients. */
    public ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    public LibService_Messenger messenger = new LibService_Messenger();

    public LibService_Service_Connection manager = null;

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    public class Local extends Binder {
        public final LibService_Service_Component service = LibService_Service_Component.this;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent.hasExtra("BINDING_TYPE")) {
            if (intent.getStringExtra("BINDING_TYPE").contentEquals("BINDING_LOCAL")) {
                return new Local();
            }
        }
        return messenger.start().getBinder();
    }

    @Override
    public void onCreate() {
        if (onCreateCallback != null) onCreateCallback.run();
    }
}
