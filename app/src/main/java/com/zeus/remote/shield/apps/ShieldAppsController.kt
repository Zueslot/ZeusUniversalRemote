package com.zeus.remote.shield.apps

import android.content.Context
import com.zeus.remote.data.FavoriteAppNames
import com.zeus.shared.CompanionLink
import com.zeus.shared.ShieldApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShieldAppsState(
    val apps: List<ShieldApp> = emptyList(), val connected: Boolean = false, val busy: Boolean = false,
    val launchAllowed: Boolean = false, val host: String = "", val favorites: Map<String, String> = emptyMap(),
    val message: String = "Install Zeus Shield Companion on your Shield, start it, then scan its QR code here.",
    val favoriteNames: Map<String, String> = emptyMap()
) {
    fun favoriteName(slot: String): String = favoriteNames[slot] ?: slot
}

class ShieldAppsController(context: Context, private val scope: CoroutineScope) : AutoCloseable {
    private val repository = ShieldAppsRepository(context.applicationContext)
    private val mutable = MutableStateFlow(ShieldAppsState())
    val state = mutable.asStateFlow()
    private var link: CompanionLink? = null
    private var job: Job? = null
    private var generation = 0
    private var favoriteJob: Job? = null

    fun refresh(address: String, qr: String? = null) {
        job?.cancel()
        val run = ++generation
        mutable.value = state.value.copy(busy = true, connected = false, host = address.trim(),
            favoriteNames = repository.favoriteNames(address.trim()), message = "Reading Shield apps...")
        job = scope.launch {
            try {
                val result = runInterruptible(Dispatchers.IO) {
                    val config = if (qr == null) repository.savedLink() else CompanionLink.parse(qr)
                    if (config == null) return@runInterruptible null
                    require(config.host == address.trim() || address.isBlank()) {
                        "QR code belongs to ${config.host}; set Shield IP to that address in Settings first."
                    }
                    val catalog = repository.fetch(config)
                    if (qr != null) repository.save(config)
                    val favorites = FavoriteAppNames.mapNotNull { name ->
                        val assigned = repository.assigned(config.host, name)
                        val app = matchFavorite(name, catalog.apps, assigned)
                        app?.let {
                            repository.assign(config.host, name, it.packageName, it.name)
                            name to it.packageName
                        }
                    }.toMap()
                    Triple(config, catalog, favorites)
                }
                if (generation != run) return@launch
                if (result == null) { link = null; mutable.value = ShieldAppsState(); return@launch }
                val (config, catalog, favorites) = result
                link = config
                mutable.value = ShieldAppsState(catalog.apps, true, false, catalog.launchAllowed, config.host, favorites,
                    if (catalog.launchAllowed) "${catalog.apps.size} installed apps. Tap an app to launch on Shield."
                    else "${catalog.apps.size} installed apps. Enable app launching in the Shield companion, then Refresh.",
                    repository.favoriteNames(config.host))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) {
                    link = null
                    mutable.value = state.value.copy(connected = false, busy = false, launchAllowed = false,
                        message = "Could not read Shield apps: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun launchApp(packageName: String) {
        val config = link ?: return
        if (state.value.busy || !state.value.launchAllowed || state.value.apps.none { it.packageName == packageName }) return
        val name = state.value.apps.first { it.packageName == packageName }.name
        val run = generation
        mutable.value = state.value.copy(busy = true, message = "Requesting $name on Shield...")
        job = scope.launch {
            try {
                runInterruptible(Dispatchers.IO) { repository.launch(config, packageName) }
                if (generation == run) mutable.value = state.value.copy(busy = false, message = "Launch requested: $name. Confirm on the TV.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) mutable.value = state.value.copy(busy = false, message = "Launch failed: ${e.message}")
            }
        }
    }

    fun assign(label: String, packageName: String) {
        val config = link ?: return
        if (label !in FavoriteAppNames) return
        val app = state.value.apps.find { it.packageName == packageName } ?: return
        repository.assign(config.host, label, packageName, app.name)
        mutable.value = state.value.copy(favorites = state.value.favorites + (label to packageName),
            favoriteNames = state.value.favoriteNames + (label to app.name))
    }
    fun launchFavorite(label: String, address: String) {
        if (label !in FavoriteAppNames || state.value.busy || favoriteJob?.isActive == true) return
        favoriteJob = scope.launch {
            if (!state.value.connected || !state.value.launchAllowed || label !in state.value.favorites) {
                refresh(address)
                job?.join()
            }
            val current = state.value
            val packageName = current.favorites[label]
            when {
                !current.connected -> mutable.value = current.copy(message = "Open Apps and reconnect the Shield companion. ${current.message}")
                !current.launchAllowed -> mutable.value = current.copy(message = "On Shield, open Zeus Shield Companion → Enable app launching, then try again.")
                packageName == null -> mutable.value = current.copy(message = "Open Apps → Set favorite to assign the installed app for $label.")
                else -> launchApp(packageName)
            }
        }
    }
    fun favoriteAvailable(label: String) = state.value.connected && !state.value.busy && state.value.launchAllowed && label in state.value.favorites
    fun forget() {
        job?.cancel(); generation++; link = null
        val run = generation
        job = scope.launch {
            try {
                runInterruptible(Dispatchers.IO) { repository.forget() }
                if (generation == run) mutable.value = ShieldAppsState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == run) mutable.value = ShieldAppsState(message = "Could not forget companion: ${e.message}")
            }
        }
    }
    override fun close() { favoriteJob?.cancel(); job?.cancel(); generation++ }
}
