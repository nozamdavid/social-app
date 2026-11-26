package am.noz.caelum.ui.post

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostGifTest {
    @Test
    fun tenorUsesItsAnimatedProxyAndEmbeddedDimensions() {
        val gif = parsePostGif("https://media.tenor.com/abcAAAAC/example.gif?hh=210&ww=280")

        assertEquals(PostGif("https://t.gifs.bsky.app/abcAAAAM/example.gif", 280f / 210f), gif)
    }

    @Test
    fun klipyStripsPlaybackMetadataAndClampsTallGifs() {
        val gif = parsePostGif("https://static.klipy.com/ii/1/2/example.gif?hh=200&ww=40&mp4=x&webm=y&other=z")

        assertEquals(PostGif("https://k.gifs.bsky.app/ii/1/2/example.gif?other=z", 0.5f), gif)
    }

    @Test
    fun unrecognizedOrMalformedUrlsRemainOrdinaryLinks() {
        assertNull(parsePostGif("https://media.tenor.com.example/abcAAAAC/example.gif?hh=150&ww=150"))
        assertNull(parsePostGif("https://media.tenor.com/abcAAAAC/example.gif?hh=0&ww=150"))
        assertNull(parsePostGif("https://static.klipy.com/other/example.gif?hh=150&ww=150"))
    }
}
