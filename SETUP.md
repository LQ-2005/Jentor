# 组员复现说明

这个工程是一个 IntelliJ IDEA 插件，不是普通 Java 可执行程序。组员需要在本机用 JDK 21 和 IntelliJ IDEA 构建出插件 ZIP，然后从磁盘安装。

## 1. 需要准备

- JDK 21
- IntelliJ IDEA 2025.2 或 2026.1（开发时使用 2026.1 验证）
- Windows 推荐 PowerShell；macOS/Linux 使用终端

工程已经包含：

- Gradle wrapper；
- `lib/` 下的 Jackson 依赖；
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

# JDK 21 根目录
jdkHome=C:/Program Files/Java/jdk-21
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

## 3. 构建插件

Windows：

```powershell
cd <项目目录>
.\build-plugin.ps1
```

脚本会从 `local.properties` 读取 `jdkHome`，设置 `JAVA_HOME`，然后执行：

```powershell
.\gradlew.bat buildPlugin --offline --stacktrace
```

macOS/Linux：

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x gradlew   # zip 若丢失可执行权限时使用
./gradlew buildPlugin --offline --stacktrace
```

成功后会生成：

```text
build/distributions/cst3492-course-ta-0.4.3.zip
```

如果 Gradle wrapper 第一次需要下载 Gradle 发行包，请保持网络连接；依赖本身不需要额外下载，Jackson 已在 `lib/` 中。

## 4. 在 IDEA 中构建

也可以直接用 IntelliJ：

1. `File → Open` 打开项目目录；
2. `Settings → Build, Execution, Deployment → Build Tools → Gradle`，把 Gradle JVM 设为 JDK 21；
3. 等待 Gradle sync；
4. 在 Gradle 工具窗口执行 `buildPlugin`，或在 Terminal 运行 `.\gradlew.bat buildPlugin --offline`。

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
3. 选择 `build/distributions/cst3492-course-ta-0.4.3.zip`；
4. 重启 IntelliJ；
5. 打开 `View → Tool Windows → Course TA`。

## 6. 配置模型

先测试离线 Mock：

1. `Settings → Tools → CST3492 Course TA`；
2. 勾选 `Use offline mock model / 使用离线 Mock 模型`；
3. `Apply`；
4. 在 Course TA 中提问，确认工具窗口、引用和兜底注脚正常。

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

检查 `local.properties` 的 `jdkHome`，或设置 `JAVA_HOME`。IDEA 中还要把 Gradle JVM 设为 JDK 21。

### `compileJava` 报 IntelliJ API 找不到

确认 `ideaHome` 指向的目录有 `lib/*.jar`。如果组员使用的 IDEA 版本差异较大，先把 IDE 升级到 2026.1，或把 `local.properties` 指向 2026.1。

### Gradle wrapper 下载失败

可以打开 IntelliJ 的 Gradle 工具窗口使用 IDE 自带 Gradle，或在有网络的网络环境先完成一次 Gradle 下载。

## 9. 验收清单

- [ ] `build-plugin.ps1` 成功生成 0.4.3 ZIP
- [ ] 在干净 IDEA 中安装并重启
- [ ] 右侧出现 Course TA
- [ ] Mock 模式能回答课程问题
- [ ] 课程外问题出现灰色“注：本回答基于通用知识……”注脚
- [ ] 引用来源可展开并查看原文摘录
- [ ] 中文/English 切换正常
- [ ] 输入框右键有剪切/复制/粘贴/全选
- [ ] 窗口变窄后消息重新换行，没有横向滚动条
- [ ] 选中代码右键可附加，且代码块可复制
