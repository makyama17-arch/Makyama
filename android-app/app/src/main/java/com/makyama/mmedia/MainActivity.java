package com.makyama.mmedia;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int AUDIO_PERMISSION = 100;
    private static final int PICK_AUDIO = 101;

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

        requestAudioPermission();

        webView.loadUrl("https://makyama.vercel.app/");
    }

    private void requestAudioPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        AUDIO_PERMISSION
                );
            }

        } else {

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        AUDIO_PERMISSION
                );
            }
        }
    }

    public void openPhoneMusic() {

        Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        );

        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri audioUri = data.getData();

            if (audioUri != null) {

                String title = getAudioTitle(audioUri);

                Toast.makeText(
                        this,
                        "🎵 " + title,
                        Toast.LENGTH_SHORT
                ).show();

                playLocalAudio(audioUri);
            }
        }
    }

    private String getAudioTitle(Uri uri) {

        String title = "Local Music";

        Cursor cursor = getContentResolver().query(
                uri,
                new String[]{
                        MediaStore.Audio.Media.DISPLAY_NAME
                },
                null,
                null,
                null
        );

        if (cursor != null) {

            if (cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        MediaStore.Audio.Media.DISPLAY_NAME
                );

                if (index >= 0) {
                    title = cursor.getString(index);
                }
            }

            cursor.close();
        }

        return title;
    }

    private void playLocalAudio(Uri uri) {

        try {

            Intent intent = new Intent(
                    Intent.ACTION_VIEW
            );

            intent.setDataAndType(
                    uri,
                    "audio/*"
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Haiwezi kufungua audio hii.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
