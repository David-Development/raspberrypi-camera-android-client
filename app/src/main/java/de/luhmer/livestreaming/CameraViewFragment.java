package de.luhmer.livestreaming;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import de.luhmer.livestreaming.h264.H264TCPSocket;
import de.luhmer.livestreaming.helper.Debouncer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CameraViewFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link CameraViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraViewFragment extends Fragment {

    private static final String ARG_IP = "mIp";
    private static final String ARG_WIDTH = "mWidth";
    private static final String ARG_HEIGHT = "mHeight";
    private static final String TAG = CameraViewFragment.class.getCanonicalName();

    private String mIp;
    private int mVideoWidth;
    private int mVideoHeight;
    //private Mjpeg mjpeg;
    //MjpegSurfaceView mjpegView;
    //private WebView webView;
    private TextureView mTextureView;
    private TextView mTvFpsCounter;
    private TextView mTvInfo;
    private ToggleButton mToggleButtonVFlip;
    private ToggleButton mToggleButtonHFlip;
    //private View mViewIndicator;

    private OnFragmentInteractionListener mListener;

    private Thread decoderThread;
    private InfoWebSocketThread infoWebSocketThread;
    private boolean isInfoWsConnected = false;

    private Debouncer<Integer> debouncer;

    private int colorGreen = Color.parseColor("#64ededed");
    //private int colorGreen = Color.parseColor("#00d80e");
    private int colorRed   = Color.parseColor("#e50000");

    public CameraViewFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param IP Parameter 1.
     * @return A new instance of fragment CameraViewFragment.
     */
    public static CameraViewFragment newInstance(String IP, int width, int height) {
        CameraViewFragment fragment = new CameraViewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IP, IP);
        args.putInt(ARG_WIDTH, width);
        args.putInt(ARG_HEIGHT, height);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIp          = getArguments().getString(ARG_IP);
        mVideoWidth  = getArguments().getInt(ARG_WIDTH);
        mVideoHeight = getArguments().getInt(ARG_HEIGHT);

        debouncer = new Debouncer<>(new Debouncer.Callback<Integer>() {
            @Override
            public void call(Integer key) {
                //Log.d(TAG, "Delay detected!!");

                //mViewIndicator.setBackgroundColor(colorRed);
                mTvInfo.setBackgroundColor(colorRed);
            }
        }, 150);

        setRetainInstance(false);
    }

    //private WebSocketClient wsc;
    private H264TCPSocket wsc;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera_view, container, false);

        mTvFpsCounter     = (TextView) view.findViewById(R.id.tvFpsCounter);
        mTextureView      = (TextureView) view.findViewById(R.id.textureView);
        mTvInfo           = (TextView) view.findViewById(R.id.tvInfo);
        mToggleButtonVFlip = (ToggleButton) view.findViewById(R.id.toggleButtonVFlip);
        mToggleButtonHFlip = (ToggleButton) view.findViewById(R.id.toggleButtonHFlip);
        //mViewIndicator    = view.findViewById(R.id.viewIndicator);

        final String wsuri = "ws://" + mIp + ":8084";

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            long lastFpsTime = 0;
            int frames = 0;

            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
                Log.d(TAG, "onSurfaceTextureAvailable() called with: surface = [" + surface + "], width = [" + width + "], height = [" + height + "]");
                adjustAspectRatio(mVideoWidth, mVideoHeight);

                //wsc = new WebSocketClient(new Surface(surface), PreferenceManager.getDefaultSharedPreferences(getActivity()), URI.create(wsuri));
                wsc = new H264TCPSocket(new Surface(surface), PreferenceManager.getDefaultSharedPreferences(getActivity()), width, height, mIp);

                decoderThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000); // Wait for socket to close (on rotation change)
                            wsc.connect(mVideoWidth, mVideoHeight);
                        } catch (final Exception e) {
                            e.printStackTrace();



                            // Activity is null if thread was ended by clicking on "back"
                            // e.getCause() is null if we shut the connection down manually
                            if(getActivity() != null && e.getCause() != null) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String message = e.getCause().getMessage();

                                        new AlertDialog.Builder(getActivity())
                                                .setTitle("Error")
                                                .setMessage(message)
                                                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        ((MainActivity) getActivity()).openCameraList();
                                                        Log.v(TAG, "Navigate back!");
                                                    }
                                                })
                                                .setCancelable(false)
                                                .show();
                                    }
                                });
                            }
                        }
                    }
                });
                decoderThread.setPriority(Thread.MAX_PRIORITY);

                if(isInfoWsConnected && !decoderThread.isAlive()) {
                    decoderThread.start();
                } else if(wsc instanceof H264TCPSocket) {
                    Log.d(TAG, "Starting thread anyways... TODO remove this exception!!!!");
                    decoderThread.start();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged() called with: surface = [" + surface + "], width = [" + width + "], height = [" + height + "]");
                adjustAspectRatio(mVideoWidth, mVideoHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "onSurfaceTextureDestroyed() called with: surface = [" + surface + "]");
                wsc.disconnect();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //Log.d(TAG, "onSurfaceTextureUpdated() called with: surface = [" + surface + "]");
                frames++;

                long currentTime = System.currentTimeMillis();
                if(lastFpsTime + 1000 < currentTime) {
                    long diff = currentTime - lastFpsTime;
                    //String text = String.format("FPS: %.1f - Diff: %d ms", (frames / (diff / 1000f)), diff);
                    String text = String.format("FPS: %.1f", (frames / (diff / 1000f)));
                    //Log.v(TAG, text);
                    mTvFpsCounter.setText(text);
                    lastFpsTime = currentTime;
                    frames = 0;
                }

                if(debouncer.call(0)) {
                    //mViewIndicator.setBackgroundColor(colorGreen);
                    mTvInfo.setBackgroundColor(colorGreen);
                }
            }
        });

        mToggleButtonVFlip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int flipper = mToggleButtonVFlip.isChecked() ? -1 : 1;
                Log.d(TAG, "FlipperX: " + flipper);
                mTextureView.setScaleX(flipper);
            }
        });

        mToggleButtonHFlip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int flipper = mToggleButtonHFlip.isChecked() ? -1 : 1;
                Log.d(TAG, "FlipperY: " + flipper);
                mTextureView.setScaleY(flipper);
            }
        });

        /*
        webView = (WebView) view.findViewById(R.id.webView);
        webView.setWebChromeClient(new WebChromeClient() { });
        webView.setWebViewClient(new WebViewClient() { });
        webView.getSettings().setJavaScriptEnabled(true);
        //webView.loadUrl("http://192.168.10.41:8080");
        webView.loadUrl("http://" + mIp + ":8080");
        */


        view.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {

                if(event.getAction() == MotionEvent.ACTION_UP){
                    if(((AppCompatActivity)getActivity()).getSupportActionBar().isShowing()) {
                        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
                    } else {
                        ((AppCompatActivity) getActivity()).getSupportActionBar().show();
                    }
                }
                return true;
            }
        });

        return view;
    }



    @Override
    public void onResume() {
        super.onResume();

        /*
        int timeout = 5; // in seconds
        String url = "http://" + mIp + ":8080/stream/video.mjpeg";
        Log.d(TAG, "Connecting to url: " + url);
        mjpeg = Mjpeg.newInstance(Mjpeg.Type.DEFAULT);

        mjpeg.open(url, timeout)
                .subscribe(new Consumer<MjpegInputStream>() {
                    @Override
                    public void accept(@NonNull MjpegInputStream inputStream) throws Exception {
                        mjpegView.setSource(inputStream);
                        mjpegView.setDisplayMode(DisplayMode.BEST_FIT);
                        mjpegView.showFps(true);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable ex) throws Exception {
                        ex.printStackTrace();
                        if(getActivity() != null) {
                            Toast.makeText(getActivity(), "Fehler beim Verbinden!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        */


        infoWebSocketThread = new InfoWebSocketThread(mIp);
        infoWebSocketThread.start();
    }

    @Override
    public void onPause() {
        if(infoWebSocketThread != null) {
            infoWebSocketThread.stopWebSocket();
            infoWebSocketThread.interrupt();
            infoWebSocketThread = null;
        }

        //decoderThread = null;

        //mjpegView.stopPlayback();
        super.onPause();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }



    private void adjustAspectRatio(int videoWidth, int videoHeight) {
        int viewWidth = mTextureView.getWidth();
        int viewHeight = mTextureView.getHeight();
        double aspectRatio = (double) videoHeight / videoWidth;

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;

        Log.v(TAG, "adjustAspectRatio() video=" + videoWidth + "x" + videoHeight +
                " view=" + viewWidth + "x" + viewHeight +
                " newView=" + newWidth + "x" + newHeight +
                " off=" + xoff + "," + yoff);

        Matrix txform = new Matrix();
        mTextureView.getTransform(txform);
        float xScale = (float) newWidth / viewWidth;
        float yScale = (float) newHeight / viewHeight;
        txform.setScale(xScale, yScale);
        //txform.postRotate(10);          // just for fun
        txform.postTranslate(xoff, yoff);
        mTextureView.setTransform(txform);
    }

    class InfoWebSocketThread extends Thread {

        private WebSocket webSocket;
        private final String ip;

        public InfoWebSocketThread(String ip) {
            this.ip = ip;
        }

        @Override
        public void run() {
            super.run();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("ws://" + ip + ":8082")
                    .build();
            webSocket = client.newWebSocket(request, webSocketListener);
        }

        public void stopWebSocket() {
            if(webSocket != null) {
                //webSocket.close(0, null);
                webSocket.close(1000, null); // https://tools.ietf.org/html/rfc6455#section-7.4
                webSocket = null;
            }
        }


        WebSocketListener webSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "onOpen() called with: webSocket = [" + webSocket + "], response = [" + response + "]");

                String val =  "Camera-Width: " + mVideoWidth + " Camera-Height: " + mVideoHeight;
                webSocket.send(val);

                isInfoWsConnected = true;
                if(decoderThread != null && !decoderThread.isAlive()) {
                    decoderThread.start();
                }

                super.onOpen(webSocket, response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                setInfoText(text);
                //Log.d(TAG, "onMessage() called with: webSocket = [" + webSocket + "], text = [" + text + "]");
                super.onMessage(webSocket, text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "onMessage() called with: webSocket = [" + webSocket + "], bytes = [" + bytes + "]");
                super.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosing() called with: webSocket = [" + webSocket + "], code = [" + code + "], reason = [" + reason + "]");
                super.onClosing(webSocket, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                setInfoText("Closed: " + reason);

                Log.d(TAG, "onClosed() called with: webSocket = [" + webSocket + "], code = [" + code + "], reason = [" + reason + "]");
                super.onClosed(webSocket, code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, final Throwable t, Response response) {
                setInfoText("Disconnected!");
                Log.d(TAG, "onFailure() called with: webSocket = [" + webSocket + "], t = [" + t + "], response = [" + response + "]");
                super.onFailure(webSocket, t, response);
            }
        };

        private void setInfoText(final String text) {
            Activity activity = getActivity();
            if(activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTvInfo.setText(text);
                    }
                });
            }
        }
    }
}
