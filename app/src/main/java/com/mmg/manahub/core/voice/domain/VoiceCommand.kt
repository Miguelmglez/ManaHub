package com.mmg.manahub.core.voice.domain

sealed interface VoiceCommand {
    data object PlayLand : VoiceCommand
    data object EndTurn : VoiceCommand
}
