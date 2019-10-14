package co.pushe.plus.notification.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.utils.HttpUtils
import co.pushe.plus.utils.IdGenerator
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.days
import co.pushe.plus.utils.log.Plog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.inject.Inject


/***
 * A class for downloading notification image
 */

class ImageDownloader @Inject constructor(
        private val context: Context,
        private val httpUtils: HttpUtils
) {

    /**
     * Attempts at downloading an image synchronously.
     *
     * @param url The image url
     * @return The downloaded Bitmap image or null if downloading fails
     */
    @Throws(IOException::class, HttpUtils.HttpError::class)
    fun downloadImage(url: String): Bitmap? {
        val input = httpUtils.requestBlockingStream(url)
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()
        return bitmap
    }

    /**
     * Attempts at downloading an image synchronously.
     *
     * @param url The image url
     * @return The downloaded Bitmap image or null if downloading fails
     */
    @Throws(IOException::class, HttpUtils.HttpError::class)
    fun downloadImage(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val input = httpUtils.requestBlockingStream(url)
        val image = decodeSampledBitmapFromStream(input, reqWidth, reqHeight)
        input.close()
        return image
    }


    /**
     * Attempts at downloading an image synchronously.
     *
     * @param url The image url
     * @return The downloaded Bitmap image or null if downloading fails
     */
    @Throws(IOException::class, HttpUtils.HttpError::class, ImageDownloaderException::class)
    fun getImage(url: String): Bitmap {
        downloadImageAndCache(url)
        return BitmapFactory.decodeFile(
                getCachedFile(url)
                        ?: throw ImageDownloaderException("Failed to retrieve saved image")
        ) ?: throw ImageDownloaderException("Failed to decode image into a bitmap")
    }

    /**
     * Attempts at downloading an image synchronously and save it in a file.
     *
     * @return The downloaded image file or null if downloading fails
     */
    @Throws(IOException::class, HttpUtils.HttpError::class)
    fun downloadImageAndCache(url: String) {
        val input = httpUtils.requestBlockingStream(url)

        // Note: instead of writing directly to the cache file we write to the temp file first
        // and then copy over to the final "cache" file. This is to prevent unexpected behaviour
        // if the same file is downloaded at the same time on different threads.

        val buffer = ByteArray(1024)
        var read = input.read(buffer)

        val directory = File(context.cacheDir, "/images/")
        if (!directory.exists()) directory.mkdir()

        val cachedFile = File(context.cacheDir, "/images/img${url.hashCode()}")
        if (cachedFile.exists()) {
            return
        }

        val tempFile = File(context.cacheDir, "/images/tmp${url.hashCode()}-${IdGenerator.generateId(5)}")
        val outputStream = FileOutputStream(tempFile)

        while (read != -1) {
            outputStream.write(buffer, 0, read)
            read = input.read(buffer)
        }
        outputStream.flush()
        outputStream.close()
        input.close()

        tempFile.copyTo(cachedFile, overwrite = true)
        try {
            tempFile.delete()
        } catch (ex: Exception) {
            Plog.warn(T_NOTIF, ex)
        }
    }

    /**
     * Attempts at loading an image synchronously from file.
     *
     * @return The loaded image drawable or null if loading fails
     */
    @Throws(IOException::class, ImageDownloaderException::class)
    fun loadImageFromCache(url: String): Drawable? {
        return Drawable.createFromPath(
                getCachedFile(url)
                        ?: throw ImageDownloaderException("Failed to retrieve cached image")
        ) ?: throw ImageDownloaderException("Failed to create drawable from cached image")
    }

    /**
     * Attempts at loading a file synchronously from cache.
     *
     * @return The loaded file path or null if loading fails
     */
    fun getCachedFile(url: String): String? {
        val file = File(context.cacheDir, "/images/img${url.hashCode()}")
        if (file.exists()) {
            return file.absolutePath
        }
        return null
    }

    /**
     * Deletes cached images which have been cached for longer than a specified time
     *
     * @param expirationTime The time duration which messages are allowed to live in the cache
     */
    fun purgeOutdatedCache(expirationTime: Time = days(7)) {
        try {
            val directory= File(context.cacheDir, "/images/")
            if (!directory.exists()) return

            val files= directory.listFiles()
            val oldFiles = mutableListOf<File>()

            files.forEach {
                val difDay = (Date().time - it.lastModified()) / (24 * 3600 * 1000)
                if (difDay >= expirationTime.toDays()) {
                    oldFiles.add(it)
                    it.delete()
                }
            }

            if (!oldFiles.isEmpty()) {
                Plog.debug(T_NOTIF, "Deleting ${oldFiles.size} cached images")
                oldFiles.forEach {
                    it.delete()
                }
            }

        } catch (e: Exception) {
            Plog.warn(T_NOTIF, "Clearing cached images failed", e)
        }
    }


    companion object {
        //private Context mContext;

        /***
         * Calculate the largest inSampleSize value that is a power of 2 and keeps both
         * height and width larger than the requested height and width.
         *
         * @param options   the options
         * @param reqWidth  the desired width
         * @param reqHeight the desired height
         * @return size
         */
        fun calculateInSampleSize(
                options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                val halfHeight = height / 2
                val halfWidth = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        /***
         * First decode with inJustDecodeBounds=true to check dimensions
         * Calculate inSampleSize
         * Decode bitmap with inSampleSize set
         *
         * @param stream
         * @param reqWidth
         * @param reqHeight
         * @return bitmap
         */
        fun decodeSampledBitmapFromStream(stream: InputStream,
                                          reqWidth: Int, reqHeight: Int): Bitmap {

            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(stream, null, options)
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeStream(stream, null, options)
        }
    }

    class ImageDownloaderException(
            message: String,
            cause: Throwable? = null
    ) : Exception(message, cause)
}
