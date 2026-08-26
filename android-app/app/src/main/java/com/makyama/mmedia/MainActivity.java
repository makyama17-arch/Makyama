package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int AUDIO_PERMISSION = 100;

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

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimeType,
                    long contentLength) {

                downloadFile(
                        url,
                        contentDisposition,
                        mimeType
                );
            }
        });

        requestAudioPermission();

        webView.loadUrl("https://makyama.vercel.app/");
    }

    private void requestAudioPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(
                    Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_AUDIO
                        },
                        AUDIO_PERMISSION
                );
            }

        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    checkSelfPermission(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        AUDIO_PERMISSION
                );
            }
        }
    }

    private void downloadFile(
            String url,
            String contentDisposition,
            String mimeType) {

        try {

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            request.setTitle("MMEDIA Download");

            request.setDescription(
                    "Downloading music..."
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setMimeType(
                    mimeType != null
                            ? mimeType
                            : "audio/mpeg"
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MUSIC,
                    getFileName(
                            url,
                            contentDisposition
                    )
            );

            DownloadManager manager =
                    (DownloadManager)
                            getSystemService(
                                    DOWNLOAD_SERVICE
                            );

            if (manager != null) {

                manager.enqueue(request);

                Toast.makeText(
                        this,
                        "⬇️ Download imeanza",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Download imeshindikana",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private String getFileName(
            String url,
            String contentDisposition) {

        String fileName = "MMEDIA_Music.mp3";

        if (contentDisposition != null &&
                contentDisposition.contains("filename=")) {

            fileName =
                    contentDisposition
                            .substring(
                                    contentDisposition
                                            .indexOf("filename=")
                                            + 9
                            )
                            .replace("\"", "")
                            .trim();
        } else {

            try {

                Uri uri = Uri.parse(url);

                String last =
                        uri.getLastPathSegment();

                if (last != null &&
                        last.contains(".")) {

                    fileName = last;
                }

            } catch (Exception ignored) {
            }
        }

        return fileName;
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
