package co.pushe.plus.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v4.content.FileProvider
import co.pushe.plus.internal.ioThread
import co.pushe.plus.utils.rx.BehaviorRelay
import com.github.javiersantos.appupdater.AppUpdaterUtils
import com.github.javiersantos.appupdater.enums.AppUpdaterError
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.github.javiersantos.appupdater.objects.Update
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Okio
import java.io.File
import java.io.IOException

class AutoUpdate(val context: Context) {
    public fun checkUpdates(): Single<Boolean> {
        return Single.create<Boolean> {
            AppUpdaterUtils(context)
                    .setUpdateFrom(UpdateFrom.JSON)
                    .setUpdateJSON("http://pushe.hadi.sh/version.json")
                    .withListener(object : AppUpdaterUtils.UpdateListener {
                        override fun onFailed(ex: AppUpdaterError?) {
                            it.onError(IOException(ex.toString()))
                        }

                        override fun onSuccess(update: Update?, updateAvailable: Boolean?) {
                            it.onSuccess(updateAvailable ?: false)
                        }
                    })
                    .start()
        }
    }

    public fun update(): Pair<Disposable, BehaviorRelay<UpdateState>> {
        val updateState = BehaviorRelay.createDefault(UpdateState.START)

        val disposable = Observable.fromCallable { updateRequest() }
                .subscribeOn(ioThread())
                .filter { it.isSuccessful }
                .doOnNext { updateState.accept(UpdateState.LOADING) }
                .observeOn(ioThread())
                .map { saveUpdateFile(it) }
                .doOnNext { openInstallActivity(it) }
                .doOnNext { updateState.accept(UpdateState.DONE) }
                .subscribe()

        return Pair(disposable, updateState)
    }

    private fun updateRequest(): Response {
        val client = OkHttpClient.Builder()
                .build()
        return client.newCall(Request.Builder()
                .url("http://pushe.hadi.sh/pushe-admin.apk")
                .build()
        ).execute()
    }

    private fun saveUpdateFile(response: Response): File {
        val downloadedFile = File(
                context.getExternalFilesDir(null)
                , "pushe-admin.apk"
        )
        val sink = Okio.buffer(Okio.sink(downloadedFile))
        sink.writeAll(response.body()?.source())
        sink.close()
        return downloadedFile
    }

    private fun openInstallActivity(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uri = FileProvider.getUriForFile(
                    context,
                    "co.pushe.plus.admin.file_provider_authority",
                    file
            )
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        }
    }

}

enum class UpdateState {
    START,
    LOADING,
    DONE
}