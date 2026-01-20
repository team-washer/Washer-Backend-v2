---
description: Run Spotless formatting with Java 25
---

Run Spotless formatting:

1. Run Spotless formatting:
   ```bash
   ./gradlew spotlessApply
   ```

2. Verify formatting results

3. If failed, analyze error messages and suggest solutions

Note: This project uses Spotless with Eclipse formatter configuration. The formatting will automatically apply to all Java files in src/main/java and src/test/java directories.