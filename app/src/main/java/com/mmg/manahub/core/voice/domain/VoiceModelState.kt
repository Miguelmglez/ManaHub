package com.mmg.manahub.core.voice.domain

sealed interface VoiceModelState {
    data object NotDownloaded : VoiceModelState
    data class Downloading(val progress: Float) : VoiceModelState
    data object Ready : VoiceModelState
    data class Error(val message: String) : VoiceModelState
}
