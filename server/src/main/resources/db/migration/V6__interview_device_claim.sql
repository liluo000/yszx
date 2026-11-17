-- Flyway V6：文字面试设备接管凭证（S-IVW-05）
-- 新设备调用 device-claims 后覆盖此值，旧凭证提交回答时返回 DEVICE_TAKEN_OVER。
ALTER TABLE interview_session
  ADD COLUMN device_claim VARCHAR(64) NULL COMMENT '当前持有面试的设备凭证' AFTER deadline_at;
