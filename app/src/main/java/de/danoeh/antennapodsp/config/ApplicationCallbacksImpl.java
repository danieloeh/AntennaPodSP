package de.danoeh.antennapodsp.config;


import android.app.Application;
import android.content.Context;
import android.content.Intent;

import de.danoeh.antennapod.core.ApplicationCallbacks;
import de.danoeh.antennapodsp.PodcastApp;
import de.danoeh.antennapodsp.activity.StorageErrorActivity;

public class ApplicationCallbacksImpl implements ApplicationCallbacks{

    @Override
    public Application getApplicationInstance() {
        return PodcastApp.getInstance();
    }

    @Override
    public Intent getStorageErrorActivity(Context context) {
        return new Intent(context, StorageErrorActivity.class);
    }
}
