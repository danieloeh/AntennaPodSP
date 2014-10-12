package de.danoeh.antennapodsp.fragment;

import android.app.Activity;
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
import de.danoeh.antennapodsp.core.asynctask.DownloadObserver;
import de.danoeh.antennapodsp.core.dialog.DownloadRequestErrorDialogCreator;
import de.danoeh.antennapodsp.core.feed.EventDistributor;
import de.danoeh.antennapodsp.core.feed.Feed;
import de.danoeh.antennapodsp.core.feed.FeedItem;
import de.danoeh.antennapodsp.core.feed.FeedMedia;
import de.danoeh.antennapodsp.core.service.download.Downloader;
import de.danoeh.antennapodsp.core.service.playback.PlaybackService;
import de.danoeh.antennapodsp.core.service.playback.PlayerStatus;
import de.danoeh.antennapodsp.core.storage.DBReader;
import de.danoeh.antennapodsp.core.storage.DBTasks;
import de.danoeh.antennapodsp.core.storage.DownloadRequestException;
import de.danoeh.antennapodsp.core.storage.DownloadRequester;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class EpisodesFragment extends ListFragment {
    private static final String TAG = "EpisodesFragment";

    private static final String ARG_FEED_ID = "feedID";

    private static final int EVENTS = EventDistributor.FEED_LIST_UPDATE
            | EventDistributor.DOWNLOAD_HANDLED;

    private Feed feed;
    private EpisodesListAdapter episodesListAdapter;
    private AsyncTask currentLoadTask = null;

    private DownloadObserver downloadObserver = null;
    private List<Downloader> downloaderList = null;

    private boolean feedsLoaded = false;
    private boolean listviewSetup = false;

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
        setRetainInstance(true);
        refreshFeed();
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
        if (feedsLoaded && listviewSetup) {
            // feed has only been refreshed
            episodesListAdapter.notifyDataSetChanged();
        } else {
            EventDistributor.getInstance().register(contentUpdateListener);
            downloadObserver = new DownloadObserver(getActivity(), new Handler(),
                    downloadObserverCallback);
            downloadObserver.onResume();
            feedsLoaded = true;
            if (getListView() != null && !listviewSetup) {
                setupListView();
            }
        }
    }

    private void setupListView() {
        setListShown(true);
        if (episodesListAdapter == null) {
            episodesListAdapter = new EpisodesListAdapter(getActivity(), itemAccess);
            getListView().setAdapter(episodesListAdapter);
        }
        listviewSetup = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        EventDistributor.getInstance().unregister(contentUpdateListener);
        try {
            getActivity().unregisterReceiver(playerStatusReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        EventDistributor.getInstance().register(contentUpdateListener);
        getActivity().registerReceiver(playerStatusReceiver, new IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (feedsLoaded) {
            downloadObserver.setActivity(activity);
            downloadObserver.onResume();
        }
        if (listviewSetup) {
            episodesListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (feedsLoaded) {
            downloadObserver.onPause();
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
        listviewSetup = false;
        episodesListAdapter = null;
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
                final FeedItem item = (FeedItem) episodesListAdapter.getItem(position);
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
                                .setNeutralButton(R.string.cancel_download_label, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        DownloadRequester.getInstance().cancelDownload(getActivity(), media);
                                    }
                                });
                        dialog.create().show();
                    } else {
                        // episode not downloaded
                        try {
                            DBTasks.downloadFeedItems(getActivity(), item);
                        } catch (DownloadRequestException e) {
                            e.printStackTrace();
                            DownloadRequestErrorDialogCreator.newRequestErrorDialog(getActivity(), e.getMessage());
                        }
                    }
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

        if (feedsLoaded) {
            setupListView();
        } else {
            setListShown(false);
        }
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
            if (episodesListAdapter != null) {
                episodesListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onDownloadDataAvailable(List<Downloader> downloaderList) {
            EpisodesFragment.this.downloaderList = downloaderList;
            if (episodesListAdapter != null) {
                episodesListAdapter.notifyDataSetChanged();
            }
        }
    };

    private final EventDistributor.EventListener contentUpdateListener = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & EVENTS) != 0) {
                if (listviewSetup) {
                    refreshFeed();
                    episodesListAdapter.notifyDataSetChanged();
                }
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
