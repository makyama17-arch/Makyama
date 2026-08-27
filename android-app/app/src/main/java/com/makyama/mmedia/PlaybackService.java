ackage com.makyama.mmedia;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

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

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build();
    }

    /**
     * MainActivity inaweza kuita hii kuanza wimbo.
     */
    public void playTrack(
            String url,
            String title,
            String artist,
            String artwork
    ) {

        if (url == null || url.trim().isEmpty()) {
            return;
        }

        MediaMetadata.Builder metadata =
                new MediaMetadata.Builder()
                        .setTitle(title == null ? "Unknown" : title)
                        .setArtist(artist == null ? "MAKYAMA" : artist);

        if (artwork != null && !artwork.trim().isEmpty()) {
            metadata.setArtworkUri(
                    android.net.Uri.parse(artwork)
            );
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

        if (url == null || url.trim().isEmpty()) {
            return;
        }

        player.setMediaItem(
                MediaItem.fromUri(url)
        );

        player.prepare();
        player.play();
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
    public void onTaskRemoved(@Nullable Intent rootIntent) {

        /*
         * Tunataka muziki uendelee hata Activity
         * ikiondolewa kwenye recent apps.
         */
        if (player != null && player.isPlaying()) {
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
