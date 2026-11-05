# 麻将计分器 · MahjongScore

四人麻将在桌上打的账本：一桌一部手机记分，四个人各自能看到实时分差；没装 App 的用浏览器打开网页版一样能上桌。

- **Android App**：Kotlin + Jetpack Compose，单文件状态机，7.3 MB
- **网页版**：单文件零依赖 HTML，iOS / 电脑浏览器直接开，与 App 混端同桌
- **服务端**：Go 标准库 + 一个 WebSocket 实现，静态编译单文件，内存占用约 2 MB

当前版本：**App v1.0.2（versionCode 34）· 服务端 0.8.2**

---

## 下载与使用

| 通道 | 说明 |
|---|---|
| GitHub Releases | 见本仓库右侧 Releases，`MahjongScore-v1.0.2.apk` |
| 应用内更新 | 已装 v0.6.0 及以上版本的，设置页点「检查更新」即可 |

安装要求：Android 8.0（API 26）及以上。首次安装需允许「安装未知来源应用」。

装好后的流程：填昵称 → 建房（得到 5 位房号）或输房号加入 → 四人坐满 → 吊风定庄 → 开打记分。

---

## 功能

**牌桌**

- 四人同桌，房号加入，断线自动重连（服务端保留座位 60 秒，超时标记离线但保留座位）
- 吊风定风位，庄家连庄可按局撤销
- 记分：平胡 / 点炮 / 自摸 / 搂宝 / 宝中宝，默认分值 5 / 20 / 10 / 20 / 40，每家各付
- 点炮由输家发起，避免多家重复记分；自摸由赢家发起，三家自动各付
- 满 4 人才能开局（吊风、记分），3 人喝茶时可以照常撤销与结束对局
- 离线玩家整卡置灰并标「离线」，仍可对其记点炮

**对账**

- 逐局流水（第 N 局 · 谁给谁点炮 / 谁自摸），分值口径写清楚
- 房主可撤销最新一局；对局结束后流水落历史
- 战绩统计：局数、胜负、净得分排行

**其他**

- 计分规则自定义：房主可改五种胡型的分值，改完全桌同步；已记的局不受影响
- 邀请好友：一键复制邀请文案或系统分享，自带房号与下载链接
- 用户协议 / 隐私政策 / 关于我们
- 应用内检查更新

---

## 目录结构

```
MahjongScore/            Android App（Kotlin + Compose）
  app/src/main/java/com/zhouge/mahjongscore/
    net/GameClient.kt    与服端通信、状态分发
    ui/                  大厅 / 牌桌 / 历史 / 战绩 / 设置 / 规则 / 文档
    HistoryStore.kt      本地历史与战绩
mjserver/                Go 服务端 + 网页版
  main.go                房间管理、WebSocket、记分校验、静态资源
  web/index.html         网页版（单文件，每请求现读，改页面不用重启）
  test-web.js            端到端测试（Node 全局 WebSocket 直连）
施工单-*.md              每个版本的需求、改动与验收记录
mahjong-icon/            App 图标概念稿与图标规范
```

---

## 本地构建

**App**（需 JDK 17、Android SDK 34、Gradle 8.5+，工程未带 wrapper）

```bash
cd MahjongScore
gradle assembleRelease      # 产物在 app/build/outputs/apk/release/
gradle assembleDebug        # 调试包
```

`release` 沿用 debug 签名（`signingConfig = signingConfigs.getByName("debug")`），目的是让正式包能直接覆盖安装此前的 debug 包、不丢本地战绩。**上架应用商店需换正式 keystore 重新签名**，换签名必须卸载重装。

**服务端**（需 Go 1.21+）

```bash
cd mjserver
GOOS=linux GOARCH=amd64 go build -o mjserver-linux-amd64 .
go build -o mjserver-test.exe .        # 本机调试
node test-web.js                       # 端到端测试，需先在本机起服务
```

服务端单文件、无外部依赖，监听 `:9100`，数据原子写入同目录 `mjserver.json`，备份就是复制这个文件。

---

## 协议摘要

- 连接：`ws://<host>:9100/ws`
- 客户端 → 服务端：`join`（建房/加入）、`play`（记分）、`wind`（吊风）、`undo`（撤销）、`end`（结束）、`setScores`（房主改规则）
- 服务端 → 客户端：`state`（全量状态）、`round`（新流水）、`undid`、`peers`（在线变化）、`error`
- 每局流水带递增 `seq`，断线重连后靠 `state` 全量对账
- 房间状态房主优先，规则为房间级

---

## 免责

仅供朋友间娱乐记分，不含任何赌博功能、不涉及金钱结算。牌局数据存在自建服务器上，不做他用。

## 许可

源码公开供参考，未声明开源许可前保留全部权利。
