## Spatial Chess v0.2.1 · Figma 图标、可关闭提示、抓边缘旋转

**变更**
- 工具栏五个图标改为 Figma「04 实装改版」导出的图标（撤销 / 重做 / 设置 / 新棋盘 / 帮助），运行时按状态着色。
- 「伸手抓住棋子，移动后松手吸附」提示改为可关闭的半透明提示条：启动默认显示，左侧感叹号徽标，点一下即隐藏；出现新的提示（选中、吸附、错误等）时再显示。
- 抓住棋盘的木质边缘即可旋转：沿边缘拖动每 12 cm 转 90°（前边向右 / 后边向左 / 左边拉近 / 右边推远都是 +90°），抬起或放下每 6 cm 倾斜 ±20°（0–40°），档位与设置面板、侧边箭头一致并立即保存；旋转期间棋子操作暂停。
- 调试广播新增 `dismissHint`、`rim <边>:<dx>,<dy>,<dz>`；文案键 `ui.text_093`。

**包含**
- `SpatialChess-debug.apk`：`pico-cli app install SpatialChess-debug.apk` → `pico-cli app launch com.example.spatialchess`（PICO Emulator 6.1 验证）。
