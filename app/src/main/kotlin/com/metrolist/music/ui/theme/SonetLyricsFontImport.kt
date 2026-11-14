package com.metrolist.music.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/** User fonts live in private app storage, not in public Downloads. */
data class ImportedLyricsFont(val preferenceValue: String, val displayName: String)

private const val MAX_FONT_BYTES = 6 * 1024 * 1024
private val SAFE_FONT_FILE = Regex("font_[0-9]+\\.(ttf|otf)")

fun storedLyricsFont(context: Context, preference: String): File? {
    if (!preference.startsWith("Custom:")) return null
    val name = preference.removePrefix("Custom:")
    if (!SAFE_FONT_FILE.matches(name)) return null
    return File(File(context.filesDir, "sonet_lyrics_fonts"), name)
}

/**
 * Import a user-selected OpenType/TrueType font with a capped, validated copy.
 * Fonts are never downloaded or loaded from third-party paths.
 */
suspend fun importLyricsFont(context: Context, uri: Uri): ImportedLyricsFont =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: throw IllegalArgumentException("No filename was provided")
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension == "ttf" || extension == "otf") { "Select a .ttf or .otf font" }

        val folder = File(context.filesDir, "sonet_lyrics_fonts")
        require(folder.isDirectory || folder.mkdirs()) { "Unable to create font storage" }
        val name = "font_${System.nanoTime()}.$extension"
        val outputFile = File(folder, name)
        try {
            val stream = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Unable to open font")
            stream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var count = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= MAX_FONT_BYTES) { "Font must be 6 MB or smaller" }
                        output.write(buffer, 0, read)
                    }
                    require(count >= 12) { "Invalid font file" }
                }
            }

            // Check the font's SFNT header before asking Android to parse it.
            val signature = ByteArray(4)
            FileInputStream(outputFile).use { input ->
                require(input.read(signature) == 4) { "Invalid font header" }
            }
            val validHeader = signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                String(signature, Charsets.US_ASCII) in setOf("OTTO", "true", "typ1")
            require(validHeader) { "Unsupported font format" }
            Typeface.createFromFile(outputFile) // Fail early if Android cannot open it.

            ImportedLyricsFont(
                preferenceValue = "Custom:$name",
                displayName = displayName.removeSuffix(".$extension").take(48),
            )
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
    }
