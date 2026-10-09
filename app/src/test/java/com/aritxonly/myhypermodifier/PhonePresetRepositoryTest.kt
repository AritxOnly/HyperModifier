package com.aritxonly.myhypermodifier

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhonePresetRepositoryTest {
    private fun item(id: String = "new-model", file: String = "images/new-model.png") = JSONObject()
        .put("id", id).put("name", "新机型").put("file", file).put("sha256", "a".repeat(64))
    private fun catalog(vararg items: JSONObject) = JSONObject().put("schemaVersion", 1)
        .put("presets", JSONArray(items.toList())).toString()

    @Test fun acceptsNewModelsWithoutAnApkListAndResolvesPackagePaths() {
        val items = PhonePresetRepository.parseCatalog(catalog(item()), PhonePresetRepository.DEFAULT_URL)
        assertEquals("new-model", items.single().id)
        assertEquals("images/new-model.png", items.single().file)
        assertTrue(items.single().url.endsWith("/phone-presets/images/new-model.png"))
    }

    @Test fun rejectsDuplicateIdsReservedSourcesInvalidChecksumsAndNonPackagePaths() {
        val invalid = listOf(
            catalog(item(), item()), catalog(item("auto")), catalog(item("custom")),
            catalog(item().put("sha256", "invalid")),
            catalog(item(file = "../image.png")),
            catalog(item(file = "https://raw.githubusercontent.com/another/image.png")),
            catalog(item(file = "images/../image.png")),
        )
        invalid.forEach { text ->
            assertTrue(runCatching { PhonePresetRepository.parseCatalog(text, PhonePresetRepository.DEFAULT_URL) }.isFailure)
        }
    }
}
