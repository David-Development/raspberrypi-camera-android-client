package de.luhmer.livestreaming.adapter;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import de.luhmer.livestreaming.CameraListFragment.OnListFragmentInteractionListener;
import de.luhmer.livestreaming.R;
import de.luhmer.livestreaming.models.CameraItems;
import de.luhmer.livestreaming.models.CameraItems.CameraItem;

/**
 * {@link RecyclerView.Adapter} that can display a {@link CameraItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class MyCameraListRecyclerViewAdapter extends RecyclerView.Adapter<MyCameraListRecyclerViewAdapter.ViewHolder> {

    private final List<CameraItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public MyCameraListRecyclerViewAdapter(List<CameraItems.CameraItem> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_cameralist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        CameraItem ci = mValues.get(position);
        holder.mItem = ci;
        holder.mIdView.setText(ci.id);
        holder.mContentView.setText(ci.ip);
        holder.mDeviceStatusView.getBackground().setColorFilter(ci.isReachable ? Color.GREEN : Color.RED, PorterDuff.Mode.SRC_ATOP);

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListClickInteraction(holder.mItem);
                }
            }
        });

        holder.mView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListLongClickInteraction(holder.mItem);
                }
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mIdView;
        public final TextView mContentView;
        public final View mDeviceStatusView;
        public CameraItem mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIdView = (TextView) view.findViewById(R.id.id);
            mContentView = (TextView) view.findViewById(R.id.content);
            mDeviceStatusView = view.findViewById(R.id.deviceStatus);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
