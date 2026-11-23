-- 上下班時間與午休：每天工時改由「下班 − 上班 − 午休」自動計算
-- 既有資料是 09:00 上班、8 小時，所以下班預設 17:00、不設午休，工時維持不變
ALTER TABLE profile ADD COLUMN work_end_time TIME NOT NULL DEFAULT '17:00:00';
ALTER TABLE profile ADD COLUMN break_start_time TIME NULL;
ALTER TABLE profile ADD COLUMN break_end_time TIME NULL;
-- 有午休時工時會出現 7.25 這種兩位小數
ALTER TABLE profile MODIFY COLUMN work_hours_per_day DECIMAL(5,2) NOT NULL DEFAULT 8;
