package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ComponentName;
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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int REQUEST_AUDIO_PERMISSION = 1001;

    private MediaController mediaController;

    private ListenableFuture<MediaController> controllerFuture;

    private final List<MediaItem> deviceQueue =
            new ArrayList<>();

    private final Executor backgroundExecutor =
            Executors.newSingleThreadExecutor();


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
    // MAIN ACTIVITY TOAST
    // =====================================================

    private void showToast(String message) {

        runOnUiThread(
                () -> Toast.makeText(
                        MainActivity.this,
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }


    // =====================================================
    // CONNECT TO PLAYBACK SERVICE
    // =====================================================

    private void connectToPlaybackService() {

        try {

            ComponentName componentName =
                    new ComponentName(
                            this,
                            PlaybackService.class
                    );

            SessionToken sessionToken =
                    new SessionToken(
                            this,
                            componentName
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

                            runOnUiThread(
                                    () -> Toast.makeText(
                                            MainActivity.this,
                                            "Media player iko tayari.",
                                            Toast.LENGTH_SHORT
                                    ).show()
                            );

                        }
                        catch (Exception e) {

                            e.printStackTrace();

                            showToast(
                                    "Media player haikuunganishwa."
                            );
                        }

                    },
                    ContextCompat.getMainExecutor(this)
            );

        }
        catch (Exception e) {

            e.printStackTrace();

            final String error =
                    e.getClass().getSimpleName()
                            + ": "
                            + (
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    );

            showToast(error);
        }
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

        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            );
        }


        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        return false;
                    }


                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url
                    ) {

                        return false;
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
                    ContextCompat.checkSelfPermission(
                            this,
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
                    ContextCompat.checkSelfPermission(
                            this,
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
            @NonNull String[] permissions,
            @NonNull int[] grantResults
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
                    grantResults.length > 0
                            &&
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

        if (webView == null) {

            super.onBackPressed();

            return;
        }


        webView.evaluateJavascript(
                "(function(){return window.exitDevicePage ? window.exitDevicePage() : false;})()",
                value -> {

                    if (
                            value == null
                                    ||
                                    value.equals("false")
                                    ||
                                    value.equals("null")
                                    ||
                                    value.equals("\"false\"")
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

                return ContextCompat.checkSelfPermission(
                        MainActivity.this,
                        Manifest.permission.READ_MEDIA_AUDIO
                )
                        ==
                        PackageManager.PERMISSION_GRANTED;
            }

            return ContextCompat.checkSelfPermission(
                    MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            )
                    ==
                    PackageManager.PERMISSION_GRANTED;
        }


        // =================================================
        // READ MUSIC FROM DEVICE
        // =================================================

        private void readDeviceMusic() {

            backgroundExecutor.execute(
                    () -> {

                        JSONArray songs =
                                new JSONArray();


                        synchronized (deviceQueue) {

                            deviceQueue.clear();
                        }


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


                        Cursor cursor = null;


                        try {

                            cursor =
                                    getContentResolver().query(
                                            collection,
                                            projection,
                                            selection,
                                            null,
                                            MediaStore.Audio.Media.TITLE
                                                    + " COLLATE NOCASE ASC"
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

                                int mimeColumn =
                                        cursor.getColumnIndex(
                                                MediaStore.Audio.Media.MIME_TYPE
                                        );

                                int durationColumn =
                                        cursor.getColumnIndex(
                                                MediaStore.Audio.Media.DURATION
                                        );

                                int displayColumn =
                                        cursor.getColumnIndex(
                                                MediaStore.Audio.Media.DISPLAY_NAME
                                        );


                                if (
                                        idColumn < 0 ||
                                                titleColumn < 0 ||
                                                artistColumn < 0 ||
                                                albumColumn < 0 ||
                                                mimeColumn < 0 ||
                                                durationColumn < 0 ||
                                                displayColumn < 0
                                ) {

                                    return;
                                }


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


                                    String finalTitle =
                                            title == null ||
                                                    title.trim().isEmpty()
                                                    ? (
                                                    displayName == null
                                                            ? "Unknown"
                                                            : displayName
                                            )
                                                    : title;


                                    String finalArtist =
                                            artist == null ||
                                                    artist.trim().isEmpty()
                                                    ? "Unknown Artist"
                                                    : artist;


                                    String finalAlbum =
                                            album == null
                                                    ? ""
                                                    : album;


                                    String finalMime =
                                            mime == null ||
                                                    mime.trim().isEmpty()
                                                    ? "audio/*"
                                                    : mime;


                                    MediaMetadata metadata =
                                            new MediaMetadata.Builder()
                                                    .setTitle(
                                                            finalTitle
                                                    )
                                                    .setArtist(
                                                            finalArtist
                                                    )
                                                    .setAlbumTitle(
                                                            finalAlbum
                                                    )
                                                    .build();


                                    MediaItem item =
                                            new MediaItem.Builder()
                                                    .setMediaId(
                                                            String.valueOf(id)
                                                    )
                                                    .setUri(
                                                            contentUri
                                                    )
                                                    .setMediaMetadata(
                                                            metadata
                                                    )
                                                    .build();


                                    synchronized (deviceQueue) {

                                        deviceQueue.add(item);
                                    }


                                    JSONObject song =
                                            new JSONObject();


                                    song.put(
                                            "id",
                                            id
                                    );

                                    song.put(
                                            "title",
                                            finalTitle
                                    );

                                    song.put(
                                            "artist",
                                            finalArtist
                                    );

                                    song.put(
                                            "album",
                                            finalAlbum
                                    );

                                    song.put(
                                            "mime",
                                            finalMime
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

                        }
                        catch (Exception e) {

                            e.printStackTrace();

                        }
                        finally {

                            if (cursor != null) {

                                cursor.close();
                            }
                        }


                        runOnUiThread(
                                () -> {

                                    if (webView == null) {
                                        return;
                                    }


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
            );
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

            if (
                    uri == null ||
                            uri.trim().isEmpty()
            ) {

                showBridgeToast(
                        "Audio URI haipo."
                );

                return;
            }


            if (mediaController == null) {

                showBridgeToast(
                        "Player bado inaunganishwa..."
                );

                connectToPlaybackService();

                return;
            }


            try {

                MediaItem selectedItem = null;

                int selectedIndex = -1;


                synchronized (deviceQueue) {

                    for (
                            int i = 0;
                            i < deviceQueue.size();
                            i++
                    ) {

                        MediaItem item =
                                deviceQueue.get(i);


                        if (
                                item.localConfiguration != null
                                        &&
                                        item.localConfiguration.uri != null
                                        &&
                                        item.localConfiguration.uri
                                                .toString()
                                                .equals(uri)
                        ) {

                            selectedItem = item;

                            selectedIndex = i;

                            break;
                        }
                    }
                }


                // =========================================
                // PLAY FROM DEVICE QUEUE
                // =========================================

                if (
                        selectedItem != null &&
                                selectedIndex >= 0
                ) {

                    List<MediaItem> queueCopy;


                    synchronized (deviceQueue) {

                        queueCopy =
                                new ArrayList<>(
                                        deviceQueue
                                );
                    }


                    mediaController.setMediaItems(
                            queueCopy,
                            selectedIndex,
                            0L
                    );


                    mediaController.prepare();

                    mediaController.play();

                    return;
                }


                // =========================================
                // FALLBACK: PLAY SINGLE URI
                // =========================================

                String safeTitle =
                        title == null ||
                                title.trim().isEmpty()
                                ? "Unknown"
                                : title;


                String safeArtist =
                        artist == null ||
                                artist.trim().isEmpty()
                                ? "MAKYAMA MEDIA"
                                : artist;


                MediaMetadata metadata =
                        new MediaMetadata.Builder()
                                .setTitle(
                                        safeTitle
                                )
                                .setArtist(
                                        safeArtist
                                )
                                .build();


                MediaItem item =
                        new MediaItem.Builder()
                                .setMediaId(
                                        uri
                                )
                                .setUri(
                                        Uri.parse(uri)
                                )
                                .setMediaMetadata(
                                        metadata
                                )
                                .build();


                mediaController.setMediaItem(
                        item
                );


                mediaController.prepare();

                mediaController.play();

            }
            catch (Exception e) {

                e.printStackTrace();

                showBridgeToast(
                        "Imeshindikana kucheza audio."
                );
            }
        }


        // =================================================
        // PLAY / PAUSE
        // =================================================

        @JavascriptInterface
        public void toggleDeviceAudio() {

            if (mediaController == null) {
                return;
            }


            if (mediaController.isPlaying()) {

                mediaController.pause();

            }
            else {

                mediaController.play();
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

            if (mediaController == null) {
                return;
            }


            if (mediaController.hasNextMediaItem()) {

                mediaController.seekToNext();

            }
            else {

                mediaController.pause();
            }
        }


        // =================================================
        // PREVIOUS
        // =================================================

        @JavascriptInterface
        public void previousDeviceAudio() {

            if (mediaController == null) {
                return;
            }


            if (mediaController.hasPreviousMediaItem()) {

                mediaController.seekToPrevious();

            }
            else {

                mediaController.seekTo(0);
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
        // IS PLAYING
        // =================================================

        @JavascriptInterface
        public boolean isDeviceAudioPlaying() {

            if (mediaController != null) {

                return mediaController.isPlaying();
            }

            return false;
        }


        // =================================================
        // CURRENT TITLE
        // =================================================

        @JavascriptInterface
        public String getCurrentTitle() {

            if (
                    mediaController != null
                            &&
                            mediaController.getCurrentMediaItem() != null
            ) {

                MediaMetadata metadata =
                        mediaController
                                .getCurrentMediaItem()
                                .mediaMetadata;


                if (
                        metadata != null
                                &&
                                metadata.title != null
                ) {

                    return metadata.title.toString();
                }
            }


            return "";
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

                showBridgeToast(
                        "Download URL haipo."
                );

                return;
            }


            try {

                String safeFilename =
                        filename == null ||
                                filename.trim().isEmpty()
                                ? "makyama_download"
                                : filename;


                DownloadManager.Request request =
                        new DownloadManager.Request(
                                Uri.parse(url)
                        );


                request.setTitle(
                        safeFilename
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
                        safeFilename
                );


                DownloadManager manager =
                        (DownloadManager)
                                getSystemService(
                                        DOWNLOAD_SERVICE
                                );


                if (manager == null) {

                    showBridgeToast(
                            "DownloadManager haipo."
                    );

                    return;
                }


                long downloadId =
                        manager.enqueue(
                                request
                        );


                runOnUiThread(
                        () -> {

                            if (webView != null) {

                                webView.evaluateJavascript(
                                        "window.updateAndroidDownload && " +
                                                "window.updateAndroidDownload(" +
                                                downloadId +
                                                "," +
                                                JSONObject.quote(
                                                        safeFilename
                                                ) +
                                                ",0," +
                                                JSONObject.quote(
                                                        "Downloading..."
                                                ) +
                                                ");",
                                        null
                                );
                            }


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
                        safeFilename
                );

            }
            catch (Exception e) {

                e.printStackTrace();

                showBridgeToast(
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

            backgroundExecutor.execute(
                    () -> {

                        boolean finished = false;


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

                                                    if (webView == null) {
                                                        return;
                                                    }


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
            );
        }


        // =================================================
        // BRIDGE TOAST
        // =================================================

        private void showBridgeToast(
                String message
        ) {

            runOnUiThread(
                    () ->
                            Toast.makeText(
                                    MainActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show()
            );
        }
    }


    // =====================================================
    // ACTIVITY DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        if (controllerFuture != null) {

            controllerFuture.cancel(true);

            controllerFuture = null;
        }


        if (mediaController != null) {

            mediaController.release();

            mediaController = null;
        }


        if (webView != null) {

            webView.removeJavascriptInterface(
                    "MMEDIA"
            );

            webView.stopLoading();

            webView.loadUrl(
                    "about:blank"
            );

            webView.destroy();

            webView = null;
        }


        super.onDestroy();
    }
                                        }
