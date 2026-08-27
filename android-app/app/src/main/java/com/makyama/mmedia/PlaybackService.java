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

    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        Intent intent = new Intent(this, MainActivity.class);

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

        mediaSession = new MediaSession.Builder(
                this,
                player
        )
                .setSessionActivity(pendingIntent)
                .build();
    }

    public void playTrack(
            String url,
            String title,
            String artist,
            String artwork
    ) {
        if (player == null ||
                url == null ||
                url.trim().isEmpty()) {
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

        if (artwork != null &&
                !artwork.trim().isEmpty()) {

            try {
                metadata.setArtworkUri(
                        Uri.parse(artwork)
                );
            } catch (Exception ignored) {
            }
        }

        MediaItem item =
                new MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(metadata.build())
                        .build();

        player.setMediaItem(item);
        player.prepare();
        player.play();
    }

    public void playUrl(String url) {
        if (player == null ||
                url == null ||
                url.trim().isEmpty()) {
            return;
        }

        player.setMediaItem(
                MediaItem.fromUri(url)
        );

        player.prepare();
        player.play();
    }

    public void addTrack(
            String url,
            String title,
            String artist,
            String artwork
    ) {
        if (player == null ||
                url == null ||
                url.trim().isEmpty()) {
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

        if (artwork != null &&
                !artwork.trim().isEmpty()) {

            try {
                metadata.setArtworkUri(
                        Uri.parse(artwork)
                );
            } catch (Exception ignored) {
            }
        }

        MediaItem item =
                new MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(metadata.build())
                        .build();

        player.addMediaItem(item);
    }

    public void playIndex(int index) {
        if (player == null) {
            return;
        }

        if (index < 0 ||
                index >= player.getMediaItemCount()) {
            return;
        }

        player.seekToDefaultPosition(index);
        player.prepare();
        player.play();
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    public void resume() {
        if (player != null) {
            player.play();
        }
    }

    public void stop() {
        if (player != null) {
            player.stop();
        }
    }

    public void next() {
        if (player != null &&
                player.hasNextMediaItem()) {

            player.seekToNext();
            player.play();
        }
    }

    public void previous() {
        if (player != null &&
                player.hasPreviousMediaItem()) {

            player.seekToPrevious();
            player.play();
        }
    }

    public void setShuffle(boolean enabled) {
        if (player != null) {
            player.setShuffleModeEnabled(enabled);
        }
    }

    public boolean isShuffleEnabled() {
        return player != null &&
                player.getShuffleModeEnabled();
    }

    public void setRepeatMode(int repeatMode) {
        if (player != null) {
            player.setRepeatMode(repeatMode);
        }
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public MediaSession getMediaSession() {
        return mediaSession;
    }

    @Nullable
    @Override
    public MediaSession onGetSession(
            MediaSession.ControllerInfo controllerInfo
    ) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(
            @Nullable Intent rootIntent
    ) {

        if (player != null &&
                player.isPlaying()) {
            return;
        }

        super.onTaskRemoved(rootIntent);
    }

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
