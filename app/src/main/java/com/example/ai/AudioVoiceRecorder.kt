package com.example.ai

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Gravador leve de áudio AAC/MP4 em cache local para transcrição e conversa por voz
 * com o Gemini via Firebase AI Logic (multimodal `inlineData`).
 */
class AudioVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(): Result<Unit> {
        if (isRecording) return Result.success(Unit)
        return runCatching {
            val file = File(context.cacheDir, "invest_ai_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(16_000)
                setMaxDuration(60_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            outputFile = file
            isRecording = true
        }.onFailure {
            cancelRecording()
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val currentRecorder = recorder
        val currentFile = outputFile
        recorder = null
        outputFile = null
        isRecording = false

        if (currentRecorder == null || currentFile == null) {
            return Result.failure(IllegalStateException("Nenhuma gravação ativa."))
        }

        return runCatching {
            try {
                currentRecorder.stop()
            } finally {
                runCatching { currentRecorder.release() }
            }
            val bytes = if (currentFile.exists()) currentFile.readBytes() else ByteArray(0)
            runCatching { currentFile.delete() }
            if (bytes.isEmpty()) {
                throw IllegalStateException("Áudio vazio. Segure ou grave por pelo menos 1 segundo.")
            }
            if (bytes.size > MAX_AUDIO_BYTES) {
                throw IllegalStateException("Áudio excede o limite de 15 MB para envio ao Gemini.")
            }
            bytes
        }
    }

    fun cancelRecording() {
        val currentRecorder = recorder
        val currentFile = outputFile
        recorder = null
        outputFile = null
        isRecording = false
        if (currentRecorder != null) {
            runCatching { currentRecorder.stop() }
            runCatching { currentRecorder.release() }
        }
        if (currentFile != null) {
            runCatching { currentFile.delete() }
        }
    }

    companion object {
        const val MIME_TYPE_AUDIO_MP4 = "audio/mp4"
        private const val MAX_AUDIO_BYTES = 15 * 1024 * 1024
    }
}
