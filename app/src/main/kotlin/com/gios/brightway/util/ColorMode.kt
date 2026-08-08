package com.gios.brightway.util

import android.content.Context
import android.provider.Settings

/**
 * The Light Phone's black-and-white is not the panel — it's a SurfaceFlinger colour matrix,
 * pinned by accessibility_display_daltonizer_enabled = 1 in mode 0 (monochromacy). With
 * WRITE_SECURE_SETTINGS granted over adb, flipping that one integer shows real colour.
 * Same mechanism LightChat uses for its image viewer.
 */
object ColorMode {
    private const val KEY = "accessibility_display_daltonizer_enabled"

    /** True if the write stuck; false means the grant is missing and we stay greyscale. */
    fun setColor(context: Context, color: Boolean): Boolean = runCatching {
        Settings.Secure.putInt(context.contentResolver, KEY, if (color) 0 else 1)
    }.getOrDefault(false)

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
