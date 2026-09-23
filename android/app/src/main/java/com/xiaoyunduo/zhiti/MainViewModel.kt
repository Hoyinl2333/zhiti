package com.xiaoyunduo.zhiti

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xiaoyunduo.zhiti.data.Catalog
import com.xiaoyunduo.zhiti.data.ContentPack
import com.xiaoyunduo.zhiti.data.Question
import com.xiaoyunduo.zhiti.data.SessionSnapshot
import com.xiaoyunduo.zhiti.data.api.CatalogVerifier
import com.xiaoyunduo.zhiti.data.content.PackDownloadWorker
import com.xiaoyunduo.zhiti.data.user.AnswerRecord
import com.xiaoyunduo.zhiti.data.user.recordAttempt
import com.xiaoyunduo.zhiti.data.user.recordSubmission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

enum class Page { ACTIVATION, DOWNLOADS, HOME, PRACTICE, SUMMARY, COLLECTION }
enum class CollectionType { WRONG, FAVORITES }

data class UiState(
    val page: Page = Page.ACTIVATION,
    val busy: Boolean = true,
    val error: String? = null,
    val catalog: Catalog? = null,
    val downloads: Map<String, Int> = emptyMap(),
    val installed: Set<String> = emptySet(),
    val installedVersions: Map<String, String> = emptyMap(),
    val categories: Map<String, Int> = emptyMap(),
    val answeredCount: Int = 0,
    val wrongCount: Int = 0,
    val favoriteCount: Int = 0,
    val session: SessionSnapshot? = null,
    val question: Question? = null,
    val selected: String? = null,
    val submitted: Boolean = false,
    val favorite: Boolean = false,
    val collectionType: CollectionType? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZhitiApplication).container
    private val verifier = CatalogVerifier(application)
    private val workManager = WorkManager.getInstance(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        observeCounts()
        viewModelScope.launch { start() }
    }

    private fun observeCounts() {
        viewModelScope.launch { container.answers.answeredCount().collect { value -> _state.update { it.copy(answeredCount = value) } } }
        viewModelScope.launch { container.answers.wrongCount().collect { value -> _state.update { it.copy(wrongCount = value) } } }
        viewModelScope.launch { container.answers.favoriteCount().collect { value -> _state.update { it.copy(favoriteCount = value) } } }
    }

    private suspend fun start() {
        val installed = installedPacks()
        _state.update { it.copy(installed = installed, installedVersions = installedVersions()) }
        val token = container.preferences.accessToken
        if (token == null) {
            _state.update { it.copy(page = Page.ACTIVATION, busy = false) }
            return
        }
        if (installed.isNotEmpty()) {
            loadHome()
            refreshCatalog(token, stayOnHome = true)
            container.preferences.currentSession?.let { resume(it) }
        } else {
            refreshCatalog(token, stayOnHome = false)
        }
    }

    fun activate(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.api.activate(code, deviceDigest(getApplication())) }
                .onSuccess { token ->
                    container.preferences.accessToken = token
                    refreshCatalog(token, stayOnHome = false)
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = error.message ?: "激活失败") } }
        }
    }

    private suspend fun refreshCatalog(token: String, stayOnHome: Boolean) {
        runCatching { container.api.catalog(token, verifier::verify) }
            .onSuccess { catalog ->
                _state.update {
                    it.copy(catalog = catalog, page = if (stayOnHome) it.page else Page.DOWNLOADS, busy = false, error = null)
                }
            }
            .onFailure { error ->
                if (stayOnHome) _state.update { it.copy(busy = false) }
                else _state.update { it.copy(page = Page.DOWNLOADS, busy = false, error = error.message ?: "无法读取题库目录") }
            }
    }

    fun download(pack: ContentPack) {
        val request = OneTimeWorkRequestBuilder<PackDownloadWorker>().setInputData(
            workDataOf(
                PackDownloadWorker.KEY_PACK_ID to pack.packId,
                PackDownloadWorker.KEY_VERSION to pack.contentVersion,
                PackDownloadWorker.KEY_URL to pack.downloadUrl,
                PackDownloadWorker.KEY_SHA256 to pack.sha256,
                PackDownloadWorker.KEY_SIZE to pack.size,
            )
        ).build()
        workManager.enqueueUniqueWork("pack-${pack.packId}", ExistingWorkPolicy.KEEP, request)
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info == null) return@collect
                val progress = info.progress.getInt(PackDownloadWorker.KEY_PROGRESS, 0)
                _state.update { it.copy(downloads = it.downloads + (pack.packId to progress)) }
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    val installed = installedPacks()
                    _state.update { it.copy(installed = installed, installedVersions = installedVersions(), downloads = it.downloads - pack.packId) }
                    loadHome()
                } else if (info.state == WorkInfo.State.FAILED || info.state == WorkInfo.State.CANCELLED) {
                    _state.update { it.copy(downloads = it.downloads - pack.packId, error = "题库下载失败") }
                }
            }
        }
    }

    fun openDownloads() { _state.update { it.copy(page = Page.DOWNLOADS, error = null) } }

    fun openHome() {
        viewModelScope.launch { loadHome() }
    }

    private suspend fun loadHome() {
        val categories = if (container.content.isInstalled("judgment")) container.content.categories("judgment") else emptyMap()
        _state.update { it.copy(page = Page.HOME, busy = false, categories = categories, installed = installedPacks(), installedVersions = installedVersions(), error = null) }
    }

    fun continueSession() {
        container.preferences.currentSession?.let { snapshot -> viewModelScope.launch { resume(snapshot) } }
    }

    fun startJudgment(category: String) {
        viewModelScope.launch {
            val attempted = container.answers.attemptedIds().toSet()
            var qids = container.content.chooseJudgment(category, attempted)
            if (qids.isEmpty()) qids = container.content.chooseJudgment(category, emptySet())
            begin(SessionSnapshot("judgment", category, qids))
        }
    }

    fun startDataAnalysis() {
        viewModelScope.launch {
            val attempted = container.answers.attemptedIds().toSet()
            var qids = container.content.chooseMaterial(attempted)
            if (qids.isEmpty()) qids = container.content.chooseMaterial(emptySet())
            begin(SessionSnapshot("data-analysis", "资料分析", qids))
        }
    }

    fun openCollection(type: CollectionType) {
        viewModelScope.launch {
            val qids = if (type == CollectionType.WRONG) container.answers.wrongIds() else container.answers.favoriteIds()
            if (qids.isEmpty()) {
                _state.update { it.copy(page = Page.COLLECTION, collectionType = type, session = null, question = null) }
            } else {
                begin(SessionSnapshot(if (type == CollectionType.WRONG) "wrong" else "favorites", if (type == CollectionType.WRONG) "错题" else "收藏", qids), type)
            }
        }
    }

    private suspend fun begin(snapshot: SessionSnapshot, collection: CollectionType? = null) {
        if (snapshot.qids.isEmpty()) return
        container.preferences.currentSession = snapshot
        val question = container.content.question(snapshot.qids[snapshot.index]) ?: return
        val record = container.answers.get(question.qid)
        val alreadySubmitted = question.qid in snapshot.submittedQids
        _state.update {
            it.copy(page = Page.PRACTICE, session = snapshot, question = question,
                selected = if (alreadySubmitted) record?.selected else null, submitted = alreadySubmitted,
                favorite = record?.favorite == true, collectionType = collection, error = null)
        }
    }

    private suspend fun resume(snapshot: SessionSnapshot) {
        if (snapshot.qids.isEmpty() || snapshot.index !in snapshot.qids.indices) return
        begin(snapshot, when (snapshot.type) { "wrong" -> CollectionType.WRONG; "favorites" -> CollectionType.FAVORITES; else -> null })
    }

    fun selectAnswer(value: String) {
        if (!_state.value.submitted) _state.update { it.copy(selected = value) }
    }

    fun submit() {
        val current = _state.value
        val question = current.question ?: return
        val selected = current.selected ?: return
        if (current.submitted) return
        viewModelScope.launch {
            val old = container.answers.get(question.qid)
            val isCorrect = selected == question.answer
            container.answers.upsert(old.recordAttempt(question.qid, question.module, selected, isCorrect, System.currentTimeMillis()))
            val session = current.session?.recordSubmission(question.qid, isCorrect)
            if (session != null) container.preferences.currentSession = session
            _state.update { it.copy(submitted = true, session = session) }
        }
    }

    fun next() {
        val current = _state.value
        val session = current.session ?: return
        if (!current.submitted) return
        viewModelScope.launch {
            if (session.index >= session.qids.lastIndex) {
                container.preferences.currentSession = null
                _state.update { it.copy(page = Page.SUMMARY, question = null, selected = null, submitted = false) }
            } else {
                val next = session.copy(index = session.index + 1)
                container.preferences.currentSession = next
                begin(next, current.collectionType)
            }
        }
    }

    fun toggleFavorite() {
        val question = _state.value.question ?: return
        viewModelScope.launch {
            val old = container.answers.get(question.qid)
            val favorite = !(old?.favorite ?: false)
            container.answers.upsert(
                old?.copy(favorite = favorite, updatedAt = System.currentTimeMillis())
                    ?: AnswerRecord(question.qid, question.module, "", false, false, false, favorite, 0, System.currentTimeMillis())
            )
            _state.update { it.copy(favorite = favorite) }
        }
    }

    fun abandonSession() {
        container.preferences.currentSession = null
        openHome()
    }

    fun clearRecords() {
        viewModelScope.launch {
            container.answers.clear()
            container.preferences.currentSession = null
            loadHome()
        }
    }

    private fun installedPacks(): Set<String> = setOf("judgment", "data-analysis").filter(container.content::isInstalled).toSet()

    private fun installedVersions(): Map<String, String> = setOf("judgment", "data-analysis").mapNotNull { packId ->
        container.preferences.installedVersion(packId)?.let { packId to it }
    }.toMap()

    private fun deviceDigest(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val signatures = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.signingInfo?.apkContentsSigners.orEmpty().joinToString { it.toCharsString() }
        return MessageDigest.getInstance("SHA-256").digest("$androidId\u0000$signatures".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
