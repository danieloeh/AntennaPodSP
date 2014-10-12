package de.danoeh.antennapodsp.service.download;

/**
 * Callback used by the Downloader-classes to notify the requester that the
 * download has completed.
 */
public interface DownloaderCallback {

    public void onDownloadCompleted(Downloader downloader);
}
