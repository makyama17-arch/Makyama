package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
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


    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);


        WebSettings settings =
                webView.getSettings();

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


        // =================================================
        // JAVASCRIPT INTERFACE
        // =================================================

        webView.addJavascriptInterface(
                new MMEDIAInterface(this),
                "MMEDIA"
        );


        // =================================================
        // LOAD WEBSITE
        // =================================================

        webView.loadUrl(
                "https://makyama.vercel.app/"
        );

    }


    // =====================================================
    // JAVASCRIPT INTERFACE
    // =====================================================

    public class MMEDIAInterface {

        private final Context context;


        MMEDIAInterface(Context context) {

            this.context = context;

        }


        // =================================================
        // ENABLE MY DEVICE
        // APK PEKEE
        // =================================================

        @JavascriptInterface
        public void enableMyDevice() {

            runOnUiThread(() -> {

                if (webView == null) {
                    return;
                }


                webView.evaluateJavascript(

                        "if(typeof window.enableMyDeviceButton===" +
                        "'function')" +
                        "{window.enableMyDeviceButton();}",

                        null

                );

            });

        }


        // =================================================
        // OPEN MY DEVICE
        // =================================================

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


        // =================================================
        // BACKUP NAME
        // =================================================

        @JavascriptInterface
        public void openDeviceMusic() {

            openMyDevice();

        }


        // =================================================
        // CHECK AUDIO PERMISSION
        // =================================================

        private boolean hasAudioPermission() {

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
            ) {

                return checkSelfPermission(
                        Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED;

            }


            return checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;

        }


        // =================================================
        // REQUEST AUDIO PERMISSION
        // =================================================

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


        // =================================================
        // LOAD DEVICE MUSIC
        // =================================================

        private void loadDeviceMusic() {

            try {

                JSONArray audioArray =
                        new JSONArray();


                Uri collection;


                // =================================================
                // MEDIASTORE URI
                // =================================================

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


                // =================================================
                // COLUMNS
                // =================================================

                String[] projection = {

                        MediaStore.Audio.Media._ID,

                        MediaStore.Audio.Media.TITLE,

                        MediaStore.Audio.Media.ARTIST,

                        MediaStore.Audio.Media.ALBUM,

                        MediaStore.Audio.Media.DISPLAY_NAME,

                        MediaStore.Audio.Media.MIME_TYPE,

                        MediaStore.Audio.Media.DURATION

                };


                // =================================================
                // ONLY MUSIC
                // =================================================

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

                    // =================================================
                    // COLUMN INDEXES
                    // =================================================

                    int idColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media._ID
                            );


                    int titleColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.TITLE
                            );


                    int artistColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.ARTIST
                            );


                    int albumColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.ALBUM
                            );


                    int displayNameColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.DISPLAY_NAME
                            );


                    int mimeColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.MIME_TYPE
                            );


                    int durationColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.DURATION
                            );


                    // =================================================
                    // LOOP MUSIC
                    // =================================================

                    while (cursor.moveToNext()) {

                        try {

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


                            // =================================================
                            // FALLBACK TITLE
                            // =================================================

                            if (
                                    title == null ||
                                    title.trim().isEmpty()
                            ) {

                                title =
                                        filename;

                            }


                            if (
                                    title == null ||
                                    title.trim().isEmpty()
                            ) {

                                title =
                                        "Unknown Song";

                            }


                            // =================================================
                            // FALLBACK ARTIST
                            // =================================================

                            if (
                                    artist == null ||
                                    artist.trim().isEmpty() ||
                                    artist.equals(
                                            "<unknown>"
                                    )
                            ) {

                                artist =
                                        "Unknown Artist";

                            }


                            // =================================================
                            // CONTENT URI
                            // =================================================

                            Uri audioUri =
                                    android.content.ContentUris
                                            .withAppendedId(

                                                    collection,

                                                    id

                                            );


                            // =================================================
                            // JSON OBJECT
                            // =================================================

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


                        } catch (Exception itemError) {

                            itemError.printStackTrace();

                        }

                    }


                    cursor.close();

                }


                // =================================================
                // SEND TO WEBSITE
                // =================================================

                final String json =
                        audioArray.toString();


                runOnUiThread(() -> {

                    try {

                        if (webView == null) {
                            return;
                        }


                        String js =
                                "window.showAndroidMusic(" +
                                JSONObject.quote(json) +
                                ");";


                        webView.evaluateJavascript(
                                js,
                                null
                        );


                    } catch (Exception e) {

                        e.printStackTrace();


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


        // =====================================================
        // PLAY DEVICE AUDIO
        // =====================================================

        @JavascriptInterface
        public void playDeviceAudio(

                String uriString,

                String title,

                String artist

        ) {

            runOnUiThread(() -> {

                try {

                    // =================================================
                    // CHECK URI
                    // =================================================

                    if (
                            uriString == null ||
                            uriString.trim().isEmpty()
                    ) {

                        Toast.makeText(

                                MainActivity.this,

                                "Audio haipatikani.",

                                Toast.LENGTH_SHORT

                        ).show();

                        return;

                    }


                    // =================================================
                    // STOP OLD PLAYER
                    // =================================================

                    releasePlayer();


                    Uri audioUri =
                            Uri.parse(
                                    uriString
                            );


                    // =================================================
                    // CREATE MEDIA PLAYER
                    // =================================================

                    mediaPlayer =
                            new MediaPlayer();


                    // =================================================
                    // AUDIO ATTRIBUTES
                    // =================================================

                    if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.LOLLIPOP
                    ) {

                        AudioAttributes attributes =
                                new AudioAttributes.Builder()

                                        .setUsage(
                                                AudioAttributes.USAGE_MEDIA
                                        )

                                        .setContentType(
                                                AudioAttributes.CONTENT_TYPE_MUSIC
                                        )

                                        .build();


                        mediaPlayer.setAudioAttributes(
                                attributes
                        );

                    }


                    // =================================================
                    // DATA SOURCE
                    // =================================================

                    mediaPlayer.setDataSource(

                            MainActivity.this,

                            audioUri

                    );


                    // =================================================
                    // PREPARED
                    // =================================================

                    mediaPlayer.setOnPreparedListener(
                            mp -> {

                                try {

                                    mp.setVolume(
                                            1.0f,
                                            1.0f
                                    );


                                    mp.start();


                                    Toast.makeText(

                                            MainActivity.this,

                                            "▶ " +
                                            (
                                                    title != null
                                                    ? title
                                                    : "Playing"
                                            ),

                                            Toast.LENGTH_SHORT

                                    ).show();


                                } catch (Exception e) {

                                    e.printStackTrace();


                                    Toast.makeText(

                                            MainActivity.this,

                                            "Imeshindikana kuanza audio.",

                                            Toast.LENGTH_SHORT

                                    ).show();

                                }

                            }
                    );


                    // =================================================
                    // COMPLETION
                    // =================================================

                    mediaPlayer.setOnCompletionListener(
                            mp -> {

                                try {

                                    mp.release();

                                } catch (Exception ignored) {}


                                mediaPlayer =
                                        null;

                            }
                    );


                    // =================================================
                    // ERROR
                    // =================================================

                    mediaPlayer.setOnErrorListener(

                            (mp, what, extra) -> {

                                Toast.makeText(

                                        MainActivity.this,

                                        "Audio haikuweza kucheza.",

                                        Toast.LENGTH_SHORT

                                ).show();


                                try {

                                    mp.reset();

                                } catch (Exception ignored) {}


                                try {

                                    mp.release();

                                } catch (Exception ignored) {}


                                mediaPlayer =
                                        null;


                                return true;

                            }

                    );


                    // =================================================
                    // PREPARE ASYNC
                    // =================================================

                    mediaPlayer.prepareAsync();


                } catch (SecurityException e) {

                    e.printStackTrace();


                    releasePlayer();


                    Toast.makeText(

                            MainActivity.this,

                            "Ruhusa ya kusoma audio haipo.",

                            Toast.LENGTH_LONG

                    ).show();


                } catch (IOException e) {

                    e.printStackTrace();


                    releasePlayer();


                    Toast.makeText(

                            MainActivity.this,

                            "Imeshindikana kufungua audio.",

                            Toast.LENGTH_LONG

                    ).show();


                } catch (Exception e) {

                    e.printStackTrace();


                    releasePlayer();


                    Toast.makeText(

                            MainActivity.this,

                            "Tatizo la playback.",

                            Toast.LENGTH_LONG

                    ).show();

                }

            });

        }


        // =====================================================
        // RELEASE PLAYER
        // =====================================================

        private void releasePlayer() {

            if (mediaPlayer != null) {

                try {

                    if (mediaPlayer.isPlaying()) {

                        mediaPlayer.stop();

                    }

                } catch (Exception ignored) {}


                try {

                    mediaPlayer.reset();

                } catch (Exception ignored) {}


                try {

                    mediaPlayer.release();

                } catch (Exception ignored) {}


                mediaPlayer =
                        null;

            }

        }


        // =====================================================
        // STOP DEVICE AUDIO
        // =====================================================

        @JavascriptInterface
        public void stopDeviceAudio() {

            runOnUiThread(() -> {

                releasePlayer();

            });

        }


        // =====================================================
        // DOWNLOAD
        // =====================================================

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


    // =====================================================
    // PERMISSION RESULT
    // =====================================================

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


    // =====================================================
    // BACK BUTTON
    // =====================================================

    @Override
    public void onBackPressed() {

        if (webView != null) {

            webView.evaluateJavascript(

                    "typeof window.exitDevicePage===" +
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


    // =====================================================
    // ACTIVITY DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        releaseMediaPlayer();


        if (webView != null) {

            webView.destroy();

            webView = null;

        }


        super.onDestroy();

    }


    // =====================================================
    // RELEASE MEDIA PLAYER
    // =====================================================

    private void releaseMediaPlayer() {

        if (mediaPlayer != null) {

            try {

                if (mediaPlayer.isPlaying()) {

                    mediaPlayer.stop();

                }

            } catch (Exception ignored) {}


            try {

                mediaPlayer.reset();

            } catch (Exception ignored) {}


            try {

                mediaPlayer.release();

            } catch (Exception ignored) {}


            mediaPlayer =
                    null;

        }

    }

        }
