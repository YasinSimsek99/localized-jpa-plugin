package com.localizedjpa.intellij.migration.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jooq.SQLDialect

object ConfigAnalyzer {

    fun detectDatabaseDialect(project: Project): SQLDialect? {
        val configFiles = findConfigFiles(project)

        for (file in configFiles) {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8).lowercase()
            
            // Check for explicit JPA dialect setting
            if (content.contains("org.hibernate.dialect.postgresqldialect") || 
                content.contains("database-platform: org.hibernate.dialect.postgre")) {
                return SQLDialect.POSTGRES
            }
            if (content.contains("org.hibernate.dialect.mysqldialect") || 
                content.contains("database-platform: org.hibernate.dialect.mysql")) {
                return SQLDialect.MYSQL
            }
            if (content.contains("org.hibernate.dialect.mariadbdialect") || 
                content.contains("database-platform: org.hibernate.dialect.mariadb")) {
                return SQLDialect.MARIADB
            }

            // Check JDBC URL
            if (content.contains("jdbc:postgresql:")) {
                return SQLDialect.POSTGRES
            }
            if (content.contains("jdbc:mysql:")) {
                return SQLDialect.MYSQL
            }
            if (content.contains("jdbc:mariadb:")) {
                return SQLDialect.MARIADB
            }
            if (content.contains("jdbc:oracle:")) {
                return null // Oracle is not supported; fallback to user's dialect selection
            }
        }
        
        return null // If no clear dialect is found, return null so we can fallback to POSTGRES default
    }

    fun detectFlywayLocation(project: Project): String {
        val configFiles = findConfigFiles(project)
        val defaultLocation = "src/main/resources/db/migration"

        for (file in configFiles) {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)
            val lines = content.split("\n", "\r\n")
            
            for (line in lines) {
                // Properties format: spring.flyway.locations=classpath:custom/db/migration
                if (line.trim().startsWith("spring.flyway.locations")) {
                    val value = line.substringAfter("=").substringAfter("locations:").trim()
                    return parseLocationValue(value) ?: defaultLocation
                }
                
                // YAML format: locations: classpath:custom/db/migration (assuming under spring.flyway)
                if (line.trim().startsWith("locations:")) {
                    val value = line.substringAfter("locations:").trim()
                    // Very simple heuristic to ensure it's likely a flyway location
                    if (value.startsWith("classpath:") || value.startsWith("filesystem:")) {
                        return parseLocationValue(value) ?: defaultLocation
                    }
                }
            }
        }
        return defaultLocation
    }

    private fun parseLocationValue(value: String): String? {
        // e.g. classpath:db/migration, filesystem:src/main/resources/db/migration
        var cleanValue = value.removePrefix("-").trim() // Remove YAML list dash if present
        cleanValue = cleanValue.removeSurrounding("\"").removeSurrounding("'")
        
        if (cleanValue.startsWith("classpath:")) {
            val path = cleanValue.removePrefix("classpath:").removePrefix("/")
            return "src/main/resources/$path"
        } else if (cleanValue.startsWith("filesystem:")) {
            val path = cleanValue.removePrefix("filesystem:").removePrefix("/")
            return path
        }
        return null
    }

    private fun findConfigFiles(project: Project): List<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val ymlFiles = FilenameIndex.getAllFilesByExt(project, "yml", scope)
        val yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope)
        val propertiesFiles = FilenameIndex.getAllFilesByExt(project, "properties", scope)

        return (ymlFiles + yamlFiles + propertiesFiles).filter {
            it.name.startsWith("application") || it.name.startsWith("bootstrap")
        }.sortedByDescending { it.name } // application.yml takes precedence
    }
}
