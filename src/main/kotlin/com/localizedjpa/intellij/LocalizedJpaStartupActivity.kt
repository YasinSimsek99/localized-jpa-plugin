package com.localizedjpa.intellij

import com.intellij.compiler.CompilerConfiguration
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that detects LocalizedJPA library and configures IntelliJ IDEA
 * for optimal development experience.
 *
 * ## Purpose
 * LocalizedJPA requires annotation processing to be enabled for the annotation processor to run.
 * This activity detects the library and offers one-click configuration.
 *
 * ## Reflection Usage
 * This class uses reflection to access IntelliJ's internal CompilerConfiguration API:
 * - Methods like `getDefaultProcessorProfile`, `setEnabled`, etc. are not part of the stable API
 * - Direct access would require depending on specific IntelliJ version
 * - Reflection allows graceful degradation on API changes
 *
 * ## Failure Modes
 * - If CompilerConfiguration API changes: AP config fails, user notified to configure manually
 *
 * @see ProjectActivity
 */
class LocalizedJpaStartupActivity : ProjectActivity {

    companion object {
        private val LOG = Logger.getInstance(LocalizedJpaStartupActivity::class.java)
        
        // Configuration constants
        private const val NOTIFICATION_GROUP_ID = "LocalizedJPA"
        private const val LOCALIZED_JPA_ARTIFACT = "localized-jpa"
        private const val DONT_ASK_AGAIN_KEY = "localizedjpa.dontAskAnnotationProcessing"
        
        // Log prefix for consistent logging
        private const val LOG_PREFIX = "LocalizedJPA:"
        
        // Reflection method names (IntelliJ internal API - may change between versions)
        private const val METHOD_GET_ANNOTATION_PROCESSING_CONFIGURATION = "getAnnotationProcessingConfiguration"
        private const val METHOD_GET_DEFAULT_PROCESSOR_PROFILE = "getDefaultProcessorProfile"
        private const val METHOD_IS_ENABLED = "isEnabled"
        private const val METHOD_SET_ENABLED = "setEnabled"
        private const val METHOD_SET_OBTAIN_PROCESSORS_FROM_CLASSPATH = "setObtainProcessorsFromClasspath"
        
        // Try to find the correct API method (varies between IntelliJ versions)
        private val annotationProcessingMethod by lazy {
            val clazz = CompilerConfiguration::class.java
            // Try new API first (IntelliJ 2024.3+)
            try {
                clazz.getMethod(METHOD_GET_ANNOTATION_PROCESSING_CONFIGURATION, com.intellij.openapi.module.Module::class.java)
            } catch (e: NoSuchMethodException) {
                // Try old API (pre-2024.3)
                try {
                    clazz.getMethod(METHOD_GET_DEFAULT_PROCESSOR_PROFILE)
                } catch (e2: NoSuchMethodException) {
                    LOG.warn("$LOG_PREFIX Neither annotation processing API method found (API changed?)")
                    null
                }
            }
        }
    }

    override suspend fun execute(project: Project) {
        LOG.info("$LOG_PREFIX StartupActivity executed for project: ${project.name}")
        
        // Check if LocalizedJPA library is in the project
        if (!hasLocalizedJpaLibrary(project)) {
            return
        }

        // Check if already fully configured
        if (isFullyConfigured(project)) {
            return
        }

        // Check if user chose "Don't ask again"
        if (isDontAskAgain(project)) {
            return
        }

        // Show notification to enable full configuration
        showConfigurationNotification(project)
    }

    /**
     * Checks if the project has LocalizedJPA library in its dependencies.
     *
     * Scans all library dependencies looking for artifacts containing "localized-jpa".
     *
     * @param project The project to check
     * @return true if LocalizedJPA is found in dependencies
     */
    internal fun hasLocalizedJpaLibrary(project: Project): Boolean {
        var found = false
        
        try {
            OrderEnumerator.orderEntries(project)
                .librariesOnly()
                .forEachLibrary { library ->
                    val name = library.name ?: ""
                    if (name.contains(LOCALIZED_JPA_ARTIFACT, ignoreCase = true)) {
                        found = true
                        return@forEachLibrary false
                    }
                    true
                }
        } catch (e: IllegalStateException) {
            LOG.warn("$LOG_PREFIX Project not fully initialized", e)
        } catch (e: RuntimeException) {
            LOG.warn("$LOG_PREFIX Error checking libraries", e)
        }
        
        return found
    }

    /**
     * Checks if annotation processing is already enabled.
     */
    internal fun isFullyConfigured(project: Project): Boolean {
        val apEnabled = isAnnotationProcessingEnabled(project)
        LOG.info("$LOG_PREFIX AP=$apEnabled")
        return apEnabled
    }

    /**
     * Checks if annotation processing is enabled for the project.
     *
     * ## API Details
     * IntelliJ 2024.3+ uses module-based annotation processing configuration.
     * We check if AP is enabled for any module in the project.
     *
     * @param project The project to check
     * @return true if annotation processing is enabled for any module
     */
    internal fun isAnnotationProcessingEnabled(project: Project): Boolean {
        return try {
            val method = annotationProcessingMethod ?: return false
            val compilerConfiguration = CompilerConfiguration.getInstance(project)
            
            // New API is module-based
            if (method.name == METHOD_GET_ANNOTATION_PROCESSING_CONFIGURATION) {
                // Get first module and check its configuration
                val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules
                if (modules.isEmpty()) return false
                
                val config = method.invoke(compilerConfiguration, modules[0])
                val isEnabledMethod = config.javaClass.getMethod(METHOD_IS_ENABLED)
                isEnabledMethod.invoke(config) as Boolean
            } else {
                // Old API - project-wide profile
                val profile = method.invoke(compilerConfiguration)
                val isEnabledMethod = profile.javaClass.getMethod(METHOD_IS_ENABLED)
                isEnabledMethod.invoke(profile) as Boolean
            }
        } catch (e: ReflectiveOperationException) {
            LOG.debug("$LOG_PREFIX Could not check AP status via reflection", e)
            false
        } catch (e: SecurityException) {
            LOG.warn("$LOG_PREFIX Security exception accessing CompilerConfiguration", e)
            false
        }
    }

