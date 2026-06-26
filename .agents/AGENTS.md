# Project-Scoped Rules for Superkassa Server

1. **Gradle Version Definition Rule**: Never hardcode version strings in any `build.gradle.kts` files. All dependency versions, tool versions, and plugin versions must be defined strictly in `gradle/libs.versions.toml` and referenced using version catalog properties.
2. **Implicit and Soft Warnings Prevention Rule**: Always analyze code for implicit and soft warnings (such as redundant imports, unnecessary wildcard imports or overlapping explicit imports, redundant compiler flags, unused variables, and compiler-level warnings like receiver nullability checks). Always resolve or eliminate them before completing any task.
   - **Verification via IntelliJ IDEA MCP**: To ensure no soft warnings are missed, always run the `get_file_problems` tool with `errorsOnly = false` on **every** modified, created, or directly related file in the module (not just the main class/file) before finalizing the work. If any warnings (including `Call on not-null type may be reduced` or unused variables) are returned, they must be resolved.

