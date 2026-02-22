package com.localizedjpa.intellij.migration.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.localizedjpa.intellij.migration.analyzer.ConfigAnalyzer
import com.localizedjpa.intellij.migration.analyzer.MigrationDiffAnalyzer
import com.localizedjpa.intellij.migration.generator.MigrationFileWriter
import com.localizedjpa.intellij.migration.psi.EntityPsiAnalyzer
import com.localizedjpa.intellij.migration.ui.MigrationPreviewDialog

object MigrationActionExecutor {

    fun execute(project: Project, psiClass: PsiClass) {
        val metadata = EntityPsiAnalyzer.extractMetadata(psiClass)

        if (metadata != null) {
            val diffResult = MigrationDiffAnalyzer.analyze(project, metadata)
            val nextVersion = MigrationDiffAnalyzer.getNextVersionNumber(project)
            val detectedDialect = ConfigAnalyzer.detectDatabaseDialect(project)
            val detectedFlywayPath = ConfigAnalyzer.detectFlywayLocation(project)

            val dialog = MigrationPreviewDialog(project, metadata, diffResult, nextVersion, detectedDialect, detectedFlywayPath)
            if (dialog.showAndGet()) {
                val generatedFileName = dialog.getFileName()
                val sqlContent = dialog.getGeneratedSql()
                val targetDirectory = dialog.getTargetDirectory()

                val saved = MigrationFileWriter.saveToFileAndOpen(project, targetDirectory, generatedFileName, sqlContent)
                
                if (!saved) {
                    Messages.showErrorDialog(project, "Failed to create or write to the migration file.", "File Operation Failed")
                }
            }
        } else {
            Messages.showErrorDialog(project, "Could not extract metadata from ${psiClass.name}", "Error")
        }
    }
}
