package app.n_zik.android.utils

/**
 * Whether `MainActivity` may call `recreate()` after a preference change.
 *
 * A backup import closes the database and leaves a restart pending. A recreated activity would
 * recompose screens that query the closed database (e.g. Data settings) and crash.
 */
internal fun shouldRecreateActivity(isDatabaseClosed: Boolean): Boolean = !isDatabaseClosed
