package de.luhmer.livestreaming.mjpeg;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import de.luhmer.livestreaming.h264.H264InputStream;
import okhttp3.internal.http.StatusLine;
import okhttp3.internal.http2.Header;

public class WebSocketClient {
    private static final String TAG = "WebSocketClient";

    private URI                      mURI;
    private Socket                   mSocket;
    Surface surface;
    SharedPreferences settings;



    public WebSocketClient(Surface surface, SharedPreferences settings, URI uri) {
        mURI = uri;
        this.settings = settings;
        this.surface = surface;
    }

    public void connect(int widthCamera, int heightCamera) throws Exception {
        String path = TextUtils.isEmpty(mURI.getPath()) ? "/" : mURI.getPath();
        if (!TextUtils.isEmpty(mURI.getQuery())) {
            path += "?" + mURI.getQuery();
        }

        String secret = createSecret();
        String wsURL = "http://" + mURI.getHost() + ":8082";
        URI origin = URI.create(wsURL);
        Log.d(TAG, "WebSocket URL: " + wsURL);

        SocketFactory factory = SocketFactory.getDefault();
        mSocket = factory.createSocket(mURI.getHost(), mURI.getPort());

        PrintWriter out = new PrintWriter(mSocket.getOutputStream());
        out.print("GET " + path + " HTTP/1.1\r\n");
        out.print("Upgrade: websocket\r\n");
        out.print("Connection: Upgrade\r\n");
        out.print("Host: " + mURI.getHost() + ":" + mURI.getPort()  + "\r\n");
        out.print("Origin: " + origin.toString() + "\r\n");
        out.print("Sec-WebSocket-Key: " + secret + "\r\n");
        out.print("Sec-WebSocket-Version: 13\r\n");
        out.print("Camera-Width: " + widthCamera + "\r\n");
        out.print("Camera-Height: " + heightCamera + "\r\n");
        out.print("\r\n");
        out.flush();


        InputStream is = mSocket.getInputStream();

        // Read HTTP response status line.
        String statusLine = readLine(is);
        //if (statusLine == null || !statusLine.equals("HTTP/1.1 101 Switching Protocols")) {
        if (statusLine == null || !statusLine.equals("HTTP/1.1 101 Web Socket Protocol Handshake")) {
            throw new Exception("Received no reply from server.");
        }

        // Read HTTP response headers.
        String line;


        while (!TextUtils.isEmpty(line = readLine(is))) {
            Log.d(TAG, line);

            if (line.startsWith("Sec-WebSocket-Accept")) {
                String expected = createSecretValidation(secret);
                String actual = line.substring(21).trim();

                if (!expected.equals(actual)) {
                    throw new Exception("Bad Sec-WebSocket-Accept header value.");
                }

            }
        }

        Log.d(TAG, "Content starts now..");
        /*
        byte[] contents = new byte[1024];
        int bytesRead;
        while((bytesRead = is.read(contents)) != -1) {
            Log.d(TAG, new String(contents, 0, bytesRead));
        }*/

        new H264InputStream(surface, is, settings, widthCamera, heightCamera);

        Log.d(TAG, "Done parsing content");
    }

    public void disconnect() {
        if (mSocket != null) {
            try {
                mSocket.close();
                mSocket = null;
            } catch (IOException ex) {
                Log.d(TAG, "Error while disconnecting", ex);
            }
        }
    }

    /*
    public void send(String data) throws IOException {
        sendFrame(mParser.frame(data));
    }

    public void send(byte[] data) throws IOException {
        sendFrame(mParser.frame(data));
    }*/

    void sendFrame(final byte[] frame) throws IOException {
        if (mSocket == null) {
            throw new IllegalStateException("Socket not connected");
        }
        OutputStream outputStream = mSocket.getOutputStream();
        outputStream.write(frame);
        outputStream.flush();
    }


    private String createSecret() {
        byte[] nonce = new byte[16];
        for (int i = 0; i < 16; i++) {
            nonce[i] = (byte) (Math.random() * 256);
        }
        return Base64.encodeToString(nonce, Base64.DEFAULT).trim();
    }

    private String createSecretValidation(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((secret + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes());
            return Base64.encodeToString(md.digest(), Base64.DEFAULT).trim();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }



    private String readLine(InputStream reader) throws IOException {
        int readChar = reader.read();
        if (readChar == -1) {
            return null;
        }
        StringBuilder string = new StringBuilder("");
        while (readChar != '\n') {
            if (readChar != '\r') {
                string.append((char) readChar);
            }

            readChar = reader.read();
            if (readChar == -1) {
                return null;
            }
        }
        return string.toString();
    }
}