# Mob Arms Race

*Creeper mortars vs. village air defence — a NeoForge mod for Minecraft 26.1.2.*

苦力怕學會了迫擊炮，村莊也組織起防空。

## 功能

### 進攻方：苦力怕

- **迫擊炮苦力怕**：自然生成的苦力怕有 40% 會變成它，也可以用生怪蛋生成。它會從最遠 120 格外，以 60° 高拋朝村民、鐵傀儡和雪傀儡齊射，每輪 4 發，裝填 8 秒後再打。它不需要看到目標，躲在山脊後面也能開火。
- **有限的壽命**：每隻迫擊炮苦力怕打完 10 發炮彈後就會燃盡死亡。
- **苦力怕炮彈**：拖著紅色發光尾跡，同時有碰炸和近炸兩種引信。被擊落時可能在空中爆炸，也可能分裂成 2 到 5 顆威力較小的小炸彈，繼續落向地面。

### 防守方：村莊

- **雪傀儡近防炮**：所有雪傀儡都會用每秒 10 發的高速雪球攔截來襲炮彈。
  - 炮彈預計 4 秒內落地時開火。曳光一開始會在炮彈周圍亂飛，再逐漸收斂咬住。
  - 通常在雪傀儡上方 50 到 70 格擊落，對村民的整體攔截率約 70% 到 80%。
  - 朝自己落下的炮彈會一直打到最後。
  - 沒有整顆炮彈來襲時，改打小炸彈，先打落點離自己最近的。
  - 天上沒有東西時，會連射附近的殭屍等敵對生物。
- **雪傀儡更耐用**：會守在第一次出現的位置附近，而且不會被雨水、水或炎熱的生態域融化。
- **鐵傀儡擲貓**：鐵傀儡把附近的貓丟到苦力怕旁邊。貓落地後，附近的苦力怕會停止引爆，迫擊炮也會停火。

### 玩家

- **手持苦力怕炮彈**：看哪裡就打哪裡，拿在手上時會顯示炮彈弧線和落點準星。
- **抓起並投擲苦力怕**：空手右鍵把苦力怕收進手中，再丟出去炸。

幾乎所有數值（射程、攔截精準度、分裂機率、炮彈數上限等）都可以在設定檔或遊戲內的「模組設定」畫面調整。從舊版更新時，改過平衡的預設值會自動套用一次。

各功能的詳細用法和所有設定值請見 [docs/FEATURES.md](docs/FEATURES.md)；平衡的量測數據和開發紀錄請見 [docs/DESIGN.md](docs/DESIGN.md)。

## 安裝

| 需求 | 版本 |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 或更新 |
| Java | 25 |

把 `mobarmsrace-<版本>.jar` 放進遊戲的 `mods` 資料夾即可。

## 從原始碼建置

```sh
./gradlew build        # 產生 build/libs/mobarmsrace-1.0.3.jar，並執行單元測試
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
