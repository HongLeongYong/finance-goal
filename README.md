# 💰 記帳與目標系統

你現在在哪 → 想去哪 → 要花多久 → 今天該做什麼。外加「你一分鐘值多少錢」。

一個在本機執行的個人理財網站：記帳、管理資產（現金、活存、定存、台股）、設定理財目標，並把每筆花費換算成「要工作多久才賺得到」。

![系統架構](docs/architecture.png)

- **技術**：Spring Boot 4.1 · Java 21 · Thymeleaf · MySQL · Flyway · Chart.js
- **安全**：沒有登入功能，伺服器只監聽 `127.0.0.1:8081`，只有你自己的電腦連得到。
- **匯報文件**：[docs/匯報.md](docs/匯報.md)（技術框架、功能、計算規則、資料模型、Demo 流程）

---

## 🚀 如何啟動

### 需要先安裝

| 軟體 | 版本 | 檢查指令 |
| --- | --- | --- |
| Java JDK | **21** 以上 | `java -version` |
| Git | 任意 | `git --version` |
| MySQL | 8 以上（只有「正式模式」需要） | `mysql --version` |

Maven 不用另外安裝，專案內附的 `./mvnw` 會自動下載。

### 下載專案

```bash
git clone https://github.com/HongLeongYong/finance-goal.git
cd finance-goal
```

### 方法 A：免裝 MySQL，馬上試玩（最快）

用 H2 記憶體資料庫啟動，**關掉伺服器資料就會清空**，適合先看看長什麼樣子：

```bash
./mvnw spring-boot:test-run
```

看到 `Started FinanceGoalApplication` 後，用瀏覽器打開 👉 http://127.0.0.1:8081

> Windows 請把 `./mvnw` 換成 `mvnw.cmd`。第一次執行會下載相依套件，大約需要 1～3 分鐘。

### 方法 B：接 MySQL，資料會保存（正式使用）

**1. 建立資料庫和帳號**（用 MySQL 的 root 執行一次就好）：

```bash
mysql -u root -p
```

進入 MySQL 後貼上（`你的密碼` 換成自己想設的密碼）：

```sql
CREATE DATABASE finance_goal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'app_user'@'localhost' IDENTIFIED BY '你的密碼';
GRANT ALL PRIVILEGES ON finance_goal.* TO 'app_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**2. 設定資料庫帳密**：複製範例設定檔，再把裡面的密碼改成上一步設定的密碼。

```bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

`application-local.properties` 已經列在 `.gitignore`，密碼不會被 commit。也可以不建這個檔，改用環境變數 `DB_USERNAME`、`DB_PASSWORD`。

**3. 啟動**：

```bash
./mvnw spring-boot:run
```

第一次啟動時，Flyway 會自動建立所有資料表（V1～V5），不用手動跑 SQL。看到 `Started FinanceGoalApplication` 後打開 👉 http://127.0.0.1:8081

**4. 停止**：在終端機按 `Ctrl + C`。

### 第一次使用建議

1. 到 **⚙️ 設定** 填月薪、上下班與午休時間，「今天已經賺了」計數器和「= 工作 xx 分鐘」才會出現。
2. 到 **🏦 資產與定存** 新增帳戶（錢包、銀行活存、信用卡）和定存。
3. 開始 **🧾 記帳**，記帳時選帳戶，餘額會自動增減。
4. 有台股的話，到 **📈 台股持股** 記買進紀錄。股價在交易日 14:45、17:30 自動更新；伺服器關著時不會更新，啟動後按一次「🔄 立即更新股價」即可。

### 常見問題

| 狀況 | 解法 |
| --- | --- |
| `Port 8081 was already in use` | 已經有一個伺服器在跑，先把它關掉；或在 `application.properties` 改 `server.port` |
| `Access denied for user 'app_user'`（1045） | `application-local.properties` 的密碼和 MySQL 設的不一樣 |
| `Access denied ... to database 'finance_goal'`（1044） | 帳號沒有授權，重新執行上面的 `GRANT` 那一行 |
| `release version 21 not supported` | Java 版本太舊，安裝 JDK 21 |
| 股價一直沒更新 | 需要連網；上櫃股票第一次查詢約 30 秒 |

