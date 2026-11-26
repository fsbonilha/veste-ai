package com.vesteai.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.vesteai.R
import java.io.File

/**
 * `ACTION_SEND` over a FileProvider uri, so nothing outside app storage is ever shared.
 * Shared by the fitting room and the lookbook viewer.
 */
internal class LookSharing(
    private val context: Context,
    private val onUnavailable: () -> Unit = {},
) {
    operator fun invoke(file: File) {
        val uri = FileProvider.getUriForFile(context, context.fileProviderAuthority(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_JPEG
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_look_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_look_chooser_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
    }
}

internal fun Context.fileProviderAuthority(): String = packageName + ".fileprovider"
