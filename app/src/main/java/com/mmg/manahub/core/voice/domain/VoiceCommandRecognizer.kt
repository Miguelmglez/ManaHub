package com.mmg.manahub.core.voice.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VoiceCommandRecognizer {
    val commands: Flow<VoiceCommand>
    val isListening: StateFlow<Boolean>
    suspend fun start(
        enabledCommands: Set<VoiceCommand> = setOf(VoiceCommand.PlayLand, VoiceCommand.EndTurn),
        language: VoiceLanguage = VoiceLanguage.ENGLISH,
    )
    fun stop()
    fun release()
}
