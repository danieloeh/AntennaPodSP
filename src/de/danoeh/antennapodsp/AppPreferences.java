package de.danoeh.antennapodsp;

/**
 * Subscriptions and preferences of AntennaPodSP. In order to update one of these values for an installed app, the version number has to be increased.
 */
public class AppPreferences {

    int versionNumber = 0;
    /**
     * List of feeds.
     */
    String[] feedUrls = {"http://feeds.feedburner.com/EinschlafenPodcastEnhanced?format=xml"};

    // Preferences

    boolean pauseOnHeadsetDisconnect = true;
    boolean followQueue = false;
    boolean downloadMediaOnWifiOnly = true;
    long updateInterval = 0;
    boolean allowMobileUpdates = true;
    boolean enableAutodownload = false;
    String episodeCacheSize = "5";
    boolean pauseForFocusLoss = true;

}
