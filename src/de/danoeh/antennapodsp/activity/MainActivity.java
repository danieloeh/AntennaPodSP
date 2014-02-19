package de.danoeh.antennapodsp.activity;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.feed.EventDistributor;
import de.danoeh.antennapodsp.fragment.EpisodesFragment;
import de.danoeh.antennapodsp.fragment.ExternalPlayerFragment;
import de.danoeh.antennapodsp.preferences.UserPreferences;
import de.danoeh.antennapodsp.service.download.DownloadService;
import de.danoeh.antennapodsp.storage.DBTasks;
import de.danoeh.antennapodsp.storage.DownloadRequester;
import de.danoeh.antennapodsp.util.StorageUtils;

/**
 * The activity that is shown when the user launches the app.
 */
public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";

    public static final String ARG_FEED_ID = "feedID";

    private static final String SAVED_STATE_ACTION_BAR_HIDDEN = "actionbar_hidden";

    private static final int EVENTS = EventDistributor.DOWNLOAD_HANDLED
            | EventDistributor.DOWNLOAD_QUEUED;

    private SlidingUpPanelLayout slidingUpPanelLayout;
    private ExternalPlayerFragment externalPlayerFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        StorageUtils.checkStorageAvailability(this);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setCustomView(R.layout.abs_layout);

        setContentView(R.layout.main);
        slidingUpPanelLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        slidingUpPanelLayout.setPanelSlideListener(panelSlideListener);
        slidingUpPanelLayout.setShadowDrawable(getResources().getDrawable(com.sothree.slidinguppanel.library.R.drawable.above_shadow));

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        int playerInitialState = ExternalPlayerFragment.ARG_INIT_ANCHORED;
        if (savedInstanceState != null && savedInstanceState.getBoolean(SAVED_STATE_ACTION_BAR_HIDDEN)) {
            getSupportActionBar().hide();
            slidingUpPanelLayout.expandPane();
            playerInitialState = ExternalPlayerFragment.ARG_INIT_EPXANDED;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fT = fragmentManager.beginTransaction();
        EpisodesFragment epf = (EpisodesFragment) fragmentManager.findFragmentById(R.id.main_view);
        if (epf == null) {
            long feedID = getIntent().getLongExtra(ARG_FEED_ID, 1L);
            epf = EpisodesFragment.newInstance(feedID);
        }
        fT.replace(R.id.main_view, epf);
        externalPlayerFragment = ExternalPlayerFragment.newInstance(playerInitialState);
        fT.replace(R.id.player_view, externalPlayerFragment);
        fT.commit();

        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                slidingUpPanelLayout.hidePane();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_ACTION_BAR_HIDDEN, !getSupportActionBar().isShowing());
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

    private SlidingUpPanelLayout.PanelSlideListener panelSlideListener = new SlidingUpPanelLayout.PanelSlideListener() {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
            if (slideOffset < 0.2) {
                if (getSupportActionBar().isShowing()) {
                    getSupportActionBar().hide();
                }
            } else {
                if (!getSupportActionBar().isShowing()) {
                    getSupportActionBar().show();
                }
            }
        }

        @Override
        public void onPanelCollapsed(View panel) {
            externalPlayerFragment.setFragmentState(ExternalPlayerFragment.FragmentState.ANCHORED);
            slidingUpPanelLayout.setDragView(externalPlayerFragment.getExpandView());
            getSupportActionBar().show();
        }

        @Override
        public void onPanelExpanded(View panel) {
            externalPlayerFragment.setFragmentState(ExternalPlayerFragment.FragmentState.EXPANDED);
            slidingUpPanelLayout.setDragView(externalPlayerFragment.getCollapseView());
            getSupportActionBar().hide();
        }

        @Override
        public void onPanelAnchored(View panel) {

        }
    };

    @Override
    public void onBackPressed() {
        if (slidingUpPanelLayout.isExpanded()) {
            slidingUpPanelLayout.collapsePane();
        } else {
            super.onBackPressed();
        }
    }

    public void onPlayerFragmentCreated(ExternalPlayerFragment fragment, ExternalPlayerFragment.FragmentState fragmentState) {
        if (fragmentState == ExternalPlayerFragment.FragmentState.EXPANDED) {
            slidingUpPanelLayout.setDragView(fragment.getCollapseView());
        } else {
            slidingUpPanelLayout.setDragView(fragment.getExpandView());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem refreshAll = menu.findItem(R.id.all_feed_refresh);
        if (DownloadService.isRunning
                && DownloadRequester.getInstance().isDownloadingFeeds()) {
            refreshAll.setVisible(false);
        } else {
            refreshAll.setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.all_feed_refresh:
                DBTasks.refreshAllFeeds(this, null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void resetPlayer() {
        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                slidingUpPanelLayout.collapsePane();
                slidingUpPanelLayout.hidePane();

            }
        });
    }

    public void openPlayer() {
        slidingUpPanelLayout.showPane();
        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                slidingUpPanelLayout.collapsePane();
            }
        });
    }
}
