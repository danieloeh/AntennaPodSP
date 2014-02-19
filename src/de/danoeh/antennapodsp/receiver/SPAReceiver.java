package de.danoeh.antennapodsp.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.AppPreferences;
import de.danoeh.antennapodsp.SPAUtil;

/**
 * Receives intents from other AntennaPod single purpose apps
 */
public class SPAReceiver extends BroadcastReceiver{
    private static final String TAG = "SPAReceiver";

    /**
     * Single purpose apps which receive this intent MUST NOT aks the user for another installation of the universal podcatcher.
     * This intent is sent after the user has been asked for the installation of a universal podcatcher
     * */
    public static final String ACTION_SP_APPS_USER_ASKED_FOR_INSTALLATION = "de.danoeh.antennapodsp.intent.SP_APPS_USER_ASKED_FOR_INSTALLATION";

    /**
     * Receiving single purpose apps MUST send an intent ACTION_SP_APPS_QUERY_FEEDS_RESPONSE which contains an
     * extra String array with all subscribed feeds of this app.
     */
    public static final String ACTION_SP_APPS_QUERY_FEEDS = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS";
    public static final String ACTION_SP_APPS_QUERY_FEEDS_REPSONSE = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS_RESPONSE";
    public static final String ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA = "feeds";


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(ACTION_SP_APPS_USER_ASKED_FOR_INSTALLATION)) {
            if (AppConfig.DEBUG) Log.d(TAG, "Received USER_DENIED_INSTALLATION intent");
            SPAUtil.setPrefUserAskedForInstallation(context.getApplicationContext(), true);
        } else if (action.equals(ACTION_SP_APPS_QUERY_FEEDS)) {
            if (AppConfig.DEBUG) Log.d(TAG, "Received QUERY_FEEDS intent");
            Intent re = new Intent(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE);
            re.putExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA, new AppPreferences().feedUrls);
            context.sendBroadcast(re);
        } else if (action.equals(Intent.ACTION_PACKAGE_ADDED)) {
            String pkgName = intent.getDataString();
            if (pkgName != null && pkgName.contains(SPAUtil.SPA_PACKAGE_PREFIX)) {
                if (AppConfig.DEBUG) Log.d(TAG, "Another single purpose app was installed on the device.");
                SPAUtil.setPrefUserAskedForInstallation(context.getApplicationContext(), false);
            }
        }
    }
}
