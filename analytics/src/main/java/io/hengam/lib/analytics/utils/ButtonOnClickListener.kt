package io.hengam.lib.analytics.utils

import android.view.View

/**
 * A simple wrapper around View.OnClickListener
 */
class ButtonOnClickListener(private val buttonOnClick: () -> Unit): View.OnClickListener{
    override fun onClick(v: View?) {
        buttonOnClick()
    }
}