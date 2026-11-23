-- 第一期：個人設定、帳戶、定存、記帳、淨資產快照

-- 個人設定（只有一筆，id = 1）：用來算時薪 / 分薪 / 秒薪
CREATE TABLE profile (
    id                  INT           NOT NULL PRIMARY KEY,
    monthly_salary      DECIMAL(15,2) NOT NULL DEFAULT 0,
    work_days_per_month INT           NOT NULL DEFAULT 22,
    work_hours_per_day  DECIMAL(4,1)  NOT NULL DEFAULT 8,
    work_start_time     TIME          NOT NULL DEFAULT '09:00:00',
    updated_at          DATETIME      NOT NULL
);
INSERT INTO profile (id, monthly_salary, work_days_per_month, work_hours_per_day, work_start_time, updated_at)
VALUES (1, 0, 22, 8, '09:00:00', CURRENT_TIMESTAMP);

-- 帳戶：現金 / 銀行活存 / 負債（負債的 balance 為「欠多少」，正數）
CREATE TABLE account (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)   NOT NULL,
    type        VARCHAR(20)   NOT NULL,      -- CASH / BANK / LIABILITY
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0,
    note        VARCHAR(200),
    archived    INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL
);

-- 收支分類
CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(30)  NOT NULL,
    type        VARCHAR(10)  NOT NULL,       -- INCOME / EXPENSE
    icon        VARCHAR(10)  NOT NULL DEFAULT '',
    sort_order  INT          NOT NULL DEFAULT 0
);
INSERT INTO category (name, type, icon, sort_order) VALUES
 ('餐飲', 'EXPENSE', '🍜', 10),
 ('交通', 'EXPENSE', '🚇', 20),
 ('居住', 'EXPENSE', '🏠', 30),
 ('日用品', 'EXPENSE', '🧴', 40),
 ('娛樂', 'EXPENSE', '🎮', 50),
 ('購物', 'EXPENSE', '🛍️', 60),
 ('醫療', 'EXPENSE', '💊', 70),
 ('學習', 'EXPENSE', '📚', 80),
 ('保險', 'EXPENSE', '🛡️', 90),
 ('其他支出', 'EXPENSE', '📦', 999),
 ('薪水', 'INCOME', '💼', 10),
 ('獎金', 'INCOME', '🎁', 20),
 ('利息', 'INCOME', '🏦', 30),
 ('股利', 'INCOME', '📈', 40),
 ('其他收入', 'INCOME', '💰', 999);

-- 記帳明細
CREATE TABLE ledger_entry (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entry_date   DATE          NOT NULL,
    type         VARCHAR(10)   NOT NULL,     -- INCOME / EXPENSE
    amount       DECIMAL(15,2) NOT NULL,
    category_id  BIGINT        NOT NULL,
    account_id   BIGINT,
    note         VARCHAR(200),
    created_at   DATETIME      NOT NULL,
    CONSTRAINT fk_entry_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_entry_account  FOREIGN KEY (account_id)  REFERENCES account (id)
);
CREATE INDEX idx_entry_date ON ledger_entry (entry_date);

-- 定存
CREATE TABLE time_deposit (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bank             VARCHAR(50)   NOT NULL,
    principal        DECIMAL(15,2) NOT NULL,
    annual_rate      DECIMAL(6,3)  NOT NULL,   -- 年利率（%），例如 1.700
    start_date       DATE          NOT NULL,
    term_months      INT           NOT NULL,
    rate_type        VARCHAR(10)   NOT NULL,   -- FIXED / FLOATING
    payout           VARCHAR(15)   NOT NULL,   -- MONTHLY（存本取息）/ AT_MATURITY（整存整付）
    maturity_action  VARCHAR(20)   NOT NULL,   -- AUTO_RENEW / PRINCIPAL_RENEW / CLOSE
    closed           INT           NOT NULL DEFAULT 0,
    note             VARCHAR(200),
    created_at       DATETIME      NOT NULL
);

-- 淨資產月快照
CREATE TABLE net_worth_snapshot (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_month     DATE          NOT NULL,  -- 該月 1 號
    total_assets       DECIMAL(15,2) NOT NULL,
    total_liabilities  DECIMAL(15,2) NOT NULL,
    net_worth          DECIMAL(15,2) NOT NULL,
    updated_at         DATETIME      NOT NULL,
    CONSTRAINT uk_snapshot_month UNIQUE (snapshot_month)
);
