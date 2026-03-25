package com.projectember.mobile.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Core logic for master backup: write, rotate, and report.
 *
 * Both [NightlyBackupWorker] and the SettingsViewModel call [performBackup] so the
 * manual "Backup Now" and the scheduled nightly backup share a single implementation.
 *
 * Backup location : Documents/Ember/Backups/
 * Filename format : ember_backup_YYYY-MM-DD_HHMM.json
 *
 * Retention rule  : after a NEW backup is successfully written, list all
 *                   ember_backup_*.json files in that folder (sorted newest-first
 *                   by name, which is safe because the name is the timestamp),
 *                   and delete every file beyond the keep-count.  Deletion is
 *                   performed ONLY after the new backup succeeds — existing good
 *                   backups are never touched on failure.
 */
class NightlyBackupEngine(
    private val context: Context,
    private val backupManager: BackupManager,
    private val store: NightlyBackupStore
) {

    private val BACKUP_NAME_PATTERN = Regex("""ember_backup_\d{4}-\d{2}-\d{2}_\d{4}\.json""")
    private val TIMESTAMP_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    /** Folder path used in both MediaStore (API 29+) and legacy File API. */
    private val RELATIVE_FOLDER = "Documents/Ember/Backups"

    /**
     * Run a full backup: build payload → write file → apply rotation → record timestamp.
     * Always safe to call; fails gracefully and never deletes existing backups on error.
     */
    suspend fun performBackup(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = backupManager.buildBackupBytes().getOrThrow()

            val filename = "ember_backup_${TIMESTAMP_FMT.format(LocalDateTime.now())}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(bytes, filename)
            } else {
                writeViaFile(bytes, filename)
            }

            // Rotation: only after the new file is safely on disk
            val keepCount = store.getRetentionCount()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rotateViaMediaStore(keepCount)
            } else {
                rotateViaFile(keepCount)
            }

            store.setLastSuccessMs(System.currentTimeMillis())
            filename
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    private fun writeViaMediaStore(bytes: ByteArray, filename: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_FOLDER)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), values
        ) ?: error("MediaStore refused to create backup file")

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(bytes)
        } ?: error("Could not open output stream for $filename")
    }

    private fun writeViaFile(bytes: ByteArray, filename: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Ember/Backups"
        )
        dir.mkdirs()
        File(dir, filename).writeBytes(bytes)
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    private fun rotateViaMediaStore(keepCount: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        // RELATIVE_PATH stored by MediaStore always has a trailing slash.
        val selection =
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$RELATIVE_FOLDER/", "ember_backup_%.json")
        // Sort newest-first by name (ISO date → lexicographic order == chronological).
        val sortOrder = "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"

        val toDelete = mutableListOf<Long>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            var kept = 0
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                // Extra safety: only touch files that match our exact pattern
                if (!BACKUP_NAME_PATTERN.matches(name)) continue
                kept++
                if (kept > keepCount) toDelete.add(cursor.getLong(idCol))
            }
        }

        toDelete.forEach { id ->
            val uri = ContentUris.withAppendedId(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id
            )
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
    }

    private fun rotateViaFile(keepCount: Int) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Ember/Backups"
        )
        if (!dir.exists()) return
        val files = dir.listFiles { f ->
            BACKUP_NAME_PATTERN.matches(f.name)
        } ?: return
        files.sortedByDescending { it.name }
            .drop(keepCount)
            .forEach { it.delete() }
    }
}
