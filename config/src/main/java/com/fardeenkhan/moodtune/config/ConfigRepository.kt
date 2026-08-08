package com.fardeenkhan.moodtune.config

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ConfigRepository(
    private val remoteConfigManager: RemoteConfigManager,
    private val configDataStore: ConfigDataStore,
    private val connectivityChecker: ConnectivityChecker
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch { refresh() }
    }

    fun observeConfig(): Flow<AppConfig> = configDataStore.configFlow

    fun observeConnectivity(): Flow<Boolean> = connectivityChecker.isConnectedFlow

    /**
     * Returns a ready AppConfig before any API call.
     * - If cached keys are present → returns immediately
     * - If offline → throws NoInternetException
     * - If online but keys missing → refreshes from Remote Config, then waits up to 15s
     * - If keys still unavailable after refresh → throws ConfigNotAvailableException
     */
    suspend fun ensureConfig(hasKey: (AppConfig) -> Boolean): AppConfig {
        val current = configDataStore.configFlow.first()
        if (hasKey(current)) return current

        val isOnline = connectivityChecker.isConnectedFlow.first()
        if (!isOnline) throw NoInternetException()

        refresh()

        return try {
            withTimeout(15_000) {
                configDataStore.configFlow.first { hasKey(it) }
            }
        } catch (e: Exception) {
            throw ConfigNotAvailableException()
        }
    }

    suspend fun refresh() {
        try {
            val config = remoteConfigManager.fetchConfig()
            configDataStore.saveConfig(config)
        } catch (e: Exception) {
            Log.w(TAG, "Remote config fetch failed, using cached values", e)
        }
    }

    private companion object {
        const val TAG = "ConfigRepository"
    }


}
