package com.localizedjpa.intellij.migration.psi.model

import com.intellij.psi.PsiClass

data class EntityMetadata(
    val psiClass: PsiClass,
    val className: String,
    val tableName: String,
    val schema: String?,
    val localizedFields: List<LocalizedFieldMetadata>
)

data class LocalizedFieldMetadata(
    val fieldName: String,
    val columnName: String,
    val type: String,
    val length: Int? = null,
    val isLob: Boolean = false
)
