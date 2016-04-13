package edu.bucknell.mage.mage_v1;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Random;

/**
 * Created by Laura on 1/9/2016.
 */
public class ActiveConfigCaptureFrag extends Fragment {

    private ProgressBar mProgressBar;
    private TextView mProgressText;
    View _rootView;
    Context mContext;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        _rootView = inflater.inflate(R.layout.active_config_capture_frag_layout, container, false);
        mContext = container.getContext();

        // get references to the progress bar and its associated text
        mProgressBar = (ProgressBar) _rootView.findViewById(R.id.progressBarActiveConfigCapture);
        mProgressText = (TextView) _rootView.findViewById(R.id.active_capture_progress_text);

        return _rootView;
    }

    public void visuallyModifyProgress(int progressValue, CharSequence progressText) {
        // update the progress bar and the text below the bar to depict
        // how far along in the configuration process a user is

        mProgressBar.setProgress(progressValue);
        mProgressText.setText(progressText);
    }

    public void pollingPlayersDisplay(boolean show) {
        // get a reference to the layout we want to display
        LinearLayout pollingPlayersDisp = (LinearLayout) _rootView.findViewById(R.id.poll_players_active_config);

        if (show) {
            if (pollingPlayersDisp != null) {
                pollingPlayersDisp.setVisibility(View.VISIBLE);
            }
        }
        else {
            if (pollingPlayersDisp != null) {
                pollingPlayersDisp.setVisibility(View.GONE);
            }
        }
    }

    public void chooseGameParamsDisplay(boolean show) {
        // get a referene to the layout we want to display
        LinearLayout gameParamsDisp = (LinearLayout) _rootView.findViewById(R.id.choose_game_params_layout);

        if (show) {
            if (gameParamsDisp != null) {
                gameParamsDisp.setVisibility(View.VISIBLE);
                // update the progress bar
                visuallyModifyProgress(25, getString(R.string.active_config_progress_text_25));
            }
        }
        else {
            if (gameParamsDisp != null) {
                gameParamsDisp.setVisibility(View.GONE);
            }
        }
    }

    public void pollingNodesDisplay(boolean show) {
        // get a reference to the layout we want to display
        LinearLayout pollingNodesDisp = (LinearLayout) _rootView.findViewById(R.id.poll_nodes_active_config);

        if (show) {
            if (pollingNodesDisp != null) {
                pollingNodesDisp.setVisibility(View.VISIBLE);
                // update the progress bar
                visuallyModifyProgress(50, getString(R.string.active_config_progress_text_50));
            }
        }
        else {
            if (pollingNodesDisp != null) {
                pollingNodesDisp.setVisibility(View.GONE);
            }
        }
    }

    public void confirmNodesDisplay(boolean show) {
        // get a reference to the layout we want to display
        LinearLayout confirmNodesDisp = (LinearLayout) _rootView.findViewById(R.id.confirm_nodes_active_config_layout);

        if (show) {
            if (confirmNodesDisp != null) {
                confirmNodesDisp.setVisibility(View.VISIBLE);
                // update the progress bar
                visuallyModifyProgress(75, getString(R.string.active_config_progress_text_75));
            }
        }
        else {
            if (confirmNodesDisp != null) {
                confirmNodesDisp.setVisibility(View.GONE);
            }
        }
    }

    public void playGameDisplay(boolean show) {
        // get a reference to the layout we want to display
        LinearLayout playGameDisp = (LinearLayout) _rootView.findViewById(R.id.active_config_play_game_display);

        if (show) {
            if (playGameDisp != null) {
                playGameDisp.setVisibility(View.VISIBLE);
                // update the progress bar
                visuallyModifyProgress(100, getString(R.string.active_config_progress_text_100));
            }
        }
        else {
            if (playGameDisp != null) {
                playGameDisp.setVisibility(View.GONE);
            }
        }
    }

    public void updateNumPlayersOrNodes(int playersOrNodes, int value) {
        // updates the display of the number of players or nodes to have joined the game
        // players = 0, else, nodes

        if (playersOrNodes == 0) {
            // update the number of players
            TextView displayNumPlayers = (TextView) _rootView.findViewById(R.id.num_players_joined_value);
            displayNumPlayers.setText(Integer.toString(value));
        }
        else {
            // update the number of nodes
            TextView displayNumNodes = (TextView) _rootView.findViewById(R.id.num_nodes_joined_value);
            displayNumNodes.setText(Integer.toString(value));
        }
    }

    @Override
    public void onDestroyView() {
        if (_rootView.getParent() != null) {
            ((ViewGroup)_rootView.getParent()).removeView(_rootView);
        }
        super.onDestroyView();
    }
}
