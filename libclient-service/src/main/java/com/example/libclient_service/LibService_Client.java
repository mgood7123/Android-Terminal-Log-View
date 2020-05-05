package com.example.libclient_service;

import android.content.Intent;
import android.app.Activity;
import android.os.IBinder;

/** this class defines a set of API's used for creating a Client that can connect to a Service */

public abstract class LibService_Client {

    private LibService_LogUtils log = new LibService_LogUtils("LibService - Client ");

    public final LibService_Messenger libService_messenger = new LibService_Messenger();

    public final void onServiceConnectedCallback() {
        onServiceConnectedCallback(null);
    };
    public abstract void onServiceConnectedCallback(IBinder boundService);

    LibService_Service_Connection connection = new LibService_Service_Connection() {
        @Override
        public void onServiceConnectedCallback(IBinder boundService) {
            libService_messenger.bind(boundService).start();
            LibService_Client.this.onServiceConnectedCallback(boundService);
        }
    };

    public void connectToService(Activity activity, String packageName, String serviceClassName) {
        Intent serviceIntent = new Intent();
        serviceIntent.setPackage(packageName);
        serviceIntent.setClassName(packageName, serviceClassName);

        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!activity.bindService(serviceIntent, connection, 0)) {
            throw new RuntimeException("bindService() failed");
        }
    }
}