---

## 🧪 測試

```bash
./mvnw test
```

36 個自動化測試，使用 H2 記憶體資料庫（MySQL 相容模式），**不需要 MySQL，也不會連網**，不會動到你的真實資料。

---

## 功能

| 頁面 | 網址 | 內容 |
| --- | --- | --- |
| 儀表板 | `/` | 淨資產、儲蓄率、「今天已經賺了」即時計數器（午休暫停）、時薪／分薪／秒薪、錢每分鐘幫你賺多少、目標進度、資產配置、定存提醒 |
| 記帳 | `/ledger` | 快速記帳（即時顯示「= 工作 xx 分鐘」）、依月份／類型／分類／帳戶篩選、可排序表格 |
| 月報表 | `/ledger/report` | 收支、儲蓄率、支出分類佔比與上月比較 |
| 資產與定存 | `/assets` | 現金／活存／負債帳戶、定存（到期日、利息、中途解約試算、自動續存）、淨資產趨勢 |
| 目標試算 | `/goals` | 三種模式：N 年後有多少／幾年達成／金額＋年限 → 行動建議（多存、少花、提高報酬、延長時間、一次投入、組合）；支援累積式存入紀錄 |
| 台股持股 | `/stocks` | 買賣紀錄（自動算手續費／證交稅、交割帳戶扣款入帳）、平均成本法損益、現金股利、每日自動抓收盤價、手動報價 |
| 設定 | `/settings` | 月薪、上下班與午休時間、券商手續費折扣、收支分類 |

## 計算規則

- **分薪** = 月薪 ÷（每月上班天數 × 每天工時）÷ 60；每天工時 = 下班 − 上班 − 午休
- **定存・每月領息（存本取息）**：每月利息 = 本金 × 年利率 ÷ 12（單利）
- **定存・到期領回（整存整付）**：到期本息 = 本金 × (1 + 年利率/12)^月數（按月複利）
- **中途解約（估算）**：未滿 1 個月不計息；滿 1 個月以上按實存月數、目前利率八折計算
- **目標試算**：每月月底存入、按月複利；FV = PV(1+r)^n + PMT·((1+r)^n−1)/r。被動收入目標 = 每月被動收入 × 12 ÷ 提領率；填通膨時改用實質報酬 (1+名目)/(1+通膨)−1
- **試算預設值**：本金 = 淨資產；每月可存 = 近 3 個月平均結餘；年報酬 = 資產依金額加權（現金 0%、定存用利率、股票以 5% 估）
- **台股手續費** = 成交金額 × 0.1425% × 折扣（無條件捨去），整股最低 20 元（可設定）、零股最低 1 元；**證交稅**（賣出）0.3%，ETF 0.1%
- **持股損益**：平均成本法；未實現 = 市值 − 持有成本，已實現 = 賣出金額 − 手續費 − 稅 − 賣掉部分的平均成本
- **股價來源**：證交所 OpenAPI（上市）、櫃買中心 OpenAPI（上櫃），上市股票另用證交所個股日成交資訊補抓當天收盤價；股價先存進資料庫，頁面只讀資料庫
- **淨資產** = 現金 + 銀行活存 + 定存 + 股票市值 − 負債；每次資產變動與每天 23:55 更新當月快照

## 專案結構

```
src/main/java/com/example/finance/
├── model/        資料表對應的實體（Entity）
├── repository/   Spring Data JPA
├── service/      商業邏輯與所有公式
├── stock/        台股：股價抓取、持股損益、買賣
├── form/         表單與驗證
└── web/          Controller（頁面與路由）
src/main/resources/
├── db/migration/ Flyway SQL（V1～V5）
├── templates/    Thymeleaf 頁面
└── static/       CSS、JavaScript
```
