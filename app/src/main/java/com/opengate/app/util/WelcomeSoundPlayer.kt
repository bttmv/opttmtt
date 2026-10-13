package com.opengate.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.opengate.app.R
import java.util.Locale

/**
 * مدير الصوت للشاشة الترحيبية:
 * 1. يشغّل نغمة هادئة رخيمة في الخلفية (welcome_ambient.wav).
 * 2. ينطق اسم التطبيق "أوبن جيت.. بوابتك المفتوحة للتجارة الحرة والمزادات" بصوت بشري جهوري رخيم
 *    عبر محرك TextToSpeech مع خفض نبرة الصوت (Pitch = 0.78) لإعطاء طابع جهوري مهيب وهادئ.
 */
class WelcomeSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isPlaying = false
    private var isMuted = false

    fun start(onSpeechDone: () -> Unit = {}) {
        if (isPlaying) return
        isPlaying = true

        // 1. تشغيل النغمة الهادئة في الخلفية
        runCatching {
            mediaPlayer = MediaPlayer.create(context, R.raw.welcome_ambient)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(0.55f, 0.55f)
                start()
            }
        }

        // 2. تهيئة ونطق اسم التطبيق بصوت بشري جهوري
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    // تحديد اللغة العربية إن كانت متوفرة على الجهاز
                    val arabicLocale = Locale("ar")
                    val result = engine.setLanguage(arabicLocale)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        // خفض حدة الصوت ليكون جهورياً ووقوراً (Deep resonant pitch)
                        engine.setPitch(0.78f)
                        // سرعة معتدلة ومريحة
                        engine.setSpeechRate(0.85f)

                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                onSpeechDone()
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                onSpeechDone()
                            }
                        })

                        if (!isMuted) {
                            val speechText = "أوبن جيت.. بوابتك المفتوحة للتجارة الحرة والمزادات"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                val params = Bundle().apply {
                                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                                }
                                engine.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "OPEN_GATE_WELCOME")
                            } else {
                                @Suppress("DEPRECATION")
                                engine.speak(speechText, TextToSpeech.QUEUE_FLUSH, null)
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            mediaPlayer?.setVolume(0f, 0f)
            tts?.stop()
        } else {
            mediaPlayer?.setVolume(0.55f, 0.55f)
        }
        return isMuted
    }

    fun stop() {
        isPlaying = false
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
