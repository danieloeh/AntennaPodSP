package de.danoeh.antennapodsp.util.comparator;

import de.danoeh.antennapodsp.service.download.DownloadStatus;

import java.util.Comparator;

/**
 * Compares the completion date of two Downloadstatus objects.
 */
public class DownloadStatusComparator implements Comparator<DownloadStatus> {

    @Override
    public int compare(DownloadStatus lhs, DownloadStatus rhs) {
        return rhs.getCompletionDate().compareTo(lhs.getCompletionDate());
    }

}
