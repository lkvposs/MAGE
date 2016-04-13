package edu.bucknell.mage.mage_v1.Bop_It;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.bucknell.mage.mage_v1.Count_Down_Timer;
import edu.bucknell.mage.mage_v1.Game_Framework;

/**
 * Created by Laura on 3/8/2016.
 */
public class BopIt extends Game_Framework {

    /*
     * Task key for the game of Bop It!
     * 1 - NFC Scan
     * 2 - Shake
     * 3 - Detect
     * 4 - Flip
     * 5 - Button
     */

    Random rand = new Random();

    String game_name = "MAGE Bop It!";
    String game_description = "Players compete against each other to see " +
            "who can trigger the most nodes within a configurable round " +
            "duration. There are 5 ways to trigger a node: NFC swiping, " +
            "shaking, flipping, pushing a button, and motion sensing. Use all of these triggering " +
            "options or just use 1, the game is up to you!";
    String IMEI = "355458061020724";                            // used for web reporting by the master kit
    String appKey = "702fa820-093d-4a56-a46e-5b848b4cdab2";     // Used to connect to Thingworx web app
    String IPaddr = "50.16.173.12";                             // IP address of Thingworx web app
    int mGameDuration;          // In minutes
    int mMyScore = 0;
    int currentTask = 0;
    int nextTask = 0;

    // The node roles to be used in the game
    int numNFCNodes = 0;
    int numShakeNodes = 0;
    int numDetectNodes = 0;
    int numFlipNodes = 0;
    int numButtonNodes = 0;
    List<Integer> nodeOptions;
    int mNumDiffOptions = 0;

    Class activeConfig = ActiveConfig_BopIt.class;
    Class passiveConfig = PassiveConfig_BopIt.class;

    @Override
    public String returnName() {return this.game_name;}

    @Override
    public String returnDesc() {return this.game_description;}

    @Override
    public Class returnConfigClass(int identifier) {
        if (identifier ==0) {return this.activeConfig;}
        else {return this.passiveConfig;}
    }

    /*
     * Name: startBopItRoundTimer
     * Begins the count down timer service when the local user begins
     * his or her Bop It! round
     */
    public void startBopItRoundTimer(Context context, ServiceConnection timer) {
        // Bind to local count down timer service
        Intent intent = new Intent(context, Count_Down_Timer.class);
        intent.putExtra("game_duration", mGameDuration);
        context.startService(intent);
        context.bindService(intent, timer, Context.BIND_AUTO_CREATE);
    }


    /*
     * Name: initializeNodeOptionsList
     * Based on the node options selected during the configuration process,
     * generate a list of possible tasks to perform ONCE in order to be
     * more efficient.
     */
    public void initializeNodeOptionsList() {
        int count = 0;
        if (numNFCNodes > 0) count ++;
        if (numShakeNodes > 0) count++;
        if (numDetectNodes > 0) count++;
        if (numFlipNodes > 0) count++;
        if (numButtonNodes > 0) count++;

        mNumDiffOptions = count;

        nodeOptions = new ArrayList<>(count);
        count = 0;
        if (numNFCNodes > 0) {
            nodeOptions.add(count, 1);
            count++;
        }
        if (numShakeNodes > 0) {
            nodeOptions.add(count, 2);
            count++;
        }
        if (numDetectNodes > 0) {
            nodeOptions.add(count, 3);
            count++;
        }
        if (numFlipNodes > 0) {
            nodeOptions.add(count, 4);
            count++;
        }
        if (numButtonNodes > 0) {
            nodeOptions.add(count, 5);
        }
    }


    /*
     * Name: generateNextTask
     * Randomly pick the next task for the user to perform during their
     * Bop It! round.
     *
     * Return values: (not all may be used)
     * 1 - NFC Scan
     * 2 - Shake
     * 3 - Detect
     * 4 - Flip
     * 5 - Button
     */
    public int generateNextTask() {
        // Randomly generate a number between 0 and mNumDiffOptions-1
        int min = 0;
        int max = mNumDiffOptions - 1;

        do {
            int randomIndex = rand.nextInt((max - min) + 1) + min;
            nextTask = nodeOptions.get(randomIndex);
        } while(nextTask == currentTask);

        currentTask = nextTask;

        return currentTask;
    }


    /*
     * Name: processNodeTrigger
     * Whenever a new node is triggered during a player's round, call this function
     * by passing the node that was triggered. It will determine if it was the correct
     * type of node to have triggered and will update the player's score accordingly.
     *
     * Returns true if the correct node was triggered
     * Returns false if an incorrect node was triggered
     */
    public boolean processNodeTrigger(int node) {
        if (node == currentTask) {
            // The correct node was triggered! -- add 5 points to the score
            mMyScore += 2;
            return true;
        }
        else {
            // An incorrect node was trigged -- subtract a point from the score
            mMyScore--;
            return false;
        }
    }
}
