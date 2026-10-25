package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class CloudStreamImportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("cloudstream_import_system", Context.MODE_PRIVATE)
        val base = prefs.getString("project_url", "").orEmpty().trimEnd('/')
        val key = prefs.getString("anon_key", "").orEmpty()
        val refreshToken = prefs.getString("refresh_token", "").orEmpty()
        val names = prefs.getStringSet("providers", emptySet()).orEmpty()
        if (base.isBlank() || key.isBlank() || refreshToken.isBlank() || names.isEmpty()) return@withContext Result.failure()
        try {
            val auth = importHttp(
                "POST",
                "$base/auth/v1/token?grant_type=refresh_token",
                key,
                null,
                JSONObject().put("refresh_token", refreshToken)
            )
            val token = auth.optString("access_token")
            val newRefresh = auth.optString("refresh_token", refreshToken)
            val userId = auth.optJSONObject("user")?.optString("id").orEmpty()
            if (token.isBlank() || userId.isBlank()) return@withContext Result.retry()
            prefs.edit().putString("refresh_token", newRefresh).apply()
            val providers = APIHolder.apis.filter { it.name in names }
            if (providers.isEmpty()) return@withContext Result.retry()
            val from = prefs.getInt("from_year", 2025)
            val to = prefs.getInt("to_year", 2026)
            val categories = prefs.getStringSet("categories", setOf("anime", "movie", "series")).orEmpty()
            importCloudStreamProviders(providers, base, key, token, userId, from, to, categories)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cloudstream_supabase_daily_import"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequest.Builder(
                CloudStreamImportWorker::class.java,
                24,
                TimeUnit.HOURS
            ).setConstraints(constraints).addTag(WORK_NAME).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
