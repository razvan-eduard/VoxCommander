package com.voxcommander.app.service

import com.voxcommander.app.utils.Logger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify Web API helper.
 * Uses the access token from SpotifyPkceManager to control playback.
 * Requires Spotify Premium and an active device (Spotify app running on device).
 *
 * Key endpoints used:
 * - PUT /me/player/play — start/resume playback
 * - PUT /me/player/pause — pause playback
 * - POST /me/player/next — skip to next track
 * - GET /search — search for tracks/artists
 * - GET /me/player/devices — list available devices
 */
object SpotifyWebApi {

    private const val TAG = "SpotifyWebApi"
    private const val BASE_URL = "https://api.spotify.com/v1"

    /**
     * Searches for a track and plays it on the active device.
     * Falls back to transferring playback to an available device if none is active.
     *
     * @param clientId The Spotify Client ID (for token refresh)
     * @param query The search query (e.g. "artist name song name")
     * @return true if playback was started successfully
     */
    fun playSearch(clientId: String, query: String): Boolean {
        val token = SpotifyPkceManager.getValidAccessToken(clientId)
        if (token == null) {
            Logger.log("SpotifyWebApi: no valid access token", TAG)
            return false
        }

        // 1. Search for the track
        val trackUri = searchTrack(token, query)
        if (trackUri == null) {
            Logger.log("SpotifyWebApi: no track found for '$query'", TAG)
            return false
        }

        Logger.log("SpotifyWebApi: found track $trackUri, starting playback", TAG)

        // 2. Try to play on active device
        val played = startPlayback(token, trackUri)
        if (played) {
            Logger.log("SpotifyWebApi: playback started successfully", TAG)
            return true
        }

        // 3. No active device — try to find one and transfer playback
        Logger.log("SpotifyWebApi: no active device, searching for devices...", TAG)
        val deviceId = findAvailableDevice(token)
        if (deviceId != null) {
            Logger.log("SpotifyWebApi: found device $deviceId, transferring playback", TAG)
            if (transferPlayback(token, deviceId)) {
                // Retry play after transfer
                val retried = startPlayback(token, trackUri)
                if (retried) {
                    Logger.log("SpotifyWebApi: playback started after device transfer", TAG)
                    return true
                }
            }
        }

        Logger.log("SpotifyWebApi: could not start playback — no active device", TAG)
        return false
    }

    /**
     * Pauses playback on the active device.
     */
    fun pause(clientId: String): Boolean {
        val token = SpotifyPkceManager.getValidAccessToken(clientId) ?: return false
        return putRequest("$BASE_URL/me/player/pause", token, null)
    }

    /**
     * Resumes playback on the active device.
     */
    fun resume(clientId: String): Boolean {
        val token = SpotifyPkceManager.getValidAccessToken(clientId) ?: return false
        return putRequest("$BASE_URL/me/player/play", token, null)
    }

    /**
     * Skips to the next track.
     */
    fun nextTrack(clientId: String): Boolean {
        val token = SpotifyPkceManager.getValidAccessToken(clientId) ?: return false
        return postRequest("$BASE_URL/me/player/next", token)
    }

    /**
     * Searches for a track and returns its Spotify URI.
     */
    private fun searchTrack(token: String, query: String): String? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/search?q=$encodedQuery&type=track&limit=1"

        val response = getRequest(url, token) ?: return null
        val json = JSONObject(response)
        val tracks = json.optJSONObject("tracks") ?: return null
        val items = tracks.optJSONArray("items") ?: return null
        if (items.length() == 0) return null

        val track = items.getJSONObject(0)
        return track.getString("uri")
    }

    /**
     * Starts playback of a specific track URI on the active device.
     */
    private fun startPlayback(token: String, trackUri: String): Boolean {
        val body = JSONObject().put("uris", org.json.JSONArray().put(trackUri)).toString()
        return putRequest("$BASE_URL/me/player/play", token, body)
    }

    /**
     * Finds an available Spotify device (e.g. the phone running Spotify app).
     */
    private fun findAvailableDevice(token: String): String? {
        val response = getRequest("$BASE_URL/me/player/devices", token) ?: return null
        val json = JSONObject(response)
        val devices = json.optJSONArray("devices") ?: return null
        for (i in 0 until devices.length()) {
            val device = devices.getJSONObject(i)
            val id = device.optString("id", null) ?: continue
            // Prefer the "Computer" or "Smartphone" type, or any active device
            return id
        }
        return null
    }

    /**
     * Transfers playback to a specific device.
     */
    private fun transferPlayback(token: String, deviceId: String): Boolean {
        val body = JSONObject().put("device_ids", org.json.JSONArray().put(deviceId)).put("play", true).toString()
        return putRequest("$BASE_URL/me/player", token, body)
    }

    // --- HTTP Helpers ---

    private fun getRequest(url: String, token: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Logger.log("SpotifyWebApi GET $url failed: ${conn.responseCode}", TAG)
                null
            }
        } catch (e: Exception) {
            Logger.log("SpotifyWebApi GET exception: ${e.message}", TAG)
            null
        }
    }

    private fun putRequest(url: String, token: String, body: String?): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            Logger.log("SpotifyWebApi PUT $url response: $code", TAG)
            code in 200..299
        } catch (e: Exception) {
            Logger.log("SpotifyWebApi PUT exception: ${e.message}", TAG)
            false
        }
    }

    private fun postRequest(url: String, token: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.outputStream.use { it.write(ByteArray(0)) }
            val code = conn.responseCode
            Logger.log("SpotifyWebApi POST $url response: $code", TAG)
            code in 200..299
        } catch (e: Exception) {
            Logger.log("SpotifyWebApi POST exception: ${e.message}", TAG)
            false
        }
    }
}
