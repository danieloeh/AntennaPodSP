package de.danoeh.antennapodsp.config;


import de.danoeh.antennapod.core.DBTasksCallbacks;
import de.danoeh.antennapod.core.storage.APSPCleanupAlgorithm;
import de.danoeh.antennapod.core.storage.APSPDownloadAlgorithm;
import de.danoeh.antennapod.core.storage.AutomaticDownloadAlgorithm;
import de.danoeh.antennapod.core.storage.EpisodeCleanupAlgorithm;
import de.danoeh.antennapodsp.AppPreferences;

public class DBTasksCallbacksImpl implements DBTasksCallbacks {

    @Override
    public AutomaticDownloadAlgorithm getAutomaticDownloadAlgorithm() {
        return new APSPDownloadAlgorithm(new AppPreferences().numberOfNewAutomaticallyDownloadedEpisodes);
    }

    @Override
    public EpisodeCleanupAlgorithm getEpisodeCacheCleanupAlgorithm() {
        return new APSPCleanupAlgorithm(new AppPreferences().numberOfNewAutomaticallyDownloadedEpisodes);
    }
}
