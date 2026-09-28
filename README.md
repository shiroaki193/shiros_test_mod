# Mob Arms Race

*Creeper mortars vs. village air defence — a NeoForge mod for Minecraft 26.1.2.*

苦力怕學會了迫擊炮，村莊也組織起防空。

- **迫擊炮苦力怕**：自然生成的苦力怕有 40% 會是它。從 120 格外朝村莊齊射苦力怕炮彈。
- **雪傀儡近防炮**：所有雪傀儡都會用高速雪球在高空擊落來襲炮彈（攔截率約 70%），沒有炮彈時也會連射附近的敵對生物。
- **手持苦力怕炮彈**：玩家看哪裡就打哪裡，拿在手上時會顯示落點準星。
- **抓起並投擲苦力怕**：空手把苦力怕收進手中，再丟出去炸。
- **鐵傀儡擲貓**：鐵傀儡把貓丟到苦力怕旁邊，把它們嚇散。

各功能的遊戲內用法和設定值請見 [docs/FEATURES.md](docs/FEATURES.md)，設計和開發紀錄請見 [docs/DESIGN.md](docs/DESIGN.md)。

## 安裝

| 需求 | 版本 |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 或更新 |
| Java | 25 |

把 `mobarmsrace-<版本>.jar` 放進遊戲的 `mods` 資料夾即可。

## 從原始碼建置

```sh
./gradlew build        # 產生 build/libs/mobarmsrace-1.0.1.jar，並執行單元測試
```

不需要事先安裝 JDK 25：Gradle 會自動下載。`gradlew` 會把所有快取（Gradle、JDK、Minecraft 檔案）放在專案內的 `.gradle-home/`，不會寫入使用者目錄。

## 測試

```sh
./gradlew test                  # 彈道數學的單元測試
./gradlew runGameTestServer     # 在無畫面的伺服器上執行實機測試（GameTest）
./gradlew runClient             # 開發用客戶端
```

`python tools/setup_demo_world.py` 之後執行 `./gradlew runDemoClient`，會直接進入一個自動上演各功能的展示世界。詳見 [docs/DESIGN.md](docs/DESIGN.md) 的「開發與測試」。

## 靈感來源

- 老丹Daniel《我把MC所有生物全都改造成了戰爭武器 #1》（[BV1nR8y6uEUP](https://www.bilibili.com/video/BV1nR8y6uEUP/)）
- 鋼蛋林《那一天，雪傀儡學會了近防炮反導》（[BV1Dcgm6VEaT](https://www.bilibili.com/video/BV1Dcgm6VEaT/)）

## 授權

本 Mod 以 [MIT License](LICENSE) 釋出。專案範本檔案來自 [NeoForged MDK](https://github.com/NeoForged/MDK)，授權見 [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt)。
