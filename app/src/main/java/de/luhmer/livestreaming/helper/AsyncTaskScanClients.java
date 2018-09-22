package de.luhmer.livestreaming.helper;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

/**
 * Created by david on 06.07.17.
 */

public class AsyncTaskScanClients extends AsyncTask<Void, Void, List<ClientScanResult>> {

    private Callback callback;
    private WifiApManager wifiApManager;

    public AsyncTaskScanClients(Context context, Callback callback) {
        this.callback = callback;
        wifiApManager = new WifiApManager(context);
    }

    @Override
    protected List<ClientScanResult> doInBackground(Void... params) {
        return scan();
    }

    @Override
    protected void onPostExecute(List<ClientScanResult> clientScanResults) {
        callback.result(clientScanResults);
        super.onPostExecute(clientScanResults);
    }




    private ArrayList<ClientScanResult> scan() {
        final ArrayList<ClientScanResult> clients = wifiApManager.getClientList(false);


        //Log.d(TAG, "Clients:");
        for (ClientScanResult clientScanResult : clients) {
            /*
            Log.d(TAG, "####################\n");
            Log.d(TAG, "IpAddr: " + clientScanResult.getIpAddr() + "\n");
            Log.d(TAG, "Device: " + clientScanResult.getDevice() + "\n");
            Log.d(TAG, "HWAddr: " + clientScanResult.getHWAddr() + "\n");
            Log.d(TAG, "isReachable: " + clientScanResult.isReachable() + "\n");
            */

            Log.d(TAG, "IpAddr: " + clientScanResult.getIpAddr() + " - isReachable: " + clientScanResult.isReachable() + "\n");
        }



        return clients;
    }





    public interface Callback {
        void result(List<ClientScanResult> clients);
    }

}
