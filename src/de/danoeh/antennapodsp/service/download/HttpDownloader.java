package de.danoeh.antennapodsp.service.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.http.AndroidHttpClient;
import android.os.IBinder;
import android.util.Log;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.PodcastApp;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.feed.FeedMedia;
import de.danoeh.antennapodsp.service.stream.StreamService;
import de.danoeh.antennapodsp.util.DownloadError;
import de.danoeh.antennapodsp.util.StorageUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class HttpDownloader extends Downloader {
    private static final String TAG = "HttpDownloader";

    private static final int BUFFER_SIZE = 8 * 1024;
    private Context context;

    public HttpDownloader(DownloadRequest request, Context context) {
        super(request);
        this.context = context;
    }

    public HttpDownloader(DownloadRequest request) {
        super(request);
    }

    private URI getURIFromRequestUrl(String source) {
        try {
            URL url = new URL(source);
            return new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected void download() {
        HttpClient httpClient = AntennapodHttpClient.getHttpClient();
        BufferedOutputStream out = null;
        InputStream connection;
        try {
            HttpGet httpGet = new HttpGet(getURIFromRequestUrl(request.getSource()));
            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity httpEntity = response.getEntity();
            int responseCode = response.getStatusLine().getStatusCode();
            Header contentEncodingHeader = response.getFirstHeader("Content-Encoding");

            final boolean isGzip = contentEncodingHeader != null &&
                    contentEncodingHeader.getValue().equalsIgnoreCase("gzip");

            if (AppConfig.DEBUG)
                Log.d(TAG, "Response code is " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK || httpEntity == null) {
                onFail(DownloadError.ERROR_HTTP_DATA_ERROR,
                        String.valueOf(responseCode));
                return;
            }

            if (!StorageUtils.storageAvailable(PodcastApp.getInstance())) {
                onFail(DownloadError.ERROR_DEVICE_NOT_FOUND, null);
                return;
            }

            File destination = new File(request.getDestination());
            if (destination.exists()) {
                Log.w(TAG, "File already exists");
                onFail(DownloadError.ERROR_FILE_EXISTS, null);
                return;
            }

            connection = new BufferedInputStream(AndroidHttpClient
                    .getUngzippedContent(httpEntity));
            out = new BufferedOutputStream(new FileOutputStream(
                    destination));

            byte[] buffer = new byte[BUFFER_SIZE];
            int count = 0;
            request.setStatusMsg(R.string.download_running);
            if (AppConfig.DEBUG)
                Log.d(TAG, "Getting size of download");
            request.setSize(httpEntity.getContentLength());
            if (AppConfig.DEBUG)
                Log.d(TAG, "Size is " + request.getSize());
            if (request.getSize() < 0) {
                request.setSize(DownloadStatus.SIZE_UNKNOWN);
            }

            long freeSpace = StorageUtils.getFreeSpaceAvailable();
            if (AppConfig.DEBUG)
                Log.d(TAG, "Free space is " + freeSpace);

            long neededSpace = request.isShouldStream() ? request.getSize() * 2 : request.getSize();
            if (request.getSize() != DownloadStatus.SIZE_UNKNOWN
                    && neededSpace > freeSpace) {
                onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null);
                return;
            }

            if (AppConfig.DEBUG)
                Log.d(TAG, "Starting download");

            FileOutputStream tempOutStream = null;
            int tempOutCount = 0; // number of temporary files
            int tempOutSize = 0; // current size of temporary file
            String currentFilename = null;
            ServiceConnection streamServiceConnection;

            if (request.isShouldStream()) {
                currentFilename = "temp" + request.getTitle() + "_" +tempOutCount;
                tempOutStream = context.openFileOutput(currentFilename, Context.MODE_PRIVATE);
                context.startService(new Intent(context, StreamService.class));
                streamServiceConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {

                    }
                };
                context.bindService(new Intent(context, StreamService.class),
                        streamServiceConnection, Context.BIND_AUTO_CREATE);
            }

            while (!cancelled
                    && (count = connection.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                if (request.isShouldStream()) {
                    if (tempOutStream != null) {
                        tempOutStream.write(buffer,0, count);
                        tempOutSize += count;
                        if (tempOutSize >= StreamService.TEMP_FILE_SIZE) {
                            tempOutStream.flush();
                            tempOutStream.close();
                            handleTempFileFinished(request, currentFilename);
                            tempOutCount++;
                            currentFilename =  "temp" + request.getTitle() + "_" +tempOutCount;
                            tempOutStream = context.openFileOutput(currentFilename, Context.MODE_PRIVATE);
                            tempOutSize = 0;
                        }
                    }
                }
                request.setSoFar(request.getSoFar() + count);
                request.setProgressPercent((int) (((double) request
                        .getSoFar() / (double) request
                        .getSize()) * 100));
            }
            if (cancelled) {
                onCancelled();
            } else {
                out.flush();
                // check if size specified in the response header is the same as the size of the
                // written file. This check cannot be made if compression was used
                if (!isGzip && request.getSize() != DownloadStatus.SIZE_UNKNOWN &&
                        request.getSoFar() != request.getSize()) {
                    onFail(DownloadError.ERROR_IO_ERROR,
                            "Download completed but size: " +
                                    request.getSoFar() +
                                    " does not equal expected size " +
                                    request.getSize());
                    return;
                }
                if (request.isShouldStream() && tempOutStream != null) {
                    tempOutStream.flush();
                    tempOutStream.close();
                    handleTempFileFinished(request, currentFilename);
                }
                onSuccess();
            }

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_MALFORMED_URL, e.getMessage());
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_CONNECTION_ERROR, e.getMessage());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_UNKNOWN_HOST, e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_IO_ERROR, e.getMessage());
        } catch (NullPointerException e) {
            // might be thrown by connection.getInputStream()
            e.printStackTrace();
            onFail(DownloadError.ERROR_CONNECTION_ERROR, request.getSource());
        } finally {
            IOUtils.closeQuietly(out);
            AntennapodHttpClient.cleanup();
        }
    }

    private void handleTempFileFinished(DownloadRequest request, String currentFilename) {
        Intent intent = new Intent(context, StreamService.class);
        intent.putExtra(StreamService.EXTRA_FILENAME, currentFilename);
        intent.putExtra(StreamService.EXTRA_REQUEST, request);
        intent.putExtra(StreamService.EXTRA_TYPE, StreamService.EXTRA_TYPE_NEW_TEMP_FILE);
        context.startService(intent);
        // if (AppConfig.DEBUG) Log.d(TAG,"Temp File " + currentFilename + " announced");
    }

    private void onSuccess() {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Download was successful");
        result.setSuccessful();
    }

    private void onFail(DownloadError reason, String reasonDetailed) {
        if (AppConfig.DEBUG) {
            Log.d(TAG, "Download failed");
        }
        result.setFailed(reason, reasonDetailed);
        cleanup();
    }

    private void onCancelled() {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Download was cancelled");
        result.setCancelled();
        cleanup();
    }

    /**
     * Deletes unfinished downloads.
     */
    private void cleanup() {
        if (request.getDestination() != null) {
            File dest = new File(request.getDestination());
            if (dest.exists()) {
                boolean rc = dest.delete();
                if (AppConfig.DEBUG)
                    Log.d(TAG, "Deleted file " + dest.getName() + "; Result: "
                            + rc);
            } else {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "cleanup() didn't delete file: does not exist.");
            }
        }
    }

}
