package de.danoeh.antennapodsp.config;


import android.content.Context;
import android.content.Intent;

import de.danoeh.antennapod.core.PlaybackServiceCallbacks;
import de.danoeh.antennapod.core.feed.MediaType;
import de.danoeh.antennapodsp.activity.MainActivity;

public class PlaybackServiceCallbacksImpl implements PlaybackServiceCallbacks {

    @Override
    public Intent getPlayerActivityIntent(Context context, MediaType mediaType) {
        return new Intent(context, MainActivity.class);
    }

    @Override
    public boolean useQueue() {
        return false;
    }
}
