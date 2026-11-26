package am.noz.caelum.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.navigation.SearchFilters
import java.time.LocalDate

@Composable
internal fun AdvancedSearchDialog(
    initialQuery: String,
    initialFilters: SearchFilters,
    canSearchOwnPosts: Boolean,
    onDismiss: () -> Unit,
    onApply: (String, SearchFilters) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var filters by remember { mutableStateOf(initialFilters) }
    val cleanFilters = filters.clean()
    val fromDate = parseDate(cleanFilters.since)
    val toDate = parseDate(cleanFilters.until)
    val sinceInvalid = cleanFilters.since.isNotEmpty() && fromDate == null
    val untilInvalid = cleanFilters.until.isNotEmpty() && toDate == null
    val rangeInvalid = fromDate != null && toDate != null && !fromDate.isBefore(toDate)
    val hasTerm = query.isNotBlank() || cleanFilters.from == "me"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Advanced search")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchFilterField(tr("Search"), query, { query = it })
                if (canSearchOwnPosts) {
                    FilterChip(
                        selected = filters.from == "me",
                        onClick = {
                            filters = if (filters.from == "me") {
                                filters.copy(from = "")
                            } else {
                                filters.copy(from = "me", author = "")
                            }
                        },
                        label = { Text(tr("My posts")) },
                    )
                }
                SearchFilterField(tr("From"), filters.author, {
                    filters = filters.copy(author = it, from = "")
                })
                SearchFilterField(tr("Mentions"), filters.mentions, { filters = filters.copy(mentions = it) })
                SearchFilterField(tr("Domain"), filters.domain, { filters = filters.copy(domain = it) })
                SearchFilterField(tr("URL"), filters.url, { filters = filters.copy(url = it) })
                SearchFilterField(tr("Hashtags"), filters.tag, { filters = filters.copy(tag = it) })
                SearchFilterField(tr("Language code"), filters.lang, { filters = filters.copy(lang = it) })
                SearchFilterField(tr("Since (YYYY-MM-DD)"), filters.since, { filters = filters.copy(since = it) }, sinceInvalid)
                SearchFilterField(tr("Until (YYYY-MM-DD)"), filters.until, { filters = filters.copy(until = it) }, untilInvalid || rangeInvalid)
                if (rangeInvalid) {
                    Text(tr("The end date must be after the start date."))
                }
                TextButton(onClick = { filters = SearchFilters() }) {
                    Text(tr("Clear filters"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
        },
        confirmButton = {
            TextButton(
                enabled = hasTerm && !sinceInvalid && !untilInvalid && !rangeInvalid,
                onClick = {
                    if (query.isBlank() && cleanFilters.from == "me") {
                        onApply("from:me", cleanFilters.copy(from = ""))
                    } else {
                        onApply(query.trim(), cleanFilters)
                    }
                },
            ) {
                Text(tr("Search"))
            }
        },
    )
}

@Composable
private fun SearchFilterField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(title) },
        singleLine = true,
        isError = isError,
    )
}

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
