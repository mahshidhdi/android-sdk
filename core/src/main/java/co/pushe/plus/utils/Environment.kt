package co.pushe.plus.utils

fun environment(): Environment {
    return when {
        BuildConfig.DEBUG -> Environment.DEVELOPMENT
        BuildConfig.VERSION_CODE % 100 == 99 -> Environment.STABLE
        BuildConfig.VERSION_CODE % 100 > 50 -> Environment.BETA
        else -> Environment.ALPHA
    }
}

enum class Environment {
    DEVELOPMENT,
    ALPHA,
    BETA,
    STABLE;
    override fun toString(): String = name.toLowerCase()
}