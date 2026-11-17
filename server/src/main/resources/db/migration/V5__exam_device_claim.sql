-- Flyway V5：模拟考试设备接管凭证（S-EXM-04/05）
-- 新设备调用 device-claims 后覆盖此值，旧凭证保存答案时返回 DEVICE_TAKEN_OVER。
ALTER TABLE exam_session
  ADD COLUMN device_claim VARCHAR(64) NULL COMMENT '当前持有考试的设备凭证' AFTER graded_at;
