package com.localizedjpa.intellij.migration.ui

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.localizedjpa.intellij.migration.analyzer.MigrationDiffAnalyzer
import com.localizedjpa.intellij.migration.generator.SqlMigrationGenerator
import com.localizedjpa.intellij.migration.psi.model.EntityMetadata
import com.localizedjpa.intellij.migration.psi.model.LocalizedFieldMetadata
import org.jooq.SQLDialect
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MigrationPreviewDialog(
    private val project: Project,
    private val metadata: EntityMetadata,
    private val diffResult: MigrationDiffAnalyzer.DiffResult,
    private val nextVersion: Int,
    private val detectedDialect: SQLDialect?,
    private val detectedFlywayPath: String
) : DialogWrapper(project) {

    private lateinit var dialectComboBox: JComboBox<SQLDialect>
    private lateinit var directoryBrowser: TextFieldWithBrowseButton
    private lateinit var filenameTextField: JTextField
    private lateinit var sqlEditorView: EditorTextField
    private lateinit var ignoreMigrationsCheckBox: JBCheckBox
    private val fieldCheckBoxes = mutableMapOf<LocalizedFieldMetadata, JBCheckBox>()

    init {
        init()
        title = "Generate LocalizedJPA Migration"
        setOKButtonText("Generate File")
        updateFieldVisibility()
        updateSqlPreview()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // Top Header using FormBuilder for perfect alignment
        dialectComboBox = JComboBox(arrayOf(SQLDialect.POSTGRES, SQLDialect.MYSQL, SQLDialect.MARIADB))
        
        val dialectWrapper = Box.createHorizontalBox()
        dialectWrapper.add(dialectComboBox)
        
        ignoreMigrationsCheckBox = JBCheckBox("Ignore Migrations (Full Create)", false)
        ignoreMigrationsCheckBox.addActionListener { 
            updateFieldVisibility()
            updateSqlPreview()
        }
        
        dialectWrapper.add(Box.createHorizontalStrut(15))
        dialectWrapper.add(ignoreMigrationsCheckBox)
        
        if (detectedDialect != null) {
            dialectComboBox.selectedItem = detectedDialect
            val infoLabel = JBLabel("<html><font color='gray'>&nbsp;<i>(Auto-detected)</i></font></html>")
            dialectWrapper.add(infoLabel)
        } else {
            dialectComboBox.selectedItem = SQLDialect.POSTGRES
        }
        
        dialectComboBox.addActionListener { updateSqlPreview() }

        directoryBrowser = TextFieldWithBrowseButton()
        val defaultDir = project.basePath + "/" + detectedFlywayPath
        directoryBrowser.text = defaultDir.replace("//", "/")
        directoryBrowser.addBrowseFolderListener(
            "Select Migration Directory",
            "Choose where the generated SQL file should be saved",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        filenameTextField = JTextField("V${nextVersion}__add_${metadata.tableName}_translations.sql")
        val filenamePanel = JPanel(BorderLayout(10, 0))
        filenamePanel.add(filenameTextField, BorderLayout.CENTER)
        
        val warningLabel = JBLabel("<html><font color='red'>⚠️ File already exists!</font></html>")
        warningLabel.isVisible = false
        filenamePanel.add(warningLabel, BorderLayout.EAST)
        
        val directoryChangeListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkFile()
            override fun removeUpdate(e: DocumentEvent?) = checkFile()
            override fun changedUpdate(e: DocumentEvent?) = checkFile()
            
            private fun checkFile() {
                val currentDirPath = directoryBrowser.text
                val currentFile = java.io.File(currentDirPath, filenameTextField.text)
                warningLabel.isVisible = currentFile.exists()
            }
        }
        directoryBrowser.textField.document.addDocumentListener(directoryChangeListener)
        filenameTextField.document.addDocumentListener(directoryChangeListener)
        
        // Initial check
        val initialFile = java.io.File(directoryBrowser.text, filenameTextField.text)
        warningLabel.isVisible = initialFile.exists()

        val northContainer = com.intellij.util.ui.FormBuilder.createFormBuilder()
            // Using FormBuilder's native padding and alignment
            .addLabeledComponent(JBLabel("Dialect:"), dialectWrapper)
            .addLabeledComponent(JBLabel("Directory:"), directoryBrowser)
            .addLabeledComponent(JBLabel("File Name:"), filenamePanel)
            .panel
            
        northContainer.border = BorderFactory.createEmptyBorder(5, 5, 10, 5)

        mainPanel.add(northContainer, BorderLayout.NORTH)

        // Splitter for Left (Tree/List) and Right (Editor)
        val splitter = JBSplitter(false, 0.3f)

        // Left Panel (Fields to add)
        val leftPanel = JPanel(BorderLayout())
        leftPanel.border = JBUI.Borders.empty(5)
        
        val leftHeader = JPanel(BorderLayout())
        // Match the right header's bottom margin to align the content below
        leftHeader.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
        
        val leftHeaderLabel = JBLabel("Missing Localized Fields:")
        leftHeaderLabel.font = leftHeaderLabel.font.deriveFont(java.awt.Font.BOLD)
        leftHeader.add(leftHeaderLabel, BorderLayout.WEST)
        
        val selectionActionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        val selectAllBtn = ActionLink("Select All") {
            fieldCheckBoxes.values.forEach { it.isSelected = true }
            updateSqlPreview()
        }
        val deselectAllBtn = ActionLink("None") {
            fieldCheckBoxes.values.forEach { it.isSelected = false }
            updateSqlPreview()
        }
        
        selectionActionsPanel.add(selectAllBtn)
        selectionActionsPanel.add(deselectAllBtn)
        leftHeader.add(selectionActionsPanel, BorderLayout.EAST)
        
        leftPanel.add(leftHeader, BorderLayout.NORTH)

        val fieldsListPanel = JPanel()
        fieldsListPanel.layout = BoxLayout(fieldsListPanel, BoxLayout.Y_AXIS)

        for (field in metadata.localizedFields) {
            val isMissing = diffResult.missingFields.contains(field)
            val cb = JBCheckBox("${field.fieldName} (${field.columnName})", isMissing)
            cb.addActionListener { updateSqlPreview() }
            fieldCheckBoxes[field] = cb
            fieldsListPanel.add(cb)
        }
        
        val scrollPane = JBScrollPane(fieldsListPanel)
        leftPanel.add(scrollPane, BorderLayout.CENTER)

        splitter.firstComponent = leftPanel

        // Right Panel (SQL Preview)
        val rightPanel = JPanel(BorderLayout())
        rightPanel.border = JBUI.Borders.empty(5)
        
        val sqlFileType = FileTypeManager.getInstance().getFileTypeByExtension("sql")
        val document = com.intellij.openapi.editor.EditorFactory.getInstance().createDocument("")
        sqlEditorView = EditorTextField(document, project, sqlFileType, false, false)
        sqlEditorView.addSettingsProvider { editor ->
            editor.settings.isUseSoftWraps = true
            // Configure the editor's own internal scroll pane (EditorTextField is a real editor, 
            // outer JBScrollPane wrappers don't receive mouse events from it)
            editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        val rightHeader = JPanel(BorderLayout())
        // Match the left header's bottom margin to align the content below
        rightHeader.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
        
        val rightHeaderLabel = JBLabel("SQL Preview:")
        rightHeaderLabel.font = rightHeaderLabel.font.deriveFont(java.awt.Font.BOLD)
        rightHeader.add(rightHeaderLabel, BorderLayout.WEST)
        
        val sqlPreviewActions = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        val copyActionLink = ActionLink("Copy", java.awt.event.ActionListener {
            val selection = StringSelection(sqlEditorView.text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            val btn = it.source as ActionLink
            btn.text = "Copied!"
            Timer(2000) { btn.text = "Copy" }.apply {
                isRepeats = false
                start()
            }
        })
        sqlPreviewActions.add(copyActionLink)
        rightHeader.add(sqlPreviewActions, BorderLayout.EAST)
        
        rightPanel.add(rightHeader, BorderLayout.NORTH)
        
        rightPanel.add(sqlEditorView, BorderLayout.CENTER)

        splitter.secondComponent = rightPanel
        splitter.isShowDividerControls = true // Make the splitter obvious

        mainPanel.add(splitter, BorderLayout.CENTER)
        
        mainPanel.preferredSize = java.awt.Dimension(800, 500)
        return mainPanel
    }

    private fun updateFieldVisibility() {
        val ignoreMigrations = ignoreMigrationsCheckBox.isSelected
        for ((field, cb) in fieldCheckBoxes) {
            val isMissing = diffResult.missingFields.contains(field)
            if (ignoreMigrations) {
                cb.isVisible = true
                cb.isSelected = true
            } else {
                cb.isVisible = isMissing
                cb.isSelected = isMissing
            }
        }
    }

    private fun updateSqlPreview() {
        val selectedFields = fieldCheckBoxes.entries
            .filter { it.value.isSelected && it.value.isVisible }
            .map { it.key }

        val dynamicExistingColumns = if (ignoreMigrationsCheckBox.isSelected) {
            emptySet()
        } else {
            diffResult.existingColumnNames
        }

        val dynamicDiffResult = MigrationDiffAnalyzer.DiffResult(
            dynamicExistingColumns,
            selectedFields
        )

        val selectedDialect = dialectComboBox.selectedItem as SQLDialect
        val sql = SqlMigrationGenerator.generateSql(metadata, dynamicDiffResult, selectedDialect)
        
        // Disable Generate button if no visible fields are selected
        isOKActionEnabled = selectedFields.isNotEmpty()
        
        if (::sqlEditorView.isInitialized) {
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                sqlEditorView.document.setText(sql)
            }
        }
    }

    fun getTargetDirectory(): String = directoryBrowser.text
    fun getFileName(): String = filenameTextField.text
    fun getGeneratedSql(): String = sqlEditorView.text
}
