package com.makyama.mmedia;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;

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

        /*
         * HII NDIYO SEHEMU MUHIMU:
         * Inawezesha <input type="file">
         * kufungua Android Storage.
         */
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                /*
                 * Kama chooser nyingine ilikuwa imefunguliwa,
                 * ifunge kwanza.
                 */
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback
                            .onReceiveValue(null);
                }

                MainActivity.this.filePathCallback =
                        filePathCallback;

                try {

                    Intent intent =
                            fileChooserParams.createIntent();

                    /*
                     * Tunaruhusu kuchagua audio nyingi,
                     * kwa sababu website ina multiple.
                     */
                    intent.putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            true
                    );

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (Exception e) {

                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Imeshindikana kufungua Storage",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }

                return true;
            }
        });

        /*
         * Download bridge ya website.
         */
        webView.addJavascriptInterface(
                new DownloadBridge(this),
                "MMEDIA"
        );

        /*
         * Fungua MAKYAMA MEDIA.
         */
        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    /*
     * =========================================================
     * FILE PICKER RESULT
     * =========================================================
     */

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        if (resultCode == RESULT_OK && data != null) {

            /*
             * Multiple files.
             */
            if (data.getClipData() != null) {

                int count =
                        data.getClipData().getItemCount();

                results = new Uri[count];

                for (int i = 0; i < count; i++) {

                    results[i] =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();
                }

            }

            /*
             * Single file.
             */
            else if (data.getData() != null) {

                results = new Uri[]{
                        data.getData()
                };
            }
        }

        /*
         * Rudisha files kwenye WebView.
         */
        filePathCallback.onReceiveValue(results);

        filePathCallback = null;
    }


    /*
     * =========================================================
     * DOWNLOAD BRIDGE
     * =========================================================
     */

    public static class DownloadBridge {

        private final Context context;

        DownloadBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void download(
                String url,
                String filename) {

            try {

                String safeName =
                        filename
                                .replaceAll(
                                        "[\\\\/:*?\"<>|]",
                                        "_"
                                )
                                .trim();

                if (safeName.length() == 0) {

                    safeName =
                            "MMEDIA_Music";
                }

                if (!safeName
                        .toLowerCase()
                        .endsWith(".mp3")) {

                    safeName += ".mp3";
                }

                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );

                request.setTitle(
                        safeName
                );

                request.setDescription(
                        "Downloading from MMEDIA"
                );

                request.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                request.setMimeType(
                        "audio/mpeg"
                );

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


    /*
     * =========================================================
     * BACK BUTTON
     * =========================================================
     */

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
