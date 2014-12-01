package de.danoeh.antennapodsp;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

import de.danoeh.antennapod.core.asynctask.PicassoProvider;
import de.danoeh.antennapod.core.feed.EventDistributor;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.storage.DBTasks;

/**
 * Main application class.
 */
public class PodcastApp extends Application {

    // make sure that ClientConfigurator executes its static code
    static {
        try {
            Class.forName("de.danoeh.antennapodsp.config.ClientConfigurator");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("ClientConfigurator not found");
        }
    }


    private static final String TAG = "PodcastApp";
    public static final String EXPORT_DIR = "export/";

    private static float LOGICAL_DENSITY;

    private static PodcastApp singleton;

    public static PodcastApp getInstance() {
        return singleton;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
        LOGICAL_DENSITY = getResources().getDisplayMetrics().density;

        PicassoProvider.setupPicassoInstance(this);
        UserPreferences.createInstance(this);
        PlaybackPreferences.createInstance(this);
        EventDistributor.getInstance();

        DBTasks.refreshAllFeeds(this, null);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Received onLowOnMemory warning. Cleaning image cache...");
    }

    public static float getLogicalDensity() {
        return LOGICAL_DENSITY;
    }

    public boolean isLargeScreen() {
        return (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE
                || (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE;

    }
}
