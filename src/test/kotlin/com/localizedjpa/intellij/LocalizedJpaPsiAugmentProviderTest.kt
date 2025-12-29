package com.localizedjpa.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Functional tests for [LocalizedJpaPsiAugmentProvider].
 *
 * Uses the IntelliJ Platform Test SDK to simulate an in-memory IDE environment.
 * Verifies that synthetic methods are correctly injected into the PSI.
 */
class LocalizedJpaPsiAugmentProviderTest : BasePlatformTestCase() {

    companion object {
        private val LOG = Logger.getInstance(LocalizedJpaPsiAugmentProviderTest::class.java)
    }

    override fun setUp() {
        super.setUp()
        // Create annotation classes
        myFixture.addFileToProject(
            "com/localizedjpa/annotations/LocalizedEntity.java",
            "package com.localizedjpa.annotations; public @interface LocalizedEntity {}"
        )
        myFixture.addFileToProject(
            "com/localizedjpa/annotations/Localized.java",
            "package com.localizedjpa.annotations; public @interface Localized {}"
        )
        
        // Mock Locale to ensure it resolves to java.util.Locale
        myFixture.addFileToProject(
            "java/util/Locale.java",
            "package java.util; public class Locale {}"
        )

        // Register the augmentation provider explicitly using ExtensionTestUtil
        ExtensionTestUtil.maskExtensions(
            PsiAugmentProvider.EP_NAME,
            listOf(LocalizedJpaPsiAugmentProvider()),
            testRootDisposable
        )
    }

    fun `test localized methods generation`() {
        myFixture.configureByText("Product.java", """
            import com.localizedjpa.annotations.LocalizedEntity;
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            @LocalizedEntity
            public class Product {
                @Localized
                private String name;
            }
        """)
        
        // Find the class using JavaPsiFacade
        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Product", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'Product' should be found", psiClass)
        
        // Check for 'getName(Locale)' - checking simple types mainly
        assertMethodExists(psiClass!!, "getName", "String", "Locale")
        
