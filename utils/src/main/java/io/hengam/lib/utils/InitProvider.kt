package io.hengam.lib.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

abstract class InitProvider : ContentProvider() {
    abstract fun initialize(context: Context)

    override fun onCreate(): Boolean {
        context?.let { initialize(it) }
        return true
    }

    // to be ignored
    override fun update(
            uri: Uri?,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    // to be ignored
    override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    // to be ignored
    override fun getType(uri: Uri?): String? {
        return null
    }

    // to be ignored
    override fun query(
            uri: Uri?,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
    ): Cursor? {
        return null
    }

    // to be ignored
    override fun insert(uri: Uri?, values: ContentValues?): Uri? {
        return null
    }
}