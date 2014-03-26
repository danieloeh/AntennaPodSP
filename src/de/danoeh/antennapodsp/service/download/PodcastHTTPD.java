package de.danoeh.antennapodsp.service.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Simple HTTPD, serving the file that is currently being downloaded only as a stream, delivered by
 * {@link de.danoeh.antennapodsp.service.download.HttpDownloader}
 */
public class PodcastHTTPD extends NanoHTTPD {
    public static final int PORT = 8123;

    private InputStream stream;
    private String mimeType;

    public PodcastHTTPD() {
        super(PORT);
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public Response serve(IHTTPSession session) {
        return new Response(Response.Status.OK, mimeType, stream);
    }

}
