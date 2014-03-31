package de.danoeh.antennapodsp.service.download;

import android.content.Context;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.service.stream.StreamService;

/**
 * Simple HTTPD, serving the file that is currently being downloaded only as a stream, delivered by
 * {@link de.danoeh.antennapodsp.service.download.HttpDownloader}
 */
public class PodcastHTTPD extends NanoHTTPD {
    private static final String TAG = "PodcastHTTPD";
    public static final int PORT = 8123;

    private Context context;
    private String mimeType;
    private long currentId;
    private int currentSize;
    private ArrayList<String> tempFiles = new ArrayList<String>();

    public PodcastHTTPD(Context context) {
        super(PORT);
        this.context = context;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (AppConfig.DEBUG) Log.d(TAG, "Stream requested, got ID " +currentId + " with size "
                + currentSize + ", mime type " + mimeType + ", in " + tempFiles.size() + " files.");
        return new Response(Response.Status.OK, mimeType, new StreamInputStream());
    }

    public long getCurrentId() {
        return currentId;
    }

    public void setCurrentId(long currentId) {
        this.currentId = currentId;
    }

    public void setCurrentSize(int currentSize) {
        this.currentSize = currentSize;
    }

    public void addTempFile(String tempFile) {
        tempFiles.add(tempFile);
    }

    public void setTempFiles(ArrayList<String> tempFiles) {
        this.tempFiles = tempFiles;
    }

    public ArrayList<String> getTempFiles() {
        return tempFiles;
    }

    private class StreamInputStream extends InputStream {

        private int position = 0;
        private int currentOpenFileNumber = -1;
        private int currentAvailable = 0;
        private FileInputStream fileInputStream;
        private String currentFileName;

        @Override
        public int available() throws IOException {
            return currentSize - position;
        }

        @Override
        public int read() throws IOException {
            if (available() <= 0) return -1;
            if (currentAvailable <= 0) {
                if (AppConfig.DEBUG) Log.d(TAG, "Need new temp file, position: " + position + ", curOpenFileNumber: " + currentOpenFileNumber);
                if (fileInputStream != null) {
                    if (AppConfig.DEBUG) Log.d(TAG, "Seems like "+ currentFileName + " finished, deleting");
                    fileInputStream.close();
                    context.deleteFile(currentFileName);
                }
                currentOpenFileNumber++;
                if (AppConfig.DEBUG) Log.d(TAG, "New curOpenFileNumber: " + currentOpenFileNumber + " (of " + tempFiles.size() + " files)");
                currentFileName = tempFiles.get(currentOpenFileNumber);
                fileInputStream = context.openFileInput(currentFileName);
                currentAvailable = fileInputStream.available();
                if (AppConfig.DEBUG) Log.d(TAG, "Opened " + currentFileName + " for " + currentAvailable + " bytes");
            }
            position++;
            currentAvailable--;
            return fileInputStream.read();
        }

        @Override
        public void close() throws IOException {
            if (AppConfig.DEBUG) Log.d(TAG, "Stream closed");
            fileInputStream.close();
        }

        @Override
        public boolean markSupported() {
            if (AppConfig.DEBUG) Log.d(TAG, "Temp file stream, cannot mark :(");
            return false;
        }
    }
}