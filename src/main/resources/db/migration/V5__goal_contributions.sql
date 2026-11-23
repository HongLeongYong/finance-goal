-- 目標的本金來源：AUTO（淨資產）/ MANUAL（手動輸入）/ CONTRIBUTIONS（累積式：存入紀錄加總）
ALTER TABLE goal ADD COLUMN principal_mode VARCHAR(15) NOT NULL DEFAULT 'AUTO';
UPDATE goal SET principal_mode = 'MANUAL' WHERE principal_override IS NOT NULL;

-- 目標的存入 / 提出紀錄（做法 A：只是「標記」這筆錢的用途，不動記帳和帳戶餘額）
-- amount 正數 = 存入，負數 = 提出
CREATE TABLE goal_contribution (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    goal_id            BIGINT        NOT NULL,
    contribution_date  DATE          NOT NULL,
    amount             DECIMAL(15,2) NOT NULL,
    note               VARCHAR(200),
    created_at         DATETIME      NOT NULL,
    CONSTRAINT fk_contribution_goal FOREIGN KEY (goal_id) REFERENCES goal (id) ON DELETE CASCADE
);
CREATE INDEX idx_contribution_goal_date ON goal_contribution (goal_id, contribution_date);
