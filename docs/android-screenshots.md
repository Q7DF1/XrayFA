# Android 16 README 截图

顺序固定为 **Config → Home → Settings → Subscription**，共四张。图片保存在原有 fastlane 截图目录，依次对应 1.png、2.png、3.png、4.png。README 使用独立的界面展示图，F-Droid 使用完整的 PNG 原图。

## 规格与来源

- 当前源码的 XrayFA 1.7.1 Debug，Android 16 / API 36，镜像包 android-36.1，x86_64。
- 截图专用 AVD：`XrayFA_Screenshots_API_36_1`，序列号 `emulator-5582`。
- 模拟器渲染尺寸 1080×2400，20:9 手机竖屏（通过 wm size 设置），420dpi；默认字体大小、浅色主题、英文界面。
- 状态栏通过 Android System UI Demo Mode 统一为 9:41、满电、Wi-Fi；应用通知关闭。
- 使用 `adb exec-out screencap -p` 直接截图，保留完整应用画面和系统栏。
- 编码为 24-bit RGB PNG，无透明通道。仅移除原本全不透明的 alpha 通道；核验 RGB 像素与原图逐像素一致，无缩放、拉伸或界面修饰。
- 节点与凭据为示例数据，无真实订阅信息；Home 的已连接状态由实际运行的本地 SOCKS 测试服务产生，空闲流量真实显示为 0。
- 未添加手机外框、宣传文字、伪造性能数字或水印。README 配有页面名称与可访问性替代文本。

格式参考 [Google Play 预览素材规范](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)，状态栏统一使用 [Android Demo Mode](https://developer.android.com/studio/debug/dev-options)。本项目截图用于 F-Droid 与 README，采用现代手机的 20:9 比例，不作为 Google Play 上传素材（其长边不能超过短边两倍）；四张数量遵循本次需求。

## 更新流程

1. 在 Android 16 或以上版本安装当前源码构建，使用独立的示例数据。
2. 固定上述尺寸、主题、语言、字体与状态栏；关闭权限弹窗、键盘和通知。
3. Config 显示完整示例节点、收藏与选中状态；Home 使用真实连接；Settings 从顶部展示常用选项。
4. 等待页面切换动画结束，再使用 `adb -s emulator-5582 exec-out screencap -p` 分别保存四页。
5. 检查图片原生尺寸、文字、隐私与编码，更新 screenshot-manifest.json 中的版本、日期与 SHA-256。
6. 重新生成 README 展示图，检查四份 README 与完整截图页面的图片引用。

图片及校验信息见 [screenshot-manifest.json](screenshot-manifest.json)。

## README 展示图

`docs/assets/android-overview.svg` 为可编辑的排版源文件，内嵌四张原始 PNG，统一背景、圆角、阴影与页面标题。浏览器导出的 `android-overview.png`（2000×1240）用于 README，避免依赖 SVG 内嵌图片的渲染支持。更新截图后需重新生成展示图。
