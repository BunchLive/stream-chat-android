package com.getstream.sdk.chat.rest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.getstream.sdk.chat.interfaces.WSResponseHandler;
import com.getstream.sdk.chat.model.Event;
import com.getstream.sdk.chat.utils.ConnectionChecker;
import com.getstream.sdk.chat.utils.Constant;
import com.getstream.sdk.chat.utils.Global;
import com.getstream.sdk.chat.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketService extends WebSocketListener {

    private final String TAG = WebSocketService.class.getSimpleName();

    private List<WSResponseHandler> webSocketListeners;
    private String wsURL;
    private OkHttpClient client;
    private Request request;
    private EchoWebSocketListener listener;
    private WebSocket webSocket;
    private Context context;

    public void setWSResponseHandler(WSResponseHandler responseHandler, Context context) {
        if (this.context == null) this.context = context;
        if (this.webSocketListeners == null) this.webSocketListeners = new ArrayList<>();
        if (!this.webSocketListeners.contains(responseHandler)) {
            this.webSocketListeners.add(responseHandler);
        }
        setBroadCast();
        ConnectionChecker.startConnectionCheck(this.context);
    }

    public void removeWSResponseHandler(WSResponseHandler responseHandler) {
        if (this.webSocketListeners == null || this.webSocketListeners.isEmpty()) return;
        if (this.webSocketListeners.contains(responseHandler)) {
            this.webSocketListeners.remove(responseHandler);
        }
    }

    private void setBroadCast(){
        if (filter != null) return;

        filter = new IntentFilter();
        filter.addAction(Constant.BC_CONNECTION_OFF);
        filter.addAction(Constant.BC_CONNECTION_ON);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        this.context.registerReceiver(receiver, filter);
    }
    IntentFilter filter;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check Ephemeral Messages
            switch (intent.getAction()) {
                case Constant.BC_CONNECTION_OFF:
                    if (webSocketListeners == null || Global.noConnection) return;

                    Global.noConnection = true;
                    for (WSResponseHandler webSocketListener : webSocketListeners){
                        webSocketListener.onFailed(Constant.NO_INTERNET, Constant.NO_INTERNET_ERROR_CODE);
                    }
                    Log.d(TAG, "Connection Off");
                    break;
                case Constant.BC_CONNECTION_ON:
                    Log.d(TAG, "Connection On");
                    break;
                default:
                    break;
            }
        }
    };
    public void connect() {
        Global.streamChat.setClientID(null);
        client = new OkHttpClient();
        request = new Request.Builder().url(this.wsURL).build();
        listener = new EchoWebSocketListener();
        webSocket = client.newWebSocket(request, listener);
        Log.d(TAG, "WebSocket Connecting...");
        client.dispatcher().executorService().shutdown();
    }

    public void setWsURL(String wsURL) {
        this.wsURL = wsURL;
    }

    // region Health Check
    private Handler mHandler = new Handler();
    private Runnable mHealthChecker = new Runnable() {
        @Override
        public void run() {
            try {
                if (Global.noConnection) {
                    clearWSClient();
                    connect();
                } else {
                    webSocket.send("");
                }
            } finally {
                int interval = Global.noConnection ? Constant.HEALTH_CHECK_INTERVAL / 4 : Constant.HEALTH_CHECK_INTERVAL;
                mHandler.postDelayed(mHealthChecker, interval);
            }
        }
    };

    private void startRepeatingTask() {
        mHealthChecker.run();
    }

    private void stopRepeatingTask() {
        mHandler.removeCallbacks(mHealthChecker);
    }

    private class EchoWebSocketListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket Connected : " + response);
            Global.noConnection = false;
//            stopRepeatingTask();
            startRepeatingTask();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "WebSocket Response : " + text);
            JSONObject json = null;
            try {
                json = new JSONObject(text);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (json == null) return;

            Event event = Parser.parseEvent(json);
            if (event == null) return;

            if (TextUtils.isEmpty(Global.streamChat.getClientID())) {
                Global.noConnection = false;
                if (!TextUtils.isEmpty(event.getConnection_id())) {
                    String connectionId = event.getConnection_id();

                    if (event.getMe() != null)
                        Global.streamChat.setUser(event.getMe());

                    Global.streamChat.setClientID(connectionId);
                    if (webSocketListeners == null) return;
                    for (WSResponseHandler webSocketListener : webSocketListeners)
                        webSocketListener.handleConnection();
                }
            } else {
                if (webSocketListeners == null) return;
                for (WSResponseHandler webSocketListener : webSocketListeners)
                    webSocketListener.handleEventWSResponse(event);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            if (webSocketListeners == null) return;
            for (WSResponseHandler webSocketListener : webSocketListeners)
                webSocketListener.handleByteStringWSResponse(bytes);
            Log.d(TAG, "Receiving bytes : " + bytes.hex());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            Log.d(TAG, "Closing : " + code + " / " + reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            try {
                Log.d(TAG, "Error: " + t.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }

            clearWSClient();
            if (webSocketListeners == null) return;
            for (WSResponseHandler webSocketListener : webSocketListeners){
                if (t != null){
                    webSocketListener.onFailed(t.getMessage(), t.hashCode());
                }else{
                    webSocketListener.onFailed("Unknown", Constant.NO_INTERNET_ERROR_CODE);
                }
            }
        }
    }


    public void clearWSClient() {
        try {
            client.dispatcher().cancelAll();
        } catch (Exception e) {
        }
        Global.noConnection = true;
        Global.streamChat.setClientID(null);
    }
}
