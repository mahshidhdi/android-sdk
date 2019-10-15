package io.hengam.lib.analytics

import io.hengam.lib.internal.HengamException
import java.lang.Exception

class AnalyticsException(message: String, vararg val data: Pair<String, Any?>?): HengamException(message)