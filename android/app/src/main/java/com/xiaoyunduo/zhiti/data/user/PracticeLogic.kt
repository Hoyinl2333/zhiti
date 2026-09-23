package com.xiaoyunduo.zhiti.data.user

import com.xiaoyunduo.zhiti.data.SessionSnapshot

fun AnswerRecord?.recordAttempt(
    qid: String,
    module: String,
    selected: String,
    correct: Boolean,
    now: Long,
): AnswerRecord {
    val wasWrong = this?.everWrong == true || !correct
    return AnswerRecord(
        qid = qid,
        module = module,
        selected = selected,
        correct = correct,
        everWrong = wasWrong,
        corrected = correct && wasWrong,
        favorite = this?.favorite ?: false,
        attempts = (this?.attempts ?: 0) + 1,
        updatedAt = now,
    )
}

fun SessionSnapshot.recordSubmission(qid: String, correct: Boolean): SessionSnapshot {
    if (qid in submittedQids) return this
    return copy(
        correct = this.correct + if (correct) 1 else 0,
        answered = answered + 1,
        submittedQids = submittedQids + qid,
    )
}

