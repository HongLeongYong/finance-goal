-- 第二期：目標試算
CREATE TABLE goal (
    id                       BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                     VARCHAR(50)   NOT NULL,
    target_type              VARCHAR(20)   NOT NULL,   -- AMOUNT（存到金額）/ PASSIVE_INCOME（每月被動收入）
    target_amount            DECIMAL(15,2),            -- AMOUNT 用
    monthly_passive_income   DECIMAL(15,2),            -- PASSIVE_INCOME 用
    withdrawal_rate          DECIMAL(5,2)  NOT NULL DEFAULT 4,  -- 安全提領率（%）
    mode                     VARCHAR(20)   NOT NULL,   -- PROJECT / TIME_TO_TARGET / PLAN
    years                    INT,                      -- PROJECT、PLAN 用

    -- 試算假設：null = 用系統自動帶入的值
    principal_override       DECIMAL(15,2),
    monthly_saving_override  DECIMAL(15,2),
    annual_return_override   DECIMAL(6,3),             -- %
    inflation_rate           DECIMAL(5,2),             -- %；null = 不考慮通膨

    created_at               DATETIME      NOT NULL
);
