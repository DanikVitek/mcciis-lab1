package me.danikvitek.lab1.data

import me.danikvitek.lab1.domain.model.TaskMap
import me.danikvitek.lab1.util.AppLanguage
import me.danikvitek.lab1.util.AppLocaleManager
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

actual object MapFileStorage {
    private fun ensureMapsDir(): File {
        val dir = File(userWorkingDirectory, "maps")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    actual fun saveMapWithDialog(map: TaskMap): String? {
        val mapsDir = ensureMapsDir()
        val sanitized = map.name.trim()
            .replace(Regex("[^a-zA-Z0-9а-яА-ЯіІїЇєЄґҐ_\\- ]"), "")
            .replace(Regex("\\s+"), "-")
            .lowercase()
            .ifBlank { "custom-map" }

        val title = if (AppLocaleManager.currentLanguage.value == AppLanguage.UKRAINIAN) {
            "Зберегти карту міст та маршрут"
        } else {
            "Save City Map & Solution"
        }
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            directory = mapsDir.absolutePath
            file = "$sanitized.json"
            filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
            isVisible = true
        }

        val chosenFile = dialog.file ?: return null
        val chosenDir = dialog.directory ?: mapsDir.absolutePath
        val fileName = if (chosenFile.endsWith(".json", ignoreCase = true)) chosenFile else "$chosenFile.json"
        val targetFile = File(chosenDir, fileName)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(MapJsonCodec.encode(map), Charsets.UTF_8)
        return targetFile.absolutePath
    }

    actual fun loadMapWithDialog(): Pair<TaskMap, String>? {
        val mapsDir = ensureMapsDir()
        val title = if (AppLocaleManager.currentLanguage.value == AppLanguage.UKRAINIAN) {
            "Завантажити карту міст"
        } else {
            "Load City Map"
        }
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            directory = mapsDir.absolutePath
            filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
            isVisible = true
        }

        val chosenFile = dialog.file ?: return null
        val chosenDir = dialog.directory ?: mapsDir.absolutePath
        val sourceFile = File(chosenDir, chosenFile)
        if (!sourceFile.exists() || !sourceFile.isFile) return null

        val json = sourceFile.readText(Charsets.UTF_8)
        val loadedMap = MapJsonCodec.decode(json)
        return loadedMap to sourceFile.name
    }
}
