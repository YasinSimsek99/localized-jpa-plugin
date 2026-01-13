package com.localizedjpa.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

/**
 * Listens for module root changes (like adding dependencies) and checks if LocalizedJPA
 * needs to be configured.
 */
class LocalizedJpaDependencyUpdater(private val project: Project) : ModuleRootListener {

    override fun rootsChanged(event: ModuleRootEvent) {
        LocalizedJpaStartupActivity.checkAndNotify(project)
    }
}
