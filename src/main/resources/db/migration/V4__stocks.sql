-- 第三期：台股持股

-- 股票基本資料（代號、名稱、上市/上櫃）
CREATE TABLE stock (
    code        VARCHAR(10)  NOT NULL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    market      VARCHAR(10)  NOT NULL,      -- TWSE（上市）/ TPEX（上櫃）/ MANUAL（手動報價）
    created_at  DATETIME     NOT NULL
);

-- 買賣紀錄（平均成本法計算持股與損益）
CREATE TABLE stock_trade (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(10)   NOT NULL,
    trade_date  DATE          NOT NULL,
    type        VARCHAR(10)   NOT NULL,     -- BUY / SELL
    shares      INT           NOT NULL,     -- 股數（1 張 = 1000 股）
    price       DECIMAL(12,2) NOT NULL,
    fee         DECIMAL(12,2) NOT NULL,     -- 手續費
    tax         DECIMAL(12,2) NOT NULL,     -- 證交稅（賣出才有）
    account_id  BIGINT,                     -- 交割帳戶：買進扣款、賣出入帳
    note        VARCHAR(200),
    created_at  DATETIME      NOT NULL,
    CONSTRAINT fk_trade_stock   FOREIGN KEY (code)       REFERENCES stock (code),
    CONSTRAINT fk_trade_account FOREIGN KEY (account_id) REFERENCES account (id)
);
CREATE INDEX idx_trade_code_date ON stock_trade (code, trade_date);

-- 現金股利（同時記一筆「股利」收入到記帳）
CREATE TABLE stock_dividend (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(10)   NOT NULL,
    pay_date         DATE          NOT NULL,
    amount           DECIMAL(15,2) NOT NULL,
    ledger_entry_id  BIGINT,
    note             VARCHAR(200),
    created_at       DATETIME      NOT NULL,
    CONSTRAINT fk_dividend_stock FOREIGN KEY (code) REFERENCES stock (code)
);

-- 每日收盤價（自動抓或手動輸入）
CREATE TABLE stock_price (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(10)   NOT NULL,
    price_date  DATE          NOT NULL,
    close       DECIMAL(12,2) NOT NULL,
    source      VARCHAR(10)   NOT NULL,     -- AUTO / MANUAL
    fetched_at  DATETIME      NOT NULL,
    CONSTRAINT uk_price_code_date UNIQUE (code, price_date)
);

-- 券商手續費設定：折扣（0.6 = 6 折）、整股最低手續費（零股最低 1 元）
ALTER TABLE profile ADD COLUMN fee_discount DECIMAL(4,2) NOT NULL DEFAULT 0.60;
ALTER TABLE profile ADD COLUMN min_fee INT NOT NULL DEFAULT 20;
