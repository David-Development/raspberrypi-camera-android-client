package de.luhmer.livestreaming;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.luhmer.livestreaming.adapter.MyCameraListRecyclerViewAdapter;
import de.luhmer.livestreaming.helper.AsyncTaskScanClients;
import de.luhmer.livestreaming.helper.ClientScanResult;
import de.luhmer.livestreaming.models.CameraItems;

import static android.content.ContentValues.TAG;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class CameraListFragment extends Fragment {


    TextView tvNoDevicesFound;
    RecyclerView recyclerView;
    Timer timer;


    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CameraListFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static CameraListFragment newInstance(int columnCount) {
        CameraListFragment fragment = new CameraListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        startScan();
    }

    @Override
    public void onPause() {
        timer.cancel();
        timer = null;

        super.onPause();
    }

    private void startScan() {
        CameraItems.ITEMS.clear();

        timer = new Timer();
        final Handler handler = new Handler();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            new AsyncTaskScanClients(getActivity(), clientListCallback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            //Log.d(TAG, "Updated Client list");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, 5 * 1000); //execute in every 5 seconds
    }

    AsyncTaskScanClients.Callback clientListCallback = new AsyncTaskScanClients.Callback() {
        @Override
        public void result(List<ClientScanResult> clients) {
            if(timer != null) {
                CameraItems.ITEMS.clear();
                for (ClientScanResult clientScanResult : clients) {
                    CameraItems.ITEMS.add(new CameraItems.CameraItem(clientScanResult.getHWAddr(), clientScanResult.getIpAddr(), clientScanResult.isReachable()));
                }
                recyclerView.getAdapter().notifyDataSetChanged();

                tvNoDevicesFound.setVisibility((CameraItems.ITEMS.size() > 0) ? View.GONE : View.VISIBLE);
            }
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cameralist_list, container, false);


        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        tvNoDevicesFound = (TextView) view.findViewById(R.id.tvNoDevicesFound);

        Context context = view.getContext();
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.setAdapter(new MyCameraListRecyclerViewAdapter(CameraItems.ITEMS, mListener));


        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
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
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        void onListClickInteraction(CameraItems.CameraItem item);
        void onListLongClickInteraction(CameraItems.CameraItem item);
    }
}
