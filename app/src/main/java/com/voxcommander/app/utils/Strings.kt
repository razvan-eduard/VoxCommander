package com.voxcommander.app.utils

object Strings {
    object Tags {
        const val VOX_COMMANDER = "VoxCommander"
        const val GOOGLE_STT_ENGINE = "GoogleSttEngine"
        const val VOICE_MANAGER = "VoiceManager"
        const val WHISPER_LIB = "WhisperLib"
        const val WHISPER_CMAKE_STT_ENGINE = "WhisperCmakeSttEngine"
        const val VULKAN_PROBE = "VulkanProbe"
        const val REMOTE_MODEL_REGISTRY = "RemoteModelRegistry"
        const val WAKE_WORD_ENGINE = "WakeWordEngine"
        const val WAKE_WORD_SERVICE = "WakeWordService"
        const val INTENT_DECISION_MAP = "IntentDecisionMap"
        const val WHISPER_CPP_STT_ENGINE = "WhisperCppSttEngine"
        const val AI_INTERPRETER = "AiInterpreter"
        const val GEMINI_NANO_INTERPRETER = "GeminiNanoInterpreter"
        const val OPENAI_INTERPRETER = "OpenAiInterpreter"
        const val LOCAL_LLM_INTERPRETER = "LocalLlmInterpreter"
    }

    object Processors {
        const val WHISPER_VULKAN = "WHISPER_VULKAN"
        const val WHISPER_NEON = "WHISPER_NEON"
        const val WHISPER_CPP = "WHISPER_CPP"
        const val VOSK = "VOSK"
        const val GOOGLE = "GOOGLE"
        const val WHISPER_API = "WHISPER_API"
        const val WHISPER_CLOUD = "WHISPER_CLOUD"
    }

    object AiProcessors {
        const val OPENAI = "OPENAI"
        const val NLU_LOCAL = "NLU_LOCAL"
        const val GEMINI_NATIVE = "GEMINI_NATIVE"
    }

    object Routes {
        const val MAIN = "main"
        const val SETTINGS = "settings"
        const val RULES = "rules"
    }

    object Preferences {
        const val PREFS_NAME = "vox_commander_settings"
        const val DEFAULT_LANGUAGE = "en"
        const val DEFAULT_PROCESSOR = Processors.WHISPER_CPP
        const val DEFAULT_WHISPER_MODEL = "tiny"
        const val KEY_API_KEY = "openai_api_key"
        const val KEY_LANGUAGE = "current_language"
        const val KEY_VOICE_LANGUAGE = "voice_language"
        const val KEY_VOICE_PROCESSOR = "voice_processor"
        const val KEY_CUSTOM_VOSK_MODEL_PATH = "custom_vosk_model_path"
        const val KEY_CUSTOM_WHISPER_MODEL_PATH = "custom_whisper_model_path"
        const val KEY_SELECTED_WHISPER_MODEL_ID = "selected_whisper_model_id"
        const val KEY_SELECTED_VOSK_MODEL_NAME = "selected_vosk_model_name"
        const val KEY_MODEL_DOWNLOADED_PREFIX = "model_downloaded_"
        const val KEY_VULKAN_INCOMPATIBLE = "vulkan_incompatible"
        const val KEY_VULKAN_PROBE_DONE = "vulkan_probe_done"
        const val KEY_VULKAN_RUNTIME_ATTEMPT = "vulkan_runtime_attempt"
        const val KEY_VULKAN_RUNTIME_VERIFIED = "vulkan_runtime_verified"
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_WAKE_WORD_MODEL_PATH = "wake_word_model_path"
        const val KEY_VERBOSE_LOGGING = "verbose_logging_enabled"
        const val KEY_OFFLINE_FALLBACK_TIMEOUT = "offline_fallback_timeout"
        const val KEY_CLOUD_INTELLIGENCE_ENABLED = "cloud_intelligence_enabled"
        const val KEY_AI_PROCESSOR = "ai_processor"
        const val KEY_SELECTED_LLAMA_MODEL_ID = "selected_llama_model_id"
        const val KEY_MODEL_REPO_BASE_URL = "model_repo_base_url"
        const val KEY_MODELS_JSON_CACHE = "models_json_cache"
        const val DEFAULT_MODEL_REPO_URL = Strings.Urls.DEFAULT_MODEL_REPO
    }

    object Database {
        const val FAST_MAP_RULES_TABLE = "fast_map_rules"
    }

    object Vosk {
        const val DEFAULT_LANG = "en"
        const val AM_FILE = "am"
        const val CONF_FILE = "conf"
        const val JSON_KEY_TEXT = "text"
        const val JSON_KEY_PARTIAL = "partial"
    }

    object Translations {
        const val DIR = "translations/"
        const val DIR_LIST = "translations"
        const val JSON_EXTENSION = ".json"
    }

    object Api {
        const val PART_FILE = "file"
        const val PART_MODEL = "model"
    }

    object IntentCategories {
        const val MEDIA = "MEDIA"
        const val SYSTEM = "SYSTEM"
        const val APP = "APP"
        const val HOME = "HOME"
        const val SEARCH = "SEARCH"
    }

    object Languages {
        const val DEFAULT = "en"
    }

    object Actions {
        const val START_WAKE_WORD = "com.voxcommander.app.action.START_WAKE_WORD"
        const val STOP_WAKE_WORD = "com.voxcommander.app.action.STOP_WAKE_WORD"
        const val PAUSE_WAKE_WORD = "com.voxcommander.app.action.PAUSE_WAKE_WORD"
        const val RESUME_WAKE_WORD = "com.voxcommander.app.action.RESUME_WAKE_WORD"
        const val EXIT_SERVICE = "com.voxcommander.app.action.EXIT_SERVICE"
    }

    object Urls {
        const val DEFAULT_MODEL_REPO = "https://github.com/razvan-eduard/VoxCommander"
        const val VOSK_MODELS = "https://alphacephei.com/vosk/models"
        const val OPENAI_API = "https://api.openai.com/"
    }

    object FallbackCategories {
        const val VOICE = "voice"
        const val INTENT = "intent"
    }
}
