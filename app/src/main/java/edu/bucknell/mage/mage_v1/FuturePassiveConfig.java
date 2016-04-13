package edu.bucknell.mage.mage_v1;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by Laura on 1/15/2016.
 */
public class FuturePassiveConfig extends AppCompatActivity {

    FutureGame game;
    FragmentManager manager;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_other_game_layout);

        // get instance of the game that was called via intent
        Intent i = getIntent();
        game = (FutureGame)i.getSerializableExtra("game");
    }

    /*
    USE ONCE FRAGMENTS HAVE BEEN IMPLEMENTED
    @Override
    public void onBackPressed() {
        // handles the backstack for fragment navigation

        if (manager.getBackStackEntryCount() > 1) {
            manager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }
    */
}
