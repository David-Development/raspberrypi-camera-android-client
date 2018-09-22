package de.luhmer.livestreaming;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

import de.luhmer.livestreaming.helper.SshClient;
import de.luhmer.livestreaming.helper.TrafficManager;
import de.luhmer.livestreaming.models.CameraItems;

public class MainActivity extends AppCompatActivity implements CameraListFragment.OnListFragmentInteractionListener, CameraViewFragment.OnFragmentInteractionListener {

    private static final String TAG = MainActivity.class.getCanonicalName();
    private TrafficManager trafficManager;
    private TextView tvTraffic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTraffic = (TextView) findViewById(R.id.tvTraffic);

        if(savedInstanceState == null) {
            openFragment(CameraListFragment.newInstance(1));
        }

        /*
        if(!isApOn(this)) {
            Toast.makeText(this, "Bitte Hotspot aktivieren..", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }*/

        trafficManager = new TrafficManager(getApplicationInfo().uid, new TrafficManager.Callback() {
            @Override
            public void onUpdate(final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvTraffic.setText(text);
                        //Log.d(TAG, text);
                    }
                });
            }
        });


        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String lf = settings.getString("latencyFile","ERROR");
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Latency Result")
                .setMessage(lf)
                .setPositiveButton("Ok", null)
                .create();
        //alertDialog.show();


        //onListClickInteraction(new CameraItems.CameraItem("1", "192.168.10.41", true));
        //onListClickInteraction(new CameraItems.CameraItem("1", "192.168.43.169", true));
    }

    @Override
    protected void onStart() {
        super.onStart();
        trafficManager.onStart();
    }

    @Override
    protected void onStop() {
        trafficManager.onStop();
        super.onStop();
    }



    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if(fragment instanceof CameraViewFragment) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } else {
            getSupportActionBar().show();
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }


    @Override
    public void onListClickInteraction(final CameraItems.CameraItem item) {
        final CharSequence[] items = {"320x240", "640x480", "800x600", "1640x1232"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int itemid) {
                int width = Integer.parseInt(items[itemid].toString().split("x")[0]);
                int height = Integer.parseInt(items[itemid].toString().split("x")[1]);
                openFragment(CameraViewFragment.newInstance(item.ip, width, height));
                dialog.dismiss();
            }
        });

        builder.create().show();
    }

    @Override
    public void onListLongClickInteraction(final CameraItems.CameraItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Warnung")
                .setMessage("Was möchten Sie tun?")
                .setPositiveButton("Shutdown", new ShutdownRestartListener(item.ip, "shutdown -h now", 15))
                .setNegativeButton("Reboot", new ShutdownRestartListener(item.ip, "reboot", 30))
                .setNeutralButton("Abort", null)
                .setCancelable(false)
                .create()
                .show();

        //shutdownListener.ip

    }

    class ShutdownRestartListener implements DialogInterface.OnClickListener {

        private String ip;
        private String command;
        private int timeout;

        public ShutdownRestartListener(@NonNull String ip, @NonNull  String command, int timeoutSeconds) {
            this.ip = ip;
            this.command = command;
            this.timeout = timeoutSeconds;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final ProgressDialog pd = new ProgressDialog(MainActivity.this);
            pd.setTitle("Bitte warten");
            //pd.setMessage("message");
            //pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            pd.setIndeterminate(true);
            pd.show();

            //TODO background thread!!
            //webSocket.send("shutdown");


            new SshClient("pi", "raspberry", ip, new SshClient.Callback() {
                @Override
                public void onFinish(Exception exception) {
                    if(exception != null) {
                        Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    if(pd.isShowing()) {
                        pd.dismiss();
                    }
                }
            }, command, timeout).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }


    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                openCameraList();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void openCameraList() {
        openFragment(CameraListFragment.newInstance(1));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if(fragment instanceof CameraViewFragment) {
                openCameraList();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void openFragment(Fragment fragment) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        //ft.replace(android.R.id.content, fragment).commit();
        ft.replace(R.id.fragment_container, fragment).commit();
    }

    //check whether wifi hotspot on or off
    public static boolean isApOn(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        }
        catch (Throwable ignored) {
            ignored.printStackTrace();
        }
        return false;
    }



}
