package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private static final int AUDIO_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(
                new AndroidBridge(this),
                "MMEDIA"
        );

        webView.loadUrl("https://makyama.vercel.app/");

        requestAudioPermission();
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

            if (checkSelfPermission(
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

    public static class AndroidBridge {

        private final MainActivity activity;

        AndroidBridge(MainActivity activity) {
            this.activity = activity;
        }

        /*
         * =====================================================
         * OPEN MY DEVICE
         * =====================================================
         */

        @JavascriptInterface
        public void openMyDevice() {

            activity.runOnUiThread(() -> {

                if (!activity.hasAudioPermission()) {

                    activity.requestAudioPermission();

                    Toast.makeText(
                            activity,
                            "Ruhusu MMEDIA kusoma audio zako.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                String musicJson =
                        activity.getDeviceMusic();

                activity.webView.evaluateJavascript(
                        "window.showAndroidMusic(" +
                                JSONObject.quote(musicJson) +
                                ")",
                        null
                );
            });
        }

        /*
         * =====================================================
         * GET ALL AUDIO FROM PHONE
         * =====================================================
         */

        private String getDeviceMusic() {

            JSONArray songs = new JSONArray();

            Uri collection;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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

            String sortOrder =
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

            Cursor cursor = null;

            try {

                cursor = activity.getContentResolver().query(
                        collection,
                        projection,
                        selection,
                        null,
                        sortOrder
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
                                cursor.getLong(idColumn);

                        String title =
                                cursor.getString(titleColumn);

                        String artist =
                                cursor.getString(artistColumn);

                        String album =
                                cursor.getString(albumColumn);

                        long duration =
                                cursor.getLong(durationColumn);

                        String mime =
                                cursor.getString(mimeColumn);

                        Uri contentUri =
                                ContentUris.withAppendedId(
                                        collection,
                                        id
                                );

                        JSONObject song =
                                new JSONObject();

                        song.put(
                                "id",
                                id
                        );

                        song.put(
                                "title",
                                title == null ||
                                title.trim().isEmpty()
                                        ? "Unknown Song"
                                        : title
                        );

                        song.put(
                                "artist",
                                artist == null ||
                                artist.trim().isEmpty() ||
                                artist.equals("<unknown>")
                                        ? "Unknown Artist"
                                        : artist
                        );

                        song.put(
                                "album",
                                album == null
                                        ? ""
                                        : album
                        );

                        song.put(
                                "duration",
                                duration
                        );

                        song.put(
                                "mime",
                                mime == null
                                        ? "audio/*"
                                        : mime
                        );

                        song.put(
                                "uri",
                                contentUri.toString()
                        );

                        songs.put(song);
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();

            } finally {

                if (cursor != null) {
                    cursor.close();
                }
            }

            return songs.toString();
        }

        /*
         * =====================================================
         * DOWNLOAD BRIDGE
         * =====================================================
         */

        @JavascriptInterface
        public void download(
                String url,
                String filename
        ) {

            activity.runOnUiThread(() -> {

                try {

                    android.app.DownloadManager.Request request =
                            new android.app.DownloadManager.Request(
                                    Uri.parse(url)
                            );

                    request.setTitle(filename);

                    request.setDescription(
                            "Downloading from MMEDIA"
                    );

                    request.setNotificationVisibility(
                            android.app.DownloadManager
                                    .Request
                                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    );

                    request.setMimeType(
                            "audio/mpeg"
                    );

                    request.setDestinationInExternalPublicDir(
                            android.os.Environment
                                    .DIRECTORY_MUSIC,
                            filename
                    );

                    android.app.DownloadManager manager =
                            (android.app.DownloadManager)
                                    activity.getSystemService(
                                            Context.DOWNLOAD_SERVICE
                                    );

                    if (manager != null) {

                        manager.enqueue(request);

                        Toast.makeText(
                                activity,
                                "⬇️ Download imeanza",
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
            });
        }
    }

    private boolean hasAudioPermission() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            return checkSelfPermission(
                    Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED;

        } else {

            return checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    public void onBackPressed() {

        /*
         * Kama My Device page imefunguliwa,
         * HTML itashughulikia kurudi Home.
         */

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
                            }
