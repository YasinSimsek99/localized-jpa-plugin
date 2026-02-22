package com.localizedjpa.intellij.migration.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.localizedjpa.intellij.migration.psi.EntityPsiAnalyzer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.asJava.toLightClass

class GenerateMigrationAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || psiFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val psiClass = getPsiClassFromContext(e)
        
        // Show action only if it's an entity class
        e.presentation.isEnabledAndVisible = psiClass != null && EntityPsiAnalyzer.isEntityClass(psiClass)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = getPsiClassFromContext(e) ?: return
        MigrationActionExecutor.execute(project, psiClass)
    }

    private fun getPsiClassFromContext(e: AnActionEvent): PsiClass? {
        var context = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (context == null) {
            val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
            context = psiFile.findElementAt(editor.caretModel.offset)
        }

        while (context != null) {
            if (context is PsiClass) return context
            if (context is KtClass) {
                return context.toLightClass()
            }
            context = context.parent
        }
        return null
    }
}
