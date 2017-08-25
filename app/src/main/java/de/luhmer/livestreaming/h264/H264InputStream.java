package de.luhmer.livestreaming.h264;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by david on 19.07.17.
 */

public class H264InputStream {

    public H264InputStream(Surface mDecoderSurface, InputStream inputStream, SharedPreferences settings, int widthCamera, int heightCamera) throws IOException {
        //UdpReceiverDecoderThread mDecoder = new UdpReceiverDecoderThread(mDecoderSurface, settings, 5000, 960, 810);
        UdpReceiverDecoderThread mDecoder = new UdpReceiverDecoderThread(mDecoderSurface, settings, 5000, widthCamera, heightCamera);
        mDecoder.InputStream(inputStream);
        mDecoder.startDecodingNoThread();
    }

}
