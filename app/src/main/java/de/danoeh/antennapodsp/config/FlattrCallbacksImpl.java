package de.danoeh.antennapodsp.config;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.shredzone.flattr4j.oauth.AccessToken;

import de.danoeh.antennapod.core.FlattrCallbacks;

public class FlattrCallbacksImpl implements FlattrCallbacks {

    @Override
    public boolean flattrEnabled() {
        return false;
    }

    @Override
    public Intent getFlattrAuthenticationActivityIntent(Context context) {
        return null;
    }

    @Override
    public PendingIntent getFlattrFailedNotificationContentIntent(Context context) {
        return null;
    }

    @Override
    public String getFlattrAppKey() {
        return null;
    }

    @Override
    public String getFlattrAppSecret() {
        return null;
    }

    @Override
    public void handleFlattrAuthenticationSuccess(AccessToken token) {

    }
}
