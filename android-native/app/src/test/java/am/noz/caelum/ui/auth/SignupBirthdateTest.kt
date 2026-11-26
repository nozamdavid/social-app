package am.noz.caelum.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignupBirthdateTest {
    @Test
    fun `input requires a real date in the requested format`() {
        assertNull(parseSignupBirthdate(""))
        assertNull(parseSignupBirthdate("2000-2-29"))
        assertNull(parseSignupBirthdate("2001-02-29"))
        assertNull(parseSignupBirthdate("0000-01-01"))
        assertEquals(LocalDate.of(2000, 2, 29), parseSignupBirthdate(" 2000-02-29 "))
    }

    @Test
    fun `signup opens on the thirteenth birthday`() {
        val date = LocalDate.of(2013, 11, 18)
        assertFalse(date.meetsSignupAge(LocalDate.of(2026, 11, 17)))
        assertTrue(date.meetsSignupAge(LocalDate.of(2026, 11, 18)))
        assertFalse(date.meetsSignupAge(LocalDate.of(2012, 11, 18)))
    }

    @Test
    fun `a leap day birthday reaches minimum age on March 1 in a common year`() {
        val date = LocalDate.of(2012, 2, 29)
        assertFalse(date.meetsSignupAge(LocalDate.of(2025, 2, 28)))
        assertTrue(date.meetsSignupAge(LocalDate.of(2025, 3, 1)))
    }
}
