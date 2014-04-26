/*
 * Original work Copyright (C) 2013 The ChameleonOS Open Source Project
 * Modified work Copyright (C) 2013 GermainZ@xda-developers.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.germainz.identiconizer;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.ContactsContract;
import android.text.method.LinkMovementMethod;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.germainz.identiconizer.services.ContactsObserverService;
import com.germainz.identiconizer.services.IdenticonCreationService;
import com.germainz.identiconizer.services.IdenticonRemovalService;

public class IdenticonsSettings extends PreferenceActivity implements OnPreferenceChangeListener {
    private SwitchPreference mEnabledPref;
    private ImageListPreference mStylePref;

    private CharSequence mPreviousTitle;

    private Config mConfig;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.identicons_prefs);
        mConfig = Config.getInstance(this);

        mEnabledPref = (SwitchPreference) findPreference(Config.PREF_ENABLED);
        mEnabledPref.setChecked(mConfig.isEnabled());
        if (mEnabledPref.isChecked() && !mConfig.isXposedModActive())
            startService(new Intent(this, ContactsObserverService.class));
        mEnabledPref.setOnPreferenceChangeListener(this);

        PreferenceScreen prefSet = getPreferenceScreen();
        mStylePref = (ImageListPreference) prefSet.findPreference(Config.PREF_STYLE);
        mStylePref.setOnPreferenceChangeListener(this);
        int style = mConfig.getIdenticonStyle();
        mStylePref.setValue(String.valueOf(style));
        updateStyleSummary(style);

        Preference startServicePref = findPreference(Config.PREF_CREATE);
        startServicePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startService(new Intent(IdenticonsSettings.this, IdenticonCreationService.class));
                return true;
            }
        });

        startServicePref = findPreference(Config.PREF_REMOVE);
        startServicePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startService(new Intent(IdenticonsSettings.this, IdenticonRemovalService.class));
                return true;
            }
        });

        Preference identiconsSizePref = findPreference(Config.PREF_SIZE);
        identiconsSizePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final NumberPicker npView = new NumberPicker(IdenticonsSettings.this);

                final int minValue = 96;
                final int maxValue = getMaxContactPhotoSize();
                final int step = 16;
                String[] valueSet = new String[(maxValue - minValue) / step + 1];
                for (int i = minValue; i <= maxValue; i += step) {
                    valueSet[(i - minValue) / step] = String.valueOf(i);
                }
                npView.setMinValue(0);
                npView.setMaxValue(valueSet.length - 1);
                npView.setDisplayedValues(valueSet);
                npView.setValue((mConfig.getIdenticonSize() - minValue) / step);

                new AlertDialog.Builder(IdenticonsSettings.this)
                        .setView(npView)
                        .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                int value = npView.getValue() * step + minValue;
                                mConfig.setIdenticonSize(value);
                            }
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .create().show();
                return true;
            }
        });

        Preference aboutPref = findPreference(Config.PREF_ABOUT);
        aboutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog alertDialog = new AlertDialog.Builder(IdenticonsSettings.this)
                        .setMessage(R.string.about)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show();
                TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                return true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        final ActionBar bar = this.getActionBar();
        mPreviousTitle = bar.getTitle();
        bar.setTitle(R.string.identicons_title);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.getActionBar().setTitle(mPreviousTitle);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnabledPref) {
            mConfig.setEnabled((Boolean) newValue);
            if ((Boolean) newValue && !mConfig.isXposedModActive())
                startService(new Intent(this, ContactsObserverService.class));
            else if (!mConfig.isXposedModActive())
                stopService(new Intent(this, ContactsObserverService.class));
            return true;
        } else if (preference == mStylePref) {
            int style = Integer.valueOf((String) newValue);
            updateStyleSummary(style);
            return true;
        }
        return false;
    }

    private void updateStyleSummary(int value) {
        mStylePref.setSummary(mStylePref.getEntries()[mStylePref.findIndexOfValue("" + value)]);
        mConfig.setIdenticonStyle(value);
    }

    public int getMaxContactPhotoSize() {
        final Uri uri = ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI;
        final String[] projection = new String[]{ContactsContract.DisplayPhoto.DISPLAY_MAX_DIM};
        final Cursor c = this.getContentResolver().query(uri, projection, null, null, null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

}