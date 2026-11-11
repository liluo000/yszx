-- Flyway V2：系统配置默认值（需求第 7 章，键见 03.数据库设计.md 5.2）和内置的 B 站平台（需求 7.8）

INSERT INTO sys_config (config_key, config_value, description) VALUES
('ai.interview_daily_limit', '2', '每人每日 AI 面试次数'),
('ai.scoring_daily_limit', '20', '每人每日简答题 AI 评分次数'),
('learning.mastery_threshold', '80', '已掌握达标线（百分比）'),
('learning.short_answer_pass_score', '60', '简答题及格线（分）'),
('practice.random_question_count', '10', '随机抽题数量'),
('practice.random_min_question_count', '5', '随机抽题最低题量'),
('exam.paper_rule', '{"true_false":{"count":5,"score":3},"single_choice":{"count":8,"score":5},"multiple_choice":{"count":5,"score":5},"short_answer":{"count":2,"score":10}}', '模拟考试组卷规则'),
('exam.duration_minutes', '30', '模拟考试时长（分钟）'),
('exam.min_question_count', '10', '模拟考试最低题量'),
('interview.main_question_count', '5', '面试主问题数量'),
('interview.max_followup_count', '2', '每题最多追问次数'),
('interview.duration_minutes', '30', '面试时长上限（分钟）'),
('interview.min_question_count', '3', '面试最低题量'),
('interview.voice_end_pause_seconds', '3', '语音说完判定停顿（秒）'),
('interview.voice_concurrency_limit', '20', '语音面试同时进行上限（场）'),
('interview.grade_thresholds', '{"excellent":80,"good":60}', '面试等级分数线'),
('help.contact', '{"wechat_qrcode_url":null,"email":null,"phone":null}', '客服联系方式');

INSERT INTO video_platform (name, status) VALUES ('B 站', 'enabled');
INSERT INTO video_platform_domain (video_platform_id, domain)
SELECT id, 'bilibili.com' FROM video_platform WHERE name = 'B 站';
INSERT INTO video_platform_domain (video_platform_id, domain)
SELECT id, 'b23.tv' FROM video_platform WHERE name = 'B 站';
