<div align="center">

# 我的宠物世界 · Lite

**把你画的小伙伴，带到桌面上。**

Android 像素创作 · 动作编辑 · 桌面陪伴

<img src="core/data/src/main/assets/sprites-v16/bear16-honey-preview.png" width="96" alt="蜂蜜抱抱熊">
<img src="core/data/src/main/assets/sprites-v16/bear16-rose-preview.png" width="96" alt="桃桃月牙熊">
<img src="core/data/src/main/assets/sprites-v16/bear16-lilac-preview.png" width="96" alt="星星晚安熊">
<img src="core/data/src/main/assets/sprites-v16/bear16-mint-preview.png" width="96" alt="薄荷小叶熊">
<img src="core/data/src/main/assets/sprites-v16/bear16-blue-preview.png" width="96" alt="蓝蓝眠星熊">

[安装与使用](docs/INSTALL.md) · [编译源码](docs/BUILDING.md) · [更新记录](CHANGELOG.md) · [隐私说明](PRIVACY.md) · [素材与许可](ASSETS.md)

</div>

这是“我的宠物世界”的伙伴版 Android 源码，当前版本 **0.16.3**。保留创作、作品、宠物、设置四个页面，不提供乐园地图、多人联机或喂食系统。

## 能做什么

- 从图片提取像素图案，选择色卡、调整尺寸和描边；图片在本机处理。
- 在最大 128 × 128 的画布上绘画，使用对称绘制、选区、移动、复制粘贴和逐帧动作编辑。
- 收藏、编辑、导入和分享 `.cyberpet` 宠物文件；分享文件不含 API 密钥、聊天记录与私人记忆。
- 同时把最多 5 位伙伴带到桌面，分别互动、收回；可设置大小、不透明度、自由活动和躲藏。
- 长按打开环绕动作菜单，使用迷你拼音键盘或手机键盘聊天。聊天需自行配置兼容接口，不附赠 API 服务。

## 内置伙伴

首次安装内置 **14 位**；用户自己创作和导入的伙伴不计入此数。

- 五只小熊：蜂蜜·抱抱熊、桃桃·月牙熊、星星·晚安熊、薄荷·小叶熊、蓝蓝·眠星熊。
- 四只龙：橘龙、红龙、蓝龙、紫龙。
- 一位鼠大师。
- 四位默认动物：草莓小狗、紫芋小蛇、奶油兔兔、薄荷小猫。

十位 128 格精灵各有 8 组动作。旧版单独的“奶龙·像素版”和“耄耋猫·像素版”不再作为 Lite 默认伙伴出现。升级保留旧文件而隐藏这两个旧 ID，不删除用户自创作品。准确名单见 [机器可读清单](docs/builtin-companions.json)。

## 下载安装

在本仓库 [Releases 下载页](https://github.com/keastforever-web/my-pet-world-lite/releases/tag/v0.16.3-lite)下载 `PetWorld-Lite-0.16.3-arm64-v8a.apk`，大部分现代安卓手机使用此版本。仅旧 32 位设备选择 `armeabi-v7a`。不要下载 GitHub 的源码 ZIP 当作安装包。

最低 Android 8.0（API26）。当前提供 **Debug 测试版**，尚不是应用商店正式版。发布者提供的 APK 沿用已有 Lite 测试签名；自行编译的默认签名通常不同，不能直接覆盖它。先导出自己的作品，避免卸载造成数据丢失。详见 [安装指南](docs/INSTALL.md)。

## 从源码构建

使用 Android Studio 打开本目录，配置 JDK25 和 Android SDK（平台 37.2、Build Tools 36.0.0）。工程使用 Gradle9.6.0、AGP9.4.0、Kotlin2.4.20，完整版本记录在 `toolchain.json`。不要仅为编译随意升级这些版本。

```sh
./gradlew :app:assembleLiteDebug
./gradlew :core:model:test :app:lintLiteDebug
```

Windows 使用 `gradlew.bat`。APK 输出至 `app/build/outputs/apk/lite/debug/`。完整配置、设备测试和签名差异见 [构建说明](docs/BUILDING.md)。无需填写 API Key 即可绘画、编辑动作和使用桌面伙伴。

## 工程结构

```text
app/                 Android 入口与 Lite 装配
core/model/          像素、动作与本地数据模型
core/data/           作品、精灵、聊天及文件持久化
core/imaging/        本机图片处理与 ONNX 主体提取
core/ui/             共用界面和画布组件
feature/studio/      图片工作台
feature/pixel/       绘画与动作编辑
feature/library/     作品收藏
feature/pet/         伙伴、桌面服务和迷你键盘
feature/settings/    偏好和接口配置
```

为兼容已有数据，部分共用模型仍保留历史字段或格式；这不表示 Lite 提供乐园界面。此仓库没有 `feature/park`、完整版入口或联机服务器。

## 反馈与参与

请使用 Issues 模板说明版本、手机系统、复现步骤及期望效果。截图前遮盖接口密钥和私人聊天。[贡献指南](CONTRIBUTING.md)说明了检查方法；安全问题请遵循 [SECURITY.md](SECURITY.md)。

## 许可状态

原创代码采用 [MIT License](LICENSE)，适用范围见 [LICENSE.md](LICENSE.md)。上游组件遵循各自许可证，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。角色、动画、模型及其他图片的来源和再分发状态单独记录在 [ASSETS.md](ASSETS.md)，不自动纳入原创代码的 MIT 授权。维护者计划补充参考角色素材的授权依据或替换方案。
