package de.danoeh.antennapodsp.fragment;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v7.appcompat.R;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import de.danoeh.antennapodsp.adapter.EpisodesListAdapter;
import de.danoeh.antennapodsp.asynctask.DownloadObserver;
import de.danoeh.antennapodsp.feed.EventDistributor;
import de.danoeh.antennapodsp.feed.Feed;
import de.danoeh.antennapodsp.feed.FeedItem;
import de.danoeh.antennapodsp.feed.FeedMedia;
import de.danoeh.antennapodsp.service.download.Downloader;
import de.danoeh.antennapodsp.storage.DBReader;
import de.danoeh.antennapodsp.storage.DBTasks;

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
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                position = position - getListView().getHeaderViewsCount();
                if (position >= 0) {
                    FeedItem item = (FeedItem) episodesListAdapter.getItem(position);
                    if (item.hasMedia()) {
                        DBTasks.playMedia(getActivity(), item.getMedia(), false, true, true);
                    }
                }
            }
        });
        // add header so that list is below actionbar
        int actionBarHeight = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height);
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
            episodesListAdapter.notifyDataSetChanged();
        }
    };


}
