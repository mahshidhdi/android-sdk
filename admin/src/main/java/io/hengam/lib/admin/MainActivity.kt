package io.hengam.lib.admin

import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import sh.hadi.bark.BarkCommands
import sh.hadi.bark.ui.BarkActivity

class MainActivity : BarkActivity() {
    private var autoUpdater: AutoUpdate = AutoUpdate(this)
    override var barkCommands: BarkCommands? = AdminDebugCommands(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        title = "v" + packageManager.getPackageInfo(packageName, 0).versionName

        MenuDrawer().initDrawer(this, MenuDrawer.DRAWER_ITEM_LOGS)
    }

    private fun promptUpdate() {
        val promptSnack = createSnackbar("Update Available")
        val loaderSnack = createSnackbar("Downloading Update...")

        promptSnack.setAction("Update") {
            val result = autoUpdater.update()

            val sub = result.second
                    .subscribe {
                        when (it) {
                            UpdateState.LOADING -> {
                                promptSnack.dismiss()
                                loaderSnack.show()
                            }
                            UpdateState.DONE -> {
                                loaderSnack.dismiss()
                            }
                            else -> {
                            }
                        }
                    }
        }

        promptSnack.show()
    }

    private fun createSnackbar(message: String) =
            Snackbar.make(
                    findViewById<CoordinatorLayout>(android.R.id.content),
                    message,
                    Snackbar.LENGTH_INDEFINITE
            )


}