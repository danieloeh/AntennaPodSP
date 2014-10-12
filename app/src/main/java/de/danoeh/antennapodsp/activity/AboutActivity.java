package de.danoeh.antennapodsp.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import de.danoeh.antennapodsp.R;

/**
 * Displays the 'about' screen
 */
public class AboutActivity extends ActionBarActivity {

    private WebView webview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.about);
        webview = (WebView) findViewById(R.id.webvAbout);
        webview.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http")) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                } else {
                    view.loadUrl(url);
                }
                return false;
            }

        });
        webview.loadUrl("file:///android_asset/about.html");
    }

}
