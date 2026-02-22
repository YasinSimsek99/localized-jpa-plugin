# LocalizedJPA IntelliJ IDEA Plugin

[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2025.2+-blue.svg)](https://www.jetbrains.com/idea/)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1+-purple.svg)](https://kotlinlang.org/)

IntelliJ IDEA plugin that provides full IDE support for the [LocalizedJPA](https://github.com/YasinSimsek99/localized-jpa) annotation processor library.

---

**🚀 [Install from JetBrains Marketplace](https://plugins.jetbrains.com/plugin/29551-localizedjpa)**

---

<p align="center">
  <img src="images/setName.gif" alt="Autocomplete Demo" width="700"/>
</p>

---

## ✨ Features

### 🔧 Autocomplete Support
The plugin recognizes methods and fields injected by LocalizedJPA's annotation processor at compile-time:

```java
@LocalizedEntity
public class Product {
    @Localized
    private String name;
    
    @Localized
    private String description;
}
```

**Synthetic methods available in IDE:**
- `getName()` - Get name (default locale)
- `setName(String value)` - Set name (default locale)
- `getName(Locale locale)` - Get localized name
- `setName(String value, Locale locale)` - Set localized name
- `getDescription()` - Get description (default locale)
- `setDescription(String value)` - Set description (default locale)
- `getDescription(Locale locale)` - Get localized description
- `setDescription(String value, Locale locale)` - Set localized description

### 🗄️ SQL Migration Generator
Generate Flyway migration scripts directly from `@LocalizedEntity` classes:
- Diff-aware: only generates columns missing from the database
- Supports PostgreSQL, MySQL, MariaDB with auto-dialect detection
- Reads `spring.flyway.locations` from project config for automated path resolution
- "Ignore Migrations" mode for full `CREATE TABLE` generation (useful after DB resets)
- Live SQL preview with syntax highlighting, copy button, and real-time re-render

### 🔔 Smart Notifications
When a project containing LocalizedJPA is opened:
- Automatically detects the library in project dependencies
- Shows a notification to configure annotation processing
- Provides quick access to Annotation Processor settings
- "Don't Show Again" option to dismiss permanently

---

## 📦 Installation

### From JetBrains Marketplace (Recommended)
1. Open IntelliJ IDEA
2. Go to **Settings → Plugins → Marketplace**
3. Search for **"LocalizedJPA"**
4. Click **Install**
5. Restart IDE

### Manual Installation
1. Download the latest `LocalizedJpa-*.zip` from [GitHub Releases](https://github.com/YasinSimsek99/localized-jpa/releases)
2. Open IntelliJ IDEA
3. Go to **Settings → Plugins → ⚙️ (Settings icon) → Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart IDE

---

## 🚀 Usage

### 1. Add LocalizedJPA to Your Project

See the [LocalizedJPA documentation](https://github.com/YasinSimsek99/localized-jpa) for installation and configuration instructions.

### 2. Enable Annotation Processing
Go to **Settings → Build, Execution, Deployment → Compiler → Annotation Processors**
- ✅ Enable annotation processing
- ✅ Obtain processors from project classpath

### 3. Use the Annotations
```java
import com.localizedjpa.annotations.LocalizedEntity;
import com.localizedjpa.annotations.Localized;

@LocalizedEntity
@Entity
public class Product {
    @Id
    private Long id;
    
    @Localized
    private String name;
    
    // IDE now recognizes ALL methods:
    // - getName() / setName(String)
    // - getName(Locale) / setName(String, Locale)
}
```

### 4. Enjoy Full IDE Support
```java
Product product = new Product();
product.setName("Laptop");                       // ✅ Default setter works!
product.setName("Laptop", Locale.ENGLISH);      // ✅ Locale-aware setter works!
product.setName("Dizüstü", new Locale("tr"));   // ✅ Turkish translation!

String name = product.getName();                 // ✅ Default getter works!
String enName = product.getName(Locale.ENGLISH); // ✅ Locale-aware getter works!
```

---

### ⚙️ SQL Migration Generator (v0.1.5+)

> **⚠️ Important:** The SQL Migration Generator requires **LocalizedJPA library v0.1.5 or newer** in your project.
> Make sure to update your `pom.xml` or `build.gradle` dependency before using this feature.

The plugin can automatically generate Flyway-compatible SQL migration scripts for your `@LocalizedEntity` classes.

#### How to Use
1. Open any `@LocalizedEntity` class.
2. Click the **🌐 gutter icon** next to the class declaration, or right-click → **Generate LocalizedJPA Migration**.
3. The **Migration Preview Dialog** opens — review and customise before generating.

#### Migration Preview Dialog

| Field | Description |
|---|---|
| **Dialect** | PostgreSQL, MySQL or MariaDB. Auto-detected from `application.yml` / `.properties`. |
| **Directory** | Target folder for the `.sql` file. Auto-detected from `spring.flyway.locations` or defaults to `src/main/resources/db/migration`. Use the 📁 folder browser to override. |
| **File Name** | Pre-filled with `V{N}__add_{table}_translations.sql`. Shows ⚠️ if the file already exists. |
| **Missing Localized Fields** | Checkboxes for each `@Localized` field that is not yet in the database. Deselect to skip individual columns. |
| **Ignore Migrations (Full Create)** | Bypasses diff analysis — generates a full `CREATE TABLE` script including all columns, FK, and unique constraints. Useful after a DB reset. |
| **SQL Preview** | Live SQL with IntelliJ syntax highlighting. Updates instantly on any change. Use **Copy** to copy to clipboard. |

#### Generated SQL Example

```sql
-- Create translation table for product
create table "inventory"."product_translations" (
  "id" bigint generated by default as identity not null,
  "product_id" bigint not null,
  "locale" varchar(10) not null,
  "name" varchar(255),
  "description" varchar(500)
);

-- Add foreign key constraint
alter table "inventory"."product_translations"
  add constraint "fk_product_translations_product"
    foreign key ("product_id")
    references "inventory"."product" ("id");

-- Add unique constraint for entity and locale
alter table "inventory"."product_translations"
  add constraint "uk_product_translations_locale"
    unique ("product_id", "locale");
```

---

## 🛠️ Development

### Prerequisites
- JDK 17+
- IntelliJ IDEA 2025.2+
- Gradle 9.0+

### Build
```bash
./gradlew build
```

### Run in Sandbox IDE
```bash
./gradlew runIde
```

### Create Distribution
```bash
./gradlew buildPlugin
```
Output: `build/distributions/LocalizedJpa-*.zip`

---

## 🏗️ Architecture

```
src/main/kotlin/com/localizedjpa/intellij/
├── migration/                          # SQL Migration Generator tool packages
│   ├── action/                         # IDE Action and Gutter Icon extensions
│   ├── analyzer/                       # Configuration scanner and Diff analyzer
│   ├── generator/                      # jOOQ SQL Code Generation
│   ├── psi/                            # PsiTree parsing to extract Entity metadata
│   └── ui/                             # Migration Preview Dialog components
├── LocalizedJpaPsiAugmentProvider.kt   # PSI augmentation for synthetic methods
└── LocalizedJpaStartupActivity.kt      # Startup notification for AP config
```

### Key Components

#### Migration Generator
- `EntityPsiAnalyzer`: Extracts `@Localized` field metadata from Java PSI.
- `MigrationDiffAnalyzer`: Parses existing SQL files via VFS to find database diffs.
- `SqlMigrationGenerator`: Constructs dialect-specific DDL scripts using jOOQ.

#### PsiAugmentProvider
Extends IntelliJ's `PsiAugmentProvider` to inject synthetic PSI elements:
- Scans for `@LocalizedEntity` annotated classes
- Uses caching for performance

#### StartupActivity
Implements `ProjectActivity` to detect LocalizedJPA:
- Checks project dependencies on startup
- Shows notification if library is found
- Provides quick settings access
- Persists user preferences

---

## 📋 Compatibility

| IntelliJ IDEA | Plugin Version |
|---------------|----------------|
| 2025.2+       | 0.1.1 — 0.1.5  |

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

## 🔗 Links

- [LocalizedJPA Library](https://github.com/YasinSimsek99/localized-jpa)
- [JetBrains Marketplace](https://plugins.jetbrains.com/)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

---

## 📧 Contact

- **Author:** Yasin Şimşek
- **Email:** yasinsimsekk67@gmail.com
- **GitHub:** [@YasinSimsek99](https://github.com/YasinSimsek99)