# Jentor Demo Workspace

This project intentionally avoids Gradle Java toolchain auto-detection. It
compiles to Java 17 bytecode using the Gradle JVM selected by IntelliJ IDEA.

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

Expected baseline output:

```text
Students: 4
Average score: 75.7
Top student: Alice
1. Alice | 92.0
2. Bob | 71.0
3. <missing>
4. Cara | 64.0
```
