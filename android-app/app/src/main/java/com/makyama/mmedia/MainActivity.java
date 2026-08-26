package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
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

    private static final int AUDIO_PERMISSION_REQUEST = 1001;

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

        /*
         * Bridge ya Android ↔ Website
         */
        webView.addJavascriptInterface(
                new MMEDIAAndroidBridge(this),
                "MMEDIA"
        );

        /*
         * Fungua website yako
         */
        webView.loadUrl(
                "https://makyama.vercel.app/"
        );
    }


    /*
     * =====================================================
     * ANDROID BRIDGE
     * =====================================================
     */

    public class MMEDIAAndroidBridge {

        private final Context context;

        MMEDIAAndroidBridge(Context context) {
            this.context = context;
        }


        /*
         * =================================================
         * MY DEVICE
         * Website itaita:
         *
         * MMEDIA.openDeviceMusic()
         * =================================================
         */

        @JavascriptInterface
        public void openDeviceMusic() {

            runOnUiThread(() -> {

                if (hasAudioPermission()) {

                    loadDeviceMusic();

                } else {

                    requestAudioPermission();

                }

            });

        }


        /*
         * =================================================
         * CHECK PERMISSION
         * =================================================
         */

        private boolean hasAudioPermission() {

            if (Build.VERSION.SDK_INT >= 33) {

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

            if (Build.VERSION.SDK_INT >= 33) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_MEDIA_AUDIO
                        },
                        AUDIO_PERMISSION_REQUEST
                );

            } else {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        AUDIO_PERMISSION_REQUEST
                );

            }

        }


        /*
         * =================================================
         * LOAD ALL MUSIC FROM PHONE
         * =================================================
         */

        private void loadDeviceMusic() {

            try {

                ContentResolver resolver =
                        getContentResolver();

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

                        MediaStore.Audio.Media.MIME_TYPE,

                        MediaStore.Audio.Media.DISPLAY_NAME

                };


                String selection =
                        MediaStore.Audio.Media.IS_MUSIC + " != 0";


                String sortOrder =
                        MediaStore.Audio.Media.TITLE +
                        " COLLATE NOCASE ASC";


                Cursor cursor =
                        resolver.query(
                                collection,
                                projection,
                                selection,
                                null,
                                sortOrder
                        );


                JSONArray songs =
                        new JSONArray();


                if (cursor != null) {

                    int idColumn =
                            cursor.getColumnIndexOrThrow(
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

                    int durationColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.DURATION
                            );

                    int mimeColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.MIME_TYPE
                            );

                    int displayNameColumn =
                            cursor.getColumnIndex(
                                    MediaStore.Audio.Media.DISPLAY_NAME
                            );


                    while (cursor.moveToNext()) {

                        long id =
                                cursor.getLong(
                                        idColumn
                                );


                        Uri audioUri =
                                Uri.withAppendedPath(
                                        collection,
                                        String.valueOf(id)
                                );


                        String title =
                                getCursorString(
                                        cursor,
                                        titleColumn
                                );


                        String artist =
                                getCursorString(
                                        cursor,
                                        artistColumn
                                );


                        String album =
                                getCursorString(
                                        cursor,
                                        albumColumn
                                );


                        String mime =
                                getCursorString(
                                        cursor,
                                        mimeColumn
                                );


                        String fileName =
                                getCursorString(
                                        cursor,
                                        displayNameColumn
                                );


                        long duration = 0;

                        if (durationColumn >= 0 &&
                                !cursor.isNull(durationColumn)) {

                            duration =
                                    cursor.getLong(
                                            durationColumn
                                    );

                        }


                        /*
                         * Kama title haipo,
                         * tumia filename.
                         */

                        if (title == null ||
                                title.trim().isEmpty()) {

                            title = fileName;

                        }


                        if (artist == null ||
                                artist.trim().isEmpty() ||
                                artist.equals(
                                        "<unknown>"
                                )) {

                            artist = "My Device";

                        }


                        JSONObject song =
                                new JSONObject();


                        song.put(
                                "id",
                                id
                        );


                        song.put(
                                "title",
                                title != null
                                        ? title
                                        : "Unknown Song"
                        );


                        song.put(
                                "artist",
                                artist
                        );


                        song.put(
                                "album",
                                album != null
                                        ? album
                                        : ""
                        );


                        song.put(
                                "duration",
                                duration
                        );


                        song.put(
                                "mime",
                                mime != null
                                        ? mime
                                        : "audio/*"
                        );


                        song.put(
                                "filename",
                                fileName != null
                                        ? fileName
                                        : ""
                        );


                        /*
                         * URI ya audio ya simu.
                         *
                         * WebView inaweza kuitumia
                         * kucheza audio.
                         */

                        song.put(
                                "audio",
                                audioUri.toString()
                        );


                        songs.put(song);

                    }


                    cursor.close();

                }


                /*
                 * Tuma list kwenda JavaScript
                 */

                String json =
                        JSONObject
                                .quote(
                                        songs.toString()
                                );


                runOnUiThread(() -> {

                    webView.evaluateJavascript(
                            "window.receiveDeviceMusic(" +
                            json +
                            ");",
                            null
                    );

                });


            } catch (Exception e) {

                runOnUiThread(() -> {

                    Toast.makeText(
                            context,
                            "Imeshindwa kusoma audio za simu.",
                            Toast.LENGTH_LONG
                    ).show();

                    webView.evaluateJavascript(
                            "window.receiveDeviceMusicError(" +
                            JSONObject.quote(
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Unknown error"
                            ) +
                            ");",
                            null
                    );

                });

            }

        }


        /*
         * =================================================
         * SAFE CURSOR STRING
         * =================================================
         */

        private String getCursorString(
                Cursor cursor,
                int column
        ) {

            if (column < 0 ||
                    cursor.isNull(column)) {

                return "";

            }

            return cursor.getString(column);

        }


        /*
         * =================================================
         * DOWNLOAD
         * =================================================
         *
         * Hii bado ipo kwa downloads zako
         * za online audio.
         *
         * =================================================
         */

        @JavascriptInterface
        public void download(
                String url,
                String filename
        ) {

            /*
             * Download function tutaendelea
             * nayo kwenye APK kama ulivyokuwa
             * umeiweka.
             *
             * Kwa sasa MY DEVICE haitumii
             * download; inasoma audio zilizopo
             * kwenye simu.
             */

            Toast.makeText(
                    context,
                    "Download: " + filename,
                    Toast.LENGTH_SHORT
            ).show();

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
                AUDIO_PERMISSION_REQUEST
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


                /*
                 * Baada ya permission,
                 * soma audio zote.
                 */

                MMEDIAAndroidBridge bridge =
                        new MMEDIAAndroidBridge(this);

                bridge.loadDeviceMusic();

            } else {

                Toast.makeText(
                        this,
                        "Ruhusa ya kusoma audio imekataliwa.",
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

        if (
                webView != null &&
                webView.canGoBack()
        ) {

            webView.goBack();

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

        if (webView != null) {

            webView.stopLoading();

            webView.destroy();

        }

        super.onDestroy();

    }

                                }
