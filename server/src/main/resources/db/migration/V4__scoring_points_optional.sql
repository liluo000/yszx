-- Flyway V4：简答题和面试题的评分要点改为选填（需求 6.4、6.5 变更）
-- 没有评分要点时，AI 评分只对照参考答案。

ALTER TABLE practice_question
  DROP CHECK chk_practice_question_short,
  ADD CONSTRAINT chk_practice_question_short CHECK (question_type <> 'short_answer' OR reference_answer IS NOT NULL);

ALTER TABLE interview_question
  MODIFY scoring_points TEXT NULL COMMENT '评分要点，选填；为空时 AI 评分只对照参考答案';
