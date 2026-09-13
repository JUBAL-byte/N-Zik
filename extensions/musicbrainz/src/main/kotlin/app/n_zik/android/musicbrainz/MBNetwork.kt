package app.n_zik.android.musicbrainz

import java.net.Proxy

/**
 * Optional network configuration for the musicbrainz module.
 * The Android app sets [proxy] from the user's proxy preferences
 * (the module itself is JVM-only and cannot read SharedPreferences).
 */
object MBNetwork {
    var proxy: Proxy? = null
}
