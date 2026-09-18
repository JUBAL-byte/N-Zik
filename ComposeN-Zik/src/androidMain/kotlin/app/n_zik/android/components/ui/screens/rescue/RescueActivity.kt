package app.n_zik.android.components.ui.screens.rescue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toArgb
import timber.log.Timber
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setDefaultPalette
import app.it.fast4x.rimusic.utils.getEnum

/**
 * Lightweight Activity that runs in the `:rescue` process.
 *
 * Because [app.n_zik.android.MainApplication.onCreate] returns early when
 * `!isMainProcess()`, none of the heavy app initialization runs here:
 * no Room, no Koin/Hilt, no `Dependencies`, no `appContext()`, no player.
 * This Activity uses only [android.content.Context] and raw files.
 *
 * Timber is NOT planted in the `:rescue` process (the tree is set up in
 * MainApplication which skips init for non-main processes), so we plant a
 * minimal DebugTree here for logging.
 */
class RescueActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Plant a Timber tree for the :rescue process (MainApplication skips this)
        if (Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag("RescueActivity").i("Rescue Center started in process: %s",
            android.app.ActivityManager.RunningAppProcessInfo().let { info ->
                android.app.ActivityManager.getMyMemoryState(info)
                android.os.Process.myPid().toString()
            }
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT,
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = this.preferences

        setContent {
            val colorPaletteName = prefs.getEnum(app.it.fast4x.rimusic.utils.colorPaletteNameKey, app.it.fast4x.rimusic.enums.ColorPaletteName.Dynamic)
            val colorPaletteMode = prefs.getEnum(app.it.fast4x.rimusic.utils.colorPaletteModeKey, app.it.fast4x.rimusic.enums.ColorPaletteMode.Dark)
            val customColor = prefs.getInt(app.it.fast4x.rimusic.utils.customColorKey, androidx.compose.ui.graphics.Color.Green.hashCode())
            
            val isDarkTheme = isSystemInDarkTheme()
            val lightTheme = colorPaletteMode == app.it.fast4x.rimusic.enums.ColorPaletteMode.Light || 
                             (colorPaletteMode == app.it.fast4x.rimusic.enums.ColorPaletteMode.System && !isDarkTheme)

            var colorPalette = app.it.fast4x.rimusic.ui.styling.colorPaletteOf(colorPaletteName, colorPaletteMode, !lightTheme)

            // Setup MonetCompat if MaterialYou is used (best effort in :rescue process)
            if (colorPaletteName == app.it.fast4x.rimusic.enums.ColorPaletteName.MaterialYou) {
                try {
                    com.kieronquinn.monetcompat.core.MonetCompat.enablePaletteCompat()
                    com.kieronquinn.monetcompat.core.MonetCompat.setup(this@RescueActivity)
                    val monet = com.kieronquinn.monetcompat.core.MonetCompat.getInstance()
                    monet.setDefaultPalette()
                    colorPalette = app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf(
                        androidx.compose.ui.graphics.Color(monet.getAccentColor(this@RescueActivity)),
                        !lightTheme
                    )
                } catch (e: Exception) {
                    Timber.e(e, "MonetCompat not ready")
                }
            } else if (colorPaletteName == app.it.fast4x.rimusic.enums.ColorPaletteName.CustomColor) {
                colorPalette = app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf(
                    androidx.compose.ui.graphics.Color(customColor),
                    !lightTheme
                )
            }

            val nzikScheme = if (lightTheme) {
                androidx.compose.material3.lightColorScheme(
                    background = colorPalette.background0,
                    surface = colorPalette.background1,
                    surfaceVariant = colorPalette.background2,
                    onSurface = colorPalette.text,
                    onSurfaceVariant = colorPalette.textSecondary,
                    primaryContainer = colorPalette.background2,
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
                    primary = colorPalette.accent
                )
            } else {
                androidx.compose.material3.darkColorScheme(
                    background = colorPalette.background0,
                    surface = colorPalette.background1,
                    surfaceVariant = colorPalette.background2,
                    onSurface = colorPalette.text,
                    onSurfaceVariant = colorPalette.textSecondary,
                    primaryContainer = colorPalette.background2,
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
                    primary = colorPalette.accent
                )
            }

            MaterialTheme(
                colorScheme = nzikScheme
            ) {
                RescueScreen()
            }
        }
    }
}
