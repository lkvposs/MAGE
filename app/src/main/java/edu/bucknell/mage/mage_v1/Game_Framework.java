package edu.bucknell.mage.mage_v1;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Parcelable;
import android.util.Log;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.RemoteZigBeeDevice;
import com.digi.xbee.api.models.XBee16BitAddress;
import com.digi.xbee.api.models.XBee64BitAddress;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by Laura on 1/7/2016.
 */

public class Game_Framework implements Serializable {
    // The parent class that all games should be constructed using

    public int mNumPlayers = 1;        // always start with the person configuring the game as a player
    public List<PlayerStats> players = new ArrayList<PlayerStats>();
    public int mNumNodes = 0;
    public List<XBeeStats> nodes = new ArrayList<XBeeStats>();
    String game_name;
    String game_description;

    // needed for intents for starting the activities associated with active configuration and passive configuration
    Class activeConfig;
    Class passiveConfig;

    public String returnName() {return game_name;}
    public String returnDesc() {return game_description;}

    public Class returnConfigClass(int identifier) {
        if (identifier == 0) {return activeConfig;}
        else {return passiveConfig;}
    }

    public String[] parseKitInfo(KitPacket kitPacket) {
        // parse the information in the kit packet using ';' as the delimiter
        String delim = "[;]";
        return kitPacket.kitInfo.split(delim);
    }

    public void processKitGameAcceptance(KitPacket kit) {
        // an active configuration method
        // when a user accepts a game -- add the player to the player array

        PlayerStats newPlayer = new PlayerStats();
        newPlayer.xBeeDevice = kit.sender;
        String[] player_info = parseKitInfo(kit);
        if (player_info.length > 1) {
            // The acceptance packet contains a username as the second parameter in kit info
            newPlayer.username = player_info[1];
        }
        players.add(newPlayer);

        // update the number of players
        this.mNumPlayers += 1;
    }


    public void processNodeGameAcceptance(NodePacket node) {
        // an active configuration method

        node.sender.node_id = node.sensorData;
        nodes.add(node.sender);

        // update the number of nodes
        this.mNumNodes += 1;
    }


    public void broadcastKitPacket(int configState, int gameStateToggle, String kitInfo, boolean broadcast, XBeeStats destination, MessageReceiver messageReceiver) {
        // information to broadcast across the game network to all other kits

        int type = 2;
        String kitToKit = Integer.toString(type) + Integer.toString(configState) + Integer.toString(gameStateToggle) + kitInfo;
        messageReceiver.sendMessage(kitToKit, broadcast, destination);
    }

    public void broadcastNodePacket(int configRequest, int gameNum, int nodeRole, int gameStateToggle, boolean broadcast, XBeeStats destination, MessageReceiver messageReceiver) {
        // information to broadcast across the game network to all nodes

        int type = 3;
        byte kitToNode[] = {(byte)type, (byte)configRequest, (byte)gameNum, (byte)nodeRole, (byte)gameStateToggle};
        messageReceiver.sendMessage(kitToNode, broadcast, destination);
    }

    public KitPacket parseKitPacket(String kitInfo, XBeeStats sender) {
        // parse a packet received from a kit

        KitPacket newPacket = new KitPacket();
        newPacket.packetType = Character.getNumericValue(kitInfo.charAt(0));
        newPacket.configState = Character.getNumericValue(kitInfo.charAt(1));
        newPacket.gameStateToggle = Character.getNumericValue(kitInfo.charAt(2));
        newPacket.kitInfo = kitInfo.substring(3);
        newPacket.sender = sender;

        return newPacket;
    }

    public NodePacket parseNodePacket(String nodeInfo, XBeeStats sender) {
        // parse a packet received from a node

        NodePacket newPacket = new NodePacket();
        newPacket.packetType = Character.getNumericValue(nodeInfo.charAt(0));
        newPacket.configState = Character.getNumericValue(nodeInfo.charAt(1));
        newPacket.sensor = Character.getNumericValue(nodeInfo.charAt(2));
        newPacket.configurationConfirmationSignal = Character.getNumericValue(nodeInfo.charAt(3));
        newPacket.sensorData = nodeInfo.substring(4);
        newPacket.sender = sender;

        return newPacket;
    }

    public class KitPacket implements Serializable {
        // Kit-to-kit packet
        public int packetType = 2;
        public int configState;
        public int gameStateToggle;
        public String kitInfo;
        public XBeeStats sender;
    }

    public class NodePacket implements Serializable {
        // Node-to-kit packet
        public int packetType = 1;
        public int configurationConfirmationSignal;
        public int configState;
        public int sensor;
        public String sensorData;
        public XBeeStats sender;
    }

    static public class PlayerStats implements Serializable {
        public XBeeStats xBeeDevice;
        public int playerTeam;
        public String username;
    }

    static public class XBeeStats implements Serializable {
        public byte[] longAddr;
        public byte[] shortAddr;
        public int node_role = 0;
        public String node_id;             // for nodes, this value will be the NFC ID
    }
}