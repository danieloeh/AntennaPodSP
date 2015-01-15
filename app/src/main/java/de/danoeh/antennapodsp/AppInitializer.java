package de.danoeh.antennapodsp;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.xml.parsers.ParserConfigurationException;

import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.service.download.DownloadRequest;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.service.download.HttpDownloader;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.syndication.handler.FeedHandler;
import de.danoeh.antennapod.core.syndication.handler.UnsupportedFeedtypeException;

import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_DOWNLOAD_MEDIA_ON_WIFI_ONLY;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_ENABLE_AUTODL;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_EPISODE_CACHE_SIZE;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_MOBILE_UPDATE;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_PAUSE_ON_HEADSET_DISCONNECT;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_EXPANDED_NOTIFICATION;
import static de.danoeh.antennapod.core.preferences.UserPreferences.PREF_PERSISTENT_NOTIFICATION;

/**
 * The AppInitializer processes the preferences that were specified in AppPreferences.
 */
public class AppInitializer {
    private static final String TAG = "AppInitializer";

    private static final String PREFS_APP_INITIALIZER = "PrefAppInit";
    private static final String PREF_IS_FIRST_LAUNCH = "prefIsFirstLaunch";
    private static final String PREF_PREF_VERSION_NUMBER = "prefPrefVersionNumber";

    public static void initializeApp(Context context) throws ExecutionException, InterruptedException, InitializerException {
        if (context == null) throw new IllegalArgumentException("context = null");
        context = context.getApplicationContext();
        if (context == null) throw new IllegalStateException("Could not get application context");

        writeUserPreferences(context);

        final AppPreferences appPreferences = new AppPreferences();
        // check if this is the first launch of the app
        SharedPreferences initPrefs = context.getSharedPreferences(PREFS_APP_INITIALIZER, Context.MODE_PRIVATE);
        final boolean isFirstLaunch = initPrefs.getBoolean(PREF_IS_FIRST_LAUNCH, true);
        final int currentVersionNumber = initPrefs.getInt(PREF_PREF_VERSION_NUMBER, 0);
        if (BuildConfig.DEBUG)
            Log.d(TAG, String.format("First start: %s, Version number: %d", String.valueOf(isFirstLaunch), currentVersionNumber));

        if (!isFirstLaunch && currentVersionNumber >= appPreferences.feedUrlsVersionNumber) {
            if (BuildConfig.DEBUG) Log.d(TAG, "AppPreferences are up-to-date");
            return;
        }
        if (!isFirstLaunch)
            Log.i(TAG, String.format("Upgrading from version %d to version %d", currentVersionNumber, appPreferences.feedUrlsVersionNumber));

        // refresh feeds
        List<Feed> savedFeeds = DBReader.getFeedList(context);
        for (Feed f : savedFeeds) {
            DBWriter.deleteFeed(context, f.getId()).get();
        }

        File destDir = context.getExternalFilesDir(DownloadRequester.FEED_DOWNLOADPATH);
        if (destDir == null)
            throw new InitializerException(context.getString(R.string.storage_access_failed));
        for (int i = 0; i < appPreferences.feedUrls.length; i++) {
            String url = appPreferences.feedUrls[i];
            if (BuildConfig.DEBUG) Log.d(TAG, "Downloading feed: " + url);

            File destFile = new File(destDir, "feed " + i);
            destFile.delete();

            downloadFeed(context, url, destFile.toString());
        }

        if (isFirstLaunch) {
            appPreferences.setUpdateAlarm();
        }

        // update init preferences
        SharedPreferences.Editor initPrefsEditor = initPrefs.edit();
        initPrefsEditor.putBoolean(PREF_IS_FIRST_LAUNCH, false);
        initPrefsEditor.putInt(PREF_PREF_VERSION_NUMBER, appPreferences.feedUrlsVersionNumber);
        initPrefsEditor.commit();
    }

    private static void downloadFeed(Context context, String downloadUrl, String fileUrl) throws InitializerException {
        Feed feed = new Feed(downloadUrl, new Date());
        feed.setFile_url(fileUrl);

        HttpDownloader downloader = new HttpDownloader(new DownloadRequest(fileUrl, downloadUrl, "feed", 0, Feed.FEEDFILETYPE_FEED));
        downloader.call();
        DownloadStatus status = downloader.getResult();
        if (!status.isSuccessful()) {
            throw new InitializerException(status.getReason().getErrorString(context));
        } else {
            FeedHandler handler = new FeedHandler();
            try {
                handler.parseFeed(feed);
            } catch (SAXException e) {
                e.printStackTrace();
                throw new InitializerException(e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new InitializerException(e);
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
                throw new InitializerException(e);
            } catch (UnsupportedFeedtypeException e) {
                e.printStackTrace();
                throw new InitializerException(e);
            }

            DBTasks.updateFeed(context, feed);
            if (feed.getImage() != null && feed.getImage().getDownload_url() != null) {
                try {
                    DownloadRequester.getInstance().downloadImage(context, feed.getImage());
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Read preference values from AppPreferences and write them into the UserPreferences file.
     */
    private static void writeUserPreferences(Context context) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Writing user preferences");
        AppPreferences appPreferences = new AppPreferences();
        SharedPreferences userPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor upe = userPrefs.edit();
        upe.putBoolean(PREF_PAUSE_ON_HEADSET_DISCONNECT, appPreferences.pauseOnHeadsetDisconnect);
        upe.putBoolean(PREF_DOWNLOAD_MEDIA_ON_WIFI_ONLY, appPreferences.downloadMediaOnWifiOnly);
        upe.putBoolean(PREF_MOBILE_UPDATE, appPreferences.allowMobileUpdates);
        upe.putBoolean(PREF_ENABLE_AUTODL, appPreferences.enableAutodownload);
        upe.putString(PREF_EPISODE_CACHE_SIZE, String.valueOf(appPreferences.episodeCacheSize));
        upe.putBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, appPreferences.pauseForFocusLoss);
        upe.putBoolean(PREF_EXPANDED_NOTIFICATION, appPreferences.expandNotification);
        upe.putBoolean(PREF_PERSISTENT_NOTIFICATION, appPreferences.persistentNotification);

        upe.commit();
    }


    public static class InitializerException extends Exception {
        public InitializerException(String detailMessage) {
            super(detailMessage);
        }

        public InitializerException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public InitializerException(Throwable throwable) {
            super(throwable);
        }
    }


}
