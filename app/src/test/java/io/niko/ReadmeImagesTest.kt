package io.niko

import java.io.File
import java.net.URLDecoder
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The README's pictures are the snapshot tests' own images (app/src/test/snapshots/images): a test renamed or gone
 * would leave the README with a broken picture, so each one it names must be there.
 */
class ReadmeImagesTest {

    @Test
    fun theReadmesPicturesAreSnapshotsThatExist() {
        val root = File("..").canonicalFile
        for (name in listOf("README.md", "README.ru.md")) {
            val text = File(root, name).readText()
            val images = Regex("""src="(app/src/test/snapshots/images/[^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
            assertTrue("$name: no snapshot pictures", images.isNotEmpty())
            for (path in images) {
                val file = File(root, URLDecoder.decode(path, "UTF-8"))
                assertTrue("$name: $path is not a snapshot", file.isFile)
            }
        }
    }
}
