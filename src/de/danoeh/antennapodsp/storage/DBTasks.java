package de.danoeh.antennapodsp.storage;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.feed.*;
import de.danoeh.antennapodsp.preferences.UserPreferences;
import de.danoeh.antennapodsp.service.download.DownloadStatus;
import de.danoeh.antennapodsp.service.playback.PlaybackService;
import de.danoeh.antennapodsp.util.DownloadError;
import de.danoeh.antennapodsp.util.NetworkUtils;
import de.danoeh.antennapodsp.util.QueueAccess;
import de.danoeh.antennapodsp.util.comparator.FeedItemPubdateComparator;
import de.danoeh.antennapodsp.util.exception.MediaFileNotFoundException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides methods for doing common tasks that use DBReader and DBWriter.
 */
public final class DBTasks {
    private static final String TAG = "DBTasks";

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private static ExecutorService autodownloadExec;

    static {
        autodownloadExec = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
    }

    private DBTasks() {
    }

    /**
     * Removes the feed with the given download url. This method should NOT be executed on the GUI thread.
     *
     * @param context     Used for accessing the db
     * @param downloadUrl URL of the feed.
     */
    public static void removeFeedWithDownloadUrl(Context context, String downloadUrl) {
        PodDBAdapter adapter = new PodDBAdapter(context);
        adapter.open();
        Cursor cursor = adapter.getFeedCursorDownloadUrls();
        long feedID = 0;
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(1).equals(downloadUrl)) {
                    feedID = cursor.getLong(0);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        adapter.close();

        if (feedID != 0) {
            try {
                DBWriter.deleteFeed(context, feedID).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        } else {
            Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: " + downloadUrl);
        }
    }

    /**
     * Starts playback of a FeedMedia object's file. This method will build an Intent based on the given parameters to
     * start the {@link PlaybackService}.
     *
     * @param context           Used for sending starting Services and Activities.
     * @param media             The FeedMedia object.
     * @param showPlayer        If true, starts the appropriate player activity ({@link de.danoeh.antennapodsp.activity.AudioplayerActivity}
     *                          or {@link de.danoeh.antennapodsp.activity.VideoplayerActivity}
     * @param startWhenPrepared Parameter for the {@link PlaybackService} start intent. If true, playback will start as
     *                          soon as the PlaybackService has finished loading the FeedMedia object's file.
     * @param shouldStream      Parameter for the {@link PlaybackService} start intent. If true, the FeedMedia object's file
     *                          will be streamed, otherwise the downloaded file will be used. If the downloaded file cannot be
     *                          found, the PlaybackService will shutdown and the database entry of the FeedMedia object will be
     *                          corrected.
     */
    public static void playMedia(final Context context, final FeedMedia media,
                                 boolean showPlayer, boolean startWhenPrepared, boolean shouldStream) {
        try {
            if (!shouldStream) {
                if (media.fileExists() == false) {
                    throw new MediaFileNotFoundException(
                            "No episode was found at " + media.getFile_url(),
                            media);
                }
            }
            // Start playback Service
            Intent launchIntent = new Intent(context, PlaybackService.class);
            launchIntent.putExtra(PlaybackService.EXTRA_PLAYABLE, media);
            launchIntent.putExtra(PlaybackService.EXTRA_START_WHEN_PREPARED,
                    startWhenPrepared);
            launchIntent.putExtra(PlaybackService.EXTRA_SHOULD_STREAM,
                    shouldStream);
            launchIntent.putExtra(PlaybackService.EXTRA_PREPARE_IMMEDIATELY,
                    true);
            context.startService(launchIntent);
            if (showPlayer) {
                // Launch media player
                context.startActivity(PlaybackService.getPlayerActivityIntent(
                        context, media));
            }
            DBWriter.addQueueItemAt(context, media.getItem().getId(), 0, false);
        } catch (MediaFileNotFoundException e) {
            e.printStackTrace();
            if (media.isPlaying()) {
                context.sendBroadcast(new Intent(
                        PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE));
            }
            notifyMissingFeedMediaFile(context, media);
        }
    }

    private static AtomicBoolean isRefreshing = new AtomicBoolean(false);

    /**
     * Refreshes a given list of Feeds in a separate Thread. This method might ignore subsequent calls if it is still
     * enqueuing Feeds for download from a previous call
     *
     * @param context Might be used for accessing the database
     * @param feeds   List of Feeds that should be refreshed.
     */
    public static void refreshAllFeeds(final Context context,
                                       final List<Feed> feeds) {
        if (isRefreshing.compareAndSet(false, true)) {
            new Thread() {
                public void run() {
                    if (feeds != null) {
                        refreshFeeds(context, feeds);
                    } else {
                        refreshFeeds(context, DBReader.getFeedList(context));
                    }
                    isRefreshing.set(false);

                    autodownloadUndownloadedItems(context);
                }
            }.start();
        } else {
            if (AppConfig.DEBUG)
                Log.d(TAG,
                        "Ignoring request to refresh all feeds: Refresh lock is locked");
        }
    }

    /**
     * Used by refreshExpiredFeeds to determine which feeds should be refreshed.
     * This method will use the value specified in the UserPreferences as the
     * expiration time.
     *
     * @param context Used for DB access.
     * @return A list of expired feeds. An empty list will be returned if there
     * are no expired feeds.
     */
    public static List<Feed> getExpiredFeeds(final Context context) {
        long millis = UserPreferences.getUpdateInterval();

        if (millis > 0) {

            List<Feed> feedList = DBReader.getExpiredFeedsList(context,
                    millis);
            if (feedList.size() > 0) {
                refreshFeeds(context, feedList);
            }
            return feedList;
        } else {
            return new ArrayList<Feed>();
        }
    }

    /**
     * Refreshes expired Feeds in the list returned by the getExpiredFeedsList(Context, long) method in DBReader.
     * The expiration date parameter is determined by the update interval specified in {@link UserPreferences}.
     *
     * @param context Used for DB access.
     */
    public static void refreshExpiredFeeds(final Context context) {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Refreshing expired feeds");

        new Thread() {
            public void run() {
                refreshFeeds(context, getExpiredFeeds(context));
            }
        }.start();
    }

    private static void refreshFeeds(final Context context,
                                     final List<Feed> feedList) {

        for (Feed feed : feedList) {
            try {
                refreshFeed(context, feed);
            } catch (DownloadRequestException e) {
                e.printStackTrace();
                DBWriter.addDownloadStatus(
                        context,
                        new DownloadStatus(feed, feed
                                .getHumanReadableIdentifier(),
                                DownloadError.ERROR_REQUEST_ERROR, false, e
                                .getMessage()));
            }
        }

    }

    /**
     * Updates a specific Feed.
     *
     * @param context Used for requesting the download.
     * @param feed    The Feed object.
     */
    public static void refreshFeed(Context context, Feed feed)
            throws DownloadRequestException {
        DownloadRequester.getInstance().downloadFeed(context,
                new Feed(feed.getDownload_url(), new Date(), feed.getTitle()));
    }

    /**
     * Notifies the database about a missing FeedImage file. This method will attempt to re-download the file.
     *
     * @param context Used for requesting the download.
     * @param image   The FeedImage object.
     */
    public static void notifyInvalidImageFile(final Context context,
                                              final FeedImage image) {
        Log.i(TAG,
                "The DB was notified about an invalid image download. It will now try to re-download the image file");
        try {
            DownloadRequester.getInstance().downloadImage(context, image);
        } catch (DownloadRequestException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to download invalid feed image");
        }
    }

    /**
     * Notifies the database about a missing FeedMedia file. This method will correct the FeedMedia object's values in the
     * DB and send a FeedUpdateBroadcast.
     */
    public static void notifyMissingFeedMediaFile(final Context context,
                                                  final FeedMedia media) {
        Log.i(TAG,
                "The feedmanager was notified about a missing episode. It will update its database now.");
        media.setDownloaded(false);
        media.setFile_url(null);
        DBWriter.setFeedMedia(context, media);
        EventDistributor.getInstance().sendFeedUpdateBroadcast();
    }

    /**
     * Request the download of all objects in the queue. from a separate Thread.
     *
     * @param context Used for requesting the download an accessing the database.
     */
    public static void downloadAllItemsInQueue(final Context context) {
        new Thread() {
            public void run() {
                List<FeedItem> queue = DBReader.getQueue(context);
                if (!queue.isEmpty()) {
                    try {
                        downloadFeedItems(context,
                                queue.toArray(new FeedItem[queue.size()]));
                    } catch (DownloadRequestException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * Requests the download of a list of FeedItem objects.
     *
     * @param context Used for requesting the download and accessing the DB.
     * @param items   The FeedItem objects.
     */
    public static void downloadFeedItems(final Context context,
                                         FeedItem... items) throws DownloadRequestException {
        downloadFeedItems(true, context, items);
    }

    private static void downloadFeedItems(boolean performAutoCleanup,
                                          final Context context, final FeedItem... items)
            throws DownloadRequestException {
        final DownloadRequester requester = DownloadRequester.getInstance();

        if (performAutoCleanup) {
            new Thread() {

                @Override
                public void run() {
                    performAutoCleanup(context,
                            getPerformAutoCleanupArgs(context, items.length));
                }

            }.start();
        }
        for (FeedItem item : items) {
            if (item.getMedia() != null
                    && !requester.isDownloadingFile(item.getMedia())
                    && !item.getMedia().isDownloaded()) {
                if (items.length > 1) {
                    try {
                        requester.downloadMedia(context, item.getMedia());
                    } catch (DownloadRequestException e) {
                        e.printStackTrace();
                        DBWriter.addDownloadStatus(context,
                                new DownloadStatus(item.getMedia(), item
                                        .getMedia()
                                        .getHumanReadableIdentifier(),
                                        DownloadError.ERROR_REQUEST_ERROR,
                                        false, e.getMessage()));
                    }
                } else {
                    requester.downloadMedia(context, item.getMedia());
                }
            }
        }
    }

    private static int getNumberOfUndownloadedEpisodes(
            final List<FeedItem> queue, final List<FeedItem> unreadItems) {
        int counter = 0;
        for (FeedItem item : queue) {
            if (item.hasMedia() && !item.getMedia().isDownloaded()
                    && !item.getMedia().isPlaying()
                    && item.getFeed().getPreferences().getAutoDownload()) {
                counter++;
            }
        }
        for (FeedItem item : unreadItems) {
            if (item.hasMedia() && !item.getMedia().isDownloaded()
                    && item.getFeed().getPreferences().getAutoDownload()) {
                counter++;
            }
        }
        return counter;
    }

    /**
     * Looks for undownloaded episodes in the queue or list of unread items and request a download if
     * 1. Network is available
     * 2. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    public static Future<?> autodownloadUndownloadedItems(final Context context) {
        return autodownloadExec.submit(new Runnable() {
            @Override
            public void run() {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "Performing auto-dl of undownloaded episodes");
                if (NetworkUtils.autodownloadNetworkAvailable(context)
                        && UserPreferences.isEnableAutodownload()) {
                    final List<FeedItem> queue = DBReader.getQueue(context);
                    final List<FeedItem> unreadItems = DBReader
                            .getUnreadItemsList(context);

                    int undownloadedEpisodes = getNumberOfUndownloadedEpisodes(queue,
                            unreadItems);
                    int downloadedEpisodes = DBReader
                            .getNumberOfDownloadedEpisodes(context);
                    long deletedEpisodes = performAutoCleanup(context,
                            getPerformAutoCleanupArgs(context, undownloadedEpisodes));
                    long episodeSpaceLeft = undownloadedEpisodes;
                    boolean cacheIsUnlimited = UserPreferences.getEpisodeCacheSize() == UserPreferences
                            .getEpisodeCacheSizeUnlimited();

                    if (!cacheIsUnlimited
                            && UserPreferences.getEpisodeCacheSize() < downloadedEpisodes
                            + undownloadedEpisodes) {
                        episodeSpaceLeft = UserPreferences.getEpisodeCacheSize()
                                - (downloadedEpisodes - deletedEpisodes);
                    }

                    List<FeedItem> itemsToDownload = new ArrayList<FeedItem>();
                    if (episodeSpaceLeft > 0 && undownloadedEpisodes > 0) {
                        for (int i = 0; i < queue.size(); i++) { // ignore playing item
                            FeedItem item = queue.get(i);
                            if (item.hasMedia() && !item.getMedia().isDownloaded()
                                    && !item.getMedia().isPlaying()
                                    && item.getFeed().getPreferences().getAutoDownload()) {
                                itemsToDownload.add(item);
                                episodeSpaceLeft--;
                                undownloadedEpisodes--;
                                if (episodeSpaceLeft == 0 || undownloadedEpisodes == 0) {
                                    break;
                                }
                            }
                        }
                    }
                    if (episodeSpaceLeft > 0 && undownloadedEpisodes > 0) {
                        for (FeedItem item : unreadItems) {
                            if (item.hasMedia() && !item.getMedia().isDownloaded()
                                    && item.getFeed().getPreferences().getAutoDownload()) {
                                itemsToDownload.add(item);
                                episodeSpaceLeft--;
                                undownloadedEpisodes--;
                                if (episodeSpaceLeft == 0 || undownloadedEpisodes == 0) {
                                    break;
                                }
                            }
                        }
                    }
                    if (AppConfig.DEBUG)
                        Log.d(TAG, "Enqueueing " + itemsToDownload.size()
                                + " items for download");

                    try {
                        downloadFeedItems(false, context,
                                itemsToDownload.toArray(new FeedItem[itemsToDownload
                                        .size()]));
                    } catch (DownloadRequestException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

    }

    private static long getPerformAutoCleanupArgs(Context context,
                                                 final int episodeSize) {
        if (episodeSize >= 0
                && UserPreferences.getEpisodeCacheSize() != UserPreferences
                .getEpisodeCacheSizeUnlimited()) {
            long downloadedEpisodesSize = DBReader
                    .getDownloadedEpisodesSize(context);
            if (downloadedEpisodesSize + episodeSize >= UserPreferences
                    .getEpisodeCacheSize()) {

                return downloadedEpisodesSize + episodeSize
                        - UserPreferences.getEpisodeCacheSize();
            }
        }
        return 0;
    }

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'playbackCompletionDate'-value will be deleted first.
     * <p/>
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     */
    public static void performAutoCleanup(final Context context) {
        performAutoCleanup(context, getPerformAutoCleanupArgs(context, 0));
    }


    /**
     * Performs an automatic cleanup. Episodes that have been downloaded first will also be deleted first.
     * The episode that is currently playing as well as the n most recent episodes (the exact value is determined
     * by AppPreferences.numberOfNewAutomaticallyDownloadedEpisodes) will never be deleted.
     * @param context
     * @param episodeSize The maximum amount of space that should be free'd by this method
     * @return
     */
    private static long performAutoCleanup(final Context context,
                                          final long episodeSize) {
        if (AppConfig.DEBUG) Log.d(TAG, String.format("performAutoCleanup(%d)", episodeSize));

        if (episodeSize <= 0) {
            return 0;
        }

        List<FeedItem> candidates = DBReader.getAutoCleanupCandidates(context);
        List<FeedItem> deleteList = new ArrayList<FeedItem>();
        long deletedEpisodesSize = 0;
        Collections.sort(candidates, new Comparator<FeedItem>() {
            @Override
            public int compare(FeedItem lhs, FeedItem rhs) {
                File lFile = new File(lhs.getMedia().getFile_url());
                File rFile = new File(rhs.getMedia().getFile_url());
                if (!lFile.exists() || !rFile.exists()) {
                    return 0;
                }
                if (FileUtils.isFileOlder(lFile, rFile)) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        // listened episodes will be deleted first
        Iterator<FeedItem> it = candidates.iterator();
        for (FeedItem i = it.next(); it.hasNext() && deletedEpisodesSize <= episodeSize; i = it.next()) {
            if (!i.getMedia().isPlaying() && i.getMedia().getPlaybackCompletionDate() != null) {
                it.remove();
                deleteList.add(i);
                deletedEpisodesSize += i.getMedia().getSize();
            }
        }

        // delete unlistened old episodes if necessary
        it = candidates.iterator();
        for (FeedItem i = it.next(); it.hasNext() && deletedEpisodesSize <= episodeSize; i = it.next()) {
            if (!i.getMedia().isPlaying()) {
                it.remove();
                deleteList.add(i);
                deletedEpisodesSize += i.getMedia().getSize();
            }
        }

        for (FeedItem item : deleteList) {
            try {
                DBWriter.deleteFeedMediaOfItem(context, item.getMedia().getId()).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        if (AppConfig.DEBUG) Log.d(TAG, String.format("performAutoCleanup(%d) deleted %d episodes and free'd %d bytes of memory",
                episodeSize, deleteList.size(), deletedEpisodesSize));

        return deletedEpisodesSize;
    }

    /**
     * Adds all FeedItem objects whose 'read'-attribute is false to the queue in a separate thread.
     */
    public static void enqueueAllNewItems(final Context context) {
        long[] unreadItems = DBReader.getUnreadItemIds(context);
        DBWriter.addQueueItem(context, unreadItems);
    }

    /**
     * Returns the successor of a FeedItem in the queue.
     *
     * @param context Used for accessing the DB.
     * @param itemId  ID of the FeedItem
     * @param queue   Used for determining the successor of the item. If this parameter is null, the method will load
     *                the queue from the database in the same thread.
     * @return Successor of the FeedItem or null if the FeedItem is not in the queue or has no successor.
     */
    public static FeedItem getQueueSuccessorOfItem(Context context,
                                                   final long itemId, List<FeedItem> queue) {
        FeedItem result = null;
        if (queue == null) {
            queue = DBReader.getQueue(context);
        }
        if (queue != null) {
            Iterator<FeedItem> iterator = queue.iterator();
            while (iterator.hasNext()) {
                FeedItem item = iterator.next();
                if (item.getId() == itemId) {
                    if (iterator.hasNext()) {
                        result = iterator.next();
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Loads the queue from the database and checks if the specified FeedItem is in the queue.
     * This method should NOT be executed in the GUI thread.
     *
     * @param context    Used for accessing the DB.
     * @param feedItemId ID of the FeedItem
     */
    public static boolean isInQueue(Context context, final long feedItemId) {
        List<Long> queue = DBReader.getQueueIDList(context);
        return QueueAccess.IDListAccess(queue).contains(feedItemId);
    }

    private static Feed searchFeedByIdentifyingValue(Context context,
                                                     String identifier) {
        List<Feed> feeds = DBReader.getFeedList(context);
        for (Feed feed : feeds) {
            if (feed.getIdentifyingValue().equals(identifier)) {
                return feed;
            }
        }
        return null;
    }

    /**
     * Get a FeedItem by its identifying value.
     */
    private static FeedItem searchFeedItemByIdentifyingValue(Feed feed,
                                                             String identifier) {
        for (FeedItem item : feed.getItems()) {
            if (item.getIdentifyingValue().equals(identifier)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Adds a new Feed to the database or updates the old version if it already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread.
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    public static synchronized Feed updateFeed(final Context context,
                                               final Feed newFeed) {
        // Look up feed in the feedslist
        final Feed savedFeed = searchFeedByIdentifyingValue(context,
                newFeed.getIdentifyingValue());
        if (savedFeed == null) {
            if (AppConfig.DEBUG)
                Log.d(TAG,
                        "Found no existing Feed with title "
                                + newFeed.getTitle() + ". Adding as new one.");
            // Add a new Feed
            try {
                DBWriter.addNewFeed(context, newFeed).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return newFeed;
        } else {
            if (AppConfig.DEBUG)
                Log.d(TAG, "Feed with title " + newFeed.getTitle()
                        + " already exists. Syncing new with existing one.");

            Collections.sort(newFeed.getItems(), new FeedItemPubdateComparator());
            savedFeed.setItems(DBReader.getFeedItemList(context, savedFeed));
            if (savedFeed.compareWithOther(newFeed)) {
                if (AppConfig.DEBUG)
                    Log.d(TAG,
                            "Feed has updated attribute values. Updating old feed's attributes");
                savedFeed.updateFromOther(newFeed);
            }
            // Look for new or updated Items
            for (int idx = 0; idx < newFeed.getItems().size(); idx++) {
                final FeedItem item = newFeed.getItems().get(idx);
                FeedItem oldItem = searchFeedItemByIdentifyingValue(savedFeed,
                        item.getIdentifyingValue());
                if (oldItem == null) {
                    // item is new
                    final int i = idx;
                    item.setFeed(savedFeed);
                    savedFeed.getItems().add(i, item);
                    item.setRead(false);
                } else {
                    oldItem.updateFromOther(item);
                }
            }
            // update attributes
            savedFeed.setLastUpdate(newFeed.getLastUpdate());
            savedFeed.setType(newFeed.getType());
            try {
                DBWriter.setCompleteFeed(context, savedFeed).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return savedFeed;
        }
    }

    /**
     * Searches the titles of FeedItems of a specific Feed for a given
     * string.
     *
     * @param context Used for accessing the DB.
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string.
     * @return A FutureTask object that executes the search request and returns the search result as a List of FeedItems.
     */
    public static FutureTask<List<FeedItem>> searchFeedItemTitle(final Context context,
                                                                 final long feedID, final String query) {
        return new FutureTask<List<FeedItem>>(new QueryTask<List<FeedItem>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor searchResult = adapter.searchItemTitles(feedID,
                        query);
                List<FeedItem> items = DBReader.extractItemlistFromCursor(context, searchResult);
                DBReader.loadFeedDataOfFeedItemlist(context, items);
                setResult(items);
                searchResult.close();
            }
        });
    }

    /**
     * Searches the descriptions of FeedItems of a specific Feed for a given
     * string.
     *
     * @param context Used for accessing the DB.
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string
     * @return A FutureTask object that executes the search request and returns the search result as a List of FeedItems.
     */
    public static FutureTask<List<FeedItem>> searchFeedItemDescription(final Context context,
                                                                       final long feedID, final String query) {
        return new FutureTask<List<FeedItem>>(new QueryTask<List<FeedItem>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor searchResult = adapter.searchItemDescriptions(feedID,
                        query);
                List<FeedItem> items = DBReader.extractItemlistFromCursor(context, searchResult);
                DBReader.loadFeedDataOfFeedItemlist(context, items);
                setResult(items);
                searchResult.close();
            }
        });
    }

    /**
     * Searches the contentEncoded-value of FeedItems of a specific Feed for a given
     * string.
     *
     * @param context Used for accessing the DB.
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string
     * @return A FutureTask object that executes the search request and returns the search result as a List of FeedItems.
     */
    public static FutureTask<List<FeedItem>> searchFeedItemContentEncoded(final Context context,
                                                                          final long feedID, final String query) {
        return new FutureTask<List<FeedItem>>(new QueryTask<List<FeedItem>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor searchResult = adapter.searchItemContentEncoded(feedID,
                        query);
                List<FeedItem> items = DBReader.extractItemlistFromCursor(context, searchResult);
                DBReader.loadFeedDataOfFeedItemlist(context, items);
                setResult(items);
                searchResult.close();
            }
        });
    }

    /**
     * Searches chapters of the FeedItems of a specific Feed for a given string.
     *
     * @param context Used for accessing the DB.
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string
     * @return A FutureTask object that executes the search request and returns the search result as a List of FeedItems.
     */
    public static FutureTask<List<FeedItem>> searchFeedItemChapters(final Context context,
                                                                    final long feedID, final String query) {
        return new FutureTask<List<FeedItem>>(new QueryTask<List<FeedItem>>(context) {
            @Override
            public void execute(PodDBAdapter adapter) {
                Cursor searchResult = adapter.searchItemChapters(feedID,
                        query);
                List<FeedItem> items = DBReader.extractItemlistFromCursor(context, searchResult);
                DBReader.loadFeedDataOfFeedItemlist(context, items);
                setResult(items);
                searchResult.close();
            }
        });
    }

    /**
     * A runnable which should be used for database queries. The onCompletion
     * method is executed on the database executor to handle Cursors correctly.
     * This class automatically creates a PodDBAdapter object and closes it when
     * it is no longer in use.
     */
    static abstract class QueryTask<T> implements Callable<T> {
        private T result;
        private Context context;

        public QueryTask(Context context) {
            this.context = context;
        }

        @Override
        public T call() throws Exception {
            PodDBAdapter adapter = new PodDBAdapter(context);
            adapter.open();
            execute(adapter);
            adapter.close();
            return result;
        }

        public abstract void execute(PodDBAdapter adapter);

        protected void setResult(T result) {
            this.result = result;
        }
    }

}
