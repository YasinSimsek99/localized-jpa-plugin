# Changelog

All notable changes to the LocalizedJPA IntelliJ IDEA Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.5] - 2026-02-23

### Added
- **SQL Migration Generator** - Generate Flyway-compatible SQL migration scripts directly from `@LocalizedEntity` classes via the gutter icon or right-click menu.
- **Diff-Aware Generation** - Compares the entity metadata against existing migration files and only generates `ALTER TABLE` statements for genuinely missing columns, preventing version conflicts.
- **Migration Preview Dialog** - A rich dialog with:
  - SQL preview panel with IntelliJ syntax highlighting and scrollbar support.
  - Dialect selector (PostgreSQL, MySQL, MariaDB) with auto-detection from `application.yml` / `application.properties`.
  - File name field with conflict warning (⚠️) if the file already exists in the target directory.
  - Directory selector with folder browser, pre-filled from `spring.flyway.locations` in project config or defaulting to `src/main/resources/db/migration`.
  - **"Ignore Migrations (Full Create)"** checkbox — bypasses diff analysis and generates a complete `CREATE TABLE` script with all localized columns, foreign key, and unique constraints (useful after database resets or first-time setup).
  - **"Select All / None"** field toggles for fine-grained control over which columns are included.
  - Real-time SQL re-render on every checkbox, dialect, or ignore-migrations change.
  - **"Copy"** action link to copy the SQL to clipboard.
- **Auto Directory Creation** - If the target Flyway directory doesn't exist on disk, the plugin creates the full path automatically and refreshes the IntelliJ VFS.
- **Flyway Path Detection** - Parses `spring.flyway.locations` from YAML / properties files; supports `classpath:` and `filesystem:` prefixes.
- **Version Numbering** - Automatically determines the next Flyway version number (`V{N}__...`) by scanning the existing migration files.
- **Schema-Aware SQL** - Generated SQL respects the `@Table(schema = "...")` value; all table, column, constraint, and reference names are schema-qualified when applicable.
- **Gutter Icon** - Line marker icon on `@LocalizedEntity` classes for one-click migration generation.

### Changed
- **SQL Formatting** - jOOQ pretty-printing enabled for `CREATE TABLE`; `ALTER TABLE` statements are formatted with explicit line breaks for readability.

### Fixed
- **Existing File Warning** - Warning label now correctly re-evaluates when either the file name or the target directory changes.
- **Full Create Missing Constraints** - Fixed an issue where "Ignore Migrations" mode could omit the `ALTER TABLE` foreign key and unique constraint blocks when all fields were already present in the diff result.

### Technical Details
- New packages: `migration.action`, `migration.analyzer`, `migration.generator`, `migration.psi`, `migration.ui`
- Key classes: `MigrationActionExecutor`, `ConfigAnalyzer`, `MigrationDiffAnalyzer`, `SqlMigrationGenerator`, `MigrationFileWriter`, `MigrationPreviewDialog`
- Uses jOOQ DSL for dialect-agnostic, type-safe SQL DDL generation
- Uses IntelliJ `LocalFileSystem` + `VfsUtil` for VFS-safe file creation

## [0.1.3] - 2026-01-13

### Added
- **Extended Compatibility** - Now compatible with IntelliJ IDEA 2024.1 and newer (Java 17+).
- **Dynamic Library Detection** - Plugin now instantly detects when `LocalizedJPA` library is added to `pom.xml` or `build.gradle` without requiring a restart.
- **Improved Notification** - Smarter prompts to enable annotation processing upon library detection.

## [0.1.2] - 2025-12-30

### Added
- **Default Getter/Setter Methods** - Plugin now generates `getName()` and `setName(String)` methods for `@Localized` fields
- **Complete Method Coverage** - IDE autocomplete now shows all four methods: default pair and locale-aware pair
- **Method Overloading Support** - Both `getName()` and `getName(Locale)` coexist without conflicts
- **Smart Method Generation** - `hasMethod()` helper function to detect existing method signatures
- **Method Conflict Prevention** - Plugin checks if user has already defined getter/setter methods before generating synthetic ones

### Changed
- **Method Generation Order** - Default getter/setter appear first in autocomplete, followed by locale-aware versions
- **Enhanced Documentation** - Updated README and plugin description to demonstrate both method types

### Technical Details
- Enhanced `LocalizedJpaPsiAugmentProvider.generateLocalizedMethods()` to create standard Java bean methods
- Plugin only generates methods that don't already exist in the class (using `ownMethods` to check)
- Users can define custom implementations of default or locale-aware methods without conflicts
- Added comprehensive test cases for default method generation, method overloading, and conflict prevention scenarios
- Test suite expanded from 10 to 15 test cases
- Maintains full backward compatibility with existing code using locale-aware methods

## [0.1.1] - 2025-12-29

### Added
- **PSI Augment Provider** - Full autocomplete support for `@LocalizedEntity` synthetic methods
- **Localized Getters/Setters** - IDE recognition of `getName(Locale)` and `setName(String, Locale)` methods
- **Translations Field Support** - Support for injected `translations` map field and accessors
- **Smart Detection** - Automatic LocalizedJPA library detection when project is opened
- **Auto Configuration** - One-click notification to enable annotation processing
- Comprehensive test suite with 10 test cases covering PSI augmentation and library detection

### Technical Details
- Minimum IntelliJ IDEA version: 2025.2 (build 252)
- Compatible with IntelliJ IDEA 2025.2.x and 2025.3.x
- Requires Java 17+
- Built with Kotlin 2.1.20 and IntelliJ Platform Gradle Plugin 2.10.2

### Known Issues
- None reported in this initial release

## [Unreleased]

### Planned
- Multi-language support for plugin descriptions
- Enhanced error messages for configuration issues
- Performance optimizations for large projects
- Support for custom annotation processor configurations

---

## Version Support Matrix

| Plugin Version | Min IntelliJ | Max IntelliJ | Java Version |
|---------------|--------------|--------------|--------------|
| 0.1.1         | 2025.2       | 2025.3.*     | 17+          |

---

**Legend:**
- `Added` - New features
- `Changed` - Changes in existing functionality
- `Deprecated` - Soon-to-be removed features
- `Removed` - Removed features
- `Fixed` - Bug fixes
- `Security` - Security vulnerability fixes
