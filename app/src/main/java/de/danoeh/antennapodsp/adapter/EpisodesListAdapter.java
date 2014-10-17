package de.danoeh.antennapodsp.adapter;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import de.danoeh.antennapod.core.asynctask.PicassoProvider;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapodsp.R;

public class EpisodesListAdapter extends BaseAdapter {


    private Context context;
    private ItemAccess itemAccess;

    public EpisodesListAdapter(Context context,
                               ItemAccess itemAccess) {
        super();
        if (context == null) throw new IllegalArgumentException("context = null");
        if (itemAccess == null) throw new IllegalArgumentException("itemAccess = null");

        this.context = context;
        this.itemAccess = itemAccess;
    }

    @Override
    public int getCount() {
        return itemAccess.getCount();
    }

    @Override
    public Object getItem(int position) {
        return itemAccess.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        final FeedItem item = (FeedItem) getItem(position);
        if (item == null) return null;

        if (convertView == null) {
            holder = new Holder();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.episodes_list_item,
                    null);
            holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
            holder.pubDate = (TextView) convertView
                    .findViewById(R.id.txtvPublished);
            holder.downloadStatus = (ImageView) convertView
                    .findViewById(R.id.imgvDownloadStatus);
            holder.statusPlaying = (ImageView) convertView
                    .findViewById(R.id.statusPlaying);
            holder.downloadProgress = (ProgressBar) convertView
                    .findViewById(R.id.pbar_download_progress);
            holder.imageView = (ImageView) convertView.findViewById(R.id.imgvImage);
            holder.txtvDuration = (TextView) convertView.findViewById(R.id.txtvDuration);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        holder.title.setText(item.getTitle());
        holder.pubDate.setText(DateUtils.formatDateTime(context, item.getPubDate().getTime(), DateUtils.FORMAT_SHOW_DATE));
        FeedItem.State state = item.getState();

        if (state == FeedItem.State.PLAYING) {
            holder.statusPlaying.setVisibility(View.VISIBLE);
        } else {
            holder.statusPlaying.setVisibility(View.INVISIBLE);
        }

        FeedMedia media = item.getMedia();
        if (media != null) {
            final boolean isDownloadingMedia = DownloadRequester.getInstance().isDownloadingFile(media);

            if (media.getDuration() > 0) {
                holder.txtvDuration.setText(Converter.getDurationStringLong(media.getDuration()));
            } else {
                holder.txtvDuration.setText("");
            }

            if (isDownloadingMedia) {
                holder.downloadProgress.setVisibility(View.VISIBLE);
                holder.txtvDuration.setVisibility(View.GONE);
            } else {
                holder.txtvDuration.setVisibility(View.VISIBLE);
                holder.downloadProgress.setVisibility(View.GONE);
            }

            TypedArray drawables = context.obtainStyledAttributes(new int[]{
                    R.attr.navigation_accept, R.attr.navigation_refresh, R.attr.av_download});
            final int[] labels = new int[]{R.string.status_downloaded_label, R.string.status_downloading_label, R.string.status_not_downloaded_label};
            if (!media.isDownloaded()) {
                if (isDownloadingMedia) {
                    // item is being downloaded
                    holder.downloadStatus.setVisibility(View.VISIBLE);
                    holder.downloadStatus.setImageDrawable(drawables
                            .getDrawable(1));
                    holder.downloadStatus.setContentDescription(context.getString(labels[1]));

                    holder.downloadProgress.setProgress(itemAccess.getItemDownloadProgressPercent(item));
                } else {
                    // item is not downloaded and not being downloaded
                    holder.downloadStatus.setVisibility(View.VISIBLE);
                    holder.downloadStatus.setImageDrawable(drawables.getDrawable(2));
                    holder.downloadStatus.setContentDescription(context.getString(labels[2]));
                }
            } else {
                // item is not being downloaded
                holder.downloadStatus.setVisibility(View.VISIBLE);
                holder.downloadStatus
                        .setImageDrawable(drawables.getDrawable(0));
                holder.downloadStatus.setContentDescription(context.getString(labels[0]));
            }
        } else {
            holder.downloadStatus.setVisibility(View.INVISIBLE);
        }

        PicassoProvider.getMediaMetadataPicassoInstance(context)
                .load(item.getImageUri())
                .fit()
                .into(holder.imageView);
        return convertView;

    }

    static class Holder {
        TextView title;
        TextView pubDate;
        ImageView downloadStatus;
        ImageView imageView;
        ImageView statusPlaying;
        ProgressBar downloadProgress;
        TextView txtvDuration;
    }

    public static interface ItemAccess {
        int getCount();

        FeedItem getItem(int position);

        int getItemDownloadProgressPercent(FeedItem item);
    }


}
