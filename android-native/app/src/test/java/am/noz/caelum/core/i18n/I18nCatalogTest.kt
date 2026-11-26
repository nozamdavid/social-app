package am.noz.caelum.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.io.File

class I18nCatalogTest {
    private fun assetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/locales/$name"),
            File("app/src/main/assets/locales/$name"),
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun `loads all 44 locale catalogs and evaluates Spanish translations and plurals`() {
        for (lang in I18nCatalog.SUPPORTED_LANGUAGES) {
            val file = assetFile("${lang.code}.json")
            assertTrue("Missing compiled locale file ${lang.code}.json", file.exists())
        }

        val catalog = I18nCatalog.fromStreams(
            localeTag = "es",
            enIndexStream = assetFile("en_index.json").inputStream(),
            localeMessagesStream = assetFile("es.json").inputStream(),
            fallbackEnStream = assetFile("en.json").inputStream(),
        )

        val now = Instant.parse("2026-04-01T10:00:00Z")
        assertEquals("ahora", catalog.formatRelativeShort(now.toString(), now))
        assertEquals("45 s", catalog.formatRelativeShort(now.minusSeconds(45).toString(), now))
        assertEquals("2 d", catalog.formatRelativeShort(now.minusSeconds(172800).toString(), now))

        assertEquals(
            "Añadir personas",
            catalog.translate("Add people"),
        )
        assertEquals(
            "Alice añadido al chat",
            catalog.translate("{0} added to chat", mapOf("0" to "Alice")),
        )
        assertEquals(
            "1 seguidor",
            catalog.translate("{0, plural, one {# follower} other {# followers}}", mapOf("0" to 1)),
        )
        assertEquals(
            "42 seguidores",
            catalog.translate("{0, plural, one {# follower} other {# followers}}", mapOf("0" to 42)),
        )
        assertEquals(
            "No hay solicitudes para unirse",
            catalog.translate(
                "{count, plural, =0 {No requests to join} one {# request to join} other {# requests to join}}",
                mapOf("count" to 0),
            ),
        )
    }

    @Test
    fun `strips catalog markup without changing search text`() {
        val catalog = I18nCatalog.fromStreams(
            localeTag = "es",
            enIndexStream = assetFile("en_index.json").inputStream(),
            localeMessagesStream = assetFile("es.json").inputStream(),
            fallbackEnStream = assetFile("en.json").inputStream(),
        )
        val args = mapOf("query" to "<0>cat</0>")

        assertEquals(
            "No se encontraron resultados para \"<0>cat</0>\".",
            catalog.translate("No results found for “<0>{query}</0>”.", args),
        )
        assertEquals(
            "Query: <0>cat</0>",
            catalog.translate("Query: <0>{query}</0>", args),
        )
    }

    @Test
    fun `normalizes regional locale tags accurately`() {
        assertEquals("zh-TW", I18nCatalog.normalizeLocaleCode("zh_TW"))
        assertEquals("zh-HK", I18nCatalog.normalizeLocaleCode("zh_HK"))
        assertEquals("zh-CN", I18nCatalog.normalizeLocaleCode("zh-CN"))
        assertEquals("pt-BR", I18nCatalog.normalizeLocaleCode("pt"))
        assertEquals("pt-PT", I18nCatalog.normalizeLocaleCode("pt-PT"))
        assertEquals("es", I18nCatalog.normalizeLocaleCode("es_MX"))
        assertEquals("en", I18nCatalog.normalizeLocaleCode("unknown"))
    }
}
