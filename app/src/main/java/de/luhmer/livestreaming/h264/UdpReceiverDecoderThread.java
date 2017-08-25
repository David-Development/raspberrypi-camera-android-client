package de.luhmer.livestreaming.h264;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaFormat;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/*
receives raw h.264 byte stream on udp port 5000,parses the data into NALU units,and passes them into a MediaCodec Instance.
Original: https://bitbucket.org/befi/h264viewer
Edited by Constantin Geier
*/

public class UdpReceiverDecoderThread {
    private static final String TAG = UdpReceiverDecoderThread.class.getCanonicalName();
    public volatile boolean next_frame=false;
    SharedPreferences settings;
    private Surface surface;
    private boolean decoderConfigured=false;
    private boolean mDecoderMultiThread =true;
    private boolean userDebug=true;
    DatagramSocket s = null;
    int port;
    int nalu_search_state = 0;
    byte[] nalu_data;
    int nalu_data_position;
    int NALU_MAXLEN = 1024 * 1024;

    int readBufferSize=1024*1024*60;
    byte buffer2[] = new byte[readBufferSize];
    private volatile boolean running = true;
    private int zaehlerFramerate=0;
    private long timeB = 0;
    private long timeB2=0;
    private long fpsSum=0,fpsCount=0,averageDecoderfps=0;
    private int current_fps=0;
    private long presentationTimeMs=0;
    private long averageHWDecoderLatency=0;
    private long HWDecoderlatencySum=0;
    private int outputCount=0;
    //time we have to wait for an Buffer to fill
    private long averageWaitForInputBufferLatency = 0;
    private long waitForInputBufferLatencySum = 0;
    private long naluCount=0;

    private int width;
    private int height;

    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo info;
    private MediaCodec decoder;
    private MediaFormat format;

    private StreamToFileRecorder mStreamToFileRecorder;


