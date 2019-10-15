package io.hengam.lib.sentry

object Settings {
    const val HENGAM_VERSION = BuildConfig.VERSION_NAME
    const val HENGAM_VERSION_CODE = BuildConfig.VERSION_CODE
    const val DEFAULT_DSN_PUBLIC = "7465bb3185b748da924b16640c6f2515"
    const val DEFAULT_DSN_PRIVATE = "9b41ecf6a8e04712bc3db68c02b33ae8"
    const val INTERNAL_PACKAGE_NAME = "io.hengam.lib"
}


object LogTag {
    const val T_SENTRY = "Sentry"
}
