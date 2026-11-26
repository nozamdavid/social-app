package am.noz.caelum.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeEngineTest {

    @Test
    fun matchesSharedWitchskyAndCaelumFixtures() {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/witchsky_themes.json")
        )
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject

        // 1. Verify stock Bluesky Light / Dim / Dark
        val stock = root["stockPalettes"]!!.jsonObject
        assertPaletteMatches("stock.light", stock["light"]!!.jsonObject, ThemeEngine.STOCK_LIGHT)
        assertPaletteMatches("stock.dim", stock["dim"]!!.jsonObject, ThemeEngine.STOCK_DIM)
        assertPaletteMatches("stock.dark", stock["dark"]!!.jsonObject, ThemeEngine.STOCK_DARK)

        // 2. Verify bundled Caelum Light / Dim / Dark
        val caelum = root["caelumPalettes"]!!.jsonObject
        assertPaletteMatches("caelum.light", caelum["light"]!!.jsonObject, ThemeEngine.CAELUM_LIGHT)
        assertPaletteMatches("caelum.dim", caelum["dim"]!!.jsonObject, ThemeEngine.CAELUM_DIM)
        assertPaletteMatches("caelum.dark", caelum["dark"]!!.jsonObject, ThemeEngine.CAELUM_DARK)

        // 3. Verify parsed requestedThemes (BlueAT + Coffee/Espresso Base, Light, Dark)
        val records = root["requestedThemeRecords"]!!.jsonArray
        val parsed = records.flatMap { elem ->
            val rec = elem.jsonObject
            ThemeEngine.parseThemeRecord(rec["uri"]!!.jsonPrimitive.content, rec["value"]!!.jsonObject)
        }
        val expectedResolved = root["resolvedRequestedThemes"]!!.jsonArray
        assertEquals(expectedResolved.size, parsed.size)

        for (i in expectedResolved.indices) {
            val exp = expectedResolved[i].jsonObject
            val expOpt = exp["option"]!!.jsonObject
            val actOpt = parsed[i]
            assertEquals("opt[$i].id", expOpt["id"]!!.jsonPrimitive.content, actOpt.id)
            assertEquals("opt[$i].mode", expOpt["mode"]!!.jsonPrimitive.content, actOpt.mode)
            assertEquals("opt[$i].variantName", expOpt["variantName"]!!.jsonPrimitive.content, actOpt.variantName)

            val actPalette = ThemeEngine.createWitchskyPalette(actOpt)
            assertPaletteMatches("resolved[$i]", exp["derivedPalette"]!!.jsonObject, actPalette)
        }
    }

    @Test
    fun readThemeSelectionRejectsForeignAccountThemes() {
        val did = "did:plc:alice"
        val foreignRaw = """{"data":{"id":"at://did:plc:bob/app.witchsky.theme.colors/1","uri":"at://did:plc:bob/app.witchsky.theme.colors/1","name":"X","variantName":"Base","mode":"dark","colors":{"canvas":"#000000","surface":"#111111","surfaceRaised":"#222222","text":"#ffffff","textMuted":"#cccccc","border":"#333333","accent":"#6550e5","accentSoft":"#221144","onAccent":"#ffffff","positive":"#00ff00","warning":"#ffff00","critical":"#ff0000","favorite":"#ff00ff"}}}"""
        assertNull(ThemeEngine.readThemeSelection(foreignRaw, did))

        val bundledRaw = """{"data":{"id":"builtin://caelum#dark","uri":"builtin://caelum","name":"Caelum","variantName":"Dark","mode":"dark","colors":{"canvas":"#09080f","surface":"#100e19","surfaceRaised":"#171421","text":"#faf9fe","textMuted":"#afaeb6","border":"#292437","accent":"#6550e5","accentSoft":"#322a5e","onAccent":"#ffffff","positive":"#09b35e","warning":"#ffc404","critical":"#e91646","favorite":"#ec4899"}}}"""
        val restored = ThemeEngine.readThemeSelection(bundledRaw, did)
        assertEquals("builtin://caelum#dark", restored?.id)
        assertEquals("#09080f", restored?.colors?.canvas)
    }

    private fun assertPaletteMatches(
        label: String,
        exp: kotlinx.serialization.json.JsonObject,
        act: AlfPalette,
    ) {
        fun chk(key: String, actualVal: String) {
            assertEquals("$label.$key", exp[key]!!.jsonPrimitive.content.lowercase(), actualVal.lowercase())
        }
        chk("contrast_0", act.contrast0)
        chk("contrast_25", act.contrast25)
        chk("contrast_50", act.contrast50)
        chk("contrast_100", act.contrast100)
        chk("contrast_200", act.contrast200)
        chk("contrast_400", act.contrast400)
        chk("contrast_700", act.contrast700)
        chk("contrast_1000", act.contrast1000)
        chk("primary_50", act.primary50)
        chk("primary_200", act.primary200)
        chk("primary_500", act.primary500)
        chk("primary_600", act.primary600)
        chk("primary_900", act.primary900)
        chk("positive_50", act.positive50)
        chk("positive_500", act.positive500)
        chk("negative_50", act.negative50)
        chk("negative_500", act.negative500)
        chk("like", act.like)
        chk("yellow", act.yellow)
    }
}
