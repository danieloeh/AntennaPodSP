package de.danoeh.antennapodsp.config;


import de.danoeh.antennapod.core.ClientConfig;
import de.danoeh.antennapodsp.AppPreferences;

public class ClientConfigurator {

    static {
        ClientConfig.USER_AGENT = AppPreferences.USER_AGENT;
        ClientConfig.downloadServiceCallbacks = new DownloadServiceCallbacksImpl();
        ClientConfig.storageCallbacks = new StorageCallbacksImpl();
        ClientConfig.flattrCallbacks = new FlattrCallbacksImpl();
        ClientConfig.gpodnetCallbacks = new GpodnetCallbacksImpl();
        ClientConfig.playbackServiceCallbacks = new PlaybackServiceCallbacksImpl();
        ClientConfig.applicationCallbacks = new ApplicationCallbacksImpl();
        ClientConfig.dbTasksCallbacks = new DBTasksCallbacksImpl();
    }
}
