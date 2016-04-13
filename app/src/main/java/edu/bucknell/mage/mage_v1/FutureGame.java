package edu.bucknell.mage.mage_v1;

/**
 * Created by Laura on 1/7/2016.
 */
public class FutureGame extends Game_Framework {

    String game_name = "Example Game";
    String game_description = "This is where you would put a short blurb about either " +
            "the plot of the game or how to play the game. You might even want to talk " +
            "about the configurable parts of the game, such as duration, number of players " +
            "etc.";

    Class activeConfig = FutureActiveConfig.class;
    Class passiveConfig = FuturePassiveConfig.class;

    @Override
    public String returnName() {return this.game_name;}

    @Override
    public String returnDesc() {return this.game_description;}

    @Override
    public Class returnConfigClass(int identifier) {
        if (identifier == 0) {return this.activeConfig;}
        else {return this.passiveConfig;}
    }

}
