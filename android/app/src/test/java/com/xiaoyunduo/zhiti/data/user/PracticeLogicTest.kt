package com.xiaoyunduo.zhiti.data.user

import com.xiaoyunduo.zhiti.data.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLogicTest {
    @Test
    fun wrongAnswerThenCorrectMarksCorrectedAndKeepsHistory() {
        val wrong = null.recordAttempt("1", "judgment", "A", false, 1)
        val corrected = wrong.recordAttempt("1", "judgment", "B", true, 2)
        assertTrue(corrected.everWrong)
        assertTrue(corrected.corrected)
        assertEquals(2, corrected.attempts)
    }

    @Test
    fun answeringWrongAgainRemovesCorrectedState() {
        val corrected = AnswerRecord("1", "judgment", "B", true, true, true, false, 2, 2)
        val wrongAgain = corrected.recordAttempt("1", "judgment", "A", false, 3)
        assertFalse(wrongAgain.corrected)
        assertTrue(wrongAgain.everWrong)
    }

    @Test
    fun repeatedSubmissionDoesNotScoreTwice() {
        val session = SessionSnapshot("judgment", "逻辑判断", listOf("1"))
        val once = session.recordSubmission("1", true)
        val twice = once.recordSubmission("1", true)
        assertEquals(1, twice.answered)
        assertEquals(1, twice.correct)
    }

    @Test
    fun favoriteSurvivesAnAttempt() {
        val favorite = AnswerRecord("1", "judgment", "", false, false, false, true, 0, 1)
        assertTrue(favorite.recordAttempt("1", "judgment", "D", true, 2).favorite)
    }
}
