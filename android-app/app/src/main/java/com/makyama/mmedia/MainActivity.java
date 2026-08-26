package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int AUDIO_PERMISSION_REQUEST = 1001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);


        // =========================
        // WEBVIEW SETTINGS
        // =========================

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);


        // =========================
        // WEBVIEW CLIENT
        // =========================

        webView.setWebViewClient(
                new WebViewClient()
        );


        // =========================
        // JAVASCRIPT BRIDGE
        // =========================

        webView.addJavascriptInterface(
                new MMediaBridge(this),
                "MMEDIA"
        );


        // =========================
        // OPEN WEBSITE
        // =========================

        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    // =========================================================
    // MAIN BRIDGE
    // =========================================================

    public static class MMediaBridge {

        private final Activity activity;


        MMediaBridge(Activity activity) {

            this.activity = activity;

        }


        // =====================================================
        // OPEN DEVICE MUSIC
        // =====================================================

        @JavascriptInterface
        public void openDeviceMusic() {

            activity.runOnUiThread(() -> {

                if (Build.VERSION.SDK_INT >= 33) {

                    if (
                            activity.checkSelfPermission(
                                    Manifest.permission.READ_MEDIA_AUDIO
                            )
                            != PackageManager.PERMISSION_GRANTED
                    ) {

                        activity.requestPermissions(
                                new String[]{
                                        Manifest.permission.READ_MEDIA_AUDIO
                                },
                                AUDIO_PERMISSION_REQUEST
                        );

                    } else {

                        sendDeviceMusic();

                    }

                } else {

                    if (
                            activity.checkSelfPermission(
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                            != PackageManager.PERMISSION_GRANTED
                    ) {

                        activity.requestPermissions(
                                new String[]{
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                },
                                AUDIO_PERMISSION_REQUEST
                        );

                    } else {

                        sendDeviceMusic();

                    }

                }

            });

        }


        // =====================================================
        // GET ALL AUDIO FROM PHONE
        // =====================================================

        private void sendDeviceMusic() {

            try {

                JSONArray songs =
                        new JSONArray();


                Uri collection;

                if (Build.VERSION.SDK_INT >= 29) {

                    collection =
                            MediaStore.Audio.Media.getContentUri(
                                    MediaStore.VOLUME_EXTERNAL
                            );

                } else {

                    collection =
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

                }


                String[] projection = {

                        MediaStore.Audio.Media._ID,

                        MediaStore.Audio.Media.TITLE,

                        MediaStore.Audio.Media.ARTIST,

                        MediaStore.Audio.Media.ALBUM,

                        MediaStore.Audio.Media.DURATION,

                        MediaStore.Audio.Media.MIME_TYPE

                };


                String selection =
                        MediaStore.Audio.Media.IS_MUSIC + " != 0";


                Cursor cursor =
                        activity.getContentResolver().query(

                                collection,

                                projection,

                                selection,

                                null,

                                MediaStore.Audio.Media.TITLE
                                        + " COLLATE NOCASE ASC"

                        );


                if (cursor != null) {

                    int idColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media._ID
                            );

                    int titleColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.TITLE
                            );

                    int artistColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.ARTIST
                            );

                    int albumColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.ALBUM
                            );

                    int durationColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.DURATION
                            );

                    int mimeColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.MIME_TYPE
                            );


                    while (cursor.moveToNext()) {

                        long id =
                                cursor.getLong(
                                        idColumn
                                );


                        String title =
                                cursor.getString(
                                        titleColumn
                                );


                        String artist =
                                cursor.getString(
                                        artistColumn
                                );


                        String album =
                                cursor.getString(
                                        albumColumn
                                );


                        long duration =
                                cursor.getLong(
                                        durationColumn
                                );


                        String mime =
                                cursor.getString(
                                        mimeColumn
                                );


                        if (
                                title == null ||
                                title.trim().isEmpty()
                        ) {

                            title = "Unknown Audio";

                        }


                        if (
                                artist == null ||
                                artist.equals(
                                        "<unknown>"
                                ) ||
                                artist.trim().isEmpty()
                        ) {

                            artist = "Unknown Artist";

                        }


                        if (album == null) {

                            album = "";

                        }


                        if (mime == null) {

                            mime = "audio/*";

                        }


                        Uri contentUri =
                                Uri.withAppendedPath(
                                        collection,
                                        String.valueOf(id)
                                );


                        JSONObject song =
                                new JSONObject();


                        song.put(
                                "id",
                                id
                        );


                        song.put(
                                "title",
                                title
                        );


                        song.put(
                                "artist",
                                artist
                        );


                        song.put(
                                "album",
                                album
                        );


                        song.put(
                                "duration",
                                duration
                        );


                        song.put(
                                "mime",
                                mime
                        );


                        song.put(
                                "audio",
                                contentUri.toString()
                        );


                        song.put(
                                "download",
                                contentUri.toString()
                        );


                        song.put(
                                "type",
                                "DEVICE"
                        );


                        songs.put(
                                song
                        );

                    }


                    cursor.close();

                }


                String json =
                        songs.toString();


                // =================================================
                // SEND MUSIC TO WEBSITE JAVASCRIPT
                // =================================================

                String safeJson =
                        JSONObject.quote(
                                json
                        );


                String javascript =
                        "javascript:" +
                        "window.receiveDeviceMusic(" +
                        safeJson +
                        ");";


                activity.runOnUiThread(() -> {

                    activity.webView.evaluateJavascript(
                            javascript,
                            null
                    );

                });


            } catch (Exception e) {

                activity.runOnUiThread(() -> {

                    Toast.makeText(
                            activity,
                            "Audio hazijasomeka: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });

            }

        }


        // =====================================================
        // DOWNLOAD BRIDGE
        // =====================================================

        @JavascriptInterface
        public void download(
                String url,
                String filename
        ) {

            try {

                String safeName =
                        filename
                                .replaceAll(
                                        "[\\\\/:*?\"<>|]",
                                        "_"
                                )
                                .trim();


                if (
                        safeName.length() == 0
                ) {

                    safeName =
                            "MMEDIA_Music";

                }


                if (
                        !safeName
                                .toLowerCase()
                                .endsWith(".mp3")
                ) {

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
                                activity.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                );


                if (manager != null) {

                    manager.enqueue(
                            request
                    );


                    Toast.makeText(
                            activity,
                            "⬇️ " + safeName,
                            Toast.LENGTH_SHORT
                    ).show();

                }


            } catch (Exception e) {

                Toast.makeText(
                        activity,
                        "Download imeshindikana",
                        Toast.LENGTH_LONG
                ).show();

            }

        }

    }


    // =========================================================
    // PERMISSION RESULT
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );


        if (
                requestCode ==
                        AUDIO_PERMISSION_REQUEST
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                Toast.makeText(
                        this,
                        "📱 Audio zimekubaliwa",
                        Toast.LENGTH_SHORT
                ).show();


                if (webView != null) {

                    webView.evaluateJavascript(
                            "MMEDIA.openDeviceMusic();",
                            null
                    );

                }

            } else {

                Toast.makeText(
                        this,
                        "Ruhusa ya kusoma audio imekataliwa.",
                        Toast.LENGTH_LONG
                ).show();

            }

        }

    }


    // =========================================================
    // BACK BUTTON
    // =========================================================

    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();

        }

    }


    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        if (webView != null) {

            webView.loadUrl(
                    "about:blank"
            );

            webView.stopLoading();

            webView.clearHistory();

            webView.removeAllViews();

            webView.destroy();

        }

        super.onDestroy();

    }

                            }
