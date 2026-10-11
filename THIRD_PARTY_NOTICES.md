# 第三方组件与致谢

工程直接依赖 AndroidX、Jetpack Compose、Kotlin、kotlinx.coroutines、ONNX Runtime、ExifInterface、JUnit 与 AndroidX Test。具体版本以各模块 `build.gradle.kts` 和 `toolchain.json` 为准；各组件沿用上游许可证，不由本项目重新授权。依赖树可运行 `./gradlew :app:dependencies` 查看。

仓库保留的许可文本：

- ONNX Runtime：`licenses/ONNX-Runtime-MIT.txt`。
- U²-Net：`licenses/U2NET-APACHE-2.txt`。
- rembg：`licenses/rembg-MIT.txt`。
- 拼豆色卡数据：`licenses/pindou-color-data-LICENSE.txt`、`core/data/src/main/assets/pindou-LICENSE.txt`。
- 拼音数据：`feature/pet/src/main/assets/pinyin-LICENSE.txt`。
- Gradle Wrapper：`licenses/gradle/LICENSE` 与 `NOTICE`。

来源： [ONNX Runtime](https://github.com/microsoft/onnxruntime)、[U²-Net](https://github.com/xuebinqin/U-2-Net)、[rembg](https://github.com/danielgatis/rembg)、[拼豆色卡](https://github.com/HansBug/pindou-color-data)、[拼音数据](https://github.com/mozillazg/pinyin-data)、[Gradle](https://github.com/gradle/gradle)。

该文件是组件与现有通知的索引，未声称完成所有传递依赖的许可证审计。角色动画和模型的资产边界另见 [ASSETS.md](ASSETS.md)。
