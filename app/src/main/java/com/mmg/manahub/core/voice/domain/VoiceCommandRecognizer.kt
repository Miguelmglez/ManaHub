package com.mmg.manahub.core.voice.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VoiceCommandRecognizer {
    val commands: Flow<VoiceCommand>
    val isListening: StateFlow<Boolean>
    suspend fun start(
        enabledCommands: Set<VoiceCommand> = setOf(VoiceCommand.PlayLand, VoiceCommand.EndTurn),
        enabledLanguages: Set<VoiceLanguage> = VoiceLanguage.entries.toSet(),
    )
    fun stop()
    fun release()
}
