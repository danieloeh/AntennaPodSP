package de.danoeh.antennapodsp.fragment;

import android.app.AlertDialog;
import android.content.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.adapter.EpisodesListAdapter;
import de.danoeh.antennapodsp.asynctask.DownloadObserver;
import de.danoeh.antennapodsp.dialog.DownloadRequestErrorDialogCreator;
import de.danoeh.antennapodsp.feed.EventDistributor;
import de.danoeh.antennapodsp.feed.Feed;
import de.danoeh.antennapodsp.feed.FeedItem;
import de.danoeh.antennapodsp.feed.FeedMedia;
import de.danoeh.antennapodsp.service.download.Downloader;
import de.danoeh.antennapodsp.service.playback.PlaybackService;
import de.danoeh.antennapodsp.service.playback.PlayerStatus;
import de.danoeh.antennapodsp.storage.DBReader;
import de.danoeh.antennapodsp.storage.DBTasks;
import de.danoeh.antennapodsp.storage.DownloadRequestException;
import de.danoeh.antennapodsp.storage.DownloadRequester;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class EpisodesFragment extends ListFragment {
    private static final String TAG = "EpisodesFragment";

    private static final String ARG_FEED_ID = "feedID";

    private static final int EVENTS = EventDistributor.QUEUE_UPDATE
            | EventDistributor.UNREAD_ITEMS_UPDATE
            | EventDistributor.FEED_LIST_UPDATE
            | EventDistributor.DOWNLOAD_HANDLED;

    private Feed feed;
    private EpisodesListAdapter episodesListAdapter;
    private AsyncTask currentLoadTask = null;

    private DownloadObserver downloadObserver = null;
    private List<Downloader> downloaderList = null;

    private boolean feedsLoaded = false;

    public static EpisodesFragment newInstance(long feedID) {
        EpisodesFragment f = new EpisodesFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_FEED_ID, feedID);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().registerReceiver(playerStatusReceiver, new IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED));
    }

    private void refreshFeed() {
        AsyncTask<Void, Void, Feed> loadTask = new AsyncTask<Void, Void, Feed>() {
            volatile long feedID;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                feedID = getArguments().getLong(ARG_FEED_ID);
            }

            @Override
            protected void onPostExecute(Feed result) {
                super.onPostExecute(result);
                if (result != null) {
                    onFeedLoaded(result);
                }
            }

            @Override
            protected Feed doInBackground(Void... params) {
                Context context = getActivity();
                if (context != null) {
                    return DBReader.getFeed(getActivity(), feedID);
                } else {
                    return null;
                }
            }
        };
        loadTask.execute();
        currentLoadTask = loadTask;
    }

    private void onFeedLoaded(Feed result) {
        feed = result;
        if (episodesListAdapter == null) {
            episodesListAdapter = new EpisodesListAdapter(getActivity(), itemAccess);
            getListView().setAdapter(episodesListAdapter);
        }
        setListShown(true);
        episodesListAdapter.notifyDataSetChanged();
        EventDistributor.getInstance().register(contentUpdateListener);
        downloadObserver = new DownloadObserver(getActivity(), new Handler(),
                downloadObserverCallback);
        downloadObserver.onResume();
        feedsLoaded = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (feedsLoaded) {
            downloadObserver.onResume();
            EventDistributor.getInstance().register(contentUpdateListener);
            episodesListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (feedsLoaded) {
            downloadObserver.onPause();
            EventDistributor.getInstance().unregister(contentUpdateListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        currentLoadTask.cancel(true);
        try {
            getActivity().unregisterReceiver(playerStatusReceiver);
        } catch (IllegalArgumentException e) {
            if (AppConfig.DEBUG) e.printStackTrace();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                position = position - getListView().getHeaderViewsCount();
                if (position < 0) {
                    return;
                }
                FeedItem item = (FeedItem) episodesListAdapter.getItem(position);
                if (item.hasMedia() && item.getMedia().isDownloaded()) {
                    // episode downloaded
                    DBTasks.playMedia(getActivity(), item.getMedia(), false, true, false);
                } else if (item.hasMedia()) {
                    final FeedMedia media = item.getMedia();
                    AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                    dialog.setCancelable(true)
                            .setTitle(media.getEpisodeTitle());

                    if (DownloadRequester.getInstance().isDownloadingFile(media)) {
                        // episode downloading
                        dialog.setMessage(R.string.episode_dialog_downloading_msg)
                                .setPositiveButton(R.string.episode_dialog_stream, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        DBTasks.playMedia(getActivity(), media, false, true, true);
                                    }
                                })
                                .setNegativeButton(R.string.cancel_download_label, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        DownloadRequester.getInstance().cancelDownload(getActivity(), media);
                                    }
                                });

                    } else {
                        // episode not downloaded
                        dialog.setMessage(R.string.episode_dialog_not_downloaded_msg)
                                .setPositiveButton(R.string.download_label, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        try {
                                            DownloadRequester.getInstance().downloadMedia(getActivity(), media);
                                        } catch (DownloadRequestException e) {
                                            e.printStackTrace();
                                            DownloadRequestErrorDialogCreator.newRequestErrorDialog(getActivity(), e.getMessage());
                                        }
                                    }
                                })
                                .setNegativeButton(R.string.episode_dialog_stream, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        DBTasks.playMedia(getActivity(), media, false, true, true);
                                    }
                                });
                    }

                    dialog.create().show();

                }


            }
        });
        // add header so that list is below actionbar
        int actionBarHeight = getResources().getDimensionPixelSize(android.support.v7.appcompat.R.dimen.abc_action_bar_default_height);
        LinearLayout header = new LinearLayout(getActivity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        AbsListView.LayoutParams lp = new AbsListView.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, actionBarHeight);
        header.setLayoutParams(lp);
        getListView().addHeaderView(header);

        setListShown(false);
        refreshFeed();
    }

    private final EpisodesListAdapter.ItemAccess itemAccess = new EpisodesListAdapter.ItemAccess() {
        @Override
        public int getCount() {
            return (feed != null) ? feed.getItems().size() : 0;
        }

        @Override
        public FeedItem getItem(int position) {
            return (feed != null) ? feed.getItems().get(position) : null;
        }

        @Override
        public int getItemDownloadProgressPercent(FeedItem item) {
            if (downloaderList != null) {
                for (Downloader downloader : downloaderList) {
                    if (downloader.getDownloadRequest().getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                            && downloader.getDownloadRequest().getFeedfileId() == item.getMedia().getId()) {
                        return downloader.getDownloadRequest().getProgressPercent();
                    }
                }
            }
            return 0;
        }
    };

    private final DownloadObserver.Callback downloadObserverCallback = new DownloadObserver.Callback() {
        @Override
        public void onContentChanged() {
            episodesListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onDownloadDataAvailable(List<Downloader> downloaderList) {
            EpisodesFragment.this.downloaderList = downloaderList;
            episodesListAdapter.notifyDataSetChanged();
        }
    };

    private final EventDistributor.EventListener contentUpdateListener = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & EVENTS) != 0) {
                episodesListAdapter.notifyDataSetChanged();
            }
        }
    };

    private final BroadcastReceiver playerStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (StringUtils.equals(intent.getAction(), PlaybackService.ACTION_PLAYER_STATUS_CHANGED)) {
                int statusOrdinal = intent.getIntExtra(PlaybackService.EXTRA_NEW_PLAYER_STATUS, -1);
                if (statusOrdinal != -1) {
                    if (PlayerStatus.fromOrdinal(statusOrdinal) == PlayerStatus.INITIALIZED) {
                        if (episodesListAdapter != null) {
                            episodesListAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        }
    };


}
