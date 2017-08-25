package de.luhmer.livestreaming.mjpeg;

import android.support.annotation.NonNull;
import android.util.Log;

import com.github.niqdev.mjpeg.MjpegInputStreamNative;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * A library wrapper for handle mjpeg streams.
 *
 * @see
 * <ul>
 *     <li><a href="https://bitbucket.org/neuralassembly/simplemjpegview">simplemjpegview</a></li>
 *     <li><a href="https://code.google.com/archive/p/android-camera-axis">android-camera-axis</a></li>
 * </ul>
 */
public class Mjpeg {
    private static final String TAG = Mjpeg.class.getSimpleName();

    /**
     * Library implementation type
     */
    public enum Type {
        DEFAULT, NATIVE
    }

    private final Type type;
    
    private boolean sendConnectionCloseHeader = false;

    private Mjpeg(Type type) {
        if (type == null) {
            throw new IllegalArgumentException("null type not allowed");
        }
        this.type = type;
    }

    /**
     * Uses {@link Type#DEFAULT} implementation.
     *
     * @return Mjpeg instance
     */
    public static Mjpeg newInstance() {
        return new Mjpeg(Type.DEFAULT);
    }

    /**
     * Choose among {@link de.luhmer.livestreaming.mjpeg.Mjpeg.Type} implementations.
     *
     * @return Mjpeg instance
     */
    public static Mjpeg newInstance(Type type) {
        return new Mjpeg(type);
    }

    /**
     * Configure authentication.
     *
     * @param username credential
     * @param password credential
     * @return Mjpeg instance
     */
    /*
    public Mjpeg credential(final String username, final String password) {
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }
        return this;
    }*/
    
    /**
     * Send a "Connection: close" header to fix 
     * <code>java.net.ProtocolException: Unexpected status line</code>
     * 
     * @return Observable Mjpeg stream
     */
    public Mjpeg sendConnectionCloseHeader() {
        sendConnectionCloseHeader = true;
        return this;
    }

    @NonNull
    private Observable<MjpegInputStream> connect(final String url) {
        return Observable.defer(new Callable<ObservableSource<? extends MjpegInputStream>>() {
            @Override
            public ObservableSource<? extends MjpegInputStream> call() throws Exception {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .build();

                    Request request = new Request.Builder()
                            .cacheControl(CacheControl.FORCE_NETWORK)
                            .url(url)
                            .build();

                    Response response = client.newCall(request).execute();
                    InputStream inputStream = response.body().byteStream();
                    Log.d(TAG, "Type: " + type.name());
                    switch (type) {
                        // handle multiple implementations
                        case DEFAULT:
                            return Observable.just((MjpegInputStream) new MjpegInputStreamDefault(inputStream));
                        case NATIVE:
                            return Observable.just((MjpegInputStream) new MjpegInputStreamNative(inputStream));
                    }
                    throw new IllegalStateException("invalid type");
                } catch (IOException e) {
                    Log.e(TAG, "error during connection", e);
                    return Observable.error(e);
                }
            }
        });
    }

    @NonNull
    private Observable<MjpegInputStream> connectWebSocket(final String url) {
        return Observable.defer(new Callable<ObservableSource<? extends MjpegInputStream>>() {
            @Override
            public ObservableSource<? extends MjpegInputStream> call() throws Exception {
                final String wsuri = "ws://192.168.10.41:8084";


                return null;

                //WebSocketClient wsc = new WebSocketClient(URI.create(wsuri));
                //return Observable.just((MjpegInputStream) new MjpegInputStreamDefault(wsc.connect()));


                /*
                OkHttpClient client = new OkHttpClient.Builder().build();

                Request request = new Request.Builder().url(wsuri).build();

                WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        Log.d(TAG, "bytes!!!!!");



                        return Observable.just((MjpegInputStream) new MjpegInputStreamDefault(inputStream));
                        super.onMessage(webSocket, bytes);
                    }
                });

                ws.().().
                client.dispatcher().executorService().shutdown();
                Log.d(TAG, "I'm here!!");

                */

                /*
                Response response = client.newCall(request).execute();
                InputStream inputStream = response.body().byteStream();
                Log.d(TAG, "Type: " + type.name());
                switch (type) {
                    // handle multiple implementations
                    case DEFAULT:
                        return Observable.just((MjpegInputStream) new MjpegInputStreamDefault(inputStream));
                    case NATIVE:
                        return Observable.just((MjpegInputStream) new MjpegInputStreamNative(inputStream));
                }
                throw new IllegalStateException("invalid type");
                */

                //return Observable.just((MjpegInputStream) new MjpegInputStreamDefault(inputStream));
            }
        });
    }

    /**
     * Connect to a Mjpeg stream.
     *
     * @param url source
     * @return Observable Mjpeg stream
     */
    public Observable<MjpegInputStream> open(String url) {
        return connect(url)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Connect to a Mjpeg stream.
     *
     * @param url source
     * @param timeout in seconds
     * @return Observable Mjpeg stream
     */
    public Observable<MjpegInputStream> open(String url, int timeout) {
        return connectWebSocket(url)
            .timeout(timeout, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }

}
