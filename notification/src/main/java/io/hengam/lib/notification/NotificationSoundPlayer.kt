package io.hengam.lib.notification

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import java.util.concurrent.TimeUnit

class NotificationSoundPlayer(
        private val url: String,
        private val maxSoundDuration: Time
) {

    @Throws(NotificationBuildException::class)
    fun play(): Completable {
        val mediaPlayer = MediaPlayer()

        val completable =  Completable.create { emitter ->
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                mediaPlayer.setAudioAttributes(audioAttributes)
            }

            mediaPlayer.setDataSource(url)

            mediaPlayer.setOnPreparedListener {
                if (!emitter.isDisposed) {
                    mediaPlayer.start()
                    emitter.onComplete()
                }
            }

            mediaPlayer.setOnErrorListener { _, what, extra ->
                emitter.tryOnError(NotificationSoundException("Preparing notification sound failed with error code $what:$extra"))
                true
            }


            mediaPlayer.prepareAsync()
        }

        return completable
                .doOnComplete {
                   Completable.timer(maxSoundDuration.toMillis(), TimeUnit.MILLISECONDS, cpuThread())
                           .justDo(T_NOTIF) {
                               if (mediaPlayer.isPlaying) {
                                   mediaPlayer.stop()
                               }
                           }
                }
    }
}

class NotificationSoundException(message: String, cause: Throwable? = null) : Exception(message, cause)