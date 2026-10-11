# 构建与验证

## 工具链

工程锁定 Gradle9.6.0（Wrapper 含发行包 SHA-256）、AGP9.4.0、Kotlin2.4.20、Compose BOM2026.08.00。编译平台 `platforms;android-37.2`，Build Tools36.0.0，minSdk26/targetSdk37。实际本机使用 Android Studio 的 JBR25；字节码目标为17，不代表 Gradle/AGP只需JDK17。

打开 Android Studio 后选择 JDK25，并用 SDK Manager 安装指定平台。SDK 路径由 Android Studio 写入本机 `local.properties`；它被忽略，不提交到 GitHub。首次构建需联网下载 Wrapper 和 Maven 依赖，后续完整缓存可加 `--offline`。如果镜像没有所需 SDK，不要静默降低编译版本，请先检查 SDK 来源与可用包。

## 常用命令

```sh
# Windows 将 ./gradlew 换成 gradlew.bat
./gradlew :app:assembleLiteDebug
./gradlew :core:model:test :app:testLiteDebugUnitTest :app:lintLiteDebug
./gradlew :app:assembleLiteDebugAndroidTest
# 仅在自行选择并授权的测试设备上执行
./gradlew :app:connectedLiteDebugAndroidTest
python tools/check_release.py
```

构建输出为 `app/build/outputs/apk/lite/debug/app-lite-<ABI>-debug.apk`，三个 ABI 是 arm64-v8a、armeabi-v7a、x86_64。当前工程只有 Lite flavor，任务名保留 Lite 是为了延续原有构建方式。

设备测试包含悬浮窗、屏幕旋转、字体大小和输入法操作，请使用专用模拟器；不要直接把自动化测试指向有个人数据的手机。CI 的构建和 Lint 不替代真实设备测试。0.16.3 实测范围见 [验证记录](VERIFICATION.md)。

## 签名

仓库不包含 `.keystore/.jks`、正式签名凭据或发布者私人密钥。维护者的可选 Debug 配置使用 Android 标准测试口令，不是正式发行凭据。普通开发者直接构建，Gradle 会使用自己机器的默认 Debug 签名。CI 也生成临时调试签名，**不能把 CI artifact 当作可覆盖官方 APK 的更新包**。

维护者为保持已有 Lite 测试版升级链，可通过本机环境变量 `PETWORLD_DEBUG_KEYSTORE` 指向仓库外的已有调试密钥。此项可选，不在 GitHub 公共任务中设置，不把密钥提交到仓库。APK 的签名证书指纹可以公开，私钥不可以。

`:app:assembleLiteRelease` 可构建未签名 Release；当前交付并未把它标成已签名正式发行版。正式签名、升级迁移和商店发行单独规划，不在本次公开源码整理中自动执行。

## 资源和兼容

预置精灵是 128×128 的压缩 `.sprbin` 数据和 PNG 预览；`SpriteCatalog` 按需读取和缓存。`docs/builtin-companions.json` 给出14位默认名单。旧视频角色不再加载或打包。历史模型/存储类中部分乐园兼容字段仍存在，但乐园模块和素材没有随此仓库提供。

快速主体提取模型随工程提供，约4.6MB。精细模型不是基础构建依赖，用户可在 App 中另行下载或本地导入。来源、校验与许可见 `licenses/model-provenance.json` 和 ASSETS.md。
