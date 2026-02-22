package com.localizedjpa.intellij.migration.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.localizedjpa.intellij.migration.psi.model.EntityMetadata
import com.localizedjpa.intellij.migration.psi.model.LocalizedFieldMetadata

object EntityPsiAnalyzer {

    private const val ENTITY_ANNOTATION = "jakarta.persistence.Entity"
    private const val JAVAX_ENTITY_ANNOTATION = "javax.persistence.Entity"
    private const val TABLE_ANNOTATION = "jakarta.persistence.Table"
    private const val JAVAX_TABLE_ANNOTATION = "javax.persistence.Table"
    private const val COLUMN_ANNOTATION = "jakarta.persistence.Column"
    private const val JAVAX_COLUMN_ANNOTATION = "javax.persistence.Column"
    private const val LOB_ANNOTATION = "jakarta.persistence.Lob"
    private const val JAVAX_LOB_ANNOTATION = "javax.persistence.Lob"
    private const val LOCALIZED_ANNOTATION = "com.shefim.localizedjpa.annotation.Localized"

    fun isEntityClass(psiClass: PsiClass): Boolean {
        return hasAnyAnnotation(psiClass, ENTITY_ANNOTATION, JAVAX_ENTITY_ANNOTATION, shortName = "Entity")
    }

    fun extractMetadata(psiClass: PsiClass): EntityMetadata? {
        if (!isEntityClass(psiClass)) return null

        val className = psiClass.name ?: return null
        val tableName = extractTableName(psiClass) ?: className.toSnakeCase()
        val schema = extractSchemaName(psiClass)

        val localizedFields = mutableListOf<LocalizedFieldMetadata>()

        for (field in psiClass.allFields) {
            if (hasAnyAnnotation(field, LOCALIZED_ANNOTATION, shortName = "Localized")) {
                val fieldName = field.name
                val columnName = extractColumnName(field) ?: fieldName.toSnakeCase()
                val type = field.type.presentableText
                val length = extractColumnLength(field)
                val isLob = hasAnyAnnotation(field, LOB_ANNOTATION, JAVAX_LOB_ANNOTATION, shortName = "Lob")
                localizedFields.add(LocalizedFieldMetadata(fieldName, columnName, type, length, isLob))
            }
        }

        return EntityMetadata(psiClass, className, tableName, schema, localizedFields)
    }

    private fun extractTableName(psiClass: PsiClass): String? {
        val tableAnnotation = getAnnotationRobust(psiClass, "Table", TABLE_ANNOTATION, JAVAX_TABLE_ANNOTATION)
        return extractAttributeValue(tableAnnotation, "name")
    }

    private fun extractSchemaName(psiClass: PsiClass): String? {
        val tableAnnotation = getAnnotationRobust(psiClass, "Table", TABLE_ANNOTATION, JAVAX_TABLE_ANNOTATION)
        return extractAttributeValue(tableAnnotation, "schema")
    }

    private fun extractColumnName(psiField: PsiField): String? {
        val columnAnnotation = getAnnotationRobust(psiField, "Column", COLUMN_ANNOTATION, JAVAX_COLUMN_ANNOTATION)
        return extractAttributeValue(columnAnnotation, "name")
    }

    private fun extractColumnLength(psiField: PsiField): Int? {
        val columnAnnotation = getAnnotationRobust(psiField, "Column", COLUMN_ANNOTATION, JAVAX_COLUMN_ANNOTATION)
        return extractAttributeValue(columnAnnotation, "length")?.toIntOrNull()
    }

    private fun hasAnyAnnotation(modifierListOwner: com.intellij.psi.PsiModifierListOwner, vararg fqns: String, shortName: String): Boolean {
        for (fqn in fqns) {
            if (modifierListOwner.hasAnnotation(fqn)) return true
        }
        return modifierListOwner.annotations.any { 
            it.qualifiedName in fqns || 
            it.nameReferenceElement?.referenceName == shortName || 
            it.qualifiedName?.endsWith(".$shortName") == true 
        }
    }

    private fun getAnnotationRobust(psiElement: com.intellij.psi.PsiModifierListOwner, shortName: String, vararg annotationNames: String): PsiAnnotation? {
        for (name in annotationNames) {
            val annotation = psiElement.getAnnotation(name)
            if (annotation != null) {
                return annotation
            }
        }
        return psiElement.annotations.find { 
            it.qualifiedName in annotationNames || 
            it.nameReferenceElement?.referenceName == shortName || 
            it.qualifiedName?.endsWith(".$shortName") == true 
        }
    }

    private fun extractAttributeValue(annotation: PsiAnnotation?, attributeName: String): String? {
        if (annotation == null) return null
        val value = annotation.findAttributeValue(attributeName)
        if (value != null) {
            val text = value.text
            // Remove quotes if present
            if (text.startsWith("\"") && text.endsWith("\"")) {
                val unquoted = text.substring(1, text.length - 1)
                return unquoted.takeIf { it.isNotBlank() }
            }
            return text.takeIf { it.isNotBlank() && it != "null" }
        }
        return null
    }

    private val SNAKE_UPPER_SEQ = Regex("([A-Z]+)([A-Z][a-z])")
    private val SNAKE_LOWER_TO_UPPER = Regex("([a-z0-9])([A-Z])")

    private fun String.toSnakeCase(): String {
        if (this.isBlank()) return this
        
        // Match LocalizedJpa's StringUtils.toSnakeCase behavior:
        // Pass 1: separate consecutive uppercase sequences from the following title-case word
        //   e.g. "HTMLParser" → "HTML_Parser"
        // Pass 2: insert underscore between a lowercase/digit and the next uppercase letter
        //   e.g. "camelCase" → "camel_Case", "myHTTPSUrl" → "my_HTTPS_Url"
        
        var result = this.replace(SNAKE_UPPER_SEQ, "$1_$2")
        result = result.replace(SNAKE_LOWER_TO_UPPER, "$1_$2")
        return result.lowercase()
    }
}
