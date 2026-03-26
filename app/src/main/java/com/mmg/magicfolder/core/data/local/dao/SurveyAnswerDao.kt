package com.mmg.magicfolder.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mmg.magicfolder.core.data.local.entity.SurveyAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyAnswerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: SurveyAnswerEntity)

    @Query("SELECT * FROM survey_answers WHERE sessionId = :sessionId ORDER BY answeredAt ASC")
    fun observeAnswers(sessionId: Long): Flow<List<SurveyAnswerEntity>>

    @Query("DELETE FROM survey_answers WHERE sessionId = :sessionId")
    suspend fun deleteAnswersForSession(sessionId: Long)
}
