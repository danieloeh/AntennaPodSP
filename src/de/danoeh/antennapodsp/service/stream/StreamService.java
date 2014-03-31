package de.danoeh.antennapodsp.service.stream;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.List;

import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.service.download.DownloadRequest;
import de.danoeh.antennapodsp.service.download.PodcastHTTPD;
import de.danoeh.antennapodsp.service.playback.PlaybackService;

/**
 * Service that handles internal streams of currently downloading files to the media player
 */
public class StreamService extends IntentService {
    public static final String EXTRA_REQUEST = "request";
    public static final String EXTRA_FILENAME = "fileName";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TYPE_NEW_TEMP_FILE = "new temp file";
    private PodcastHTTPD httpd;
    private Context context;
    private PlaybackService playbackService;

    private static final String TAG = "StreamService";
    public static final int TEMP_FILE_SIZE = 1024 * 1024;
    public static final String NOTIFICATION = "de.danoeh.antennapodsp.service.stream";

    public StreamService() {
        super("StreamService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (EXTRA_TYPE_NEW_TEMP_FILE.equals(intent.getStringExtra(EXTRA_TYPE))) {
            addTempFile(intent);
        }
    }

    private void addTempFile(Intent intent) {
        DownloadRequest request = intent.getParcelableExtra(StreamService.EXTRA_REQUEST);
        String fileName = intent.getStringExtra(EXTRA_FILENAME);
        if (request != null && request.getFeedfileId() != httpd.getCurrentId()) {
            // this is the first temp file for this episode
            if (AppConfig.DEBUG) Log.d(TAG, "New episode ready for streaming: " + request.getTitle());
            httpd.setCurrentId(request.getFeedfileId());
            httpd.setMimeType(request.getMimeType());
            httpd.setCurrentSize(request.getStreamSize());
            List<String> files = httpd.getTempFiles();
            for (String file : files) {
                try {
                    context.deleteFile(file);
                } catch (Exception e) {
                    Log.d(TAG, "Could not delete temp file " + file);
                }
            }
        }
        httpd.addTempFile(fileName);
    }


    @Override
    public void onCreate() {
        if (AppConfig.DEBUG) Log.d(TAG, "Starting Stream Service");
        super.onCreate();
        context = getApplicationContext();
        if (context == null) {
            Log.e(TAG, "I have no context, waaa!");
        }
        httpd = new PodcastHTTPD(context);
        try {
            httpd.start();
        } catch (IOException e) {
            Log.e(TAG, "Error starting HTTPD", e);
        }
    }

    @Override
    public void onDestroy() {
        if (AppConfig.DEBUG) Log.d(TAG, "Shutting Down Stream Service");
        super.onDestroy();
        if (httpd.isAlive()) {
            httpd.stop();
            if (AppConfig.DEBUG) Log.d(TAG, "httpd stopped");
        }
    }

    private final IBinder mBinder = new LocalBinder();

    public PlaybackService getPlaybackService() {
        return playbackService;
    }

    public void setPlaybackService(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    public class LocalBinder extends Binder {
        public StreamService getService() {
            return StreamService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
