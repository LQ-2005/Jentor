# Jentor 组员复现说明

这个工程是一个 IntelliJ IDEA 插件，不是普通 Java 可执行程序。组员需要在本机用 JDK 和 IntelliJ IDEA 构建出插件 ZIP，然后从磁盘安装。

## 1. 需要准备

- JDK 21，用于运行 Gradle；若使用 IntelliJ IDEA 2026.2，还需要 JDK 25 编译插件（见下方“编译工具链”）
- IntelliJ IDEA 2025.2 到 2026.2（开发时使用 2026.1 验证）
- Windows 推荐 PowerShell；macOS/Linux 使用终端

工程已经包含：

- Gradle wrapper；
- `lib/` 下的 Jackson 和 CommonMark 依赖；
- 预生成的课程索引 `src/main/resources/index/course-index.json`；
- 构建脚本 `build-plugin.ps1`。

组员不需要先拿到 PDF 课件就能运行插件；索引已经打包在源码里。

## 2. 配置本机路径

复制示例文件：

```powershell
Copy-Item local.properties.example local.properties
```

编辑 `local.properties`：

```properties
# IntelliJ 安装根目录，里面应该有 bin、lib、plugins
ideaHome=C:/Program Files/JetBrains/IntelliJ IDEA 2026.1

# JDK 根目录：IDEA 2025.2-2026.1 用 JDK 21，IDEA 2026.2 用 JDK 25
jdkHome=C:/Program Files/Java/jdk-21

# 可选：运行 Gradle 的 JDK，推荐 21；脚本未能自动找到时显式设置
# gradleJdkHome=C:/Program Files/Java/jdk-21
```

Windows 上常见路径：

```properties
ideaHome=C:/Users/<用户名>/AppData/Local/Programs/IntelliJ IDEA Ultimate 2026.1
jdkHome=C:/Program Files/Java/jdk-21
```

macOS：

```properties
ideaHome=/Applications/IntelliJ IDEA.app/Contents
jdkHome=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
```

`local.properties` 已被 `.gitignore` 忽略，不要提交，也不要把自己的本机路径发给别人。

### 编译工具链

编译器必须能读取它所依赖的 IntelliJ jar：IDEA 2025.2–2026.1 的 jar 是 Java 21 字节码，IDEA 2026.2 的 jar 是 Java 25 字节码。因此用 2025.2–2026.1 构建时 `jdkHome` 指向 JDK 21，用 2026.2 构建时指向 JDK 25。

`jdkHome` 指定的是**编译器**。`build-plugin.ps1` 会读取它的 `release` 文件并自动传入编译器版本和安装路径，不需要手工指定。产物始终是 Java 21 字节码，同一个 ZIP 在两类 IDE 上都能运行。

**运行 Gradle 的 JDK 与编译器分开选择。** 当前 wrapper 是 Gradle 8.14.2，不能用 Java 25 运行。脚本接受 JDK 17–24 作为 Gradle 运行环境，推荐 JDK 21。可以在 `local.properties` 中显式设置 `gradleJdkHome`；否则脚本先检查原有 `JAVA_HOME`、兼容的 `jdkHome`，再搜索常见安装目录并优先选择 JDK 21。未找到时会给出配置提示。

使用 IDEA 2026.2 时，例如：

```properties
ideaHome=C:/Program Files/JetBrains/IntelliJ IDEA 2026.2
jdkHome=C:/Program Files/JetBrains/IntelliJ IDEA 2026.2/jbr
gradleJdkHome=C:/Program Files/Java/jdk-21
```

这里 IDEA 自带的 JBR 25 负责编译，独立安装的 JDK 21 负责运行 Gradle。

## 3. 构建插件

Windows：

```powershell
cd <项目目录>
.\build-plugin.ps1
```

脚本会从 `local.properties` 读取编译器路径，选择运行 Gradle 的 JDK，并显式传入两者：

```powershell
.\gradlew.bat buildPlugin --offline --stacktrace `
  "-Dorg.gradle.java.home=<Gradle 运行 JDK 路径>" `
  "-PjavaVersion=<编译器 JDK 主版本>" `
  "-Porg.gradle.java.installations.paths=<编译器 JDK 路径>"
```

脚本结束后会恢复调用前的 `JAVA_HOME` 和工作目录。

macOS/Linux：

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x gradlew   # zip 若丢失可执行权限时使用
./gradlew buildPlugin --offline --stacktrace
```

如果使用 IDEA 2026.2，运行 Gradle 的 `JAVA_HOME` 仍指向 JDK 21，另行传入 JDK 25 编译器路径：

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew buildPlugin --offline --stacktrace \
  "-Dorg.gradle.java.home=$JAVA_HOME" \
  -PjavaVersion=25 \
  "-Porg.gradle.java.installations.paths=/path/to/jdk-25"
