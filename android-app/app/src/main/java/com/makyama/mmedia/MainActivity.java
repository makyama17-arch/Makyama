package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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

    private static final int MUSIC_PERMISSION_REQUEST = 2001;


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
         * Android Bridge
         */
        webView.addJavascriptInterface(
                new MMediaBridge(this),
                "MMEDIA"
        );

        /*
         * Website
         */
        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    /*
     * =========================================================
     * PERMISSION
     * =========================================================
     */

    private boolean hasMusicPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            return checkSelfPermission(
                    Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED;

        } else {

            return checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }


    private void requestMusicPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_MEDIA_AUDIO
                    },
                    MUSIC_PERMISSION_REQUEST
            );

        } else {

            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    MUSIC_PERMISSION_REQUEST
            );
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == MUSIC_PERMISSION_REQUEST) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                scanDeviceMusic();

            } else {

                Toast.makeText(
                        this,
                        "Ruhusa ya kusoma Music kwenye simu inahitajika.",
                        Toast.LENGTH_LONG
                ).show();

                sendMusicToWebsite(
                        new JSONArray()
                );
            }
        }
    }


    /*
     * =========================================================
     * SCAN ALL DEVICE MUSIC
     * =========================================================
     */

    private void scanDeviceMusic() {

        JSONArray musicArray =
                new JSONArray();

        List<MusicItem> musicList =
                new ArrayList<>();

        Uri collection;

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

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

                MediaStore.Audio.Media.DISPLAY_NAME,

                MediaStore.Audio.Media.DURATION
        };


        String selection =
                MediaStore.Audio.Media.IS_MUSIC + " != 0";


        try {

            Cursor cursor =
                    getContentResolver().query(
                            collection,
                            projection,
                            selection,
                            null,
                            MediaStore.Audio.Media.TITLE +
                                    " COLLATE NOCASE ASC"
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

                int nameColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Audio.Media.DISPLAY_NAME
                        );

                int durationColumn =
                        cursor.getColumnIndexOrThrow(
                                MediaStore.Audio.Media.DURATION
                        );


                while (cursor.moveToNext()) {

                    long id =
                            cursor.getLong(idColumn);

                    String title =
                            cursor.getString(titleColumn);

                    String artist =
                            cursor.getString(artistColumn);

                    String filename =
                            cursor.getString(nameColumn);

                    long duration =
                            cursor.getLong(durationColumn);


                    if (title == null ||
                            title.trim().isEmpty()) {

                        title = filename;
                    }

                    if (artist == null ||
                            artist.trim().isEmpty() ||
                            artist.equals("<unknown>")) {

                        artist = "My Device";
                    }


                    Uri audioUri =
                            Uri.withAppendedPath(
                                    collection,
                                    String.valueOf(id)
                            );


                    musicList.add(
                            new MusicItem(
                                    title,
                                    artist,
                                    audioUri.toString(),
                                    duration
                            )
                    );
                }

                cursor.close();
            }


            /*
             * A-Z
             */
            Collections.sort(
                    musicList,
                    new Comparator<MusicItem>() {

                        @Override
                        public int compare(
                                MusicItem a,
                                MusicItem b) {

                            return a.title
                                    .toLowerCase()
                                    .compareTo(
                                            b.title.toLowerCase()
                                    );
                        }
                    }
            );


            /*
             * Convert to JSON
             */
            for (MusicItem item : musicList) {

                JSONObject obj =
                        new JSONObject();

                obj.put(
                        "title",
                        item.title
                );

                obj.put(
                        "artist",
                        item.artist
                );

                obj.put(
                        "audio",
                        item.audio
                );

                obj.put(
                        "duration",
                        item.duration
                );

                musicArray.put(obj);
            }


            sendMusicToWebsite(
                    musicArray
            );


        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Imeshindikana kusoma Music.",
                    Toast.LENGTH_LONG
            ).show();

            sendMusicToWebsite(
                    new JSONArray()
            );
        }
    }


    /*
     * =========================================================
     * SEND MUSIC TO WEBSITE
     * =========================================================
     */

    private void sendMusicToWebsite(
            JSONArray music
    ) {

        final String json =
                music.toString();

        runOnUiThread(() -> {

            String javascript =
                    "window.onDeviceMusicLoaded(" +
                    JSONObject.quote(json) +
                    ");";

            webView.evaluateJavascript(
                    javascript,
                    null
            );
        });
    }


    /*
     * =========================================================
     * MUSIC ITEM
     * =========================================================
     */

    private static class MusicItem {

        String title;
        String artist;
        String audio;
        long duration;

        MusicItem(
                String title,
                String artist,
                String audio,
                long duration
        ) {

            this.title = title;
            this.artist = artist;
            this.audio = audio;
            this.duration = duration;
        }
    }


    /*
     * =========================================================
     * JAVASCRIPT BRIDGE
     * =========================================================
     */

    public class MMediaBridge {

        private final Context context;

        MMediaBridge(Context context) {
            this.context = context;
        }


        /*
         * Website ikibonyeza:
         *
         * MMEDIA.openDeviceMusic()
         *
         * APK inaanza kuscan Music zote.
         */
        @JavascriptInterface
        public void openDeviceMusic() {

            runOnUiThread(() -> {

                if (hasMusicPermission()) {

                    scanDeviceMusic();

                } else {

                    requestMusicPermission();
                }
            });
        }


        /*
         * =====================================================
         * DOWNLOAD
         * =====================================================
         */

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
