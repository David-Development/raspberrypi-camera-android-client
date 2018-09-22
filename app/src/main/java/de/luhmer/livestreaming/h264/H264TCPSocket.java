package de.luhmer.livestreaming.h264;

import android.content.SharedPreferences;
import android.view.Surface;

import java.io.IOException;

/**
 * Created by david on 19.07.17.
 */

public class H264TCPSocket {

    private static final int UDP_SERVER_PORT = 2004;

    private UdpReceiverDecoderThread mDecoder;

    public H264TCPSocket(Surface mDecoderSurface, SharedPreferences settings, int widthCamera, int heightCamera, String ip) {
        mDecoder = new UdpReceiverDecoderThread(mDecoderSurface, settings, 5000, widthCamera, heightCamera);
        mDecoder.setIp(ip);
        mDecoder.port = UDP_SERVER_PORT;
    }

    public void connect(int videoWidth, int videoHeight) throws IOException {
        mDecoder.startDecodingNoThread();
    }

    public void disconnect() {
        mDecoder.stopDecoding();
    }
}
