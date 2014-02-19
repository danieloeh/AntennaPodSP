package de.danoeh.antennapodsp;

import de.danoeh.antennapodsp.preferences.UserPreferences;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Subscriptions and preferences of AntennaPodSP. In order to update one of these values for an installed app, the version number has to be increased.
 */
public class AppPreferences {

    int versionNumber = 0;
    /**
     * List of feeds.
     */
    public String[] feedUrls = {"http://feeds.feedburner.com/EinschlafenPodcastEnhanced?format=xml"};

    /**
     * User-Agent header for HTTP-requests.
     */
    public final static String USER_AGENT = "EinschlafenPodcastAndroid/1.0";

    /**
     * As soon as the user installs a second AntennaPod single purpose app, this app will ask the user
     * if a universal podcatcher app should be installed. If the user accepts, the device will install the
     * app that this URL points to.
     */
    public final static String PODCATCHER_MARKET_URL = "market://search?q=pname:de.danoeh.antennapod";
    /**
     * Package name of the universal podcatcher
     */
    public final static String PODCATCHER_PACKAGE_NAME = "de.danoeh.antennapod";

    public final static String PODCATCHER_WEBSITE = "http://antennapod.com";

    // Preferences

    public boolean pauseOnHeadsetDisconnect = false;
    public boolean followQueue = false;
    public boolean downloadMediaOnWifiOnly = true;
    public boolean allowMobileUpdates = true;
    public boolean enableAutodownload = true;
    public long episodeCacheSize = 300 * 1024 * 1024;
    public boolean pauseForFocusLoss = true;
    public int numberOfNewAutomaticallyDownloadedEpisodes = 3;


    /**
     * This method determines when automatic feed updates will happen.
     * Use UserPreferences.restartUpdateAlarm to specify when the first update will happen
     * and the time period for for all the following updates.
     */
    public void setUpdateAlarm() {
        // Set first update to 12 o'clock, update interval to 24 hours
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(GregorianCalendar.HOUR_OF_DAY, 12);
        Calendar now = GregorianCalendar.getInstance();
        if (now.after(cal)) {
            cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
        }
        long firstAlarm = cal.getTimeInMillis() - now.getTimeInMillis();
        long nextAlarms = 24 * 3600 * 1000;
        UserPreferences.restartUpdateAlarm(firstAlarm, nextAlarms);
    }

}
