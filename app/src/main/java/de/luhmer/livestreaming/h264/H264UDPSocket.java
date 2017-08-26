package de.luhmer.livestreaming.h264;

import android.content.SharedPreferences;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by david on 19.07.17.
 */

public class H264UDPSocket {

    private static final int UDP_SERVER_PORT = 2004;

    private UdpReceiverDecoderThread mDecoder;

    public H264UDPSocket(Surface mDecoderSurface, SharedPreferences settings, int widthCamera, int heightCamera) {
        mDecoder = new UdpReceiverDecoderThread(mDecoderSurface, settings, 5000, widthCamera, heightCamera);
        mDecoder.port = UDP_SERVER_PORT;
    }

    public void connect(int videoWidth, int videoHeight) throws IOException {
        mDecoder.startDecodingNoThread();
    }

    public void disconnect() {
        mDecoder.stopDecoding();
    }
}
