package edu.bucknell.mage.mage_v1;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Laura on 1/7/2016.
 */
public class CaptureTheNodes extends Game_Framework {

    String game_name = "Capture the Nodes";
    String game_description = "Each team tries to 'capture' as many nodes as possible in a " +
            "configurable amount of time. A node is captured by swiping it. A node can change " +
            "teams as many times as it is swiped throughout the duration of the game. Whichever " +
            "team has captured the most nodes by the end of the game wins.";
    int mNumTeams;
    int mGameDuration;  // in minutes
    int mMyTeamNum;

    // needed for intents for starting the activities associated with active configuration and passive configuration
    Class activeConfig = ActiveConfig_CaptureTheNodes.class;
    Class passiveConfig = PassiveConfig_Capture.class;

    @Override
    public String returnName() {return this.game_name;}

    @Override
    public String returnDesc() {return this.game_description;}

    @Override
    public Class returnConfigClass(int identifier) {
        if (identifier == 0) {return this.activeConfig;}
        else {return this.passiveConfig;}
    }

    public void startGamePlay(Context context, CaptureTheNodes gameInstance) {
        // start the activity needed to play the game Capture the Nodes

        Intent i = new Intent(context, PlayGame_Capture.class);
        i.putExtra("game", gameInstance);
        context.startActivity(i);
    }

}