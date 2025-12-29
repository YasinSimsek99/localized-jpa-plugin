# Changelog

All notable changes to the LocalizedJPA IntelliJ IDEA Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
