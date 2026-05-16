package com.heimdall.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the user's custom avatar photo.
 *
 * The avatar is stored as a JPEG in the app's internal [filesDir] so it:
 *   - Persists across app restarts
 *   - Is included in Android Auto Backup (backed up to Drive)
 *   - Is not accessible to other apps (no external storage needed)
 */
object AvatarManager {

    private const val AVATAR_FILENAME = "user_avatar.jpg"
    private const val AVATAR_SIZE = 256  // Save at 256px; map pin scales to 148px

    /** Returns the [File] where the avatar JPEG is stored. */
    fun getAvatarFile(context: Context): File =
        File(context.filesDir, AVATAR_FILENAME)

    /** Returns true if the user has set a custom avatar. */
    fun hasAvatar(context: Context): Boolean = getAvatarFile(context).exists()

    /**
     * Saves the image at [uri] as the user's avatar.
     *
     * The image is decoded, scaled to a square [AVATAR_SIZE]×[AVATAR_SIZE] JPEG,
     * and written to internal storage.
     *
     * @return true on success, false if the URI couldn't be decoded or written.
     */
    fun saveAvatar(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val raw = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            raw ?: return false

            // Scale to square
            val size = minOf(raw.width, raw.height)
            val xOffset = (raw.width - size) / 2
            val yOffset = (raw.height - size) / 2
            val cropped = Bitmap.createBitmap(raw, xOffset, yOffset, size, size)
            val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)

            FileOutputStream(getAvatarFile(context)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Deletes the saved avatar, reverting to the default pin. */
    fun deleteAvatar(context: Context) {
        getAvatarFile(context).delete()
    }
}
