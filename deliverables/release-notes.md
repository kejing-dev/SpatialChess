## Spatial Chess v0.1.1 · 真机修复

PICO Spatial SDK 6.1.9 · Shared Space 体积窗口 · PICO Spatial UI + PICO Sans

**变更**
- 修复真机（PICO swan / PICO OS 6）启动即闪退：SDK 的 `MeshResource.createBox / createCylinder` 依赖系统 Spatial 运行时缺失的 foundation 扩展类；托盘与标记几何体改为随包 `primitives.usdz`。
- 移除桌面锚定 / 平面检测相关 UI 与流程（Shared Space 暂无该能力）：不再有「锚定到桌面」面板、锚定芯片、半透明摆放模式和虚线桌面范围；首次使用看完教程后直接开始摆棋。

**包含**
- `SpatialChess-debug.apk`：`pico-cli app install SpatialChess-debug.apk` → `pico-cli app launch com.example.spatialchess`（已在 PICO swan 真机与 Emulator 6.1 验证启动）。

**功能**
- 32 枚棋子标准开局、64 格棋盘、两侧各 16 槽收纳盒（胡桃木 + 绿绒）
- 手势抓取 / 点选移动，松手吸附格心；吃子先收纳后落子并可单步撤销
- 撤销 / 重做（≤120 步）、标准开局 / 清空棋盘、兵到底线升变
- 摆放与显示（缩放 80–140%、朝向、高度、坐标、音效、减少动态效果）、中文 / English 原地切换
- 本地自动保存（原子写入 + 备份）与失败重试；落子 / 收纳空间音效
