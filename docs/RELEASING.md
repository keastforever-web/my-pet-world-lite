# GitHub发布步骤（维护者）

## 先检查

- 阅读 ASSETS.md，确认角色、视频和模型的再分发范围；当前记录仍有待核实项。
- 原创代码已由维护者选择 MIT，保留 LICENSE 正文和 LICENSE.md 中的素材范围说明。
- 运行 `python tools/check_release.py`、构建/测试/Lint；设备验证、APK版本、包名、签名指纹和 SHA256 对应当前源码。
- 不上传旧开发工程整个文件夹。该仓库不包含签名密钥、私有数据库、本机路径、服务器、聊天或开发记录。

## 上传源码

维护者仓库为 [keastforever-web/my-pet-world-lite](https://github.com/keastforever-web/my-pet-world-lite)。后续开发请克隆该仓库，再检查待提交文件、提交并推送。首次公开使用独立的干净 Git 历史；不要重新初始化已有仓库或覆盖别人的远程。

新增源码提交前执行 `git status --short`，尤其检查签名文件、.env、local.properties、数据库与 APK 没有被跟踪。新仓库应选择不包含旧工程历史的干净目录，旧项目的 .git 不应复制进来。Linux/macOS 下运行 `chmod +x gradlew`；必要时用 `git update-index --chmod=+x gradlew` 保存执行权限。

## 创建发行版

源码提交完成后，为该提交创建 `v0.16.3-lite` tag，基于该 tag 创建 GitHub Release。当前 APK 是 Debug 验收包，建议勾选 **Pre-release**。发行说明可用 docs/RELEASE_NOTES_0.16.3.md。

附加文件：

- PetWorld-Lite-0.16.3-arm64-v8a.apk
- PetWorld-Lite-0.16.3-armeabi-v7a.apk（可选旧设备）
- SHA256SUMS.txt

APK放 Release assets，不提交到源码分支。GitHub会为 tag 提供源码 ZIP/TAR 链接；其源码内容应与构建使用的版本相符。[官方 Releases 说明](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)。

发布前先保存为 Draft，检查素材状态、许可证、APK 哈希和说明，再由维护者决定公开。仓库中的 CI 权限为 contents:read，只进行构建/检查，不会自动创建 Release、上传维护者密钥或发布正式版。

## 后续版本

同时递增 versionName/versionCode、更新 CHANGELOG 和版本文档，维护同一签名升级链。不要覆盖已经公布的同名 APK，避免哈希和版本记录混乱。正式商店签名另行规划，不能直接把当前 Debug 包称作正式上架版。
