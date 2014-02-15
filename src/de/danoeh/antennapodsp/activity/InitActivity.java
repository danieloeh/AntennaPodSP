package de.danoeh.antennapodsp.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.AppInitializer;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.feed.Feed;
import de.danoeh.antennapodsp.storage.DBReader;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Activity that is shown when the application is launched.
 */
public class InitActivity extends Activity {
    private static final String TAG = "InitActivity";

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.init);
        progressBar = (ProgressBar) findViewById(R.id.progBar);
        startInitTask();
    }

    private void startInitTask() {
        if (AppConfig.DEBUG) Log.d(TAG, "Starting init task");

        InitTask task = new InitTask();
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD_MR1) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }


    private void openErrorDialog(Exception e) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.init_error_label)
                .setMessage(getString(R.string.init_error_msg_prefix) + e.getMessage() + ".\n" + getString(R.string.init_error_detail))
                .setPositiveButton(R.string.try_again_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        startInitTask();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        InitActivity.this.finish();
                    }
                })
                .setCancelable(false);
        dialog.create().show();
    }

    private class InitTask extends AsyncTask<Void, Void, Long> {
        private Exception exception = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            progressBar.setVisibility(View.INVISIBLE);
            if (exception != null) {
                openErrorDialog(exception);
            } else {
                finish();
                Intent intent = new Intent(InitActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.ARG_FEED_ID, result);
                startActivity(intent);
            }
        }

        @Override
        protected Long doInBackground(Void... params) {
            try {
                AppInitializer.initializeApp(InitActivity.this.getApplicationContext());
                List<Feed> feeds = DBReader.getFeedList(InitActivity.this);
                return feeds.get(0).getId();
            } catch (ExecutionException e) {
                e.printStackTrace();
                exception = e;
            } catch (InterruptedException e) {
                e.printStackTrace();
                exception = e;
            } catch (AppInitializer.InitializerException e) {
                e.printStackTrace();
                exception = e;
            }
            return null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
