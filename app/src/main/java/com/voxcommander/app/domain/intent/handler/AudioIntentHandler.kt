package com.voxcommander.app.domain.intent.handler

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.net.Uri
import android.view.KeyEvent
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.domain.intent.registry.AppRegistry
import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy
import com.voxcommander.app.utils.Logger

/**
 * Handles audio domain intents: play, pause, next, prev.
 * Uses URI templates from AppEntry for generic search-based playback.
 * Supports any app registered in AppRegistry (Spotify, YouTube, LibreTube, VLC, etc.).
 */
class AudioIntentHandler : IntentHandler {

    override fun canHandle(intent: NluIntent): Boolean {
        return intent.domain == IntentTaxonomy.Domains.AUDIO
    }

    override fun execute(context: Context, intent: NluIntent, resolvedApp: AppRegistry.AppEntry?): Boolean {
        return when (intent.action) {
            IntentTaxonomy.Actions.PLAY -> play(context, intent, resolvedApp)
            IntentTaxonomy.Actions.PAUSE -> sendMediaKey(context, resolvedApp, "pause")
            IntentTaxonomy.Actions.NEXT -> sendMediaKey(context, resolvedApp, "next")
            IntentTaxonomy.Actions.PREV -> sendMediaKey(context, resolvedApp, "prev")
            else -> {
                Logger.log("Unsupported audio action: ${intent.action}", TAG)
                false
            }
        }
    }

    private fun play(context: Context, intent: NluIntent, resolvedApp: AppRegistry.AppEntry?): Boolean {
        val artist = intent.param(NluIntent.PARAM_ARTIST)
        val track = intent.param(NluIntent.PARAM_TRACK)
        val query = intent.param(NluIntent.PARAM_QUERY)

        // Build search query from available parameters
        val searchQuery = listOfNotNull(artist, track, query)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (searchQuery.isNotBlank()) {
            return playSearch(context, intent, resolvedApp, searchQuery)
        }

        // No specific track — just launch the app in play mode
        return launchAppPlay(context, resolvedApp)
    }

    /**
     * Generic search-based playback using URI templates.
     * Priority: intent.uriTemplate (from FastMap rule) > resolvedApp.uriTemplates > launch intent.
     */
    private fun playSearch(context: Context, intent: NluIntent, resolvedApp: AppRegistry.AppEntry?, query: String): Boolean {
        val pkg = resolvedApp?.packageName

        // 1. Try MEDIA_PLAY_FROM_SEARCH (standard Android, plays directly)
        if (pkg != null) {
            val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)
                putExtra(android.app.SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (tryLaunch(context, playIntent)) return true
        }

        // 2. Use URI template: intent.uriTemplate first, then resolvedApp.uriTemplates
        val searchTemplate = intent.uriTemplate ?: resolvedApp?.uriTemplates?.get(AppRegistry.TemplateActions.SEARCH)
        if (searchTemplate != null) {
            val uri = searchTemplate.replace(AppRegistry.TemplateParams.QUERY, Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
                if (pkg != null) setPackage(pkg)
                putExtra(Intent.EXTRA_REFERRER, "android-app://com.voxcommander.app")
            }
            if (tryLaunch(context, intent)) return true
        }

        // No template or template failed — try launching the app directly
        if (pkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (tryLaunch(context, launchIntent)) return true
            }
        }

        // Fallback: open market search
        return tryLaunch(context, Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://search?q=${Uri.encode(query)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    /**
     * Launches the app with a "play" media button simulation.
     */
    private fun launchAppPlay(context: Context, resolvedApp: AppRegistry.AppEntry?): Boolean {
        val pkg = resolvedApp?.packageName

        if (pkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (tryLaunch(context, launchIntent)) {
                    // Also send a play media key to start playback
                    sendMediaKey(context, resolvedApp, "play")
                    return true
                }
            }
        }

        // Fallback: send play media key globally
        return sendMediaKey(context, null, "play")
    }

    /**
     * Sends a media key event (play/pause/next/prev) via broadcast intent.
     * Falls back to AudioManager.dispatchMediaKeyEvent if no specific package.
     */
    private fun sendMediaKey(context: Context, resolvedApp: AppRegistry.AppEntry?, action: String): Boolean {
        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }

        val pkg = resolvedApp?.packageName

        // Try to send to specific package first
        if (pkg != null) {
            val intent = Intent("com.android.intent.action.MEDIA_BUTTON").apply {
                setPackage(pkg)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.sendBroadcast(intent)
                intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                context.sendBroadcast(intent)
                Logger.log("Sent media key $action to $pkg", TAG)
                return true
            } catch (e: Exception) {
                Logger.log("Failed to send media key to $pkg: ${e.message}", TAG)
            }
        }

        // Fallback: send to audio service globally
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Logger.log("Sent media key $action via AudioManager", TAG)
            true
        } catch (e: Exception) {
            Logger.log("Failed to dispatch media key: ${e.message}", TAG)
            false
        }
    }

    private fun tryLaunch(context: Context, intent: Intent): Boolean {
        return try {
            intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.log("Failed to launch intent: ${e.message}", TAG)
            false
        }
    }

    companion object {
        private const val TAG = "AudioIntentHandler"
    }
}
