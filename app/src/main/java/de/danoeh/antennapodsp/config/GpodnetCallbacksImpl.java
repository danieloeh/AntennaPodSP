package de.danoeh.antennapodsp.config;


import android.app.PendingIntent;
import android.content.Context;

import de.danoeh.antennapod.core.GpodnetCallbacks;

public class GpodnetCallbacksImpl implements GpodnetCallbacks{

    @Override
    public boolean gpodnetEnabled() {
        return false;
    }

    @Override
    public PendingIntent getGpodnetSyncServiceErrorNotificationPendingIntent(Context context) {
        return null;
    }
}
