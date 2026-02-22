package com.localizedjpa.intellij.migration.generator

import com.localizedjpa.intellij.migration.analyzer.MigrationDiffAnalyzer
import com.localizedjpa.intellij.migration.psi.model.EntityMetadata
import com.localizedjpa.intellij.migration.psi.model.LocalizedFieldMetadata
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.conf.Settings
import org.jooq.conf.RenderKeywordStyle

object SqlMigrationGenerator {

    fun generateSql(
        entityMetadata: EntityMetadata,
        diffResult: MigrationDiffAnalyzer.DiffResult,
        dialect: SQLDialect = SQLDialect.POSTGRES
    ): String {
        if (diffResult.missingFields.isEmpty()) {
            return "-- No new localized fields found. Database is up to date."
        }

        val settings = Settings()
            .withRenderFormatted(true)
            .withRenderKeywordStyle(RenderKeywordStyle.LOWER)
        val dsl: DSLContext = DSL.using(dialect, settings)
        val translationTableName = "${entityMetadata.tableName}_translations"
        
        // If there are no existing column names, we assume the table doesn't exist and needs to be CREATED
        val isCreateTable = diffResult.existingColumnNames.isEmpty()

        // Append extra newlines at the bottom for breathing room in the preview
        return if (isCreateTable) {
            generateCreateTableSql(dsl, translationTableName, entityMetadata.tableName, entityMetadata.schema, diffResult.missingFields) + "\n\n"
        } else {
            generateAlterTableSql(dsl, translationTableName, entityMetadata.schema, diffResult.missingFields) + "\n\n"
        }
    }

    private fun getTable(schema: String?, tableName: String): org.jooq.Table<*> {
        return if (schema.isNullOrBlank()) {
            DSL.table(DSL.name(tableName))
        } else {
            DSL.table(DSL.name(schema, tableName))
        }
    }

    private fun generateCreateTableSql(
        dsl: DSLContext,
        translationTableName: String,
        baseTableName: String,
        schema: String?,
        fields: List<LocalizedFieldMetadata>
    ): String {
        val foreignKeyCol = "${baseTableName}_id"
        val translatedTable = getTable(schema, translationTableName)
        
        var createTableStep = dsl.createTable(translatedTable)
            .column("id", SQLDataType.BIGINT.identity(true))
            .column(foreignKeyCol, SQLDataType.BIGINT.nullable(false))
            .column("locale", SQLDataType.VARCHAR(10).nullable(false))

        for (field in fields) {
            val dataType = mapJavaTypeToSqlType(field.type, field.length, field.isLob, dsl.dialect())
            createTableStep = createTableStep.column(field.columnName, dataType)
        }

        val createTableSql = createTableStep.getSQL(org.jooq.conf.ParamType.INLINED)
        
        val baseTableNameObj = if (schema.isNullOrBlank()) DSL.name(baseTableName) else DSL.name(schema, baseTableName)
        
        val addForeignKeySql = dsl.alterTable(translatedTable)
            .add(DSL.constraint("fk_${translationTableName}_${baseTableName}")
                .foreignKey(DSL.name(foreignKeyCol))
                .references(baseTableNameObj, DSL.name("id")))
            .getSQL(org.jooq.conf.ParamType.INLINED)
            .replace(" add constraint ", "\n  add constraint ")
            .replace(" foreign key ", "\n    foreign key ")
            .replace(" references ", "\n    references ")
            
        val addUniqueConstraintSql = dsl.alterTable(translatedTable)
            .add(DSL.constraint("uk_${translationTableName}_locale")
                .unique(foreignKeyCol, "locale"))
            .getSQL(org.jooq.conf.ParamType.INLINED)
            .replace(" add constraint ", "\n  add constraint ")
            .replace(" unique ", "\n    unique ")

        return StringBuilder().apply {
            append("-- Create translation table for $baseTableName\n")
            append(createTableSql).append(";\n\n")
            append("-- Add foreign key constraint\n")
            append(addForeignKeySql).append(";\n\n")
            append("-- Add unique constraint for entity and locale\n")
            append(addUniqueConstraintSql).append(";")
        }.toString()
    }

    private fun generateAlterTableSql(
        dsl: DSLContext,
        translationTableName: String,
        schema: String?,
        missingFields: List<LocalizedFieldMetadata>
    ): String {
        val translatedTable = getTable(schema, translationTableName)
        val schemaPrefix = if (schema.isNullOrBlank()) "" else "$schema."
        val sqlBuilder = StringBuilder()
        sqlBuilder.append("-- Add new localized columns to $schemaPrefix$translationTableName\n")

        for (field in missingFields) {
            val dataType = mapJavaTypeToSqlType(field.type, field.length, field.isLob, dsl.dialect())
            val alterSql = dsl.alterTable(translatedTable)
                .addColumn(field.columnName, dataType)
                .getSQL(org.jooq.conf.ParamType.INLINED)
                .replace(" add ", "\n  add ")
            
            sqlBuilder.append("$alterSql;\n")
        }

        return sqlBuilder.toString()
    }

    private fun mapJavaTypeToSqlType(javaType: String, length: Int?, isLob: Boolean, dialect: SQLDialect): org.jooq.DataType<*> {
        val lobType = if (dialect == SQLDialect.POSTGRES) {
            // Hibernate expects 'oid' for @Lob in Postgres, not 'text'
            org.jooq.impl.DefaultDataType(SQLDialect.POSTGRES, SQLDataType.BIGINT, "oid")
        } else {
            SQLDataType.CLOB
        }
        
        return when (javaType) {
            "String" -> if (isLob) lobType else SQLDataType.VARCHAR(length ?: 255)
            "Integer", "int" -> SQLDataType.INTEGER
            "Long", "long" -> SQLDataType.BIGINT
            "Boolean", "boolean" -> SQLDataType.BOOLEAN
            "Double", "double" -> SQLDataType.DOUBLE
            "Float", "float" -> SQLDataType.REAL
            "LocalDate" -> SQLDataType.LOCALDATE
            "LocalDateTime" -> SQLDataType.LOCALDATETIME
            else -> if (isLob) lobType else SQLDataType.VARCHAR(length ?: 255) // Fallback
        }
    }
}
