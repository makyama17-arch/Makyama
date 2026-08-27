package com.makyama.mmedia;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession mediaSession;


    // =========================================================
    // CREATE SERVICE
    // =========================================================

    @Override
    public void onCreate() {
        super.onCreate();

        // -----------------------------------------------------
        // AUDIO ATTRIBUTES
        // -----------------------------------------------------

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(
                                C.AUDIO_CONTENT_TYPE_MUSIC
                        )
                        .build();


        // -----------------------------------------------------
        // EXOPLAYER
        // -----------------------------------------------------

        player =
                new ExoPlayer.Builder(this)
                        .setAudioAttributes(
                                audioAttributes,
                                true
                        )
                        .setHandleAudioBecomingNoisy(true)
                        .build();


        // -----------------------------------------------------
        // OPEN MAIN ACTIVITY FROM NOTIFICATION
        // -----------------------------------------------------

        Intent intent =
                new Intent(this, MainActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        100,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
                );


        // -----------------------------------------------------
        // MEDIA SESSION
        // -----------------------------------------------------

        mediaSession =
                new MediaSession.Builder(
                        this,
                        player
                )
                        .setSessionActivity(
                                pendingIntent
                        )
                        .build();
    }


    // =========================================================
    // PLAY TRACK
    // =========================================================

    public void playTrack(
            String url,
            String title,
            String artist,
            String artwork
    ) {

        if (
                url == null ||
                url.trim().isEmpty()
        ) {
            return;
        }


        // -----------------------------------------------------
        // METADATA
        // -----------------------------------------------------

        MediaMetadata.Builder metadata =
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
                        );


        // -----------------------------------------------------
        // ARTWORK
        // -----------------------------------------------------

        if (
                artwork != null &&
                !artwork.trim().isEmpty()
        ) {

            try {

                metadata.setArtworkUri(
                        Uri.parse(artwork)
                );

            }
            catch (Exception ignored) {
            }
        }


        // -----------------------------------------------------
        // MEDIA ITEM
        // -----------------------------------------------------

        MediaItem item =
                new MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(
                                metadata.build()
                        )
                        .build();


        // -----------------------------------------------------
        // PLAY
        // -----------------------------------------------------

        player.setMediaItem(item);

        player.prepare();

        player.play();
    }


    // =========================================================
    // PLAY URL
    // =========================================================

    public void playUrl(String url) {

        if (
                url == null ||
                url.trim().isEmpty()
        ) {
            return;
        }


        MediaItem item =
                MediaItem.fromUri(url);


        player.setMediaItem(item);

        player.prepare();

        player.play();
    }


    // =========================================================
    // ADD SINGLE ITEM
    // =========================================================

    public void addTrack(
            String url,
            String title,
            String artist,
            String artwork
    ) {

        if (
                url == null ||
                url.trim().isEmpty()
        ) {
            return;
        }


        MediaMetadata.Builder metadata =
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
                        );


        if (
                artwork != null &&
                !artwork.trim().isEmpty()
        ) {

            try {

                metadata.setArtworkUri(
                        Uri.parse(artwork)
                );

            }
            catch (Exception ignored) {
            }
        }


        MediaItem item =
                new MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(
                                metadata.build()
                        )
                        .build();


        player.addMediaItem(item);
    }


    // =========================================================
    // PLAY INDEX
    // =========================================================

    public void playIndex(int index) {

        if (player == null) {
            return;
        }

        if (
                index < 0 ||
                index >= player.getMediaItemCount()
        ) {
            return;
        }


        player.seekToDefaultPosition(index);

        player.prepare();

        player.play();
    }


    // =========================================================
    // PAUSE
    // =========================================================

    public void pause() {

        if (player != null) {
            player.pause();
        }
    }


    // =========================================================
    // RESUME
    // =========================================================

    public void resume() {

        if (player != null) {
            player.play();
        }
    }


    // =========================================================
    // STOP
    // =========================================================

    public void stop() {

        if (player != null) {
            player.stop();
        }
    }


    // =========================================================
    // NEXT
    // =========================================================

    public void next() {

        if (player == null) {
            return;
        }


        if (player.hasNextMediaItem()) {

            player.seekToNext();

            player.play();
        }
    }


    // =========================================================
    // PREVIOUS
    // =========================================================

    public void previous() {

        if (player == null) {
            return;
        }


        if (player.hasPreviousMediaItem()) {

            player.seekToPrevious();

            player.play();
        }
    }


    // =========================================================
    // SHUFFLE ON/OFF
    // =========================================================

    public void setShuffle(boolean enabled) {

        if (player != null) {

            player.setShuffleModeEnabled(
                    enabled
            );
        }
    }


    // =========================================================
    // CHECK SHUFFLE
    // =========================================================

    public boolean isShuffleEnabled() {

        if (player != null) {

            return player
                    .getShuffleModeEnabled();
        }

        return false;
    }


    // =========================================================
    // REPEAT
    // =========================================================

    public void setRepeatMode(int repeatMode) {

        if (player != null) {

            player.setRepeatMode(
                    repeatMode
            );
        }
    }


    // =========================================================
    // GET PLAYER
    // =========================================================

    public ExoPlayer getPlayer() {

        return player;
    }


    // =========================================================
    // GET MEDIA SESSION
    // =========================================================

    public MediaSession getMediaSession() {

        return mediaSession;
    }


    // =========================================================
    // MEDIA SESSION CONNECTION
    // =========================================================

    @Nullable
    @Override
    public MediaSession onGetSession(
            MediaSession.ControllerInfo controllerInfo
    ) {

        return mediaSession;
    }


    // =========================================================
    // APP REMOVED FROM RECENTS
    // =========================================================

    @Override
    public void onTaskRemoved(
            @Nullable Intent rootIntent
    ) {

        /*
         * Kama music inaendelea,
         * service ibaki hai ili background
         * playback iendelee.
         */

        if (
                player != null &&
                player.isPlaying()
        ) {
            return;
        }


        super.onTaskRemoved(
                rootIntent
        );
    }


    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        if (mediaSession != null) {

            mediaSession.release();

            mediaSession = null;
        }


        if (player != null) {

            player.release();

            player = null;
        }


        super.onDestroy();
    }
    }
