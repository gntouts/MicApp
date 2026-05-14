package com.gntouts.micapp

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPassthrough {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(inputDevice: AudioDeviceInfo?, outputDevice: AudioDeviceInfo?) {
        if (isRunning) return

        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .build()

        inputDevice?.let { record.preferredDevice = it }

        // Suppress the speaker signal leaking back into the mic (feedback loop)
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        outputDevice?.let { track.preferredDevice = it }

        audioRecord = record
        audioTrack = track

        record.startRecording()
        track.play()

        job = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    track.write(buffer, 0, read)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioRecord?.stop()
            audioTrack?.stop()
        } catch (_: IllegalStateException) {
        }
        aec?.release()
        aec = null
        ns?.release()
        ns = null
        audioRecord?.release()
        audioRecord = null
        audioTrack?.release()
        audioTrack = null
    }
}
