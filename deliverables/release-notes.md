## Spatial Chess v0.1.0 · 自由摆棋首版

PICO Spatial SDK 6.1.9 · Shared Space 体积窗口 · PICO Spatial UI + PICO Sans

**包含**
- `SpatialChess-debug.apk`：安装到 PICO Emulator 6.1 或 PICO OS 6 设备（`pico-cli app install SpatialChess-debug.apk` → `pico-cli app launch com.example.spatialchess`）。

**功能**
- 32 枚棋子标准开局、64 格棋盘、两侧各 16 槽收纳盒（胡桃木 + 绿绒）
- 手势抓取 / 点选移动，松手吸附格心；吃子先收纳后落子并可单步撤销
- 撤销 / 重做（≤120 步）、标准开局 / 清空棋盘、兵到底线升变（王后 / 车 / 象 / 马）
- 桌面锚定流程、摆放与显示（缩放 80–140%、朝向、高度、坐标、音效、减少动态效果）
- 本地自动保存（原子写入 + 备份）与失败重试；中文 / English 原地切换
- 落子 / 收纳空间音效

**验证**
- 模拟器截图与 PRD 功能覆盖：仓库 `deliverables/SpatialChess_模拟器截图与验证.xlsx`
- 真机手势抓取与桌面识别仍需在 PICO 设备上实测
