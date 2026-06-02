package com.mmg.manahub.core.voice.data

import android.util.Log
import com.mmg.manahub.core.voice.domain.CommandGrammar
import com.mmg.manahub.core.voice.domain.VoiceCommand
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import javax.inject.Inject

class VoskVoiceRecognizer @Inject constructor(
    private val modelRepository: VoiceModelRepository,
) : VoiceCommandRecognizer {

    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 1)
    override val commands: Flow<VoiceCommand> = _commands.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    // 2-second cooldown prevents duplicate emissions on back-to-back Vosk finals
    private var lastCommandTimeMs = 0L
    private val cooldownMs = 2_000L

    // Languages currently active for the running session — used to scope phrase matching.
    private var enabledLanguages: Set<VoiceLanguage> = emptySet()

    override suspend fun start(
        enabledCommands: Set<VoiceCommand>,
        enabledLanguages: Set<VoiceLanguage>,
    ) {
        if (_isListening.value) return
        val dir = modelRepository.modelDir() ?: return

        this.enabledLanguages = enabledLanguages

        try {
            val m = withContext(Dispatchers.IO) { Model(dir.absolutePath) }
            val r = withContext(Dispatchers.IO) { Recognizer(m, 16000f, CommandGrammar.grammarJson(enabledCommands, enabledLanguages)) }
            model = m
            recognizer = r
            val service = SpeechService(r, 16000f)
            service.startListening(recognitionListener)
            speechService = service
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("VoskVoiceRecognizer", "Failed to start recognition", e)
            releaseResources()
        }
    }

    override fun stop() {
        speechService?.stop()
        _isListening.value = false
    }

    override fun release() {
        stop()
        releaseResources()
    }

    private fun releaseResources() {
        runCatching { speechService?.shutdown() }
        runCatching { recognizer?.close() }
        runCatching { model?.close() }
        speechService = null
        recognizer = null
        model = null
        _isListening.value = false
    }

    private fun maybeEmit(hypothesis: String?) {
        hypothesis ?: return
        val text = runCatching { JSONObject(hypothesis).getString("text") }.getOrNull() ?: return
        if (text.isBlank() || text == "[unk]") return
        val command = CommandGrammar.match(text, enabledLanguages) ?: return
        val now = System.currentTimeMillis()
        if (now - lastCommandTimeMs < cooldownMs) return
        lastCommandTimeMs = now
        _commands.tryEmit(command)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {}

        override fun onResult(hypothesis: String?) {
            maybeEmit(hypothesis)
        }

        override fun onFinalResult(hypothesis: String?) {
            maybeEmit(hypothesis)
        }

        override fun onError(e: Exception?) {
            Log.e("VoskVoiceRecognizer", "Recognition error", e)
            // SpeechService self-recovers; no restart needed unlike native SpeechRecognizer
        }

        override fun onTimeout() {}
    }
}
