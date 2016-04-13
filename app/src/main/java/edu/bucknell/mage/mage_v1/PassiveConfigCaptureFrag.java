package edu.bucknell.mage.mage_v1;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Created by Laura on 1/24/2016.
 */
public class PassiveConfigCaptureFrag extends Fragment {

    View _rootView;
    Context mContext;

    private ProgressBar mProgressBar;
    private TextView mProgressText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        _rootView = inflater.inflate(R.layout.passive_config_capture_frag_layout, container, false);
        mContext = container.getContext();

        mProgressBar = (ProgressBar) _rootView.findViewById(R.id.progressBarPassiveConfigCapture);
        mProgressText = (TextView) _rootView.findViewById(R.id.passive_capture_progress_text);

        return _rootView;
    }

    public void visuallyModifyProgress(int progressValue, CharSequence progressText) {
        // update the progress bar and the text below the bar to depict
        // how far along in the configuration process a user is

        mProgressBar.setProgress(progressValue);
        mProgressText.setText(progressText);
    }

    @Override
    public void onDestroyView() {
        if (_rootView.getParent() != null) {
            ((ViewGroup)_rootView.getParent()).removeView(_rootView);
        }
        super.onDestroyView();
    }
}
