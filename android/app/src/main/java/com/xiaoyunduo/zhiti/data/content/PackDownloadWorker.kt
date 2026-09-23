package com.xiaoyunduo.zhiti.data.content

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoyunduo.zhiti.R
import com.xiaoyunduo.zhiti.ZhitiApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import org.json.JSONObject
import java.util.zip.ZipInputStream

class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container = (context.applicationContext as ZhitiApplication).container

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val packId = inputData.getString(KEY_PACK_ID) ?: return@withContext Result.failure()
        val version = inputData.getString(KEY_VERSION) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val expectedHash = inputData.getString(KEY_SHA256) ?: return@withContext Result.failure()
        val expectedSize = inputData.getLong(KEY_SIZE, -1)
        val token = container.preferences.accessToken ?: return@withContext Result.failure()
        setForeground(foreground(packId, 0))
        val downloadDir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        val partial = File(downloadDir, "$packId-$version.zip.part")
        try {
            download(url, token, partial, expectedSize, packId)
            if (sha256(partial) != expectedHash.lowercase()) throw IllegalStateException("题库包摘要不匹配")
            install(packId, version, partial)
            container.preferences.setInstalled(packId, version)
            partial.delete()
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount >= 2) Result.failure(workDataOf("error" to (error.message ?: "download failed"))) else Result.retry()
        }
    }

    private suspend fun download(url: String, token: String, file: File, expectedSize: Long, packId: String) {
        var existing = if (file.isFile) file.length() else 0L
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()
        container.api.client.newCall(request).execute().use { response ->
            if (response.code == 200 && existing > 0) {
                file.delete(); existing = 0
            }
            if (response.code !in listOf(200, 206)) error("下载失败 ${response.code}")
            RandomAccessFile(file, "rw").use { output ->
                output.seek(existing)
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var total = existing
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        val progress = if (expectedSize > 0) ((total * 100) / expectedSize).toInt().coerceIn(0, 100) else 0
                        setProgress(Data.Builder().putInt(KEY_PROGRESS, progress).build())
                        setForeground(foreground(packId, progress))
                    }
                }
            }
        }
    }

    private fun install(packId: String, version: String, archive: File) {
        val contentRoot = File(applicationContext.filesDir, "content").apply { mkdirs() }
        val staging = File(contentRoot, ".$packId-$version-staging")
        val target = File(contentRoot, packId)
        val backup = File(contentRoot, ".$packId-backup")
        staging.deleteRecursively(); staging.mkdirs()
        val stagingRoot = staging.canonicalPath + File.separator
        var extractedBytes = 0L
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(staging, entry.name)
                if (!output.canonicalPath.startsWith(stagingRoot)) error("题库包路径无效")
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { extractedBytes += zip.copyTo(it) }
                    if (extractedBytes > 1024L * 1024L * 1024L) error("题库包过大")
                }
                zip.closeEntry()
            }
        }
        check(File(staging, "content.sqlite").isFile && File(staging, "pack.json").isFile)
        val metadata = JSONObject(File(staging, "pack.json").readText())
        check(metadata.getString("packId") == packId && metadata.getString("contentVersion") == version)
        check(metadata.getInt("schemaVersion") == 1)
        backup.deleteRecursively()
        if (target.exists() && !target.renameTo(backup)) error("无法备份旧题库")
        if (!staging.renameTo(target)) {
            backup.renameTo(target)
            error("无法安装新题库")
        }
        backup.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun foreground(packId: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "题库下载", NotificationManager.IMPORTANCE_LOW))
        val name = if (packId == "judgment") "判断推理" else "资料分析"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("正在下载$name")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true).build()
        return ForegroundInfo(packId.hashCode(), notification)
    }

    companion object {
        const val KEY_PACK_ID = "pack_id"
        const val KEY_VERSION = "version"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_PROGRESS = "progress"
        const val CHANNEL = "content-downloads"
    }
}
