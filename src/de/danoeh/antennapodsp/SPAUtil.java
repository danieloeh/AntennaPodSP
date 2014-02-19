package de.danoeh.antennapodsp;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import de.danoeh.antennapodsp.receiver.SPAReceiver;

import java.util.List;

/**
 * Asks the user for the installation of a universal podcatcher if possible.
 */
public class SPAUtil {
    private static final String TAG = "SPAUtil";

    public static final String SPA_PACKAGE_PREFIX = "de.danoeh.antennapodsp";

    private static final String PREF_USER_ASKED_FOR_INSTALLATION = "de.danoeh.antennapodsp.preferences.SPAUtil.USER_ASKED_FOR_INSTALLATION";

    private SPAUtil() {}

    /**
     * Asks the user for the installation of a universal podcatcher
     * if there is more than one AntennaPod single purpose app installed on the device
     * and only if the user hasn't been asked before.
     *
     * @return true if the user has been asked, false otherwise
     * */
    public static boolean askForPodcatcherInstallation(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_USER_ASKED_FOR_INSTALLATION, false)) {
            if (AppConfig.DEBUG) Log.d(TAG, "User has already been asked for an installation");
            return false;
        }

        if (!hasPodcatcherInstalled(context) && hasOtherSPAppsInstalled(context)) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
            dialog.setTitle(R.string.spa_ask_installation_dialog_title)
                    .setMessage(R.string.spa_ask_installation_dialog_msg)
                    .setPositiveButton(R.string.spa_ask_installation_dialog_accept, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(AppPreferences.PODCATCHER_MARKET_URL));
                            try {
                                context.startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                e.printStackTrace();
                                Intent webIntent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(AppPreferences.PODCATCHER_WEBSITE));
                                context.startActivity(webIntent);
                            }
                        }
                    })
                    .setNegativeButton(R.string.spa_ask_installation_dialog_deny, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            context.sendBroadcast(new Intent(SPAReceiver.ACTION_SP_APPS_USER_ASKED_FOR_INSTALLATION));
                        }
                    })
                    .setCancelable(true);
            dialog.show();
            return true;
        }

        return false;
    }

    private static boolean hasPodcatcherInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            Log.e(TAG, "Unable to get package manager reference");
            return false;
        }

        List<PackageInfo> packages = pm.getInstalledPackages(0);
        for (PackageInfo packageInfo : packages) {
            String packageName = packageInfo.packageName;
            if (packageName.equals(AppPreferences.PODCATCHER_PACKAGE_NAME)) {
                Log.i(TAG, "User has already installed " + AppPreferences.PODCATCHER_PACKAGE_NAME);
                return true;
            }
        }

        return false;
    }

    private static boolean hasOtherSPAppsInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            Log.e(TAG, "Unable to get package manager reference");
            return false;
        }

        List<PackageInfo> packages = pm.getInstalledPackages(0);
        final String thisPackage = context.getPackageName();

        for (PackageInfo otherPackageInfo : packages) {
            String otherPackage = otherPackageInfo.packageName;
            if (!thisPackage.equals(otherPackage) && otherPackage.startsWith(SPA_PACKAGE_PREFIX)) {
                Log.i(TAG, "Found another single purpose app: " + otherPackage);
                return true;
            }
        }

        return false;
    }

    public static void setPrefUserAskedForInstallation(Context c, boolean value) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(c).edit();
        editor.putBoolean(PREF_USER_ASKED_FOR_INSTALLATION, value);
        editor.commit();
    }
}
