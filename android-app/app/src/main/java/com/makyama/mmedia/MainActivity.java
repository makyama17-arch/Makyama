package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.database.Cursor;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int AUDIO_PERMISSION_CODE = 1001;

    private MediaPlayer mediaPlayer;

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

        webView.setWebViewClient(
                new WebViewClient()
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        webView.addJavascriptInterface(
                new MMEDIAInterface(this),
                "MMEDIA"
        );

        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    /*
     * =====================================================
     * JAVASCRIPT INTERFACE
     * =====================================================
     */

    public class MMEDIAInterface {

        private final Context context;

        MMEDIAInterface(Context context) {
            this.context = context;
        }


        /*
         * =================================================
         * ENABLE MY DEVICE
         * =================================================
         */

        @JavascriptInterface
        public void enableMyDevice() {

            runOnUiThread(() -> {

                webView.evaluateJavascript(
                        "window.enableMyDeviceButton && " +
                        "window.enableMyDeviceButton();",
                        null
                );

            });

        }


        /*
         * =================================================
         * OPEN MY DEVICE
         * =================================================
         */

        @JavascriptInterface
        public void openMyDevice() {

            runOnUiThread(() -> {

                if (hasAudioPermission()) {

                    loadDeviceMusic();

                } else {

                    requestAudioPermission();

                }

            });

        }


        /*
         * BACKUP NAME
         */

        @JavascriptInterface
        public void openDeviceMusic() {

            openMyDevice();

        }


        /*
         * =================================================
         * CHECK PERMISSION
         * =================================================
         */

        private boolean hasAudioPermission() {

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
            ) {

                return checkSelfPermission(
                        Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED;

            } else {

                return checkSelfPermission(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED;

            }

        }


        /*
         * =================================================
         * REQUEST PERMISSION
         * =================================================
         */

        private void requestAudioPermission() {

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_AUDIO
                        },
                        AUDIO_PERMISSION_CODE
                );

            } else {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        AUDIO_PERMISSION_CODE
                );

            }

        }


        /*
         * =================================================
         * LOAD DEVICE MUSIC
         * =================================================
         */

        private void loadDeviceMusic() {

            try {

                JSONArray audioArray =
                        new JSONArray();

                Uri collection;

                if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {

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

                        MediaStore.Audio.Media.DISPLAY_NAME,

                        MediaStore.Audio.Media.MIME_TYPE,

                        MediaStore.Audio.Media.DURATION

                };


                String selection =
                        MediaStore.Audio.Media.IS_MUSIC +
                        " != 0";


                String sortOrder =
                        MediaStore.Audio.Media.TITLE +
                        " COLLATE NOCASE ASC";


                Cursor cursor =
                        getContentResolver().query(
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

                    int displayNameColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.DISPLAY_NAME
                            );

                    int mimeColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.MIME_TYPE
                            );

                    int durationColumn =
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Audio.Media.DURATION
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


                        String filename =
                                cursor.getString(
                                        displayNameColumn
                                );


                        String mime =
                                cursor.getString(
                                        mimeColumn
                                );


                        long duration =
                                cursor.getLong(
                                        durationColumn
                                );


                        if (
                                title == null ||
                                title.trim().isEmpty()
                        ) {

                            title = filename;

                        }


                        if (
                                artist == null ||
                                artist.trim().isEmpty()
                        ) {

                            artist =
                                    "Unknown Artist";

                        }


                        Uri audioUri =
                                android.content.ContentUris
                                        .withAppendedId(
                                                collection,
                                                id
                                        );


                        JSONObject audio =
                                new JSONObject();


                        audio.put(
                                "id",
                                "local_" + id
                        );


                        audio.put(
                                "title",
                                title
                        );


                        audio.put(
                                "artist",
                                artist
                        );


                        audio.put(
                                "album",
                                album != null
                                        ? album
                                        : ""
                        );


                        audio.put(
                                "filename",
                                filename != null
                                        ? filename
                                        : ""
                        );


                        audio.put(
                                "mime",
                                mime != null
                                        ? mime
                                        : "audio/*"
                        );


                        audio.put(
                                "duration",
                                duration
                        );


                        audio.put(
                                "uri",
                                audioUri.toString()
                        );


                        audio.put(
                                "type",
                                "DEVICE"
                        );


                        audioArray.put(
                                audio
                        );

                    }

                    cursor.close();

                }


                final String json =
                        audioArray.toString();


                runOnUiThread(() -> {

                    try {

                        String js =
                                "window.showAndroidMusic(" +
                                JSONObject.quote(json) +
                                ");";


                        webView.evaluateJavascript(
                                js,
                                null
                        );


                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "Imeshindikana kuonyesha audio",
                                Toast.LENGTH_LONG
                        ).show();

                    }

                });


            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "Imeshindikana kusoma audio za simu",
                            Toast.LENGTH_LONG
                    ).show();

                });

            }

        }


        /*
         * =================================================
         * PLAY DEVICE AUDIO
         * =================================================
         */

        @JavascriptInterface
        public void playDeviceAudio(
                String uriString,
                String title,
                String artist
        ) {

            runOnUiThread(() -> {

                try {

                    if (mediaPlayer != null) {

                        try {

                            mediaPlayer.stop();

                        } catch (Exception ignored) {}

                        mediaPlayer.release();

                        mediaPlayer = null;

                    }


                    Uri uri =
                            Uri.parse(
                                    uriString
                            );


                    mediaPlayer =
                            new MediaPlayer();


                    mediaPlayer.setDataSource(
                            MainActivity.this,
                            uri
                    );


                    mediaPlayer.setOnPreparedListener(
                            mp -> {

                                mp.start();

                                Toast.makeText(
                                        MainActivity.this,
                                        "▶ " + title,
                                        Toast.LENGTH_SHORT
                                ).show();

                            }
                    );


                    mediaPlayer.setOnCompletionListener(
                            mp -> {

                                mp.release();

                                mediaPlayer =
                                        null;

                            }
                    );


                    mediaPlayer.setOnErrorListener(
                            (mp, what, extra) -> {

                                Toast.makeText(
                                        MainActivity.this,
                                        "Audio haikuweza kucheza.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                try {

                                    mp.release();

                                } catch (Exception ignored) {}

                                mediaPlayer =
                                        null;

                                return true;

                            }
                    );


                    mediaPlayer.prepareAsync();


                } catch (IOException e) {

                    e.printStackTrace();

                    Toast.makeText(
                            MainActivity.this,
                            "Imeshindikana kufungua audio.",
                            Toast.LENGTH_LONG
                    ).show();

                } catch (Exception e) {

                    e.printStackTrace();

                    Toast.makeText(
                            MainActivity.this,
                            "Tatizo la playback.",
                            Toast.LENGTH_LONG
                    ).show();

                }

            });

        }


        /*
         * =================================================
         * STOP DEVICE AUDIO
         * =================================================
         */

        @JavascriptInterface
        public void stopDeviceAudio() {

            runOnUiThread(() -> {

                if (mediaPlayer != null) {

                    try {

                        if (mediaPlayer.isPlaying()) {

                            mediaPlayer.stop();

                        }

                    } catch (Exception ignored) {}

                    mediaPlayer.release();

                    mediaPlayer =
                            null;

                }

            });

        }


        /*
         * =================================================
         * DOWNLOAD
         * =================================================
         */

        @JavascriptInterface
        public void download(
                String url,
                String filename
        ) {

            runOnUiThread(() -> {

                Toast.makeText(
                        MainActivity.this,
                        "Download inaanza...",
                        Toast.LENGTH_SHORT
                ).show();

            });

        }

    }


    /*
     * =====================================================
     * PERMISSION RESULT
     * =====================================================
     */

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
                AUDIO_PERMISSION_CODE
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                new MMEDIAInterface(
                        MainActivity.this
                ).loadDeviceMusic();

            } else {

                Toast.makeText(
                        this,
                        "Ruhusa ya kusoma audio haijatolewa.",
                        Toast.LENGTH_LONG
                ).show();

            }

        }

    }


    /*
     * =====================================================
     * BACK BUTTON
     * =====================================================
     */

    @Override
    public void onBackPressed() {

        if (webView != null) {

            webView.evaluateJavascript(

                    "typeof window.exitDevicePage === " +
                    "'function' ? " +
                    "window.exitDevicePage() : false;",

                    value -> {

                        if (
                                "false".equals(value) ||
                                "null".equals(value)
                        ) {

                            if (
                                    webView.canGoBack()
                            ) {

                                webView.goBack();

                            } else {

                                MainActivity.super
                                        .onBackPressed();

                            }

                        }

                    }
            );

        } else {

            super.onBackPressed();

        }

    }


    /*
     * =====================================================
     * CLEANUP
     * =====================================================
     */

    @Override
    protected void onDestroy() {

        if (mediaPlayer != null) {

            try {

                if (
                        mediaPlayer.isPlaying()
                ) {

                    mediaPlayer.stop();

                }

            } catch (Exception ignored) {}

            mediaPlayer.release();

            mediaPlayer =
                    null;

        }


        if (webView != null) {

            webView.destroy();

        }

        super.onDestroy();

    }

                                }
