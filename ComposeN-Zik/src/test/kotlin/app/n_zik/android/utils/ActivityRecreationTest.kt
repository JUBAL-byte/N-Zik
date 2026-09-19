package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivityRecreationTest {

    @Test
    fun `activity may be recreated while the database is open`() {
        assertTrue(shouldRecreateActivity(isDatabaseClosed = false))
    }

    @Test
    fun `activity is not recreated once a backup import closed the database`() {
        assertFalse(shouldRecreateActivity(isDatabaseClosed = true))
    }
}
