# Spatial Chess · 自由摆棋（PICO Spatial SDK）

离线、Shared Space 的立体国际象棋：一副常驻共享空间的棋盘，可锚定到桌面，自由移动黑白双方，吃子先收纳再落子，支持撤销/重做、升变、本地自动保存和中英文原地切换。实现依据 `Spatial_Chess_PRD_UIUX.docx` 与 Figma「Spatial Chess 自由摆棋 PRD 与 UI UX」。

## 技术要点

- **容器**：一个 `Form.Volumetric` 的 `DefaultWindowContainer`（1.3 × 0.62 × 1.0 m，`WorldScale.Fixed`，`VolumeAlignment.Gravity`），全程不打开 Stage。
- **UI**：PICO Spatial UI —— `PicoTheme`（Figma 配色 + **PICO Sans** 字体）、系统 `Toolbar`（撤销/重做/摆放与显示/重置/帮助）、顶部 `Augment`（标题、锚定与保存芯片、保存失败横幅）、体积内 `AttachmentPanel`（上下文面板、提示条、托盘计数、坐标）、`Sheet`（摆放与显示、帮助）、`AlertDialog`（重置）。
- **3D**：`SpatialView` + ECS。`chess.usdz` 加载一次，32 个 prim 重新挂到各自的 pivot（自动检测 Z-up 并校正），64 个格子碰撞体 + 两侧托盘 + 选中/落点/吃子/禁止标记；升变从隐藏原型 `clone`。
- **输入**：`detectSpatialTapGesture`（点选辅助）与 `detectSpatialDragGesture`（默认抓取；保留抓取偏移，松手投影到最近有效格并 120 ms 吸附）。
- **数据**：`BoardState` 为唯一事实来源，Move/Capture/Store/Restore/Promote/Reset/Clear 均产生完整快照并记一条历史（≤120 步）；提交后临时文件 → 校验 → 原子替换，保留一份备份。
- **本地化**：`assets/locales/{zh-CN,en-US}.json`，键与占位符完全一致，缺键回退 en-US。
- **音效**：`assets/sfx/*.ogg`，落子/收纳时经 Spatial SDK 在棋子位置播放，失败回退 `SoundPool`。

## 下载

- **APK**：见 GitHub Releases（`SpatialChess-debug.apk`，安装到 PICO Emulator 6.1 / PICO OS 6 设备：`pico-cli app install SpatialChess-debug.apk`）。
- **模拟器截图与验证报告**：[`deliverables/SpatialChess_模拟器截图与验证.xlsx`](deliverables/SpatialChess_模拟器截图与验证.xlsx)（34 个场景，含 PRD 功能覆盖表）；单张截图在 [`deliverables/screenshots/`](deliverables/screenshots/)。

## 构建与运行

```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home
./gradlew :app:assembleDebug
pico-cli emulator start            # 或已在运行的 PICO 设备
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.spatialchess
pico-cli capture screenshot --out shot.png
```

## 调试通道（模拟器不支持脚本化空间输入）

```bash
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd select --es arg e2
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd square --es arg e4
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd drag --es arg "'g2 g4'"
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd view --es arg "30,20,1.6"   # 绕棋盘视角 yaw,pitch,zoom
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd state
```

其他命令：`tap` `store` `cancel` `confirm` `undo` `redo` `reset` `resetStandard` `clear` `settings` `apply` `help` `closeHelp` `promote <queen|rook|bishop|knight>` `skipPromotion` `lang <zh|en>` `anchor` `orient <deg>` `scale <80-140>` `height <cm>` `coords <on|off>` `reduceMotion <on|off>` `failsave <on|off>` `save` `pieces` `wipe`。这些命令走与真实输入相同的 ViewModel 路径，仅用于测试。

## 目录

- `app/src/main/java/com/example/spatialchess/`
  - `Main.kt` 入口 DSL · `ui/` 主题与 Spatial UI · `scene/ChessScene.kt` 3D 场景 · `app/ChessViewModel.kt` 状态机 · `model/Chess.kt` 数据与命令 · `data/` 持久化 · `i18n/` 文案 · `debug/` 调试通道
- `deliverables/` 模拟器截图与验证表（Excel）