    /**
     * Checks if user has chosen "Don't ask again".
     */
    private fun isDontAskAgain(project: Project): Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(DONT_ASK_AGAIN_KEY, false)
    }

    /**
     * Enables annotation processing for all modules in the project.
     *
     * ## API Details
     * IntelliJ 2024.3+ uses module-based annotation processing configuration.
     * We enable AP for all modules in the project.
     *
     * @param project The project to configure
     * @return true if successfully enabled
     */
    private fun enableAnnotationProcessing(project: Project): Boolean {
        return try {
            val method = annotationProcessingMethod ?: run {
                LOG.error("$LOG_PREFIX No annotation processing API method available")
                return false
            }
            val compilerConfiguration = CompilerConfiguration.getInstance(project)
            
            // New API is module-based
            if (method.name == METHOD_GET_ANNOTATION_PROCESSING_CONFIGURATION) {
                val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules
                if (modules.isEmpty()) {
                    LOG.warn("$LOG_PREFIX No modules found in project")
                    return false
                }
                
                var success = true
                for (module in modules) {
                    try {
                        val config = method.invoke(compilerConfiguration, module)
                        val setEnabledMethod = config.javaClass.getMethod(METHOD_SET_ENABLED, Boolean::class.java)
                        setEnabledMethod.invoke(config, true)
                        
                        // Try to set obtain from classpath if method exists
                        try {
                            val setObtainMethod = config.javaClass.getMethod(METHOD_SET_OBTAIN_PROCESSORS_FROM_CLASSPATH, Boolean::class.java)
                            setObtainMethod.invoke(config, true)
                        } catch (e: NoSuchMethodException) {
                            // Method might not exist in this API version
                            LOG.debug("$LOG_PREFIX setObtainProcessorsFromClasspath not available")
                        }
                    } catch (e: Exception) {
                        LOG.warn("$LOG_PREFIX Failed to configure module: ${module.name}", e)
                        success = false
                    }
                }
                
                if (success) {
                    LOG.info("$LOG_PREFIX Annotation processing enabled for ${modules.size} module(s)")
                }
                success
            } else {
                // Old API - project-wide profile
                val profile = method.invoke(compilerConfiguration)
                
                val setEnabledMethod = profile.javaClass.getMethod(METHOD_SET_ENABLED, Boolean::class.java)
                setEnabledMethod.invoke(profile, true)
                
                val setObtainMethod = profile.javaClass.getMethod(METHOD_SET_OBTAIN_PROCESSORS_FROM_CLASSPATH, Boolean::class.java)
                setObtainMethod.invoke(profile, true)
                
                LOG.info("$LOG_PREFIX Annotation processing enabled")
                true
            }
        } catch (e: NoSuchMethodException) {
            LOG.error("$LOG_PREFIX API method not found for AP configuration", e)
            false
        } catch (e: ReflectiveOperationException) {
            LOG.error("$LOG_PREFIX Failed to enable annotation processing", e)
            false
        } catch (e: SecurityException) {
            LOG.error("$LOG_PREFIX Security exception while enabling AP", e)
            false
        }
    }

    /**
     * Enables annotation processing for the project.
     */
    private fun performConfiguration(project: Project): Boolean {
        var apSuccess = false
        
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction {
                apSuccess = enableAnnotationProcessing(project)
            }
        }
        
        return apSuccess
    }

    /**
     * Shows a notification asking the user to configure the project.
     */
    private fun showConfigurationNotification(project: Project) {
        try {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID) ?: return

            val notification = notificationGroup.createNotification(
                "LocalizedJPA Setup Required",
                "LocalizedJPA library detected. Click 'Enable' to automatically enable annotation processing.",
                NotificationType.WARNING
            )

            // Add "Enable" action - enables annotation processing
            notification.addAction(NotificationAction.createSimple("Enable") {
                val apSuccess = performConfiguration(project)
                notification.expire()
                
                if (apSuccess) {
                    notificationGroup.createNotification(
                        "LocalizedJPA Configured ✓",
                        "Annotation processing enabled successfully.",
                        NotificationType.INFORMATION
                    ).notify(project)
                } else {
                    notificationGroup.createNotification(
                        "Configuration Failed",
                        "Could not enable annotation processing automatically. Please enable it manually in Settings → Compiler → Annotation Processors.",
                        NotificationType.ERROR
                    ).notify(project)
                }
            })

            // Add "Don't Ask Again" action
            notification.addAction(NotificationAction.createSimple("Don't Ask Again") {
                PropertiesComponent.getInstance(project).setValue(DONT_ASK_AGAIN_KEY, true)
                notification.expire()
            })

            notification.notify(project)
            LOG.info("$LOG_PREFIX Configuration notification shown")
        } catch (e: IllegalStateException) {
            LOG.error("$LOG_PREFIX Notification group not found", e)
        } catch (e: RuntimeException) {
            LOG.error("$LOG_PREFIX Failed to show notification", e)
        }
    }
}