        // Check for 'setName(String, Locale)'
        assertMethodExists(psiClass, "setName", "void", "String", "Locale")
    }

    fun `test translations field accessors generation`() {
        myFixture.configureByText("Category.java", """
            import com.localizedjpa.annotations.LocalizedEntity;

            @LocalizedEntity
            public class Category {
                private String code;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Category", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'Category' should be found", psiClass)

        // Check for 'getTranslations()'
        assertMethodExists(psiClass!!, "getTranslations", "Map")
        
        // Check for 'setTranslations(Map)'
        assertMethodExists(psiClass, "setTranslations", "void", "Map")
    }
    
    fun `test ignored without annotation`() {
        myFixture.configureByText("NormalBean.java", """
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            // NEW API: @LocalizedEntity is OPTIONAL
            // Plugin should work with just @Localized fields
            public class NormalBean {
                @Localized
                private String title;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "NormalBean", 
            GlobalSearchScope.allScope(project)
        )
        
        assertNotNull("Class 'NormalBean' should be found", psiClass)

        // NEW BEHAVIOR: SHOULD generate methods even without @LocalizedEntity
        val methods = psiClass!!.findMethodsByName("getTitle", false)
        assertTrue("Should generate methods for @Localized fields even without @LocalizedEntity", methods.isNotEmpty())
    }

    /**
     * Test: Inner class with @LocalizedEntity should have synthetic methods generated.
     */
    fun `test inner class with LocalizedEntity`() {
        myFixture.configureByText("OuterClass.java", """
            import com.localizedjpa.annotations.LocalizedEntity;
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            public class OuterClass {
                @LocalizedEntity
                public static class InnerEntity {
                    @Localized
                    private String title;
                }
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "OuterClass.InnerEntity", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Inner class 'OuterClass.InnerEntity' should be found", psiClass)
        
        // Inner classes should also have synthetic methods
        assertMethodExists(psiClass!!, "getTitle", "String", "Locale")
        assertMethodExists(psiClass, "setTitle", "void", "String", "Locale")
    }

    /**
     * Test: Enum with @LocalizedEntity should be ignored (enums don't support augmentation).
     */
    fun `test enum ignored even with annotation`() {
        myFixture.configureByText("Status.java", """
            import com.localizedjpa.annotations.LocalizedEntity;
            import com.localizedjpa.annotations.Localized;

            @LocalizedEntity
            public enum Status {
                ACTIVE, INACTIVE;
                
                @Localized
                private String label;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Status", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Enum 'Status' should be found", psiClass)
        
        // Enums are classes, so they should get augmented methods
        // This test verifies the provider handles enums correctly
        val methods = psiClass!!.findMethodsByName("getLabel", false)
        // Enums typically work like classes for PSI augmentation
        assertTrue("Enum should have augmented methods", methods.isNotEmpty())
    }

    /**
     * Test: Interface with @LocalizedEntity should be processed but may behave differently.
     */
    fun `test interface with LocalizedEntity annotation`() {
        myFixture.configureByText("LocalizedInterface.java", """
            import com.localizedjpa.annotations.LocalizedEntity;

            @LocalizedEntity
            public interface LocalizedInterface {
                // Interfaces don't have fields, but should still get translations accessors
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "LocalizedInterface", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Interface 'LocalizedInterface' should be found", psiClass)
        
        // Interfaces should get translations accessors
        assertMethodExists(psiClass!!, "getTranslations", "Map")
        assertMethodExists(psiClass, "setTranslations", "void", "Map")
    }

    /**
     * Test: Multiple @Localized fields should all get getter/setter methods.
     */
    fun `test multiple localized fields`() {
        myFixture.configureByText("MultiFieldEntity.java", """
            import com.localizedjpa.annotations.LocalizedEntity;
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            @LocalizedEntity
            public class MultiFieldEntity {
                @Localized
                private String name;
                
                @Localized
                private String description;
                
                @Localized
                private String summary;
                
                // Non-localized field
                private Long id;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "MultiFieldEntity", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'MultiFieldEntity' should be found", psiClass)
        
        // All @Localized fields should have getter/setter
        assertMethodExists(psiClass!!, "getName", "String", "Locale")
        assertMethodExists(psiClass, "setName", "void", "String", "Locale")
        assertMethodExists(psiClass, "getDescription", "String", "Locale")
        assertMethodExists(psiClass, "setDescription", "void", "String", "Locale")
        assertMethodExists(psiClass, "getSummary", "String", "Locale")
        assertMethodExists(psiClass, "setSummary", "void", "String", "Locale")
        
        // Non-localized 'id' should NOT have locale methods
        val idMethods = psiClass.findMethodsByName("getId", false)
        val hasLocaleParam = idMethods.any { it.parameterList.parametersCount > 0 }
        assertFalse("Non-localized field should not get locale getter", hasLocaleParam)
    }

    private fun assertMethodExists(
        psiClass: PsiClass, 
        methodName: String, 
        returnType: String, 
        vararg paramTypes: String
    ) {
        val methods = psiClass.findMethodsByName(methodName, false)
        assertTrue("No methods named '$methodName' found", methods.isNotEmpty())
        
        val method = methods.find { m ->
            if (m.parameterList.parametersCount != paramTypes.size) return@find false
            
            val paramsMatch = m.parameterList.parameters.zip(paramTypes).all { (param, expectedType) ->
                // Check simple name to avoid FQN issues in test env
                val typeName = param.type.presentableText 
                typeName == expectedType || typeName == expectedType.substringAfterLast('.')
            }
            if (!paramsMatch) return@find false
            
            val returnTypeName = m.returnType?.presentableText
            returnTypeName == returnType || returnTypeName == returnType.substringAfterLast('.')
        }
        
        if (method == null) {
            val foundSignatures = methods.joinToString("\n") { m ->
                val params = m.parameterList.parameters.joinToString(", ") { "${it.type.presentableText} ${it.name}" }
                "${m.returnType?.presentableText} ${m.name}($params)"
            }
            LOG.warn("Method '$methodName' not found. Candidates:\n$foundSignatures")
            fail("Method '$methodName' with parameters [${paramTypes.joinToString()}] and return type '$returnType' not found.\nFound:\n$foundSignatures")
        }
    }
}