    public UdpReceiverDecoderThread(Surface surface, SharedPreferences settings, int port, int width, int height) {
        this.surface = surface;
        this.settings = settings;
        this.port = port;
        nalu_data = new byte[NALU_MAXLEN];
        nalu_data_position = 0;
        info = new MediaCodec.BufferInfo();

        this.width = width;
        this.height = height;

        //mStreamToFileRecorder = new StreamToFileRecorder("recorded.h264");
    }
    private void configureDecoder(ByteBuffer csd0, ByteBuffer csd1){
        try {
            //decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder");
            decoder = MediaCodec.createDecoderByType("video/avc");

            /*
            if(settings.getString("decoder","HW").equals("SW")){
                //This Decoder Seems to exist on most android devices,but is pretty slow
                decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder");
            } else {
                decoder = MediaCodec.createDecoderByType("video/avc");
            }
            */

        } catch (Exception e) {
            System.out.println("Error creating decoder");
            handleDecoderException(e, "create decoder");
            running = false;
            return;
        }
        Log.d(TAG, "Codec Info: " + decoder.getCodecInfo().getName());
        //format = MediaFormat.createVideoFormat("video/avc", 1920, 1080);
        format = MediaFormat.createVideoFormat("video/avc", width, height);

        format.setByteBuffer("csd-0", csd0);
        format.setByteBuffer("csd-1", csd1);
        try {
            //This configuration will be overwritten anyway when we put an sps into the buffer
            //But: My decoder agrees with this,but some may not; to be improved
            decoder.configure(format, surface, null, 0);
            if (decoder == null) {
                Log.e(TAG, "Can't configure decoder!");
                running = false;
                return;
            }
        } catch (Exception e) {
            System.out.println("error config decoder");
            handleDecoderException(e,"configure decoder");
        }
        decoder.start();
        decoderConfigured=true;
        if(mDecoderMultiThread){
            Thread thread2 = new Thread(){
                @Override
                public void run() {
                    try {
                        while (running) {
                            checkOutput();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        running = false;
                    }
                }
            };
            thread2.setPriority(Thread.MAX_PRIORITY);
            thread2.start();
        }
    }

    public void startDecoding(){
        running = true;
        Thread thread = new Thread(){
            @Override
            public void run() {
                try {
                    startFunction();
                } catch (IOException e) {
                    e.printStackTrace();
                    stopDecoding();
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void startDecodingNoThread() throws IOException {
        running = true;
        startFunction();
    }

    public void stopDecoding() {
        running = false;
        writeLatencyFile();

        // Clean up
        surface = null;
        buffer2 = null;
        inputBuffers = null;
        outputBuffers = null;
        nalu_data = null;

        Log.d(TAG, "stopDecoding");
    }

    public void startFunction() throws IOException {
        //receiveFromFile("Download/rpi960mal810.h264");
        //receiveFromFile("Download/sample.h264");
        //receiveFromFile("Download/sample (1).h264");
        //receiveFromUDP();
        receiveFromInputStream();
    }

    InputStream inputStream;
    public void InputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    private void receiveFromUDP() {
        int server_port = this.port;
        byte[] message = new byte[1024];
        DatagramPacket p = new DatagramPacket(message, message.length);
        try {
            s = new DatagramSocket(server_port);
            s.setSoTimeout(500);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        boolean exception=false;
        while (running && s != null) {
            try {
                s.receive(p);
            } catch (IOException e) {
                if(! (e instanceof SocketTimeoutException)) {
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
                exception=true;
            }
            if(!exception){
                parseDatagram(message, p.getLength());

                if(mStreamToFileRecorder != null) {
                    mStreamToFileRecorder.writeGroundRecording(message, p.getLength());
                }
            } else{
                exception = false; //The timeout happened
            }
        }
        if (s != null) {
            s.close();
        }
        if(mStreamToFileRecorder != null) {
            mStreamToFileRecorder.stop();
        }

        if(decoder != null){
            decoder.flush();
            decoder.stop();
            decoder.release();
        }
    }

    private void receiveFromInputStream() throws IOException {
        byte[] message = new byte[1024];
        int length = -1;
        while (running && inputStream != null) {

            try {
                length = inputStream.read(message);
            } catch (SocketException se) {
                se.printStackTrace();
                break;
            }

            if(length == -1) {
                break;
            }

            parseDatagram(message, length);

            if(mStreamToFileRecorder != null) {
                mStreamToFileRecorder.writeGroundRecording(message, length);
            }


        }
        if (s != null) {
            s.close();
        }
        if(mStreamToFileRecorder != null) {
            mStreamToFileRecorder.stop();
        }

        if(decoder != null){
            decoder.flush();
            decoder.stop();
            decoder.release();
        }
        stopDecoding();
    }

    private void receiveFromFile(String fileName) {
        FileInputStream in;
        try {
            String fullPath = Environment.getExternalStorageDirectory() + "/" + fileName;
            Log.d(TAG, "Using file: " + fullPath);
            in = new FileInputStream(fullPath);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening File");
            return;
        }
        for (; ; ) {
            if(!running){break;}
            int sampleSize = 0;
            try {
                sampleSize=in.read(buffer2,0,readBufferSize);
            } catch (IOException e) {e.printStackTrace();}
            if(sampleSize>0) {
                parseDatagram(buffer2, sampleSize);
            } else {
                Log.d("File", "End of stream");
                running = false;
                break;
            }
        }
        if(decoder != null){
            decoder.flush();
            decoder.stop();
            decoder.release();
        }
    }

    private void parseDatagram(byte[] p, int plen) {
        //Maybe: use System.arraycopy ...
        try {
            for (int i = 0; i < plen; ++i) {
                nalu_data[nalu_data_position++] = p[i];
                if (nalu_data_position == NALU_MAXLEN - 1) {
                    Log.w("parseDatagram", "NALU Overflow");
                    nalu_data_position = 0;
                }
                switch (nalu_search_state) {
                    case 0:
                    case 1:
                    case 2:
                        if (p[i] == 0)
                            nalu_search_state++;
                        else
                            nalu_search_state = 0;
                        break;
                    case 3:
                        if (p[i] == 1) {
                            //nalupacket found
                            nalu_data[0] = 0;
                            nalu_data[1] = 0;
                            nalu_data[2] = 0;
                            nalu_data[3] = 1;
                            interpretNalu(nalu_data, nalu_data_position - 4);
                            nalu_data_position = 4;
                        }
                        nalu_search_state = 0;
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("error parsing");
            handleDecoderException(e,"parseDatagram");
        }
    }

    private void interpretNalu(byte[] n, int len) {
        if(decoderConfigured) {
            timeB = System.currentTimeMillis();
            feedDecoder(n, len); //takes beteen 2 and 20ms (1ms,1ms,20ms,1ms,1ms,20ms,... in this order),
            // beacause there isn't always an input buffer available immediately;
            //may be improved (multithreading)

        } else {
            configureDecoder(MediaCodecFormatHelper.getCsd0(), MediaCodecFormatHelper.getCsd1());
        }
        long time=System.currentTimeMillis()-timeB;
        if(time>=0 && time<=200){
            naluCount++;
            waitForInputBufferLatencySum+=time;
            averageWaitForInputBufferLatency=(waitForInputBufferLatencySum/naluCount);
            //Log.w("1","Time spent waiting for an input buffer:"+time);
            //Log.w("2","average Time spent waiting for an input buffer:"+averageWaitForInputBufferLatency);
        }
    }
    @SuppressWarnings("deprecation")
    private void feedDecoder(byte[] n, int len) {
        //
        for (; ; ) {
            try {
                inputBuffers = decoder.getInputBuffers();
                int inputBufferIndex = decoder.dequeueInputBuffer(0);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.put(n, 0, len);
                    //decoder.queueInputBuffer(inputBufferIndex, 0, len, 0, 0);
                    presentationTimeMs=System.nanoTime();
                    decoder.queueInputBuffer(inputBufferIndex, 0, len,presentationTimeMs,0);
                    break;
                }else if(inputBufferIndex!=MediaCodec.INFO_TRY_AGAIN_LATER){
                    if(userDebug){
                        Log.d(TAG, "queueInputBuffer unusual: "+inputBufferIndex);
                        makeDebugFile("queueInputBuffer unusual: "+inputBufferIndex);
                    }
                }
                if(!mDecoderMultiThread){
                    checkOutput();
                }
            } catch (Exception e) {
                handleDecoderException(e,"feedDecoder");
            }
        }


    }


    private void checkOutput() {
        //outputBuffers = decoder.getOutputBuffers();
        int outputBufferIndex = decoder.dequeueOutputBuffer(info, 0);
        if (outputBufferIndex >= 0) {
            //
            zaehlerFramerate++;
            if((System.currentTimeMillis()-timeB2)>1000) {
                int fps = (zaehlerFramerate );
                current_fps = fps;
                timeB2 = System.currentTimeMillis();
                zaehlerFramerate = 0;
                //Log.w("ReceiverDecoderThread", "fps:" + fps);
                fpsSum+=fps;
                fpsCount++;
                if(fpsCount==1){
                    Log.d(TAG, "First video frame has been decoded");
                }
            }
            long latency = ((System.nanoTime()-info.presentationTimeUs)/1000000);
            if(latency>=0 && latency<=400){
                outputCount++;
                HWDecoderlatencySum+=latency;
                averageHWDecoderLatency = HWDecoderlatencySum/outputCount;
                //Log.w("checkOutput 1","hw decoder latency:"+latency);
                //Log.w("checkOutput 2","Average HW decoder latency:"+averageHWDecoderLatency);
            }
            //on my device this code snippet from Moonlight is not needed,after testing I doubt if it is really working at all;
            //if(decoder.dequeueOutputBuffer(info, 0) >= 0){ Log.w("...","second available");}
            //for GLSurfaceView,to drop the latest frames except the newest one,the timestamp has to be near the VSYNC signal.
            //requires android 5
            decoder.releaseOutputBuffer(outputBufferIndex, System.nanoTime()); //needs api 21
            //decoder.releaseOutputBuffer(outputBufferIndex,true);

        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || outputBufferIndex==MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            Log.d("UDP", "output format / buffers changed");
        } else if(outputBufferIndex!=MediaCodec.INFO_TRY_AGAIN_LATER) {
            Log.d(TAG, "dequeueOutputBuffer;" + "not normal;" + "number:"+outputBufferIndex);
            makeDebugFile("dequeueOutputBuffer;" + "not normal;" + "number:" + outputBufferIndex);
        }
    }

    public void writeLatencyFile(){
        //Todo: measure time between realeasing output buffer and rendering it onto Screen
        /*
        Display mDisplay=getWindowManager().getDefaultDisplay();
        long PresentationDeadlineMillis=mDisplay.getPresentationDeadlineNanos()/1000000;
        Log.w(TAG,"Display:"+PresentationDeadlineMillis);
         */

        //String lf = settings.getString("latencyFile","ERROR");
        String lf = "";

        if(lf.length()>=1000 || lf.length()<=20){
            lf="These values only show the measured lag of the app; \n"+
                    "The overall App latency may be much more higher,because you have to add the 'input lag' of your phone-about 32-48ms on android \n"+
                    "Every 'time' values are in ms. \n";
        }
        if(fpsCount==0){fpsCount=1;}
        averageDecoderfps=fpsSum/fpsCount;

        lf+="\n Average HW Decoder fps: "+(averageDecoderfps);
        lf+="\n Average measured app Latency: " + (averageWaitForInputBufferLatency + averageHWDecoderLatency) + "ms";
        lf+="\n Average time waiting for an input Buffer: " + averageWaitForInputBufferLatency + "ms";
        lf+="\n Average time HW decoding: " + averageHWDecoderLatency;
        lf+="\n ";
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("latencyFile",lf);
        editor.apply();
    }

    public int getDecoderFps(){
        return current_fps;
    }

    private void makeDebugFile(String message){
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("debugFile",message + settings.getString("debugFile",""));
        editor.commit();
    }
    private void handleDecoderException(Exception e,String tag){
        if(userDebug) {
            //makeToast("Exception on "+tag+": ->exception file");
            if (e instanceof CodecException) {
                CodecException codecExc = (CodecException) e;
                makeDebugFile("CodecException on " + tag + " :" + codecExc.getDiagnosticInfo());
            } else {
                makeDebugFile("Exception on "+tag+":"+Log.getStackTraceString(e));
            }
            try {Thread.sleep(100,0);} catch (InterruptedException e2) {e2.printStackTrace();}
        }
        e.printStackTrace();
    }

    private class StreamToFileRecorder {
        private FileOutputStream out;

        StreamToFileRecorder(String fileName){
            out = null;
            try {
                out=new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + fileName,false);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e("GroundRecorder", "couldn't create");
            }
        }

        void writeGroundRecording(byte[] p, int len){
            try {
                out.write(p,0,len);
            } catch (IOException e) {
                e.printStackTrace();
                Log.w("GroundRecorder", "couldn't write");
            }
        }

        void stop(){
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.w("GroundRecorder", "couldn't close");
            }
        }
    }
}