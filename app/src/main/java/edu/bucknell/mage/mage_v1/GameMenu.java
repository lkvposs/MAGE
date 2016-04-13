package edu.bucknell.mage.mage_v1;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.digi.xbee.api.RemoteXBeeDevice;

import edu.bucknell.mage.mage_v1.Bop_It.BopIt;

/**
 * Created by Laura on 1/5/2016.
 */
public class GameMenu extends Fragment implements NoUsernameFound.NoUsernameFoundDialogListener {
    View _rootView;
    TextView gameDesc_text;
    TextView gameDesc_header_text;
    Context mContext;
    Button play_game_button;
    int gameClicked;            // stores the last game to have been selected by a user
                                // this value is very important when it comes to starting the
                                // correct game to play

    // fill the game container with all of the available games
    // for aesthetic purposes, add instances of nonexistent games to the container
    Game_Framework[] games_container = {new CaptureTheNodes(), new BopIt(),
            new FutureGame(), new FutureGame(),
            new FutureGame(), new FutureGame(),
            new FutureGame(), new FutureGame()};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (_rootView == null) {
            // Inflate the layout for this fragment
            _rootView = inflater.inflate(R.layout.game_menu_layout, container, false);
        }
        else {
            // Do not inflate the layout again.
            // The returned View of onCreateView will be added into the fragment.
            // However it is not allowed to be added twice even if the parent is same.
            // So we must remove _rootView from the existing parent view group
            // in onDestroyView() (it will be added back).
        }
        mContext = container.getContext();

        // fill in the linear layout for the first scroll view for the buttons for each game
        // find the linear layout element
        LinearLayout button_layout = (LinearLayout) _rootView.findViewById(R.id.game_menu_for_buttons);
        // add a button for each game in the container
        for (int i=0; i<games_container.length; i++) {
            Button button = new Button(mContext);
            LinearLayout.LayoutParams button_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            button_params.gravity = Gravity.CENTER_HORIZONTAL;
            button.setLayoutParams(button_params);
            button.setId(i);
            button.setText(games_container[i].returnName());
            button_layout.addView(button);
            button.setOnClickListener(gameMenuClickHandler);
        }

        gameDesc_text = (TextView) _rootView.findViewById(R.id.game_menu_desc_text);
        gameDesc_header_text = (TextView) _rootView.findViewById(R.id.game_menu_desc_header);
        play_game_button = (Button) _rootView.findViewById(R.id.game_desc_play_button);
        play_game_button.setOnClickListener(gameMenuClickHandler);

        return _rootView;
    }

    View.OnClickListener gameMenuClickHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.game_desc_play_button) {
                // user wants to begin playing a selected game
                launchActiveConfig();
            }
            else {
                // if the Play button was not pressed, then the name of a game button was pressed
                // so bring up that game's description and store what the last game to be selected was
                // in the variable gameClicked
                gameClicked = v.getId();
                // display game name in description
                gameDesc_header_text.setText(games_container[gameClicked].returnName());
                // display game description
                gameDesc_text.setText(games_container[gameClicked].returnDesc());

                // make the game play button visible
                if (play_game_button.getVisibility() == View.INVISIBLE) {
                    play_game_button.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    public int checkUsername() {
        // check to make sure that the user has entered a username before starting a game
        // check the SharedPreferences file for this information
        // if username is found, return 1, else return 0

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String checkUsername = sharedPreferences.getString("username", "~");
        if (checkUsername.equals("~")) {
            // launch dialog for user to enter username before preceding
            DialogFragment newFragment = new NoUsernameFound();
            newFragment.setTargetFragment(this, 0);
            newFragment.show(getFragmentManager(), "no_username_found");
            return 0;
        }

        return 1;
    }

    @Override
    public void onDialogOkClick(DialogFragment dialog) {
        // user has just entered a username and clicked OK
        // username has already been saved in the NoUsernameFound fragment
        // launch the activity associated with active configuration
        startActiveConfigActivity();
    }

    public void startActiveConfigActivity() {
        // start the activity associated with active configuration for which game was selected
        Intent i = new Intent(mContext, games_container[gameClicked].returnConfigClass(0));
        // passing instance of selected game to its active configuration activity
        i.putExtra("game", games_container[gameClicked]);
        mContext.startActivity(i);
    }

    public void launchActiveConfig() {
        // begin active_configuration() of whatever game was last selected
        // the last selected game is found from games_container[gameClicked]
        // pass instance of the game to be played onto the next activity

        int found = checkUsername();

        if (found == 1) {
            // if a username has previously been entered, launch
            // the new activity
            startActiveConfigActivity();
        }
    }

    public void launchPassiveConfig(String gameName, Game_Framework.XBeeStats invitationSenderXBee, Context homeContext) {
        // this method is called when a player receives a game request from someone else
        // the game request will contain the name of the game to be played, which must be an input
        // into this method such that the correct game is launched

        int gameIndex = -1;

        // cycle through the games in games_container until we have a match
        for(int i=0; i<games_container.length; i++) {
            if (games_container[i].returnName().equals(gameName)) {
                // we have a match
                gameIndex = i;
                break;
            }
        }

        // if we found a match, start the passive configuration activity for that game
        // and pass along the game instance
        if (gameIndex != -1) {
            Intent i = new Intent(homeContext, games_container[gameIndex].returnConfigClass(1));
            // passing instance of selected game to its active configuration activity
            i.putExtra("game", games_container[gameIndex]);
            i.putExtra("xBeeLongAddr", invitationSenderXBee.longAddr);
            i.putExtra("xBeeShortAddr", invitationSenderXBee.shortAddr);
            i.putExtra("xBeeNI", invitationSenderXBee.node_id);
            homeContext.startActivity(i);
        }
        else {
            // if we didn't find a match, alert the user and then go back to the home menu
            DialogFragment newFragment = new NoGameFoundDialog();
            newFragment.show(getFragmentManager(), "no_game_found");
            getFragmentManager().popBackStack();
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
