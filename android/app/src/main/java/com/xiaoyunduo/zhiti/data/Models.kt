package com.xiaoyunduo.zhiti.data

import kotlinx.serialization.Serializable

@Serializable
data class ContentBlock(
    val type: String,
    val text: String = "",
    val asset: String = "",
    val alt: String = "",
)

data class Question(
    val qid: String,
    val module: String,
    val category: String,
    val year: Int?,
    val region: String,
    val paper: String,
    val title: String,
    val stem: List<ContentBlock>,
    val options: Map<String, List<ContentBlock>>,
    val answer: String,
    val explanation: List<ContentBlock>,
    val fastSolution: List<ContentBlock>,
    val reasoning: List<ContentBlock>,
    val pitfalls: List<ContentBlock>,
    val materialId: String?,
    val material: List<ContentBlock> = emptyList(),
)

@Serializable
data class Catalog(
    val schemaVersion: Int,
    val contentVersion: String,
    val upstreamCommit: String,
    val packs: List<ContentPack>,
)

@Serializable
data class ContentPack(
    val schemaVersion: Int,
    val contentVersion: String,
    val upstreamCommit: String,
    val packId: String,
    val questionCount: Int,
    val materialCount: Int,
    val assetCount: Int,
    val size: Long,
    val sha256: String,
    val downloadUrl: String,
)

@Serializable
data class SessionSnapshot(
    val type: String,
    val title: String,
    val qids: List<String>,
    val index: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val submittedQids: List<String> = emptyList(),
)
