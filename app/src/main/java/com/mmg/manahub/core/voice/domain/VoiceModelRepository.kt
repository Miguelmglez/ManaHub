package com.mmg.manahub.core.voice.domain

import kotlinx.coroutines.flow.Flow
import java.io.File

interface VoiceModelRepository {
    fun observeState(): Flow<VoiceModelState>
    suspend fun download()
    fun modelDir(): File?
}
