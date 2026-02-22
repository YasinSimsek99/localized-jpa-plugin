package com.localizedjpa.intellij.migration.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.io.IOException

object MigrationFileWriter {

    /**
     * Attempts to find the target folder or creates it if it doesn't exist.
     * Then it writes the SQL content to the specified file and opens it in the editor.
     */
    fun saveToFileAndOpen(
        project: Project,
        targetDirectoryPath: String,
        fileName: String,
        sqlContent: String
    ): Boolean {
        var success = false
        
        ApplicationManager.getApplication().runWriteAction {
            try {
                // Ensure the physical directory exists first
                val targetIoFile = File(targetDirectoryPath)
                if (!targetIoFile.exists()) {
                    targetIoFile.mkdirs()
                }

                // Let IntelliJ VFS find or refresh the directory
                val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetIoFile)
                
                if (targetDir == null) {
                    return@runWriteAction
                }
                
                // Create the SQL file
                val sqlFile: VirtualFile = targetDir.createChildData(this, fileName)
                VfsUtil.saveText(sqlFile, sqlContent)
                
                // Open file in Editor
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openFile(sqlFile, true)
                }
                
                success = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        
        return success
    }
}
