package de.luhmer.livestreaming.helper;

import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.telecom.Call;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by david on 08.07.17.
 */

// https://github.com/antonpinchuk/trafficstats-example/blob/master/app/src/main/java/pro/kinect/trafficstats/

public class TrafficManager {

    private long tx = 0;
    private long rx = 0;

    private long current_tx = 0;
    private long current_rx = 0;

    private TimerTask mTask;
    private Timer mTimer;
    private final int uid;
    private final int TIME_APPLICATION_UPDATE = 1 * 1000; // 1 second
    private final Callback callback;


    public TrafficManager(int uid, Callback callback) {
        tx = TrafficStats.getUidTxBytes(uid);
        rx = TrafficStats.getUidRxBytes(uid);

        this.callback = callback;
        this.uid = uid;
    }

    private void update() {
        long delta_tx = TrafficStats.getUidTxBytes(uid) - tx;
        long delta_rx = TrafficStats.getUidRxBytes(uid) - rx;

        tx = TrafficStats.getUidTxBytes(uid);
        rx = TrafficStats.getUidRxBytes(uid);

        current_tx = current_tx + delta_tx;
        current_rx = current_rx + delta_rx;


        int currentUsageKb = Math.round((delta_tx + delta_rx)/ 1024);
        callback.onUpdate(currentUsageKb + " kb/s");
    }

    public int getTotalUsageKb() {
        return Math.round((tx + rx)/ 1024);
    }



    public void onStart() {
        mTask = new TimerTask() {
            @Override
            public void run() {
                update();
            }
        };

        mTimer = new Timer();
        mTimer.schedule(mTask, 0, TIME_APPLICATION_UPDATE);
    }

    public void onStop() {
        if (mTimer != null) {
            mTimer.purge();
            mTimer.cancel();
        }
        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }
    }

    public interface Callback {
        void onUpdate(String text);
    }

}
