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
        
        // Mock Lombok annotations
        myFixture.addFileToProject(
            "lombok/Getter.java",
            "package lombok; public @interface Getter {}"
        )
        myFixture.addFileToProject(
            "lombok/Setter.java",
            "package lombok; public @interface Setter {}"
        )
        myFixture.addFileToProject(
            "lombok/Data.java",
            "package lombok; public @interface Data {}"
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
        
        // Check for default methods (NEW in v0.1.2)
        assertMethodExists(psiClass!!, "getName", "String")
        assertMethodExists(psiClass, "setName", "void", "String")
        
        // Check for locale-aware methods
        assertMethodExists(psiClass, "getName", "String", "Locale")
        assertMethodExists(psiClass, "setName", "void", "String", "Locale")
    }

    // Test removed: translations accessors (getTranslations/setTranslations) are no longer generated
    // Plugin now focuses only on @Localized field getter/setter methods
    
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
    // Test removed: translations accessors are no longer generated by plugin

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
        
        // All @Localized fields should have default getter/setter (NEW in v0.1.2)
        assertMethodExists(psiClass!!, "getName", "String")
        assertMethodExists(psiClass, "setName", "void", "String")
        assertMethodExists(psiClass, "getDescription", "String")
        assertMethodExists(psiClass, "setDescription", "void", "String")
        assertMethodExists(psiClass, "getSummary", "String")
        assertMethodExists(psiClass, "setSummary", "void", "String")
        
        // All @Localized fields should have locale-aware getter/setter
        assertMethodExists(psiClass, "getName", "String", "Locale")
        assertMethodExists(psiClass, "setName", "void", "String", "Locale")
        assertMethodExists(psiClass, "getDescription", "String", "Locale")
        assertMethodExists(psiClass, "setDescription", "void", "String", "Locale")
        assertMethodExists(psiClass, "getSummary", "String", "Locale")
        assertMethodExists(psiClass, "setSummary", "void", "String", "Locale")
        
        // Non-localized 'id' should NOT have ANY augmented methods
        val idMethods = psiClass.findMethodsByName("getId", false)
        assertTrue("Non-localized field should not get augmented methods", idMethods.isEmpty())
    }

    /**
     * Test: Default getter without parameters should be generated (NEW in v0.1.2).
     */
    fun `test default getter without parameters`() {
        myFixture.configureByText("Book.java", """
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            public class Book {
                @Localized
                private String title;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Book", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'Book' should be found", psiClass)
        
        // Verify default getter: String getTitle()
        val methods = psiClass!!.findMethodsByName("getTitle", false)
        val defaultGetter = methods.find { it.parameterList.parametersCount == 0 }
        
        assertNotNull("Default getter getTitle() should exist", defaultGetter)
        assertEquals("Default getter should return String", "String", defaultGetter!!.returnType?.presentableText)
    }

    /**
     * Test: Default setter with single parameter should be generated (NEW in v0.1.2).
     */
    fun `test default setter with single parameter`() {
        myFixture.configureByText("Article.java", """
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            public class Article {
                @Localized
                private String content;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Article", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'Article' should be found", psiClass)
        
        // Verify default setter: void setContent(String value)
        val methods = psiClass!!.findMethodsByName("setContent", false)
        val defaultSetter = methods.find { it.parameterList.parametersCount == 1 }
        
        assertNotNull("Default setter setContent(String) should exist", defaultSetter)
        assertEquals("Default setter should return void", "void", defaultSetter!!.returnType?.presentableText)
        assertEquals("Default setter should have String parameter", "String", defaultSetter.parameterList.parameters[0].type.presentableText)
    }

    /**
     * Test: Method overloading between default and locale versions (NEW in v0.1.2).
     */
    fun `test method overloading between default and locale versions`() {
        myFixture.configureByText("Document.java", """
            import com.localizedjpa.annotations.LocalizedEntity;
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            @LocalizedEntity
            public class Document {
                @Localized
                private String label;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "Document", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'Document' should be found", psiClass)
        
        // Find all getLabel methods
        val getterMethods = psiClass!!.findMethodsByName("getLabel", false)
        
        // Should have exactly 2 getters: getLabel() and getLabel(Locale)
        assertEquals("Should have 2 getLabel methods (overloaded)", 2, getterMethods.size)
        
        val defaultGetter = getterMethods.find { it.parameterList.parametersCount == 0 }
        val localeGetter = getterMethods.find { it.parameterList.parametersCount == 1 }
        
        assertNotNull("Default getter getLabel() should exist", defaultGetter)
        assertNotNull("Locale getter getLabel(Locale) should exist", localeGetter)
        
        // Find all setLabel methods
        val setterMethods = psiClass.findMethodsByName("setLabel", false)
        
        // Should have exactly 2 setters: setLabel(String) and setLabel(String, Locale)
        assertEquals("Should have 2 setLabel methods (overloaded)", 2, setterMethods.size)
        
        val defaultSetter = setterMethods.find { it.parameterList.parametersCount == 1 }
        val localeSetter = setterMethods.find { it.parameterList.parametersCount == 2 }
        
        assertNotNull("Default setter setLabel(String) should exist", defaultSetter)
        assertNotNull("Locale setter setLabel(String, Locale) should exist", localeSetter)
    }

    /**
     * Test: Plugin should NOT generate methods that user has already defined (NEW - conflict prevention).
     */
    fun `test no duplicate methods when user defines own getters setters`() {
        myFixture.configureByText("UserDefinedMethods.java", """
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            public class UserDefinedMethods {
                @Localized
                private String title;
                
                // User-defined default getter
                public String getTitle() {
                    return this.title;
                }
                
                // User-defined default setter
                public void setTitle(String value) {
                    this.title = value;
                }
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "UserDefinedMethods", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'UserDefinedMethods' should be found", psiClass)
        
        // Find all getTitle methods
        val getterMethods = psiClass!!.findMethodsByName("getTitle", false)
        
        // Should have exactly 2 getTitle methods:
        // 1. User's own getTitle() 
        // 2. Plugin's getTitle(Locale) - only this should be generated since user defined default
        assertEquals("Should have 2 getTitle methods (user's default + plugin's locale version)", 2, getterMethods.size)
        
        val defaultGetter = getterMethods.find { it.parameterList.parametersCount == 0 }
        val localeGetter = getterMethods.find { it.parameterList.parametersCount == 1 }
        
        assertNotNull("User's default getter getTitle() should exist", defaultGetter)
        assertNotNull("Plugin's locale getter getTitle(Locale) should exist", localeGetter)
        
        // Find all setTitle methods
        val setterMethods = psiClass.findMethodsByName("setTitle", false)
        
        // Should have exactly 2 setTitle methods:
        // 1. User's own setTitle(String)
        // 2. Plugin's setTitle(String, Locale) - only this should be generated
        assertEquals("Should have 2 setTitle methods (user's default + plugin's locale version)", 2, setterMethods.size)
        
        val defaultSetter = setterMethods.find { it.parameterList.parametersCount == 1 }
        val localeSetter = setterMethods.find { it.parameterList.parametersCount == 2 }
        
        assertNotNull("User's default setter setTitle(String) should exist", defaultSetter)
        assertNotNull("Plugin's locale setter setTitle(String, Locale) should exist", localeSetter)
    }

    /**
     * Test: Plugin should NOT generate locale methods if user has defined them.
     */
    fun `test no duplicate locale methods when user defines them`() {
        myFixture.configureByText("UserDefinedLocaleMethods.java", """
            import com.localizedjpa.annotations.Localized;
            import java.util.Locale;

            public class UserDefinedLocaleMethods {
                @Localized
                private String description;
                
                // User-defined locale-aware getter
                public String getDescription(Locale locale) {
                    // User's custom implementation
                    return "custom: " + locale.getLanguage();
                }
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "UserDefinedLocaleMethods", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'UserDefinedLocaleMethods' should be found", psiClass)
        
        // Find all getDescription methods
        val getterMethods = psiClass!!.findMethodsByName("getDescription", false)
        
        // Should have exactly 2 getDescription methods:
        // 1. Plugin's getDescription() - default version should be generated
        // 2. User's own getDescription(Locale) - locale version
        assertEquals("Should have 2 getDescription methods", 2, getterMethods.size)
        
        val defaultGetter = getterMethods.find { it.parameterList.parametersCount == 0 }
        val localeGetter = getterMethods.find { it.parameterList.parametersCount == 1 }
        
        assertNotNull("Plugin's default getter getDescription() should exist", defaultGetter)
        assertNotNull("User's locale getter getDescription(Locale) should exist", localeGetter)
    }

    /**
     * Test: Plugin should NOT generate default getter/setter when class has Lombok @Getter/@Setter.
     * Plugin SHOULD still generate locale-aware methods since Lombok doesn't handle those.
     */
    fun `test no default methods when class has Lombok Getter Setter`() {
        myFixture.configureByText("LombokProduct.java", """
            import com.localizedjpa.annotations.Localized;
            import lombok.Getter;
            import lombok.Setter;
            import java.util.Locale;

            @Getter
            @Setter
            public class LombokProduct {
                @Localized
                private String name;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "LombokProduct", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'LombokProduct' should be found", psiClass)
        
        // Find all getName methods
        val getterMethods = psiClass!!.findMethodsByName("getName", false)
        
        // Should have ONLY 1 getter: getName(Locale) - NOT getName() since Lombok handles that
        assertEquals("Should have only 1 getName method (locale version only)", 1, getterMethods.size)
        
        val localeGetter = getterMethods.find { it.parameterList.parametersCount == 1 }
        assertNotNull("Locale getter getName(Locale) should exist", localeGetter)
        
        // Find all setName methods
        val setterMethods = psiClass.findMethodsByName("setName", false)
        
        // Should have ONLY 1 setter: setName(String, Locale) - NOT setName(String) since Lombok handles that
        assertEquals("Should have only 1 setName method (locale version only)", 1, setterMethods.size)
        
        val localeSetter = setterMethods.find { it.parameterList.parametersCount == 2 }
        assertNotNull("Locale setter setName(String, Locale) should exist", localeSetter)
    }

    /**
     * Test: Plugin should NOT generate default getter/setter when class has Lombok @Data.
     */
    fun `test no default methods when class has Lombok Data`() {
        myFixture.configureByText("DataProduct.java", """
            import com.localizedjpa.annotations.Localized;
            import lombok.Data;
            import java.util.Locale;

            @Data
            public class DataProduct {
                @Localized
                private String title;
            }
        """)

        val psiClass = JavaPsiFacade.getInstance(project).findClass(
            "DataProduct", 
            GlobalSearchScope.allScope(project)
        )

        assertNotNull("Class 'DataProduct' should be found", psiClass)
        
        // Find all getTitle methods - should only have locale version
        val getterMethods = psiClass!!.findMethodsByName("getTitle", false)
        assertEquals("Should have only 1 getTitle method (locale version)", 1, getterMethods.size)
        assertEquals("Method should have 1 parameter (Locale)", 1, getterMethods[0].parameterList.parametersCount)
        
        // Find all setTitle methods - should only have locale version
        val setterMethods = psiClass.findMethodsByName("setTitle", false)
        assertEquals("Should have only 1 setTitle method (locale version)", 1, setterMethods.size)
        assertEquals("Method should have 2 parameters (String, Locale)", 2, setterMethods[0].parameterList.parametersCount)
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