```

成功后会生成：

```text
build/distributions/jentor-1.0.0.zip
```

如果 Gradle wrapper 第一次需要下载 Gradle 发行包，请保持网络连接；插件运行依赖已在 `lib/` 中，不需要额外下载。

## 4. 在 IDEA 中构建

也可以直接用 IntelliJ：

1. `File → Open` 打开项目目录；
2. `Settings → Build, Execution, Deployment → Build Tools → Gradle`，把 Gradle JVM 设为 JDK 21；
3. 等待 Gradle sync；
4. Windows 上在 Terminal 运行 `.\build-plugin.ps1`。若直接在 Gradle 工具窗口构建 IDEA 2026.2 插件，还需传入 `-PjavaVersion=25` 和 `-Porg.gradle.java.installations.paths=<JDK 25 路径>`；Gradle JVM 保持为 JDK 21。

工程只使用 Gradle 内置 `java` 插件，不使用 IntelliJ Platform Gradle 插件或 Kotlin DSL。

运行检索测试：

```powershell
.\gradlew.bat runRetrievalHarness --offline
```

运行 JUnit 单元测试：

```powershell
.\gradlew.bat test
```

JUnit 首次运行会从 Maven Central 下载，因此这一步在全新环境中需要联网；插件构建本身不依赖 JUnit。

## 5. 安装插件

1. `Settings → Plugins`；
2. 齿轮 → `Install Plugin from Disk`；
3. 选择 `build/distributions/jentor-1.0.0.zip`；
4. 重启 IntelliJ；
5. 打开 `View → Tool Windows → Jentor`。

## 6. 配置模型

先测试离线 Mock：

1. `Settings → Tools → Jentor`；
2. 勾选 `Use offline mock model / 使用离线 Mock 模型`；
3. `Apply`；
4. 在 Jentor 中提问，确认工具窗口、引用和兜底注脚正常。

接真实模型时取消 Mock，填写：

```text
Base URL: https://api.deepseek.com
Chat model: deepseek-chat
API key: 你自己的 Key
```

API Key 保存在每台电脑各自的 IntelliJ `PasswordSafe` 中，不会进入源码或 Git。

## 7. 可选：重建课程索引

插件运行不需要这一步。需要重新解析 PDF 时，在一台有 Python 和 `pypdf` 的电脑上执行：

```powershell
python tools/build_index.py `
  --materials "<课件目录>" `
  --output "src\main\resources\index\course-index.json"
```

然后重新构建插件，或在工具窗口的“课程资料 → 导入课程索引”中选择新 JSON。

## 8. 常见问题

### `Missing ideaHome`

没有创建 `local.properties`，或没有填写 `ideaHome`。复制 `local.properties.example` 后修改。

### `ideaHome does not contain a lib directory`

`ideaHome` 应指向 IDEA 安装根目录，而不是 `bin` 目录。该目录下应能看到 `lib`、`plugins` 和 `bin`。

### 找不到 JDK 21

安装 JDK 21，并在 `local.properties` 中设置 `gradleJdkHome`，或把 `JAVA_HOME` 指向 JDK 21。IDEA 中也要把 Gradle JVM 设为 JDK 21。`jdkHome` 则继续按编译目标选择 21 或 25。

### `compileJava` 报 IntelliJ API 找不到

确认 `ideaHome` 指向的目录有 `lib/*.jar`。如果组员使用的 IDEA 版本差异较大，先把 IDE 升级到 2026.1，或把 `local.properties` 指向 2026.1。

### `类文件具有错误的版本 69.0, 应为 65.0`

用 JDK 21 去编译 IDEA 2026.2 的 jar（Java 25 字节码）。把 `jdkHome` 换成 JDK 25 后重新构建；`build-plugin.ps1` 会自动选择对应工具链。

### `_BuildScript_ Unsupported class file major version 69`

这是 Java 25 被用于运行 Gradle 8.14.2 时的构建脚本解析错误。使用更新后的 `build-plugin.ps1`，让 Gradle 运行在 JDK 21；必要时在 `local.properties` 中设置 `gradleJdkHome`。如果编译目标是 IDEA 2026.2，`jdkHome` 仍应指向 JDK 25。直接从 IDEA 的 Gradle 工具窗口执行时，也要确认 Gradle JVM 为 JDK 21。

### Gradle wrapper 下载失败

可以打开 IntelliJ 的 Gradle 工具窗口使用 IDE 自带 Gradle，或在有网络的网络环境先完成一次 Gradle 下载。

## 9. 验收清单

- [ ] `build-plugin.ps1` 成功生成 1.0.0 ZIP
- [ ] 在干净 IDEA 中安装并重启
- [ ] 右侧出现 Jentor
- [ ] Mock 模式能回答课程问题
- [ ] 课程外问题出现灰色“注：本回答基于通用知识……”注脚
- [ ] 引用来源可展开并查看原文摘录
- [ ] 中文/English 切换正常
- [ ] 输入框右键有剪切/复制/粘贴/全选
- [ ] 窗口变窄后消息重新换行，没有横向滚动条
- [ ] 选中代码右键可附加，且代码块可复制
