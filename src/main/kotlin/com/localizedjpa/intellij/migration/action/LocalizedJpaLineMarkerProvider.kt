package com.localizedjpa.intellij.migration.action

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.localizedjpa.intellij.migration.psi.EntityPsiAnalyzer
import com.localizedjpa.intellij.migration.ui.LocalizedJpaIcons
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.asJava.toLightClass

class LocalizedJpaLineMarkerProvider : com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo<*>>
    ) {
        // Java Support
        if (element is PsiIdentifier && element.parent is PsiClass) {
            val parentClass = element.parent as PsiClass
            if (parentClass.nameIdentifier == element) {
                createMarker(element, parentClass, result)
                return
            }
        }

        // Kotlin Support 
        if (element.node?.elementType?.toString() == "IDENTIFIER" && element.parent is KtClass) {
            val parentClass = element.parent as KtClass
            if (parentClass.nameIdentifier == element) {
                val lightClass = parentClass.toLightClass()
                if (lightClass != null) {
                    createMarker(element, lightClass, result)
                    return
                }
            }
        }
    }

    private fun createMarker(
        element: PsiElement, 
        psiClass: PsiClass, 
        result: MutableCollection<in com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo<*>>
    ) {
        if (!EntityPsiAnalyzer.isEntityClass(psiClass)) return

        val metadata = EntityPsiAnalyzer.extractMetadata(psiClass)
        
        // Show icon only if there is at least one localized field
        if (metadata != null && metadata.localizedFields.isNotEmpty()) {
            // Create a simple generic marker for clicking actions using RelatedItemLineMarkerInfo
            val markerInfo = com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo(
                element,
                element.textRange,
                LocalizedJpaIcons.GutterIcon,
                { "Generate LocalizedJPA Migration" },
                { _, _ -> MigrationActionExecutor.execute(element.project, psiClass) },
                com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
                { emptyList<com.intellij.navigation.GotoRelatedItem>() }
            )
            result.add(markerInfo)
        }
    }
}
