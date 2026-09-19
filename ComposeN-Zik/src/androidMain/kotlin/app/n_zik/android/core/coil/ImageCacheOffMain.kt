package app.n_zik.android.core.coil

import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/**
 * Purges the whole image cache (RAM, disk, playlist signatures, quality metadata) on
 * [NzikDispatchers.DATA] (issue #606 M4). [ImageCacheFactory.clearImageCache] deletes the disk
 * cache folder, so it must not run on Main, where the settings confirmation dialog used to call
 * it. It is thread-safe and already swallows its own errors.
 *
 * The [clear] seam only exists so the dispatch can be unit-tested without building the Coil
 * loader.
 */
internal suspend fun clearImageCacheOffMain(
    clear: () -> Unit = ImageCacheFactory::clearImageCache,
) = withContext(NzikDispatchers.DATA) { clear() }
