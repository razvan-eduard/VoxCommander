package com.voxcommander.app.domain.intent.taxonomy

/**
 * Single source of truth for intent domains, actions, and their valid parameters.
 * Used by prompt generation, intent validation, and intent handlers.
 */
object IntentTaxonomy {

    object Domains {
        const val AUDIO = "audio"
        const val SETTINGS = "settings"
        const val MAPS = "maps"
        const val MESSAGING = "messaging"
        const val SYSTEM = "system"
        const val HOME = "home"

        val ALL = listOf(AUDIO, SETTINGS, MAPS, MESSAGING, SYSTEM, HOME)
    }

    object Actions {
        // Audio
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val NEXT = "next"
        const val PREV = "prev"

        // Settings
        const val VOLUME_UP = "volume_up"
        const val VOLUME_DOWN = "volume_down"
        const val WIFI_TOGGLE = "wifi_toggle"
        const val BLUETOOTH_TOGGLE = "bluetooth_toggle"

        // Maps
        const val NAVIGATE = "navigate"

        // Messaging
        const val SEND = "send"

        val ALL = listOf(PLAY, PAUSE, NEXT, PREV, VOLUME_UP, VOLUME_DOWN,
            WIFI_TOGGLE, BLUETOOTH_TOGGLE, NAVIGATE, SEND)
    }

    /**
     * Maps legacy actionType values (from old IntentPayload / FastMapRule) to new domain+action pairs.
     * Used for backward compatibility with existing FastMap rules.
     */
    object LegacyMapper {
        data class Mapped(val domain: String, val action: String, val targetApp: String?)

        fun fromActionType(actionType: String): Mapped? = when (actionType) {
            "audio_youtube" -> Mapped(Domains.AUDIO, Actions.PLAY, "youtube")
            "audio_spotify" -> Mapped(Domains.AUDIO, Actions.PLAY, "spotify")
            "media_pause" -> Mapped(Domains.AUDIO, Actions.PAUSE, null)
            "media_play" -> Mapped(Domains.AUDIO, Actions.PLAY, null)
            "media_next" -> Mapped(Domains.AUDIO, Actions.NEXT, null)
            "media_prev" -> Mapped(Domains.AUDIO, Actions.PREV, null)
            "vol_up" -> Mapped(Domains.SETTINGS, Actions.VOLUME_UP, null)
            "vol_down" -> Mapped(Domains.SETTINGS, Actions.VOLUME_DOWN, null)
            "wifi_toggle" -> Mapped(Domains.SETTINGS, Actions.WIFI_TOGGLE, null)
            "bluetooth_toggle" -> Mapped(Domains.SETTINGS, Actions.BLUETOOTH_TOGGLE, null)
            "waze_nav" -> Mapped(Domains.MAPS, Actions.NAVIGATE, "waze")
            "maps_nav" -> Mapped(Domains.MAPS, Actions.NAVIGATE, "google maps")
            else -> null
        }
    }
}
