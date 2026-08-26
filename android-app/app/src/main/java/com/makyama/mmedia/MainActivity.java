package com.makyama.mmedia;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(
                new DownloadBridge(this),
                "MMEDIA"
        );

        webView.loadUrl("https://makyama.vercel.app/");
    }

    public static class DownloadBridge {

        private final Context context;

        DownloadBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void download(String url, String filename) {

            try {

                String safeName = filename
                        .replaceAll("[\\\\/:*?\"<>|]", "_")
                        .trim();

                if (safeName.length() == 0) {
                    safeName = "MMEDIA_Music";
                }

                if (!safeName.toLowerCase().endsWith(".mp3")) {
                    safeName += ".mp3";
                }

                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );

                request.setTitle(safeName);

                request.setDescription(
                        "Downloading from MMEDIA"
                );

                request.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                request.setMimeType("audio/mpeg");

                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MUSIC,
                        safeName
                );

                DownloadManager manager =
                        (DownloadManager)
                                context.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                );

                if (manager != null) {

                    manager.enqueue(request);

                    Toast.makeText(
                            context,
                            "⬇️ " + safeName,
                            Toast.LENGTH_SHORT
                    ).show();
                }

            } catch (Exception e) {

                Toast.makeText(
                        context,
                        "Download imeshindikana",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
            }
