package edu.bucknell.mage.mage_v1;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Created by Laura on 2/1/2016.
 */
public class PlayGame_Capture_Frag extends Fragment {

    View _rootView;
    Context mContext;

    TextView mRemainingTime;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        _rootView = inflater.inflate(R.layout.playgame_capture_frag_layout, container, false);
        mContext = container.getContext();

        mRemainingTime = (TextView) _rootView.findViewById(R.id.remaining_game_time);

        return _rootView;
    }

    public void updateRemainingGameTime(long duration) {
        // The value of duration will be in milliseconds -- we want to display in minutes
        mRemainingTime.setText(String.valueOf(duration/1000));
    }

    /*
     * Name: initDisplayTeams
     * Dynamically adds each team with its corresponding node count to the UI.
     * Is called from onStart in the game play activity code.
     */
    public void initDisplayTeams(List<Integer> nodeCountPerTeam, int myTeam) {
        // Programmatically display each team and its corresponding node count

        // Get reference to the layout we want to add each team to
        LinearLayout parentLayout = (LinearLayout) _rootView.findViewById(R.id.game_play_team_nodes_captured_layout);

        int i;
        for (i=0; i < nodeCountPerTeam.size(); i++) {

            // Create the new linear layout we want to add for a team
            LinearLayout linearLayout = new LinearLayout(mContext);
            // Specify horizontal orientation
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            // Specify padding
            linearLayout.setPadding(30, 0, 30, 0);
            // Create LayoutParams for horizontal linear layout
            LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linearLayout.setLayoutParams(linearLayoutParams);

            // Create the TextView with the team number name
            TextView teamName = new TextView(mContext);
            // Create LayoutParams for TextView -- set the weight to be 0.5
            LinearLayout.LayoutParams teamNameLayoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f);
            teamName.setLayoutParams(teamNameLayoutParams);
            // Additional parameters
            teamName.setTextSize(20);
            teamName.setGravity(Gravity.CENTER_HORIZONTAL);
            if ((i+1) == myTeam) {
                teamName.setTextColor(ContextCompat.getColor(mContext, R.color.colorAccent));
            }
            // Set the text
            teamName.setText("Team Number " + (i+1));
            // Add the TextView to the newly created LinearLayout
            linearLayout.addView(teamName);

            // Create the TextView with the node count
            TextView nodeCount = new TextView(mContext);
            // Create Layout Params for TextView -- set the weight to be 0.5
            LinearLayout.LayoutParams nodeCountLayoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f);
            nodeCount.setLayoutParams(nodeCountLayoutParams);
            // Additional parameters
            nodeCount.setTextSize(20);
            nodeCount.setGravity(Gravity.CENTER_HORIZONTAL);
            if ((i+1) == myTeam) {
                nodeCount.setTextColor(ContextCompat.getColor(mContext, R.color.colorAccent));
            }
            // Set the text
            nodeCount.setText(Integer.toString(nodeCountPerTeam.get(i)));
            // Set ID so we can change the value as needed
            nodeCount.setTag("teamNumber" + (i+1) + "NodeCount");
            // Add the TextView to the newly created Linear Layout
            linearLayout.addView(nodeCount);

            // Add the entirety of the newly created Linear Layout to the existing layout
            parentLayout.addView(linearLayout);
        }
    }

    /*
     * Name: updateNodeCount
     * Whenever a new node is captured, this function is called to update the UI displaying
     * each team's node count.
     */
    public void updateNodeCount(int teamNumber, int previousTeam, List<Integer> nodeCountPerTeam) {

        if (previousTeam != 0) {
            // Need to display the new decremented node value for previousTeam

            // Get a reference to the TextView object to change
            TextView prevTeamNodeCount = (TextView) _rootView.findViewWithTag("teamNumber" + previousTeam + "NodeCount");
            // Update the value
            int newValue = nodeCountPerTeam.get(previousTeam-1);
            prevTeamNodeCount.setText(String.valueOf(newValue));
        }
        // Need to display the new incremented node value for teamNumber
        // Get a reference to the TextView object to change
        TextView newTeamNodeCount = (TextView) _rootView.findViewWithTag("teamNumber" + teamNumber + "NodeCount");
        // Update the value
        newTeamNodeCount.setText(Integer.toString(nodeCountPerTeam.get(teamNumber-1)));
    }

    public void displayWinningTeam(List<Integer> nodeCountPerTeam) {

        // Hide the currently displayed Controls and Info Section Layout
        LinearLayout controlsAndInfoLayout = (LinearLayout) _rootView.findViewById(R.id.game_play_controls_info_layout);
        controlsAndInfoLayout.setVisibility(View.GONE);

        // Determine which team has the most nodes
        int mostNodes = 0;
        int mostNodesTeamIndex = 0;
        int curNodeVal;
        int i;

        // First go through and determine what the highest node value is
        for (i=0; i < nodeCountPerTeam.size(); i++) {
            curNodeVal = nodeCountPerTeam.get(i);
            if (curNodeVal > mostNodes) {
                mostNodes = curNodeVal;
                mostNodesTeamIndex = i;
            }
        }

        int numTeamsWithHighestNodeValue = 0;
        // Go through again to determine how many teams have the highest value
        for (i=0; i < nodeCountPerTeam.size(); i++) {
            curNodeVal = nodeCountPerTeam.get(i);
            if (curNodeVal == mostNodes) {
                numTeamsWithHighestNodeValue++;
            }
        }

        // If numTeamsWithHighestNodeValue is greater than 1, then we have a tie
        if (numTeamsWithHighestNodeValue > 1) {
            // Show the Display TIE Layout
            LinearLayout dispTieLayout = (LinearLayout) _rootView.findViewById(R.id.game_play_tie_results_layout);
            dispTieLayout.setVisibility(View.VISIBLE);
        }
        else {
            // Show the Display Winning Team Layout
            LinearLayout dispWinTeamLayout = (LinearLayout) _rootView.findViewById(R.id.game_play_winning_team_layout);
            dispWinTeamLayout.setVisibility(View.VISIBLE);

            // Get a reference to the TextView to display the winning team number
            TextView winningTeamNum = (TextView) _rootView.findViewById(R.id.game_play_winning_team);
            winningTeamNum.setText(Integer.toString(mostNodesTeamIndex+1));
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
