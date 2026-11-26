package am.noz.caelum.ui.auth

import java.time.LocalDate
import java.time.format.DateTimeParseException

private val signupBirthdateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private const val MIN_SIGNUP_AGE = 13L

internal fun parseSignupBirthdate(input: String): LocalDate? {
    val value = input.trim()
    if (!signupBirthdateFormat.matches(value)) return null
    return try {
        LocalDate.parse(value).takeIf { it.year > 0 }
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun LocalDate.meetsSignupAge(today: LocalDate = LocalDate.now()): Boolean =
    !isAfter(today.minusYears(MIN_SIGNUP_AGE))
