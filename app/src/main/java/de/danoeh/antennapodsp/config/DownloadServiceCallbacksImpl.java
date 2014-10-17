package de.danoeh.antennapodsp.config;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.danoeh.antennapod.core.DownloadServiceCallbacks;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.service.download.DownloadRequest;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.DownloadError;
import de.danoeh.antennapodsp.BuildConfig;
import de.danoeh.antennapodsp.activity.MainActivity;


public class DownloadServiceCallbacksImpl implements DownloadServiceCallbacks {
    private static final String TAG = "DownloadServiceCallbacksImpl";


    @Override
    public PendingIntent getNotificationContentIntent(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(
                        context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public PendingIntent getAuthentificationNotificationContentIntent(Context context, DownloadRequest request) {
        // there is no authentication gui yet
        return getNotificationContentIntent(context);
    }

    @Override
    public PendingIntent getReportNotificationContentIntent(Context context) {
        return null;
    }

    @Override
    public void onFeedParsed(Context context, Feed feed) {
        for (FeedItem item : feed.getItems()) {
            if (item.hasItemImage() && (!item.getImage().isDownloaded())) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Item has image; Downloading....");
                try {
                    DownloadRequester.getInstance().downloadImage(context,
                            item.getImage());
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                    DBWriter.addDownloadStatus(
                            context,
                            new DownloadStatus(
                                    item.getImage(),
                                    item
                                            .getImage()
                                            .getHumanReadableIdentifier(),
                                    DownloadError.ERROR_REQUEST_ERROR,
                                    false, e.getMessage()
                            )
                    );
                }
            }
        }
    }

    @Override
    public boolean shouldCreateReport() {
        return false;
    }
}
