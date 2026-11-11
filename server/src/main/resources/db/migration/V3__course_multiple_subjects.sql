-- Flyway V3：一门课程可以属于多个学科（需求 3.2、6.2 变更）
-- 新增课程与学科的关联表，把原 course.subject_id 迁移过去后删除该字段。

CREATE TABLE course_subject (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  course_id   BIGINT UNSIGNED NOT NULL COMMENT '课程',
  subject_id  BIGINT UNSIGNED NOT NULL COMMENT '学科',
  sort_no     INT          NOT NULL COMMENT '课程内学科的展示顺序，越小越靠前',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_subject (course_id, subject_id),
  KEY idx_course_subject_subject (subject_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程所属的学科，一门课程至少属于一个学科';

INSERT INTO course_subject (course_id, subject_id, sort_no)
SELECT id, subject_id, 1 FROM course;

ALTER TABLE course
  DROP INDEX idx_course_subject_status,
  DROP COLUMN subject_id;
