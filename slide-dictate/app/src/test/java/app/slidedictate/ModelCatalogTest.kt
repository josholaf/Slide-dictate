package app.slidedictate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks on the model catalog: no Context needed, so plain JUnit works. */
class ModelCatalogTest {

    @Test fun catalogHasEntries() {
        assertTrue(ModelStore.models.isNotEmpty())
    }

    @Test fun idsAreUnique() {
        val ids = ModelStore.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun filesAreUnique() {
        val files = ModelStore.models.map { it.file }
        assertEquals(files.size, files.toSet().size)
    }

    @Test fun everySha256IsHex64() {
        for (m in ModelStore.models) {
            assertEquals(64, m.sha256.length)
            assertTrue(m.sha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test fun urlsMatchPinnedRepoAndFile() {
        for (m in ModelStore.models) {
            assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + m.file, m.url)
        }
    }

    @Test fun englishIdsUseEnFiles() {
        for (m in ModelStore.models) {
            if (m.id.contains(".en")) assertTrue(m.file.contains(".en"))
        }
    }

    @Test fun sizesAreSane() {
        for (m in ModelStore.models) assertTrue(m.mb in 1..3000)
    }
}
