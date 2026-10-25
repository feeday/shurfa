# 轻语输入法 · Qingyu IME

一个原生 Android 离线输入法基础项目。Java + 系统 View，无第三方运行时依赖。

## 已实现

- 26 键拼音：内置基础字词库，完整词组优先，支持按已知前缀逐段选字。
- 横向滑动候选栏；空格选择首项；点击拼音栏提交原文。
- 英文与大小写切换，数字和常用符号面板。
- 删除选中文本、按 Unicode 码点退格（不拆开常见 emoji 的代理对；组合 emoji 仍可能逐码点删除）、长按连续删除。
- 按输入框提供“搜索 / 发送 / 前往 / 下项 / 完成 / 换行”。
- 数字框自动显示数字面板；密码、邮箱、网址默认英文；密码框不启用中文。
- 启用向导与试打区。长按空格打开系统输入法选择器。
- 无网络权限、不存储输入历史、不读取剪贴板。

## 当前边界

这是 0.1.0 原型，词库规模有限，不等同于成熟输入法。没有智能整句解码、简拼、模糊音、自学习、语音、手写、九宫格、剪贴板面板或主题设置。中文模式输入完整小写拼音，ü 用 v。未知拼音可点击上方原文提交。系统字体放大、横屏、平板及不同厂商设备仍需实测。

## GitHub 自动生成 APK

1. 在 GitHub 新建仓库（例如 `android-ime`），提交本目录全部文件。注意包含 `.github/workflows/android.yml`。
2. 进入仓库 **Actions → Build Android APK**，等待构建成功。
3. 下载 **Artifacts → QingyuIME-debug**，解压得到 `app-debug.apk`。
4. 安装 APK，打开“轻语输入法”，依次启用、切换、试打。

也可在 Actions 页面用 **Run workflow** 手动构建。未运行 Actions 或构建失败时不会有 APK。仓库内的自动构建会执行单元测试、Android Lint 和 debug APK 打包。debug APK 仅用于测试；正式发行需配置自己的稳定签名，CI 的临时 debug 签名不保证跨次安装可覆盖升级。

## 本地构建

需要 JDK 17、Gradle 8.9、Android SDK Platform 35 和 Build Tools 34.0.0，以及首次下载依赖所需的网络。

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

项目没有附带 Gradle Wrapper 二进制；已安装 Gradle 8.9 后可运行 `gradle wrapper --gradle-version 8.9` 生成标准 Wrapper，再使用 `./gradlew` 或 `gradlew.bat`。Android Studio 可选择本地 Gradle 8.9，SDK 路径可设置在本机 `local.properties`（不要提交）。

输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 词库扩展

编辑 `app/src/main/assets/pinyin.txt`，每行：

```text
nihao 你好
qingyu 轻语
shi 是 时 事 十
```

不带声调，候选按书写顺序排列。词库为本项目的小型人工基础词表；如引入外部词库，需另行遵守其许可。

## 验证状态

交付环境缺少 Android SDK 和 Gradle，未完成 APK 编译、JUnit 执行或实机验证。已通过 JDK 17 编译并实际运行拼音引擎的 9 项独立检查（包含逐段选词、大小写、ü、重复词条与未知输入），并通过全部 Java 文件语法解析、XML / YAML 结构及 486 个拼音条目、1,377 个候选字词的格式检查。附有 JUnit 测试、自动构建配置和下面的实机检查表。以上不代表 Android 应用已成功编译或通过实机验证。

## 实机检查表

- 启用后在试打区输入 `nihao`，点选“你好”；输入 `nihaoma` 选择“你好吗”。
- 输入 `nihaoshijie`，选“你好”后剩余 `shijie`，再选“世界”。
- 输入拼音后退格、移动光标、切换输入框，不应串入上一个输入框的候选。
- 中文/英文/数字切换、英文大写、符号、长按删除、长按空格。
- 分别在密码、邮箱、数字、聊天和搜索输入框检查布局及回车动作。
- 插入常见 emoji 后退格；选中一段文本后删除；切到其他应用再返回。
- Android 6 / Android 15+、横竖屏、手势导航、三键导航与大字体布局。

## 官方开发参考

- https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- https://developer.android.com/build/releases/agp-8-7-0-release-notes

## License

MIT，见 `LICENSE`。
