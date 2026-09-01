package com.gios.brightway.util

import android.content.Context
import android.provider.Settings

/**
 * The Light Phone's black-and-white is not the panel — it's a SurfaceFlinger colour matrix,
 * pinned by accessibility_display_daltonizer_enabled = 1 in mode 0 (monochromacy). With
 * WRITE_SECURE_SETTINGS granted over adb, flipping that one integer shows real colour.
 * Same mechanism LightChat uses for its image viewer.
 *
 * Since v1.12 the nav screen no longer flips this itself — it asks BrightControl through
 * light-common's ColourEffect, whose hold dies with the binder. What remains here is the
 * state-driven safety net for the library's direct-write fallback: MainActivity.onCreate and
 * NavService.shutdown() call [setColor] false whenever nobody is navigating, so a crash or a
 * pocket arrival can't strand the whole phone in colour. And Settings still asks [granted].
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
