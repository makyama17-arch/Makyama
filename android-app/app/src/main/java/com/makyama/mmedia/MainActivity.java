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
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int REQUEST_AUDIO_PERMISSION = 1001;

    private MediaController mediaController;

    private ListenableFuture<MediaController> controllerFuture;

    private final List<String> pendingDownloads =
            new ArrayList<>();


    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);

        setupWebView();

        requestAudioPermission();

        connectToPlaybackService();

        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    // =====================================================
    // CONNECT TO MEDIA SERVICE
    // =====================================================

    private void connectToPlaybackService() {

        SessionToken sessionToken =
                new SessionToken(
                        this,
                        new android.content.ComponentName(
                                this,
                                PlaybackService.class
                        )
                );

        controllerFuture =
                new MediaController.Builder(
                        this,
                        sessionToken
                ).buildAsync();

        controllerFuture.addListener(
                () -> {

                    try {

                        mediaController =
                                controllerFuture.get();

                    }
                    catch (Exception e) {

                        e.printStackTrace();

                        runOnUiThread(
                                () -> Toast.makeText(
                                        MainActivity.this,
                                        "Media player haikuunganishwa.",
                                        Toast.LENGTH_SHORT
                                ).show()
                        );
                    }

                },
                androidx.core.content.ContextCompat
                        .getMainExecutor(this)
        );
    }


    // =====================================================
    // WEBVIEW
    // =====================================================

    private void setupWebView() {

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

        settings.setSupportZoom(false);

        settings.setLoadWithOverviewMode(false);

        settings.setUseWideViewPort(false);


        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        view.loadUrl(
                                request.getUrl().toString()
                        );

                        return true;
                    }
                }
        );


        webView.setWebChromeClient(
                new WebChromeClient()
        );


        webView.addJavascriptInterface(
                new MMEDIABridge(this),
                "MMEDIA"
        );
    }


    // =====================================================
    // AUDIO PERMISSION
    // =====================================================

    private void requestAudioPermission() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (
                    checkSelfPermission(
                            Manifest.permission.READ_MEDIA_AUDIO
                    )
                    != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_AUDIO
                        },
                        REQUEST_AUDIO_PERMISSION
                );
            }

        }
        else {

            if (
                    checkSelfPermission(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                    != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        REQUEST_AUDIO_PERMISSION
                );
            }
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
                        REQUEST_AUDIO_PERMISSION
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                Toast.makeText(
                        this,
                        "Audio permission imeruhusiwa.",
                        Toast.LENGTH_SHORT
                ).show();

            }
            else {

                Toast.makeText(
                        this,
                        "Ruhusu Music/Audio ili My Device ifanye kazi.",
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
                    "(function(){return window.exitDevicePage ? window.exitDevicePage() : false;})()",
                    value -> {

                        if (
                                value == null ||
                                value.equals("false") ||
                                value.equals("null")
                        ) {

                            if (webView.canGoBack()) {

                                webView.goBack();

                            }
                            else {

                                finish();
                            }
                        }
                    }
            );

        }
        else {

            super.onBackPressed();
        }
    }


    // =====================================================
    // JAVASCRIPT BRIDGE
    // =====================================================

    public class MMEDIABridge {

        private final Context context;


        MMEDIABridge(Context context) {

            this.context = context;
        }


        // =================================================
        // ENABLE MY DEVICE
        // =================================================

        @JavascriptInterface
        public void enableMyDevice() {

            runOnUiThread(
                    () -> {

                        if (webView == null) {
                            return;
                        }

                        webView.evaluateJavascript(
                                "window.enableMyDeviceButton && window.enableMyDeviceButton();",
                                null
                        );
                    }
            );
        }


        // =================================================
        // OPEN MY DEVICE
        // =================================================

        @JavascriptInterface
        public void openMyDevice() {

            runOnUiThread(
                    () -> {

                        if (!hasAudioPermission()) {

                            requestAudioPermission();

                            return;
                        }

                        readDeviceMusic();
                    }
            );
        }


        // =================================================
        // CHECK AUDIO PERMISSION
        // =================================================

        private boolean hasAudioPermission() {

            if (Build.VERSION.SDK_INT >= 33) {

                return checkSelfPermission(
                        Manifest.permission.READ_MEDIA_AUDIO
                )
                        ==
                        PackageManager.PERMISSION_GRANTED;

            }
            else {

                return checkSelfPermission(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                )
                        ==
                        PackageManager.PERMISSION_GRANTED;
            }
        }


        // =================================================
        // READ MUSIC FROM DEVICE
        // =================================================

        private void readDeviceMusic() {

            new Thread(
                    () -> {

                        JSONArray songs =
                                new JSONArray();

                        Uri collection =
                                MediaStore.Audio.Media
                                        .EXTERNAL_CONTENT_URI;

                        String[] projection = {

                                MediaStore.Audio.Media._ID,

                                MediaStore.Audio.Media.TITLE,

                                MediaStore.Audio.Media.ARTIST,

                                MediaStore.Audio.Media.ALBUM,

                                MediaStore.Audio.Media.MIME_TYPE,

                                MediaStore.Audio.Media.DURATION,

                                MediaStore.Audio.Media.DISPLAY_NAME
                        };

                        String selection =
                                MediaStore.Audio.Media.IS_MUSIC
                                        + " != 0";


                        Cursor cursor =
                                getContentResolver().query(

                                        collection,

                                        projection,

                                        selection,

                                        null,

                                        MediaStore.Audio.Media.TITLE
                                                + " COLLATE NOCASE ASC"
                                );


                        if (cursor != null) {

                            try {

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

                                int mimeColumn =
                                        cursor.getColumnIndexOrThrow(
                                                MediaStore.Audio.Media.MIME_TYPE
                                        );

                                int durationColumn =
                                        cursor.getColumnIndexOrThrow(
                                                MediaStore.Audio.Media.DURATION
                                        );

                                int displayColumn =
                                        cursor.getColumnIndexOrThrow(
                                                MediaStore.Audio.Media.DISPLAY_NAME
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

                                    String mime =
                                            cursor.getString(
                                                    mimeColumn
                                            );

                                    long duration =
                                            cursor.getLong(
                                                    durationColumn
                                            );

                                    String displayName =
                                            cursor.getString(
                                                    displayColumn
                                            );


                                    Uri contentUri =
                                            Uri.withAppendedPath(
                                                    MediaStore.Audio.Media
                                                            .EXTERNAL_CONTENT_URI,
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
                                            title == null ||
                                                    title.trim().isEmpty()
                                                    ? displayName
                                                    : title
                                    );

                                    song.put(
                                            "artist",
                                            artist == null
                                                    ? ""
                                                    : artist
                                    );

                                    song.put(
                                            "album",
                                            album == null
                                                    ? ""
                                                    : album
                                    );

                                    song.put(
                                            "mime",
                                            mime == null
                                                    ? "audio/*"
                                                    : mime
                                    );

                                    song.put(
                                            "duration",
                                            duration
                                    );

                                    song.put(
                                            "uri",
                                            contentUri.toString()
                                    );

                                    songs.put(song);
                                }

                            }
                            catch (Exception e) {

                                e.printStackTrace();

                            }
                            finally {

                                cursor.close();
                            }
                        }


                        runOnUiThread(
                                () -> {

                                    try {

                                        String json =
                                                JSONObject.quote(
                                                        songs.toString()
                                                );

                                        webView.evaluateJavascript(
                                                "window.showAndroidMusic && " +
                                                        "window.showAndroidMusic(" +
                                                        json +
                                                        ");",
                                                null
                                        );

                                    }
                                    catch (Exception e) {

                                        e.printStackTrace();
                                    }

                                }
                        );

                    }
            ).start();
        }


        // =================================================
        // PLAY DEVICE AUDIO
        // =================================================

        @JavascriptInterface
        public void playDeviceAudio(
                String uri,
                String title,
                String artist
        ) {

            if (mediaController == null) {

                showToast(
                        "Player bado inaunganishwa..."
                );

                return;
            }


            try {

                MediaMetadata metadata =
                        new MediaMetadata.Builder()
                                .setTitle(
                                        title == null ||
                                                title.trim().isEmpty()
                                                ? "Unknown"
                                                : title
                                )
                                .setArtist(
                                        artist == null ||
                                                artist.trim().isEmpty()
                                                ? "MAKYAMA MEDIA"
                                                : artist
                                )
                                .build();


                MediaItem item =
                        new MediaItem.Builder()
                                .setUri(uri)
                                .setMediaMetadata(metadata)
                                .build();


                mediaController.setMediaItem(item);

                mediaController.prepare();

                mediaController.play();

            }
            catch (Exception e) {

                e.printStackTrace();

                showToast(
                        "Imeshindikana kucheza audio."
                );
            }
        }


        // =================================================
        // PAUSE
        // =================================================

        @JavascriptInterface
        public void pauseDeviceAudio() {

            if (mediaController != null) {

                mediaController.pause();
            }
        }


        // =================================================
        // RESUME
        // =================================================

        @JavascriptInterface
        public void resumeDeviceAudio() {

            if (mediaController != null) {

                mediaController.play();
            }
        }


        // =================================================
        // STOP
        // =================================================

        @JavascriptInterface
        public void stopDeviceAudio() {

            if (mediaController != null) {

                mediaController.stop();
            }
        }


        // =================================================
        // NEXT
        // =================================================

        @JavascriptInterface
        public void nextDeviceAudio() {

            if (mediaController != null) {

                mediaController.seekToNext();
            }
        }


        // =================================================
        // PREVIOUS
        // =================================================

        @JavascriptInterface
        public void previousDeviceAudio() {

            if (mediaController != null) {

                mediaController.seekToPrevious();
            }
        }


        // =================================================
        // SHUFFLE
        // =================================================

        @JavascriptInterface
        public void shuffleDeviceAudio(
                boolean enabled
        ) {

            if (mediaController != null) {

                mediaController.setShuffleModeEnabled(
                        enabled
                );
            }
        }


        // =================================================
        // CHECK SHUFFLE
        // =================================================

        @JavascriptInterface
        public boolean isShuffleEnabled() {

            if (mediaController != null) {

                return mediaController
                        .getShuffleModeEnabled();
            }

            return false;
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

                showToast(
                        "Download URL haipo."
                );

                return;
            }


            try {

                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );


                request.setTitle(
                        filename
                );

                request.setDescription(
                        "Downloading from MMEDIA"
                );


                request.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );


                request.setAllowedOverMetered(
                        true
                );

                request.setAllowedOverRoaming(
                        true
                );


                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MUSIC,
                        filename
                );


                DownloadManager manager =
                        (DownloadManager)
                                getSystemService(
                                        DOWNLOAD_SERVICE
                                );


                long downloadId =
                        manager.enqueue(
                                request
                        );


                runOnUiThread(
                        () -> {

                            webView.evaluateJavascript(
                                    "window.updateAndroidDownload && " +
                                            "window.updateAndroidDownload(" +
                                            downloadId +
                                            "," +
                                            JSONObject.quote(filename) +
                                            ",0," +
                                            JSONObject.quote(
                                                    "Downloading..."
                                            ) +
                                            ");",
                                    null
                            );


                            Toast.makeText(
                                    MainActivity.this,
                                    "Download imeanza.",
                                    Toast.LENGTH_SHORT
                            ).show();

                        }
                );


                monitorDownload(
                        manager,
                        downloadId,
                        filename
                );

            }
            catch (Exception e) {

                e.printStackTrace();

                showToast(
                        "Download imeshindikana kuanza."
                );
            }
        }


        // =================================================
        // MONITOR DOWNLOAD
        // =================================================

        private void monitorDownload(
                DownloadManager manager,
                long downloadId,
                String filename
        ) {

            new Thread(
                    () -> {

                        boolean finished =
                                false;


                        while (!finished) {

                            DownloadManager.Query query =
                                    new DownloadManager.Query();

                            query.setFilterById(
                                    downloadId
                            );


                            Cursor cursor =
                                    manager.query(query);


                            if (cursor != null) {

                                try {

                                    if (cursor.moveToFirst()) {

                                        int bytesDownloaded =
                                                cursor.getInt(
                                                        cursor.getColumnIndexOrThrow(
                                                                DownloadManager
                                                                        .COLUMN_BYTES_DOWNLOADED_SO_FAR
                                                        )
                                                );


                                        int bytesTotal =
                                                cursor.getInt(
                                                        cursor.getColumnIndexOrThrow(
                                                                DownloadManager
                                                                        .COLUMN_TOTAL_SIZE_BYTES
                                                        )
                                                );


                                        int status =
                                                cursor.getInt(
                                                        cursor.getColumnIndexOrThrow(
                                                                DownloadManager
                                                                        .COLUMN_STATUS
                                                        )
                                                );


                                        int percent = 0;


                                        if (bytesTotal > 0) {

                                            percent =
                                                    (int)
                                                            (
                                                                    bytesDownloaded
                                                                            * 100L
                                                                            /
                                                                            bytesTotal
                                                            );
                                        }


                                        String statusText =
                                                "Downloading...";


                                        if (
                                                status ==
                                                        DownloadManager
                                                                .STATUS_SUCCESSFUL
                                        ) {

                                            percent = 100;

                                            statusText =
                                                    "Completed";

                                            finished = true;

                                        }
                                        else if (
                                                status ==
                                                        DownloadManager
                                                                .STATUS_FAILED
                                        ) {

                                            statusText =
                                                    "Failed";

                                            finished = true;
                                        }


                                        final int finalPercent =
                                                percent;

                                        final String finalStatus =
                                                statusText;


                                        runOnUiThread(
                                                () -> {

                                                    if (
                                                            finalStatus.equals(
                                                                    "Completed"
                                                            )
                                                    ) {

                                                        webView.evaluateJavascript(
                                                                "window.finishAndroidDownload && " +
                                                                        "window.finishAndroidDownload(" +
                                                                        downloadId +
                                                                        "," +
                                                                        JSONObject.quote(
                                                                                filename
                                                                        ) +
                                                                        ");",
                                                                null
                                                        );

                                                    }
                                                    else if (
                                                            finalStatus.equals(
                                                                    "Failed"
                                                            )
                                                    ) {

                                                        webView.evaluateJavascript(
                                                                "window.failAndroidDownload && " +
                                                                        "window.failAndroidDownload(" +
                                                                        downloadId +
                                                                        "," +
                                                                        JSONObject.quote(
                                                                                filename
                                                                        ) +
                                                                        ");",
                                                                null
                                                        );

                                                    }
                                                    else {

                                                        webView.evaluateJavascript(
                                                                "window.updateAndroidDownload && " +
                                                                        "window.updateAndroidDownload(" +
                                                                        downloadId +
                                                                        "," +
                                                                        JSONObject.quote(
                                                                                filename
                                                                        ) +
                                                                        "," +
                                                                        finalPercent +
                                                                        "," +
                                                                        JSONObject.quote(
                                                                                finalStatus
                                                                        ) +
                                                                        ");",
                                                                null
                                                        );
                                                    }

                                                }
                                        );
                                    }

                                }
                                catch (Exception e) {

                                    e.printStackTrace();

                                }
                                finally {

                                    cursor.close();
                                }
                            }


                            if (!finished) {

                                try {

                                    Thread.sleep(500);

                                }
                                catch (
                                        InterruptedException e
                                ) {

                                    Thread.currentThread()
                                            .interrupt();

                                    break;
                                }
                            }
                        }

                    }
            ).start();
        }


        // =================================================
        // TOAST
        // =================================================

        private void showToast(
                String message
        ) {

            runOnUiThread(
                    () ->
                            Toast.makeText(
                                    MainActivity.this,
                                    message,
                                    Toast.LENGTH_SHORT
                            ).show()
            );
        }
    }


    // =====================================================
    // ACTIVITY DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        if (mediaController != null) {

            mediaController.release();

            mediaController = null;
        }

        if (
                controllerFuture != null &&
                !controllerFuture.isDone()
        ) {

            controllerFuture.cancel(true);
        }


        if (webView != null) {

            webView.removeJavascriptInterface(
                    "MMEDIA"
            );

            webView.destroy();

            webView = null;
        }


        super.onDestroy();
    }
                    }
