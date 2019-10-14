package co.pushe.plus.sentry

object Settings {
    const val PUSHE_VERSION = BuildConfig.VERSION_NAME
    const val PUSHE_VERSION_CODE = BuildConfig.VERSION_CODE
    const val DEFAULT_DSN_PUBLIC = "b6fd6e67c07345f1a594d0c63135eb8d"
    const val DEFAULT_DSN_PRIVATE = "aaed9ff89ccb47c896a976225345264a"
    const val INTERNAL_PACKAGE_NAME = "co.pushe.plus"
}


object LogTag {
    const val T_SENTRY = "Sentry"
}
