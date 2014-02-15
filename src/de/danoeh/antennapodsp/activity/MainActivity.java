package de.danoeh.antennapodsp.activity;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Window;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.feed.EventDistributor;
import de.danoeh.antennapodsp.fragment.EpisodesFragment;
import de.danoeh.antennapodsp.preferences.UserPreferences;
import de.danoeh.antennapodsp.service.download.DownloadService;
import de.danoeh.antennapodsp.storage.DownloadRequester;
import de.danoeh.antennapodsp.util.StorageUtils;

/**
 * The activity that is shown when the user launches the app.
 */
public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";

    public static final String ARG_FEED_ID = "feedID";

    private static final int EVENTS = EventDistributor.DOWNLOAD_HANDLED
            | EventDistributor.DOWNLOAD_QUEUED;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        StorageUtils.checkStorageAvailability(this);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setContentView(R.layout.main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);


        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fT = fragmentManager.beginTransaction();

        long feedID = getIntent().getLongExtra(ARG_FEED_ID, 1L);
        EpisodesFragment epf = EpisodesFragment.newInstance(feedID);
        fT.replace(R.id.main_view, epf);
        fT.commit();

    }


    @Override
    protected void onPause() {
        super.onPause();
        EventDistributor.getInstance().unregister(contentUpdate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        StorageUtils.checkStorageAvailability(this);
        updateProgressBarVisibility();
        EventDistributor.getInstance().register(contentUpdate);

    }

    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {

        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((EVENTS & arg) != 0) {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "Received contentUpdate Intent.");
                updateProgressBarVisibility();
            }
        }
    };

    private void updateProgressBarVisibility() {
        if (DownloadService.isRunning
                && DownloadRequester.getInstance().isDownloadingFeeds()) {
            setSupportProgressBarIndeterminateVisibility(true);
        } else {
            setSupportProgressBarIndeterminateVisibility(false);
        }
        supportInvalidateOptionsMenu();
    }

}
