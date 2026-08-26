package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int AUDIO_PERMISSION_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_CODE = 1002;

    private MediaPlayer mediaPlayer;

    private DownloadManager downloadManager;

    private Handler downloadHandler;

    private Runnable downloadRunnable;

    private final List<Long> downloadIds = new ArrayList<>();


    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);


        // =================================================
        // DOWNLOAD MANAGER
        // =================================================

        downloadManager =
                (DownloadManager)
                        getSystemService(
                                DOWNLOAD_SERVICE
                        );


        downloadHandler =
                new Handler(
                        Looper.getMainLooper()
                );


        // =================================================
        // REQUEST NOTIFICATION PERMISSION
        // Android 13+
        // =================================================

        requestNotificationPermission();


        // =================================================
        // WEBVIEW
        // =================================================

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setBuiltInZoomControls(false);

        settings.setDisplayZoomControls(false);


        // =================================================
        // WEBVIEW CLIENT
        // =================================================

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
        // LOAD MAKYAMA
        // =================================================

        webView.loadUrl(
                "https://makyama.vercel.app/"
        );


        // =================================================
        // START DOWNLOAD MONITOR
        // =================================================

        startDownloadMonitor();

    }


    // =====================================================
    // NOTIFICATION PERMISSION
    // =====================================================

    private void requestNotificationPermission() {

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(

                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },

                        NOTIFICATION_PERMISSION_CODE

                );

            }

        }

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

                    sendDownloadsToWebsite();

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
        // AUDIO PERMISSION
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


                            if (
                                    title == null ||
                                    title.trim().isEmpty()
                            ) {

                                title = filename;

                            }


                            if (
                                    title == null ||
                                    title.trim().isEmpty()
                            ) {

                                title = "Unknown Song";

                            }


                            if (
                                    artist == null ||
                                    artist.trim().isEmpty() ||
                                    artist.equals("<unknown>")
                            ) {

                                artist = "Unknown Artist";

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


                            audioArray.put(audio);


                        } catch (Exception itemError) {

                            itemError.printStackTrace();

                        }

                    }


                    cursor.close();

                }


                final String json =
                        audioArray.toString();


                runOnUiThread(() -> {

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


        // =================================================
        // PLAY DEVICE AUDIO
        // =================================================

        @JavascriptInterface
        public void playDeviceAudio(

                String uriString,

                String title,

                String artist

        ) {

            runOnUiThread(() -> {

                try {

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


                    releasePlayer();


                    Uri audioUri =
                            Uri.parse(uriString);


                    mediaPlayer =
                            new MediaPlayer();


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


                    mediaPlayer.setDataSource(

                            MainActivity.this,

                            audioUri

                    );


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

                                }

                            }
                    );


                    mediaPlayer.setOnCompletionListener(
                            mp -> {

                                try {

                                    mp.release();

                                } catch (Exception ignored) {}


                                mediaPlayer = null;

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
                                    mp.reset();
                                } catch (Exception ignored) {}


                                try {
                                    mp.release();
                                } catch (Exception ignored) {}


                                mediaPlayer = null;


                                return true;

                            }

                    );


                    mediaPlayer.prepareAsync();


                } catch (Exception e) {

                    e.printStackTrace();

                    releasePlayer();


                    Toast.makeText(

                            MainActivity.this,

                            "Tatizo la playback.",

                            Toast.LENGTH_SHORT

                    ).show();

                }

            });

        }


        // =================================================
        // STOP DEVICE AUDIO
        // =================================================

        @JavascriptInterface
        public void stopDeviceAudio() {

            runOnUiThread(() -> {

                releasePlayer();

            });

        }


        // =================================================
        // DOWNLOAD
        // =================================================

        @JavascriptInterface
        public void download(

                String url,

                String filename

        ) {

            if (
                    url == null ||
                    url.trim().isEmpty()
            ) {

                runOnUiThread(() -> {

                    Toast.makeText(

                            MainActivity.this,

                            "Download URL haipo.",

                            Toast.LENGTH_SHORT

                    ).show();

                });

                return;

            }


            try {

                // =================================================
                // CLEAN FILE NAME
                // =================================================

                String cleanFilename =
                        filename;


                if (
                        cleanFilename == null ||
                        cleanFilename.trim().isEmpty()
                ) {

                    cleanFilename =
                            "MAKYAMA_Music.mp3";

                }


                cleanFilename =
                        cleanFilename
                                .replace("/", "_")
                                .replace("\\", "_")
                                .replace(":", "_")
                                .replace("*", "_")
                                .replace("?", "_")
                                .replace("\"", "_")
                                .replace("<", "_")
                                .replace(">", "_")
                                .replace("|", "_");


                // =================================================
                // DOWNLOAD REQUEST
                // =================================================

                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );


                // =================================================
                // TITLE
                // =================================================

                request.setTitle(
                        cleanFilename
                );


                // =================================================
                // DESCRIPTION
                // =================================================

                request.setDescription(
                        "MAKYAMA MEDIA • Downloading..."
                );


                // =================================================
                // SHOW ANDROID DOWNLOAD NOTIFICATION
                // =================================================

                request.setNotificationVisibility(

                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED

                );


                // =================================================
                // NETWORK
                // =================================================

                request.setAllowedOverMetered(true);

                request.setAllowedOverRoaming(true);


                // =================================================
                // MIME TYPE
                // =================================================

                request.setMimeType(
                        "audio/*"
                );


                // =================================================
                // SAVE TO DOWNLOADS
                // =================================================

                request.setDestinationInExternalPublicDir(

                        Environment.DIRECTORY_DOWNLOADS,

                        cleanFilename

                );


                // =================================================
                // START
                // =================================================

                long downloadId =
                        downloadManager.enqueue(
                                request
                        );


                // =================================================
                // SAVE ID
                // =================================================

                synchronized (downloadIds) {

                    downloadIds.add(
                            downloadId
                    );

                }


                // =================================================
                // IMMEDIATE WEBSITE UPDATE
                // =================================================

                sendDownloadsToWebsite();


                // =================================================
                // MESSAGE
                // =================================================

                runOnUiThread(() -> {

                    Toast.makeText(

                            MainActivity.this,

                            "⬇ Download inaanza...",

                            Toast.LENGTH_SHORT

                    ).show();

                });


            } catch (Exception e) {

                e.printStackTrace();


                runOnUiThread(() -> {

                    Toast.makeText(

                            MainActivity.this,

                            "Download imeshindikana kuanza.",

                            Toast.LENGTH_LONG

                    ).show();

                });

            }

        }


        // =================================================
        // GET DOWNLOADS
        // =================================================

        @JavascriptInterface
        public String getDownloads() {

            return getDownloadsJson();

        }

    }


    // =====================================================
    // DOWNLOAD MONITOR
    // =====================================================

    private void startDownloadMonitor() {

        downloadRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        sendDownloadsToWebsite();


                        if (downloadHandler != null) {

                            downloadHandler.postDelayed(

                                    this,

                                    500

                            );

                        }

                    }

                };


        downloadHandler.post(
                downloadRunnable
        );

    }


    // =====================================================
    // SEND DOWNLOADS TO WEBSITE
    // =====================================================

    private void sendDownloadsToWebsite() {

        if (webView == null) {
            return;
        }


        final String json =
                getDownloadsJson();


        runOnUiThread(() -> {

            if (webView == null) {
                return;
            }


            String js =

                    "if(typeof window.showAndroidDownloads===" +
                    "'function')" +

                    "{" +

                    "window.showAndroidDownloads(" +
                    JSONObject.quote(json) +
                    ");" +

                    "}";


            webView.evaluateJavascript(

                    js,

                    null

            );

        });

    }


    // =====================================================
    // GET DOWNLOAD JSON
    // =====================================================

    private String getDownloadsJson() {

        JSONArray array =
                new JSONArray();


        synchronized (downloadIds) {

            for (
                    int i = 0;
                    i < downloadIds.size();
                    i++
            ) {

                long id =
                        downloadIds.get(i);


                Cursor cursor = null;


                try {

                    DownloadManager.Query query =
                            new DownloadManager.Query();


                    query.setFilterById(id);


                    cursor =
                            downloadManager.query(
                                    query
                            );


                    if (
                            cursor == null ||
                            !cursor.moveToFirst()
                    ) {

                        continue;

                    }


                    int statusColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_STATUS
                            );


                    int titleColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_TITLE
                            );


                    int totalColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                            );


                    int downloadedColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            );


                    int reasonColumn =
                            cursor.getColumnIndex(
                                    DownloadManager.COLUMN_REASON
                            );


                    int status =
                            cursor.getInt(
                                    statusColumn
                            );


                    String title =
                            cursor.getString(
                                    titleColumn
                            );


                    long total =
                            cursor.getLong(
                                    totalColumn
                            );


                    long downloaded =
                            cursor.getLong(
                                    downloadedColumn
                            );


                    int reason =
                            cursor.getInt(
                                    reasonColumn
                            );


                    // =================================================
                    // CALCULATE PERCENT
                    // =================================================

                    int progress = 0;


                    if (total > 0) {

                        progress =
                                (int)
                                (
                                        (
                                                downloaded * 100
                                        )
                                        /
                                        total
                                );

                    }


                    // =================================================
                    // STATUS
                    // =================================================

                    String state =
                            "downloading";


                    if (
                            status ==
                            DownloadManager.STATUS_PENDING
                    ) {

                        state =
                                "pending";

                    }

                    else if (
                            status ==
                            DownloadManager.STATUS_RUNNING
                    ) {

                        state =
                                "downloading";

                    }

                    else if (
                            status ==
                            DownloadManager.STATUS_PAUSED
                    ) {

                        state =
                                "paused";

                    }

                    else if (
                            status ==
                            DownloadManager.STATUS_SUCCESSFUL
                    ) {

                        state =
                                "completed";

                        progress = 100;

                    }

                    else if (
                            status ==
                            DownloadManager.STATUS_FAILED
                    ) {

                        state =
                                "failed";

                    }


                    // =================================================
                    // JSON ITEM
                    // =================================================

                    JSONObject item =
                            new JSONObject();


                    item.put(
                            "id",
                            id
                    );


                    item.put(
                            "title",
                            title != null
                                    ? title
                                    : "Download"
                    );


                    item.put(
                            "progress",
                            progress
                    );


                    item.put(
                            "downloaded",
                            downloaded
                    );


                    item.put(
                            "total",
                            total
                    );


                    item.put(
                            "status",
                            state
                    );


                    item.put(
                            "reason",
                            reason
                    );


                    array.put(item);


                } catch (Exception e) {

                    e.printStackTrace();

                } finally {

                    if (cursor != null) {

                        cursor.close();

                    }

                }

            }

        }


        return array.toString();

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


            mediaPlayer = null;

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


                sendDownloadsToWebsite();


            } else {

                Toast.makeText(

                        this,

                        "Ruhusa ya kusoma audio haijatolewa.",

                        Toast.LENGTH_LONG

                ).show();

            }

        }


        if (
                requestCode ==
                NOTIFICATION_PERMISSION_CODE
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                Toast.makeText(

                        this,

                        "Download notifications zimewashwa.",

                        Toast.LENGTH_SHORT

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

                            if (webView.canGoBack()) {

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
    // DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        if (
                downloadHandler != null &&
                downloadRunnable != null
        ) {

            downloadHandler.removeCallbacks(
                    downloadRunnable
            );

        }


        releasePlayer();


        if (webView != null) {

            webView.destroy();

            webView = null;

        }


        super.onDestroy();

    }

                        }
