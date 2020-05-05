package com.example.libclient_service;

// SERVICE MAP - IMPORTANT - https://i.stack.imgur.com/VxM9P.png

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/** messenger builder */

public class LibService_Messenger {

    public LibService_LogUtils log = new LibService_LogUtils("LibService - libMessenger");

    /**
     * this object is used for synchronization
     */

    private final Object waitOnMe = new Object();

    public void waitForReply() {
        log.log_Info("waiting for reply");
        synchronized (waitOnMe) {
            try {
                waitOnMe.wait();
            } catch (InterruptedException e) {
                // we should have gotten our answer now.
            }
        }
        log.log_Info("replied");
    }

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
            msg.replyTo = messengerToHandleMessages;
            messengerToSendMessagesTo.send(msg);
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
            msg.replyTo = messengerToHandleMessages;
            messengerToSendMessagesTo.send(msg);
            return true;
        } catch (RemoteException e) {
            // In this case the service has crashed before we could even
            // do anything with it
            return false;
        }
    }

    public Messenger messengerToHandleMessages;
    public Messenger messengerToSendMessagesTo;
    final int DEFAULT_CODE = 9999;

    public RunnableArgument<Message> defaultCallback = new RunnableArgument<Message>() {
        @Override
        public void run() {
            sendMessageToServerNonBlocking(DEFAULT_CODE);
        }

        @Override
        public void run(Message object) {
            sendMessageToServerNonBlocking(DEFAULT_CODE);
        }
    };

    public final HandlerThread handlerThread = new HandlerThread("libMessenger");
    public Looper looper;
    public Handler handler;
    public final Handler.Callback callback = new Handler.Callback() {
        @SuppressWarnings("DuplicateBranchesInSwitch")
        @Override
        public boolean handleMessage(Message message) {
            if (message == null) {
                log.log_Warning("message was null");
                return true;
            } else {
                boolean messageHandled = false;
                for (int i = 0, responsesSize = responses.size(); i < responsesSize; i++) {
                    Response response = responses.get(i);
                    if (message.what == response.what) {
                        messageHandled = true;
                        if (response.whatToExecute != null) response.whatToExecute.run(message);
                        break;
                    }
                }
                // TODO: handle unspecified response, by default we send and do not wait for a reply
                if (!messageHandled) {
                    log.log_Warning("recieved an unknown response code: " + message.what);
                    log.errorAndThrowIfNull(defaultCallback).run(message);
                }
                // handled messages are handled in background thread
                // then notify about finished message.
                synchronized (waitOnMe) {
                    waitOnMe.notifyAll();
                }
                return true;
            }
        }
    };

    class Response {
        int what = 0;
        RunnableArgument<Message> whatToExecute = null;
    }

    private ArrayList<Response> responses = new ArrayList<>();

    /*
server.defineResponseCodes(
    // service
    {
        // ...
    },
    // client
    {
        // ...
    }
};
     */

    public LibService_Messenger addResponse(int what) {
        Response response = new Response();
        response.what = what;
        responses.add(response);
        return this;
    }

    public LibService_Messenger addResponse(int what, RunnableArgument<Message> whatToExecute) {
        Response response = new Response();
        response.what = what;
        response.whatToExecute = whatToExecute;
        responses.add(response);
        return this;
    }

    public LibService_Messenger bind (IBinder serviceContainingMessenger) {
        log.errorAndThrowIfNull(serviceContainingMessenger);
        messengerToSendMessagesTo = new Messenger(serviceContainingMessenger);
        log.log_Info("binded to remote service");
        return this;
    }

    public LibService_Messenger start() {
        if (handlerThread.getState() == Thread.State.NEW) {
            handlerThread.start();
            looper = handlerThread.getLooper();
            handler = new Handler(looper, callback);
            messengerToHandleMessages = new Messenger(handler);
            log.log_Info("started messenger");
        }
        return this;
    }

    public IBinder getBinder() {
        return messengerToHandleMessages.getBinder();
    }
}

