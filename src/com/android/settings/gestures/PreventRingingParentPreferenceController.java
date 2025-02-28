/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.gestures;

import static android.provider.Settings.Secure.VOLUME_HUSH_GESTURE;
import static android.provider.Settings.Secure.YAAP_VOLUME_HUSH_MUTE;
import static android.provider.Settings.Secure.YAAP_VOLUME_HUSH_NORMAL;
import static android.provider.Settings.Secure.YAAP_VOLUME_HUSH_OFF;
import static android.provider.Settings.Secure.YAAP_VOLUME_HUSH_VIBRATE;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settings.widget.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import com.google.common.annotations.VisibleForTesting;

/** The controller manages the behaviour of the Prevent Ringing gesture setting. */
public class PreventRingingParentPreferenceController extends TogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    @VisibleForTesting
    static final int KEY_CHORD_POWER_VOLUME_UP_MUTE_TOGGLE = 1;

    final String SECURE_KEY = VOLUME_HUSH_GESTURE;

    private PrimarySwitchPreference mPreference;
    private SettingObserver mSettingObserver;

    public PreventRingingParentPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        mSettingObserver = new SettingObserver(mPreference);
    }

    @Override
    public boolean isChecked() {
        if (!isVolumePowerKeyChordSetToHush()) {
            return false;
        }

        String preventRinging = Settings.Secure.getString(
                mContext.getContentResolver(), VOLUME_HUSH_GESTURE);
        if (preventRinging == null) preventRinging = YAAP_VOLUME_HUSH_OFF;
        return !preventRinging.equals(YAAP_VOLUME_HUSH_OFF);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        String preventRingingSetting = Settings.Secure.getString(
                mContext.getContentResolver(), VOLUME_HUSH_GESTURE);
        if (preventRingingSetting == null) preventRingingSetting = YAAP_VOLUME_HUSH_OFF;

        final String newRingingSetting = preventRingingSetting.equals(YAAP_VOLUME_HUSH_OFF)
                ? YAAP_VOLUME_HUSH_VIBRATE : preventRingingSetting;

        return Settings.Secure.putString(mContext.getContentResolver(),
                VOLUME_HUSH_GESTURE, isChecked
                        ? newRingingSetting
                        : YAAP_VOLUME_HUSH_OFF);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        String value = Settings.Secure.getString(
                mContext.getContentResolver(), SECURE_KEY);
        if (value == null) value = YAAP_VOLUME_HUSH_OFF;
        String summary = null;
        if (isVolumePowerKeyChordSetToHush()) {
            if (value.equals(YAAP_VOLUME_HUSH_OFF)) {
                summary = mContext.getText(R.string.switch_off_text).toString();
            } else {
                String[] values = value.split(",", 0);
                for (String str : values) {
                    if (summary == null) {
                        summary = mContext.getText(R.string.switch_on_text).toString()
                                + " (" + getStringForMode(str);
                        continue;
                    }
                    summary += ", " + getStringForMode(str);
                }
                summary += ")";
            }
            preference.setEnabled(true);
            mPreference.setSwitchEnabled(true);
        } else {
            summary = mContext.getText(
                    R.string.prevent_ringing_option_unavailable_lpp_summary).toString();
            preference.setEnabled(false);
            mPreference.setSwitchEnabled(false);
        }

        preference.setSummary(summary);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_volumeHushGestureEnabled)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (isVolumePowerKeyChordSetToHush()) {
            return AVAILABLE;
        }
        if (mContext.getResources().getBoolean(
                com.android.internal
                        .R.bool.config_longPressOnPowerForAssistantSettingAvailable)) {
            // The power + volume key chord is not set to hush gesture - it's been disabled
            // by long press power for Assistant.
            return DISABLED_DEPENDENT_SETTING;
        }

        return UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void onStart() {
        if (mSettingObserver != null) {
            mSettingObserver.register(mContext.getContentResolver());
            mSettingObserver.onChange(false, null);
        }
    }

    @Override
    public void onStop() {
        if (mSettingObserver != null) {
            mSettingObserver.unregister(mContext.getContentResolver());
        }
    }

    /**
     * Returns true if power + volume up key chord is actually set to "mute toggle". If not,
     * this setting will have no effect and should be disabled.
     *
     * This handles the condition when long press on power for Assistant changes power + volume
     * chord to power menu and this setting needs to be disabled.
     */
    private boolean isVolumePowerKeyChordSetToHush() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.KEY_CHORD_POWER_VOLUME_UP,
                mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_keyChordPowerVolumeUp))
                == KEY_CHORD_POWER_VOLUME_UP_MUTE_TOGGLE;
    }

    private class SettingObserver extends ContentObserver {
        private final Uri mVolumeHushGestureUri = Settings.Secure.getUriFor(
                Settings.Secure.VOLUME_HUSH_GESTURE);
        private final Uri mKeyChordVolumePowerUpUri = Settings.Global.getUriFor(
                Settings.Global.KEY_CHORD_POWER_VOLUME_UP);

        private final Preference mPreference;

        SettingObserver(Preference preference) {
            super(new Handler());
            mPreference = preference;
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(mKeyChordVolumePowerUpUri, false, this);
            cr.registerContentObserver(mVolumeHushGestureUri, false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri == null || mVolumeHushGestureUri.equals(uri)
                    || mKeyChordVolumePowerUpUri.equals(uri)) {
                updateState(mPreference);
            }
        }
    }

    private String getStringForMode(String mode) {
        switch (mode) {
            case YAAP_VOLUME_HUSH_VIBRATE:
                return mContext.getText(R.string.prevent_ringing_option_vibrate).toString();
            case YAAP_VOLUME_HUSH_MUTE:
                return mContext.getText(R.string.prevent_ringing_option_mute).toString();
        }
        // YAAP_VOLUME_HUSH_NORMAL
        return mContext.getText(R.string.prevent_ringing_option_normal).toString();
    }
}
