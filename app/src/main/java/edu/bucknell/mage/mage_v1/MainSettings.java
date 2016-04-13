package edu.bucknell.mage.mage_v1;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/**
 * Created by Laura on 1/4/2016.
 */
public class MainSettings extends Fragment {

    String mUsername = null;
    String mBLETag = null;
    Context homeContext;
    View vRootView;
    Button bSave;
    EditText eUsernameText;
    EditText eBLETag;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        vRootView = inflater.inflate(R.layout.main_settings_layout, container, false);
        homeContext = container.getContext();

        bSave = (Button) vRootView.findViewById(R.id.save_settings_button);
        bSave.setOnClickListener(eventHandler);
        eUsernameText = (EditText) vRootView.findViewById(R.id.settings_name);
        eUsernameText.setOnClickListener(eventHandler);
        eBLETag = (EditText) vRootView.findViewById(R.id.settings_bleTag);
        eBLETag.setOnClickListener(eventHandler);

        // check SharedPreferences file to see if a username has been chosen
        SharedPreferences sharedPreferences = homeContext.getSharedPreferences(getResources().getString(R.string.pref_file), Context.MODE_PRIVATE);
        String checkUsername = sharedPreferences.getString(getResources().getString(R.string.settings_username), "~");
        if (!checkUsername.equals("~")){
            // a username has been saved; load this username
            mUsername = checkUsername;
            // also update the text field in the display to show the username
            eUsernameText.setText(mUsername);
        }

        // Check SharedPreferences file to see if a BLE Tag has been entered
        String checkBLETag = sharedPreferences.getString(getResources().getString(R.string.settings_BLEtag), "~~");
        if (!checkBLETag.equals("~~")) {
            // A BLE Tag has been saved; load this tag
            mBLETag = checkBLETag;
            // Also update the text field in the display to show the tag
            eBLETag.setText(mBLETag);
        }

        return vRootView;
    }

    public void saveSettings(){
        // save the user's username and BLE tag in a SharedPreferences file

        // first retrieve the username that the user entered into the field
        String entered_username = eUsernameText.getText().toString();

        // then save the value in the SharedPreferences file
        SharedPreferences sharedPreferences = homeContext.getSharedPreferences(getResources().getString(R.string.pref_file),Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getResources().getString(R.string.settings_username), entered_username);
        editor.commit();

        // Retrieve the BLE tag
        String entered_BLETag = eBLETag.getText().toString();

        // Save the value
        editor.putString(getResources().getString(R.string.settings_BLEtag), entered_BLETag);
        editor.commit();
    }

    public void clearText(int id) {
        // clear the text upon selecting the text field for entering a username if the default text is displayed

        if (id == R.id.settings_name) {
            if (mUsername == null) {
                // clear whatever text is in the username field
                eUsernameText.setText("");
            }
        }
        else if (id == R.id.settings_bleTag) {
            if (mBLETag == null) {
                // clear whatever text is in the BLE Tag field
                eBLETag.setText("");
            }
        }
    }

    View.OnClickListener eventHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.settings_name) {
                // user clicked the username text field
                clearText(v.getId());
            }
            else if (v.getId() == R.id.settings_bleTag) {
                // user clicked the BLE Tag text field
                clearText(v.getId());
            }
            else if (v.getId() == R.id.save_settings_button) {
                // user wants to save settings
                saveSettings();
            }
        }
    };
}
