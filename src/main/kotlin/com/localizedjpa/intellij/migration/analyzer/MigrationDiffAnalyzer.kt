package com.localizedjpa.intellij.migration.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.localizedjpa.intellij.migration.psi.model.EntityMetadata
import com.localizedjpa.intellij.migration.psi.model.LocalizedFieldMetadata

object MigrationDiffAnalyzer {

    data class DiffResult(
        val existingColumnNames: Set<String>,
        val missingFields: List<LocalizedFieldMetadata>
    )

    fun analyze(project: Project, entityMetadata: EntityMetadata): DiffResult {
        val translationTableName = "${entityMetadata.tableName}_translations"
        val migrationFiles = getMigrationFiles(project)

        val existingColumnNames = mutableSetOf<String>()

        if (migrationFiles.isNotEmpty()) {
            val sqlContentBuilder = StringBuilder()
            
            // Limit search to files that might contain the translation table
            for (file in migrationFiles) {
                val content = String(file.contentsToByteArray(), Charsets.UTF_8).lowercase()
                
                // Split SQL file into statements by semicolon to isolate contexts
                val statements = content.split(";")
                for (stmt in statements) {
                    if (stmt.contains(translationTableName.lowercase())) {
                        sqlContentBuilder.append(stmt).append(";")
                    }
                }
            }
            
            val sqlContent = sqlContentBuilder.toString()

            // Try to find which fields already exist in the SQL content
            for (field in entityMetadata.localizedFields) {
                val safeColumnName = Regex.escape(field.columnName.lowercase())
                
                // 1. MatchCREATE TABLE syntax (with or without quotes): "column" type OR column type
                // 2. Match ALTER TABLE ADD syntax: add "column" OR add column "column"
                
                val pattern = """(?:add\s+(?:column\s+)?|create\s+table.*?\(\s*(?:.*?,s*)*)["`]?($safeColumnName)["`]?\b"""
                // Simpler, foolproof approach: since we isolate `stmt.contains(translationTableName)`,
                // ANY definition of the column name preceded by typical DDL keywords or just existing in the CREATE block is safe to assume as existing, EXCEPT when preceded by 'references ', 'index ', 'key ', 'constraint '
                
                val isDefinedInCreate = Regex("""create\s+table.*?\b["`]?$safeColumnName["`]?\b.*?(?:varchar|bigint|int|boolean|double|real|date|timestamp|text|clob|oid|char)""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(sqlContent)
                val isAddedInAlter = Regex("""add\s+(?:column\s+)?["`]?$safeColumnName["`]?\b""").containsMatchIn(sqlContent)
                
                if (isDefinedInCreate || isAddedInAlter) {
                    existingColumnNames.add(field.columnName)
                }
            }
        }

        val missingFields = entityMetadata.localizedFields.filter { 
            !existingColumnNames.contains(it.columnName) 
        }

        return DiffResult(existingColumnNames, missingFields)
    }

    fun getNextVersionNumber(project: Project): Int {
        val migrationFiles = getMigrationFiles(project)
        var maxVersion = 0
        val versionRegex = Regex("^V(\\d+)__.*", RegexOption.IGNORE_CASE)
        
        for (file in migrationFiles) {
            val match = versionRegex.find(file.name)
            if (match != null) {
                val version = match.groupValues[1].toIntOrNull() ?: 0
                if (version > maxVersion) {
                    maxVersion = version
                }
            }
        }
        return if (maxVersion > 0) maxVersion + 1 else 1
    }

    fun getMigrationFileNames(project: Project): Set<String> {
        return getMigrationFiles(project).map { it.name }.toSet()
    }

    private fun getMigrationFiles(project: Project): List<VirtualFile> {
        // Find all .sql files in the project, we can filter them by path if needed
        val scope = GlobalSearchScope.projectScope(project)
        val sqlFiles = FilenameIndex.getAllFilesByExt(project, "sql", scope)
        
        return sqlFiles.filter { file -> 
            val path = file.path
            path.contains("/db/migration") || path.contains("\\db\\migration")
        }.sortedBy { it.name }
    }
}
