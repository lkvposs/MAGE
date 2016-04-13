package edu.bucknell.mage.mage_v1;

import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by Laura on 1/4/2016.
 */
public class GameRequest extends DialogFragment {
    String mRequestPlayer;
    String mRequestGame;

    public static GameRequest newInstance(String player, String game) {
        // input String player is the name of the player who is requesting invitation to join the game
        // input String game is the name of the game to be played

        Bundle gameRequestArgs = new Bundle();
        gameRequestArgs.putString("request_player", player);
        gameRequestArgs.putString("request_game", game);

        GameRequest gameReqFrag = new GameRequest();
        gameReqFrag.setArguments(gameRequestArgs);
        return gameReqFrag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // convert XML UI to Java
        View game_req_dialog = inflater.inflate(R.layout.game_request, container, false);

        // update class members
        mRequestPlayer = getArguments().getString("request_player");
        mRequestGame = getArguments().getString("request_game");

        // display who has initiated the game and what game is to be played
        View game_text = game_req_dialog.findViewById(R.id.game_request_info);
        ((TextView)game_text).setText("Player " + mRequestPlayer + " has sent you a game request to play "
                + mRequestGame + "!");

        return game_req_dialog;
    }

}
