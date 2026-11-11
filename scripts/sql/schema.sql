-- 云枢智学 建表脚本
-- 数据库：MySQL 8（8.0.16 及以上，CHECK 约束生效）
-- 设计说明：docs/design/03.数据库设计.md
-- 约定：
--   1. 不创建物理外键，表之间的关联由应用层维护（docs/rules/database-rules.md）。
--   2. 所有 DATETIME 字段按 UTC 存储；*_date 字段是北京时间的自然日。
--   3. 执行前请先选择目标数据库。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================
-- 一、账号
-- ============================================================

CREATE TABLE student (
  id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  phone                      CHAR(11)     NOT NULL COMMENT '手机号，明文存储，显示时隐藏中间四位',
  phone_last4                CHAR(4)      GENERATED ALWAYS AS (RIGHT(phone, 4)) STORED COMMENT '手机号后四位，用于后台搜索',
  password_hash              VARCHAR(100) NULL COMMENT '登录密码哈希；未设置过密码时为空',
  nickname                   VARCHAR(30)  NOT NULL COMMENT '昵称；注册时为学员加手机号后四位',
  avatar_url                 VARCHAR(500) NULL COMMENT '头像地址；未上传时为空，显示默认头像',
  education                  VARCHAR(30)  NULL COMMENT '学历：high_school_or_below、junior_college、bachelor、master_or_above；选填',
  job_intent_learning_route_id BIGINT UNSIGNED NULL COMMENT '求职意向对应的学习路线；选填',
  job_intent_name            VARCHAR(100) NULL COMMENT '求职意向名称快照，路线下架或删除后仍可显示',
  work_years                 VARCHAR(30)  NULL COMMENT '工作年限：student_or_graduate、under_1_year、1_to_3_years、3_to_5_years、over_5_years；选填',
  status                     VARCHAR(20)  NOT NULL COMMENT '账号状态：normal 正常、banned 封禁、cancelling 注销中',
  ban_reason                 VARCHAR(200) NULL COMMENT '封禁原因',
  banned_at                  DATETIME(3)  NULL COMMENT '封禁时间',
  cancellation_requested_at  DATETIME(3)  NULL COMMENT '申请注销时间',
  cancellation_effective_at  DATETIME(3)  NULL COMMENT '注销生效时间，即冷静期结束时间',
  last_active_at             DATETIME(3)  NULL COMMENT '最近活跃时间，最近一次有学习行为的时间',
  created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
  updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_phone (phone),
  KEY idx_student_phone_last4 (phone_last4),
  KEY idx_student_status_effective (status, cancellation_effective_at),
  KEY idx_student_created (created_at),
  KEY idx_student_last_active (last_active_at),
  KEY idx_student_job_intent (job_intent_learning_route_id),
  CONSTRAINT chk_student_phone CHECK (REGEXP_LIKE(phone, '^1[0-9]{10}$')),
  CONSTRAINT chk_student_status CHECK (status IN ('normal', 'banned', 'cancelling')),
  CONSTRAINT chk_student_education CHECK (education IS NULL OR education IN ('high_school_or_below', 'junior_college', 'bachelor', 'master_or_above')),
  CONSTRAINT chk_student_work_years CHECK (work_years IS NULL OR work_years IN ('student_or_graduate', 'under_1_year', '1_to_3_years', '3_to_5_years', 'over_5_years')),
  CONSTRAINT chk_student_banned CHECK (status <> 'banned' OR ban_reason IS NOT NULL),
  CONSTRAINT chk_student_cancelling CHECK (status <> 'cancelling' OR cancellation_effective_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员账号';

CREATE TABLE student_agreement_consent (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id      BIGINT UNSIGNED NOT NULL COMMENT '学员',
  agreement_type  VARCHAR(20)  NOT NULL COMMENT '协议类型：user_agreement 用户协议、privacy_policy 隐私政策',
  version_no      INT UNSIGNED NOT NULL COMMENT '同意的协议版本号',
  agreed_at       DATETIME(3)  NOT NULL COMMENT '同意时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consent_student_version (student_id, agreement_type, version_no),
  CONSTRAINT chk_consent_type CHECK (agreement_type IN ('user_agreement', 'privacy_policy'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员同意协议的记录';

CREATE TABLE sys_user (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  account               VARCHAR(50)  NOT NULL COMMENT '登录账号',
  name                  VARCHAR(50)  NOT NULL COMMENT '姓名',
  phone                 CHAR(11)     NOT NULL COMMENT '手机号',
  password_hash         VARCHAR(100) NOT NULL COMMENT '登录密码哈希',
  role                  VARCHAR(20)  NOT NULL COMMENT '角色：super_admin 超级管理员、content_operator 内容运营、student_operator 学员运营',
  status                VARCHAR(20)  NOT NULL COMMENT '账号状态：enabled 启用、disabled 停用',
  must_change_password  TINYINT(1)   NOT NULL COMMENT '是否必须先修改密码才能使用后台：1 是、0 否',
  password_changed_at   DATETIME(3)  NULL COMMENT '最近一次修改密码时间',
  last_login_at         DATETIME(3)  NULL COMMENT '最近登录时间',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_account (account),
  KEY idx_sys_user_role_status (role, status),
  CONSTRAINT chk_sys_user_role CHECK (role IN ('super_admin', 'content_operator', 'student_operator')),
  CONSTRAINT chk_sys_user_status CHECK (status IN ('enabled', 'disabled')),
  CONSTRAINT chk_sys_user_must_change CHECK (must_change_password IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台系统用户';

-- ============================================================
-- 二、系统配置与审计
-- ============================================================

CREATE TABLE sys_config (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key              VARCHAR(64)  NOT NULL COMMENT '配置项键，取值见设计文档',
  config_value            JSON         NOT NULL COMMENT '配置项的值，JSON 格式',
  description             VARCHAR(200) NOT NULL COMMENT '配置项说明',
  updated_by_sys_user_id  BIGINT UNSIGNED NULL COMMENT '最近修改人；初始化数据为空',
  created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置项';

CREATE TABLE agreement_version (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  agreement_type            VARCHAR(20) NOT NULL COMMENT '协议类型：user_agreement 用户协议、privacy_policy 隐私政策',
  version_no                INT UNSIGNED NOT NULL COMMENT '版本号，同一类型内递增',
  content                   MEDIUMTEXT  NOT NULL COMMENT '协议正文',
  published_by_sys_user_id  BIGINT UNSIGNED NOT NULL COMMENT '发布人',
  published_at              DATETIME(3) NOT NULL COMMENT '发布时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agreement_type_version (agreement_type, version_no),
  CONSTRAINT chk_agreement_type CHECK (agreement_type IN ('user_agreement', 'privacy_policy'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协议版本';

CREATE TABLE sensitive_word (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  word                    VARCHAR(100) NOT NULL COMMENT '敏感词',
  created_by_sys_user_id  BIGINT UNSIGNED NOT NULL COMMENT '添加人',
  created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sensitive_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词库';

CREATE TABLE video_platform (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50) NOT NULL COMMENT '平台名称',
  status      VARCHAR(20) NOT NULL COMMENT '状态：enabled 启用、disabled 停用',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_platform_name (name),
  CONSTRAINT chk_video_platform_status CHECK (status IN ('enabled', 'disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='允许使用的视频平台';

CREATE TABLE video_platform_domain (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  video_platform_id  BIGINT UNSIGNED NOT NULL COMMENT '视频平台',
  domain             VARCHAR(253) NOT NULL COMMENT '允许的域名',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_platform_domain (domain),
  KEY idx_domain_platform (video_platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频平台允许的域名';

CREATE TABLE faq (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  question    VARCHAR(200) NOT NULL COMMENT '问题',
  answer      TEXT         NOT NULL COMMENT '回答',
  sort_no     INT          NOT NULL COMMENT '排序号，越小越靠前',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_faq_sort (sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帮助中心常见问题';

CREATE TABLE operation_log (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  sys_user_id    BIGINT UNSIGNED NOT NULL COMMENT '操作人',
  sys_user_name  VARCHAR(50)  NOT NULL COMMENT '操作人姓名快照',
  role           VARCHAR(20)  NOT NULL COMMENT '操作时的角色：super_admin、content_operator、student_operator',
  module         VARCHAR(30)  NOT NULL COMMENT '操作模块',
  action         VARCHAR(30)  NOT NULL COMMENT '操作类型',
  target_type    VARCHAR(30)  NULL COMMENT '操作对象类型；后台登录等操作为空',
  target_id      BIGINT UNSIGNED NULL COMMENT '操作对象 ID',
  target_label   VARCHAR(200) NULL COMMENT '操作对象描述；学员类对象不得写入昵称和手机号',
  ip_address     VARCHAR(45)  NOT NULL COMMENT 'IP 地址，兼容 IPv6',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_operation_user_time (sys_user_id, created_at),
  KEY idx_operation_module_time (module, created_at),
  KEY idx_operation_time (created_at),
  CONSTRAINT chk_operation_role CHECK (role IN ('super_admin', 'content_operator', 'student_operator'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作日志，保留 1 年';

CREATE TABLE student_export_log (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  sys_user_id        BIGINT UNSIGNED NOT NULL COMMENT '操作人',
  sys_user_name      VARCHAR(50)  NOT NULL COMMENT '操作人姓名快照',
  filter_conditions  JSON         NOT NULL COMMENT '导出时的筛选条件',
  exported_count     INT UNSIGNED NOT NULL COMMENT '导出条数',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '导出时间',
  PRIMARY KEY (id),
  KEY idx_export_user_time (sys_user_id, created_at),
  KEY idx_export_time (created_at),
  CONSTRAINT chk_export_count CHECK (exported_count <= 5000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员名单导出记录，保留 1 年';

CREATE TABLE sensitive_word_hit_log (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id            BIGINT UNSIGNED NOT NULL COMMENT '学员；注销后关联不到时显示已注销用户',
  scene                 VARCHAR(30)  NOT NULL COMMENT '场景：text_interview 文字面试、voice_interview 语音面试、short_answer_scoring 简答题评分',
  source                VARCHAR(10)  NOT NULL COMMENT '命中来源：student 学员、ai AI 输出',
  word                  VARCHAR(100) NOT NULL COMMENT '命中的敏感词',
  sentence              VARCHAR(500) NOT NULL COMMENT '命中词所在的原句，超长时截断',
  interview_session_id  BIGINT UNSIGNED NULL COMMENT '所属面试场次；简答题评分场景为空',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '命中时间',
  PRIMARY KEY (id),
  KEY idx_hit_time (created_at),
  KEY idx_hit_student_time (student_id, created_at),
  CONSTRAINT chk_hit_scene CHECK (scene IN ('text_interview', 'voice_interview', 'short_answer_scoring')),
  CONSTRAINT chk_hit_source CHECK (source IN ('student', 'ai'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词命中日志，保留 1 年';

-- ============================================================
-- 三、内容与题库
-- ============================================================

CREATE TABLE subject (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)  NOT NULL COMMENT '学科名称，唯一',
  intro       VARCHAR(500) NULL COMMENT '简介',
  sort_no     INT          NOT NULL COMMENT '排序号，越小越靠前',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_name (name),
  KEY idx_subject_sort (sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科';

CREATE TABLE course (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  title              VARCHAR(100)  NOT NULL COMMENT '课程名称',
  video_platform_id  BIGINT UNSIGNED NOT NULL COMMENT '视频平台',
  video_url          VARCHAR(1000) NOT NULL COMMENT '视频链接，域名须属于所选平台',
  intro              VARCHAR(1000) NULL COMMENT '简介',
  duration_minutes   INT UNSIGNED  NULL COMMENT '时长，单位分钟',
  sort_no            INT           NOT NULL COMMENT '排序号，越小越靠前',
  status             VARCHAR(20)   NOT NULL COMMENT '状态：on_shelf 上架、off_shelf 下架',
  created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_course_status_sort (status, sort_no),
  KEY idx_course_platform (video_platform_id),
  CONSTRAINT chk_course_status CHECK (status IN ('on_shelf', 'off_shelf'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程';

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

CREATE TABLE learning_route (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100)  NOT NULL COMMENT '路线名称',
  intro       VARCHAR(1000) NULL COMMENT '简介',
  sort_no     INT           NOT NULL COMMENT '排序号，越小越靠前',
  status      VARCHAR(20)   NOT NULL COMMENT '状态：on_shelf 上架、off_shelf 下架',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_route_status_sort (status, sort_no),
  CONSTRAINT chk_route_status CHECK (status IN ('on_shelf', 'off_shelf'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习路线';

CREATE TABLE route_stage (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  learning_route_id  BIGINT UNSIGNED NOT NULL COMMENT '所属路线',
  name               VARCHAR(100)  NOT NULL COMMENT '阶段名称',
  intro              VARCHAR(1000) NULL COMMENT '简介',
  sort_no            INT           NOT NULL COMMENT '排序号，越小越靠前',
  created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_stage_route_sort (learning_route_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='路线阶段';

CREATE TABLE stage_course (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  learning_route_id  BIGINT UNSIGNED NOT NULL COMMENT '所属路线；冗余字段，用于保证同一路线内课程唯一',
  route_stage_id     BIGINT UNSIGNED NOT NULL COMMENT '所属阶段',
  course_id          BIGINT UNSIGNED NOT NULL COMMENT '引用的课程',
  sort_no            INT             NOT NULL COMMENT '阶段内排序号，越小越靠前',
  created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stage_course_route_course (learning_route_id, course_id),
  KEY idx_stage_course_stage_sort (route_stage_id, sort_no),
  KEY idx_stage_course_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶段引用的课程';

CREATE TABLE practice_question (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  course_id         BIGINT UNSIGNED NOT NULL COMMENT '所属课程',
  question_type     VARCHAR(20) NOT NULL COMMENT '题型：true_false 判断、single_choice 单选、multiple_choice 多选、short_answer 简答',
  stem              TEXT        NOT NULL COMMENT '题干',
  options           JSON        NULL COMMENT '选项列表，单选和多选使用，2 到 8 个',
  correct_answer    JSON        NULL COMMENT '正确答案，判断、单选、多选使用',
  reference_answer  TEXT        NULL COMMENT '参考答案，简答题使用',
  scoring_points    TEXT        NULL COMMENT '评分要点，简答题选填；为空时 AI 评分只对照参考答案',
  analysis          TEXT        NULL COMMENT '解析',
  sort_no           INT         NOT NULL COMMENT '排序号，用于顺序练习',
  status            VARCHAR(20) NOT NULL COMMENT '状态：on_shelf 上架、off_shelf 下架',
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_practice_question_course (course_id, status, sort_no),
  KEY idx_practice_question_status_type (status, question_type),
  CONSTRAINT chk_practice_question_type CHECK (question_type IN ('true_false', 'single_choice', 'multiple_choice', 'short_answer')),
  CONSTRAINT chk_practice_question_status CHECK (status IN ('on_shelf', 'off_shelf')),
  CONSTRAINT chk_practice_question_objective CHECK (question_type = 'short_answer' OR correct_answer IS NOT NULL),
  CONSTRAINT chk_practice_question_short CHECK (question_type <> 'short_answer' OR reference_answer IS NOT NULL),
  CONSTRAINT chk_practice_question_options CHECK (question_type NOT IN ('single_choice', 'multiple_choice') OR JSON_LENGTH(options) BETWEEN 2 AND 8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷题题目';

CREATE TABLE interview_question (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  course_id           BIGINT UNSIGNED NOT NULL COMMENT '所属课程',
  stem                TEXT        NOT NULL COMMENT '题干',
  reference_answer    TEXT        NOT NULL COMMENT '参考答案',
  scoring_points      TEXT        NULL COMMENT '评分要点，选填；为空时 AI 评分只对照参考答案',
  followup_direction  TEXT        NULL COMMENT '追问方向',
  status              VARCHAR(20) NOT NULL COMMENT '状态：on_shelf 上架、off_shelf 下架',
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_interview_question_course (course_id, status),
  KEY idx_interview_question_status (status),
  CONSTRAINT chk_interview_question_status CHECK (status IN ('on_shelf', 'off_shelf'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试题目';

-- ============================================================
-- 四、学习进度与记录
-- ============================================================

CREATE TABLE student_course_state (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id         BIGINT UNSIGNED NOT NULL COMMENT '学员',
  course_id          BIGINT UNSIGNED NOT NULL COMMENT '课程；学习状态按课程全局共用',
  first_opened_at    DATETIME(3) NULL COMMENT '首次打开时间',
  last_opened_at     DATETIME(3) NULL COMMENT '最近打开时间',
  first_answered_at  DATETIME(3) NULL COMMENT '首次作答该课程题目的时间',
  is_learned         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已学：1 是、0 否；默认未学',
  learned_at         DATETIME(3) NULL COMMENT '标记已学时间',
  is_mastered        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已掌握：1 是、0 否；默认未掌握，达成后不取消',
  mastered_at        DATETIME(3) NULL COMMENT '达成已掌握时间',
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_state_student_course (student_id, course_id),
  KEY idx_state_course (course_id),
  CONSTRAINT chk_state_flags CHECK (is_learned IN (0, 1) AND is_mastered IN (0, 1)),
  CONSTRAINT chk_state_mastered_learned CHECK (is_mastered = 0 OR is_learned = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员的课程学习状态';

CREATE TABLE course_open_log (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id         BIGINT UNSIGNED NOT NULL COMMENT '学员',
  course_id          BIGINT UNSIGNED NOT NULL COMMENT '课程',
  learning_route_id  BIGINT UNSIGNED NULL COMMENT '打开时所在路线；从搜索等入口进入时可为空',
  route_stage_id     BIGINT UNSIGNED NULL COMMENT '打开时所在阶段；从搜索等入口进入时可为空',
  opened_at          DATETIME(3) NOT NULL COMMENT '打开时间',
  PRIMARY KEY (id),
  KEY idx_open_student_time (student_id, opened_at),
  KEY idx_open_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程打开记录，每次打开一条';

CREATE TABLE student_learning_position (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id         BIGINT UNSIGNED NOT NULL COMMENT '学员，每人一行',
  learning_route_id  BIGINT UNSIGNED NOT NULL COMMENT '上次学习的路线',
  route_stage_id     BIGINT UNSIGNED NOT NULL COMMENT '上次学习的阶段',
  course_id          BIGINT UNSIGNED NOT NULL COMMENT '上次学习的课程',
  last_learned_at    DATETIME(3) NOT NULL COMMENT '上次学习时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_position_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员的继续学习位置';

CREATE TABLE student_daily_activity (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id     BIGINT UNSIGNED NOT NULL COMMENT '学员',
  activity_date  DATE NOT NULL COMMENT '有学习行为的日期，北京时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_student_date (student_id, activity_date),
  KEY idx_activity_date (activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员每日活跃记录，用于活跃和留存统计';

-- ============================================================
-- 五、刷题、考试与错题
-- ============================================================

CREATE TABLE practice_session (
  id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id                   BIGINT UNSIGNED NOT NULL COMMENT '学员',
  practice_mode                VARCHAR(20) NOT NULL COMMENT '练习模式：sequential 顺序练习、random 随机抽题、wrong_redo 错题重做',
  course_id                    BIGINT UNSIGNED NULL COMMENT '课程，顺序练习使用',
  learning_route_id            BIGINT UNSIGNED NULL COMMENT '路线，随机抽题使用',
  route_stage_id               BIGINT UNSIGNED NULL COMMENT '阶段，随机抽题使用',
  route_name                   VARCHAR(100) NULL COMMENT '路线名称快照，用于历史记录',
  stage_name                   VARCHAR(100) NULL COMMENT '阶段名称快照，用于历史记录',
  course_title                 VARCHAR(100) NULL COMMENT '课程名称快照，用于历史记录',
  status                       VARCHAR(20) NOT NULL COMMENT '状态：in_progress 进行中、completed 已完成、unfinished 未完成',
  planned_question_ids         JSON        NULL COMMENT '本次抽取的题目 ID 列表，随机抽题和错题重做使用',
  question_count               INT UNSIGNED NOT NULL COMMENT '本次题目总数',
  answered_count               INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已作答题数，开始时为 0',
  correct_count                INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '答对题数，开始时为 0',
  started_at                   DATETIME(3) NOT NULL COMMENT '开始时间',
  finished_at                  DATETIME(3) NULL COMMENT '结束时间',
  active_sequential_course_id  BIGINT UNSIGNED GENERATED ALWAYS AS (IF(practice_mode = 'sequential' AND status = 'in_progress', course_id, NULL)) VIRTUAL COMMENT '进行中的顺序练习课程，用于保证同一课程只有一轮进行中',
  created_at                   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at                   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_practice_active_sequential (student_id, active_sequential_course_id),
  KEY idx_practice_student_mode_time (student_id, practice_mode, started_at),
  KEY idx_practice_course (course_id),
  CONSTRAINT chk_practice_session_mode CHECK (practice_mode IN ('sequential', 'random', 'wrong_redo')),
  CONSTRAINT chk_practice_session_status CHECK (status IN ('in_progress', 'completed', 'unfinished')),
  CONSTRAINT chk_practice_session_sequential CHECK (practice_mode <> 'sequential' OR course_id IS NOT NULL),
  CONSTRAINT chk_practice_session_random CHECK (practice_mode <> 'random' OR (learning_route_id IS NOT NULL AND route_stage_id IS NOT NULL AND planned_question_ids IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习场次，顺序练习每轮一条';

CREATE TABLE practice_answer (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  practice_session_id   BIGINT UNSIGNED NOT NULL COMMENT '所属练习场次',
  student_id            BIGINT UNSIGNED NOT NULL COMMENT '学员',
  practice_question_id  BIGINT UNSIGNED NOT NULL COMMENT '题目',
  course_id             BIGINT UNSIGNED NOT NULL COMMENT '题目所属课程',
  practice_mode         VARCHAR(20) NOT NULL COMMENT '练习模式：sequential、random、wrong_redo，错题重做不进入学员刷题记录',
  question_type         VARCHAR(20) NOT NULL COMMENT '题型：true_false、single_choice、multiple_choice、short_answer',
  question_snapshot     JSON        NOT NULL COMMENT '作答时的题目快照',
  student_answer        JSON        NOT NULL COMMENT '学员答案',
  judge_method          VARCHAR(10) NOT NULL COMMENT '判定方式：auto 自动判分、ai AI 评分、self 学员自评',
  is_correct            TINYINT(1)  NOT NULL COMMENT '是否答对：1 是、0 否',
  ai_score              TINYINT UNSIGNED NULL COMMENT 'AI 评分，百分制，仅 AI 评分时有值',
  ai_comment            TEXT        NULL COMMENT 'AI 点评，仅 AI 评分时有值',
  answered_at           DATETIME(3) NOT NULL COMMENT '作答时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_answer_session_question (practice_session_id, practice_question_id),
  KEY idx_answer_student_time (student_id, answered_at),
  KEY idx_answer_question (practice_question_id),
  KEY idx_answer_time (answered_at),
  CONSTRAINT chk_answer_mode CHECK (practice_mode IN ('sequential', 'random', 'wrong_redo')),
  CONSTRAINT chk_answer_type CHECK (question_type IN ('true_false', 'single_choice', 'multiple_choice', 'short_answer')),
  CONSTRAINT chk_answer_method CHECK (judge_method IN ('auto', 'ai', 'self')),
  CONSTRAINT chk_answer_correct CHECK (is_correct IN (0, 1)),
  CONSTRAINT chk_answer_score CHECK (ai_score IS NULL OR ai_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习作答记录';

CREATE TABLE sequential_question_result (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id            BIGINT UNSIGNED NOT NULL COMMENT '学员',
  practice_question_id  BIGINT UNSIGNED NOT NULL COMMENT '题目',
  course_id             BIGINT UNSIGNED NOT NULL COMMENT '题目所属课程',
  is_correct            TINYINT(1)  NOT NULL COMMENT '最近一次顺序练习是否答对：1 是、0 否',
  answered_at           DATETIME(3) NOT NULL COMMENT '最近一次作答时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_result_student_question (student_id, practice_question_id),
  KEY idx_result_student_course (student_id, course_id),
  CONSTRAINT chk_result_correct CHECK (is_correct IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='顺序练习每题最近一次结果，用于判定已掌握';

CREATE TABLE exam_session (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id          BIGINT UNSIGNED NOT NULL COMMENT '学员',
  learning_route_id   BIGINT UNSIGNED NOT NULL COMMENT '路线',
  route_stage_id      BIGINT UNSIGNED NOT NULL COMMENT '阶段',
  route_name          VARCHAR(100) NOT NULL COMMENT '路线名称快照',
  stage_name          VARCHAR(100) NOT NULL COMMENT '阶段名称快照',
  status              VARCHAR(20)  NOT NULL COMMENT '状态：in_progress 作答中、submitted 已交卷待判分、scoring_pending 简答题评分中、graded 已出成绩、abandoned 已放弃',
  paper_rule          JSON         NOT NULL COMMENT '开考时固定的组卷规则',
  duration_minutes    SMALLINT UNSIGNED NOT NULL COMMENT '开考时固定的考试时长，单位分钟',
  has_short_answer    TINYINT(1)   NOT NULL COMMENT '试卷是否含简答题：1 是、0 否',
  scoring_quota_status VARCHAR(10) NOT NULL COMMENT '评分次数状态：none 无需预留、reserved 已预留、consumed 已扣减、released 已释放',
  quota_date          DATE         NOT NULL COMMENT '次数归属日期，北京时间，即开考当日',
  full_score          DECIMAL(6,2) NOT NULL COMMENT '试卷实际满分，用于换算百分制',
  score               DECIMAL(4,1) NULL COMMENT '总分，百分制，保留 1 位小数；出成绩后有值',
  started_at          DATETIME(3)  NOT NULL COMMENT '开考时间',
  deadline_at         DATETIME(3)  NOT NULL COMMENT '截止时间',
  submitted_at        DATETIME(3)  NULL COMMENT '交卷时间',
  graded_at           DATETIME(3)  NULL COMMENT '出成绩时间',
  active_student_id   BIGINT UNSIGNED GENERATED ALWAYS AS (IF(status = 'in_progress', student_id, NULL)) VIRTUAL COMMENT '作答中的学员，用于保证每人最多一场作答中的考试',
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_active_student (active_student_id),
  KEY idx_exam_student_time (student_id, started_at),
  KEY idx_exam_status_deadline (status, deadline_at),
  KEY idx_exam_route_stage (route_stage_id),
  CONSTRAINT chk_exam_status CHECK (status IN ('in_progress', 'submitted', 'scoring_pending', 'graded', 'abandoned')),
  CONSTRAINT chk_exam_quota_status CHECK (scoring_quota_status IN ('none', 'reserved', 'consumed', 'released')),
  CONSTRAINT chk_exam_short_answer CHECK (has_short_answer IN (0, 1)),
  CONSTRAINT chk_exam_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
  CONSTRAINT chk_exam_graded CHECK (status <> 'graded' OR score IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟考试场次';

CREATE TABLE exam_session_question (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  exam_session_id       BIGINT UNSIGNED NOT NULL COMMENT '所属考试场次',
  seq_no                INT UNSIGNED NOT NULL COMMENT '题号',
  practice_question_id  BIGINT UNSIGNED NOT NULL COMMENT '题目',
  course_id             BIGINT UNSIGNED NOT NULL COMMENT '题目所属课程',
  question_type         VARCHAR(20)  NOT NULL COMMENT '题型：true_false、single_choice、multiple_choice、short_answer',
  question_snapshot     JSON         NOT NULL COMMENT '开考时固定的题目快照',
  question_score        DECIMAL(6,2) NOT NULL COMMENT '本题分值',
  student_answer        JSON         NULL COMMENT '学员答案；未作答时为空',
  judge_status          VARCHAR(10)  NOT NULL COMMENT '判分状态：pending 待判分、judged 已判分、failed 评分失败',
  is_correct            TINYINT(1)   NULL COMMENT '是否答对：1 是、0 否；判分后有值',
  earned_score          DECIMAL(6,2) NULL COMMENT '本题得分；判分后有值',
  ai_score              TINYINT UNSIGNED NULL COMMENT 'AI 评分，百分制，简答题使用',
  ai_comment            TEXT         NULL COMMENT 'AI 点评，简答题使用',
  answered_at           DATETIME(3)  NULL COMMENT '最近一次保存答案的时间',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_question_seq (exam_session_id, seq_no),
  KEY idx_exam_question_question (practice_question_id),
  CONSTRAINT chk_exam_question_type CHECK (question_type IN ('true_false', 'single_choice', 'multiple_choice', 'short_answer')),
  CONSTRAINT chk_exam_question_judge CHECK (judge_status IN ('pending', 'judged', 'failed')),
  CONSTRAINT chk_exam_question_correct CHECK (is_correct IS NULL OR is_correct IN (0, 1)),
  CONSTRAINT chk_exam_question_ai_score CHECK (ai_score IS NULL OR ai_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟考试试题与作答';

CREATE TABLE wrong_question (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id            BIGINT UNSIGNED NOT NULL COMMENT '学员',
  practice_question_id  BIGINT UNSIGNED NOT NULL COMMENT '题目',
  course_id             BIGINT UNSIGNED NOT NULL COMMENT '题目所属课程',
  question_type         VARCHAR(20)  NOT NULL COMMENT '题型：true_false、single_choice、multiple_choice、short_answer',
  wrong_count           INT UNSIGNED NOT NULL COMMENT '累计答错次数；移出后再次答错从 1 重新计算',
  last_student_answer   JSON         NOT NULL COMMENT '学员上次的答案，保留作答时的原样',
  last_wrong_at         DATETIME(3)  NOT NULL COMMENT '最近一次答错时间',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收录时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wrong_student_question (student_id, practice_question_id),
  KEY idx_wrong_student_course (student_id, course_id),
  KEY idx_wrong_question (practice_question_id),
  CONSTRAINT chk_wrong_type CHECK (question_type IN ('true_false', 'single_choice', 'multiple_choice', 'short_answer')),
  CONSTRAINT chk_wrong_count CHECK (wrong_count >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本';

-- ============================================================
-- 六、AI 面试
-- ============================================================

CREATE TABLE interview_session (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id           BIGINT UNSIGNED NOT NULL COMMENT '学员',
  learning_route_id    BIGINT UNSIGNED NOT NULL COMMENT '路线',
  route_name           VARCHAR(100) NOT NULL COMMENT '路线名称快照',
  interview_mode       VARCHAR(10)  NOT NULL COMMENT '面试模式：text 文字面试、voice 语音面试',
  scope_type           VARCHAR(20)  NOT NULL COMMENT '面试范围：whole_route 整条路线、selected_stages 所选阶段',
  status               VARCHAR(20)  NOT NULL COMMENT '状态：in_progress 进行中、ended 已结束',
  end_reason           VARCHAR(20)  NULL COMMENT '结束方式：completed 答完、student_ended 主动结束、timeout 超时、ai_failure AI 服务异常；结束后有值',
  config_snapshot      JSON         NOT NULL COMMENT '开始时固定的面试参数',
  main_question_count  TINYINT UNSIGNED NOT NULL COMMENT '本场主问题数量',
  answered_count       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已作答的主问题数量，开始时为 0',
  duration_minutes     SMALLINT UNSIGNED NOT NULL COMMENT '开始时固定的时长上限，单位分钟',
  quota_date           DATE         NOT NULL COMMENT '次数归属日期，北京时间，即开始当日',
  is_quota_refunded    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '次数是否已退还：1 是、0 否；默认未退还',
  started_at           DATETIME(3)  NOT NULL COMMENT '开始时间',
  deadline_at          DATETIME(3)  NOT NULL COMMENT '截止时间；AI 服务异常期间相应顺延',
  ended_at             DATETIME(3)  NULL COMMENT '结束时间',
  active_student_id    BIGINT UNSIGNED GENERATED ALWAYS AS (IF(status = 'in_progress', student_id, NULL)) VIRTUAL COMMENT '进行中的学员，用于保证每人最多一场进行中的面试',
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_interview_active_student (active_student_id),
  KEY idx_interview_student_time (student_id, started_at),
  KEY idx_interview_status_deadline (status, deadline_at),
  KEY idx_interview_route (learning_route_id),
  KEY idx_interview_started (started_at),
  CONSTRAINT chk_interview_mode CHECK (interview_mode IN ('text', 'voice')),
  CONSTRAINT chk_interview_scope CHECK (scope_type IN ('whole_route', 'selected_stages')),
  CONSTRAINT chk_interview_status CHECK (status IN ('in_progress', 'ended')),
  CONSTRAINT chk_interview_end_reason CHECK (end_reason IS NULL OR end_reason IN ('completed', 'student_ended', 'timeout', 'ai_failure')),
  CONSTRAINT chk_interview_ended CHECK (status <> 'ended' OR end_reason IS NOT NULL),
  CONSTRAINT chk_interview_refunded CHECK (is_quota_refunded IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 面试场次';

CREATE TABLE interview_session_stage (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_session_id  BIGINT UNSIGNED NOT NULL COMMENT '所属面试场次',
  route_stage_id        BIGINT UNSIGNED NOT NULL COMMENT '阶段',
  stage_name            VARCHAR(100) NOT NULL COMMENT '阶段名称快照',
  question_count        TINYINT UNSIGNED NOT NULL COMMENT '本阶段分配的主问题数量',
  score                 DECIMAL(4,1) NULL COMMENT '分阶段得分，保留 1 位小数；为空表示尚未评分或未测评',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_stage (interview_session_id, route_stage_id),
  KEY idx_session_stage_stage (route_stage_id),
  CONSTRAINT chk_session_stage_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试覆盖的阶段及分阶段得分';

CREATE TABLE interview_session_question (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_session_id   BIGINT UNSIGNED NOT NULL COMMENT '所属面试场次',
  seq_no                 TINYINT UNSIGNED NOT NULL COMMENT '主问题序号',
  interview_question_id  BIGINT UNSIGNED NOT NULL COMMENT '面试题目',
  course_id              BIGINT UNSIGNED NOT NULL COMMENT '题目所属课程，用于学习建议跳转',
  route_stage_id         BIGINT UNSIGNED NOT NULL COMMENT '抽题时所属阶段，用于分阶段得分',
  question_snapshot      JSON         NOT NULL COMMENT '开始时固定的题目快照',
  answer_status          VARCHAR(20)  NOT NULL COMMENT '作答状态：not_asked 未问到、answered 已作答、unanswered 未作答',
  followup_count         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已追问次数，开始时为 0',
  score                  TINYINT UNSIGNED NULL COMMENT '本题得分，百分制，主问题连同追问整体打分',
  ai_comment             TEXT         NULL COMMENT 'AI 点评',
  missing_points         TEXT         NULL COMMENT '缺失要点',
  created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_question_seq (interview_session_id, seq_no),
  KEY idx_session_question_question (interview_question_id),
  CONSTRAINT chk_session_question_status CHECK (answer_status IN ('not_asked', 'answered', 'unanswered')),
  CONSTRAINT chk_session_question_score CHECK (score IS NULL OR score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试抽取的主问题及逐题点评';

CREATE TABLE interview_message (
  id                             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_session_id           BIGINT UNSIGNED NOT NULL COMMENT '所属面试场次',
  interview_session_question_id  BIGINT UNSIGNED NULL COMMENT '所属主问题；开场白和结束语为空',
  seq_no                         INT UNSIGNED NOT NULL COMMENT '消息序号',
  speaker                        VARCHAR(10) NOT NULL COMMENT '说话方：ai 面试官、student 学员',
  message_type                   VARCHAR(20) NOT NULL COMMENT '消息类型：opening 开场白、main_question 主问题、followup 追问、answer 回答、transition 过渡语、closing 结束语',
  content                        TEXT        NOT NULL COMMENT '消息文字；语音面试为转写文字',
  created_at                     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_session_seq (interview_session_id, seq_no),
  KEY idx_message_question (interview_session_question_id),
  CONSTRAINT chk_message_speaker CHECK (speaker IN ('ai', 'student')),
  CONSTRAINT chk_message_type CHECK (message_type IN ('opening', 'main_question', 'followup', 'answer', 'transition', 'closing'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试对话消息';

CREATE TABLE interview_report (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  interview_session_id  BIGINT UNSIGNED NOT NULL COMMENT '所属面试场次；一题未答的异常场次不生成报告',
  status                VARCHAR(20)  NOT NULL COMMENT '状态：generating 生成中、generated 已生成、failed 生成失败',
  retry_count           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已自动重试次数，开始时为 0，上限 3 次',
  next_retry_at         DATETIME(3)  NULL COMMENT '下次重试时间',
  total_score           DECIMAL(4,1) NULL COMMENT '总分，百分制，保留 1 位小数',
  grade                 VARCHAR(20)  NULL COMMENT '等级：excellent 优秀、good 良好、needs_improvement 待提升',
  learning_advice       JSON         NULL COMMENT '学习建议及推荐课程',
  failure_reason        VARCHAR(500) NULL COMMENT '最近一次失败原因，仅供排查',
  generated_at          DATETIME(3)  NULL COMMENT '生成完成时间',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_session (interview_session_id),
  KEY idx_report_status_retry (status, next_retry_at),
  CONSTRAINT chk_report_status CHECK (status IN ('generating', 'generated', 'failed')),
  CONSTRAINT chk_report_grade CHECK (grade IS NULL OR grade IN ('excellent', 'good', 'needs_improvement')),
  CONSTRAINT chk_report_score CHECK (total_score IS NULL OR total_score BETWEEN 0 AND 100),
  CONSTRAINT chk_report_retry CHECK (retry_count <= 3),
  CONSTRAINT chk_report_generated CHECK (status <> 'generated' OR (total_score IS NOT NULL AND grade IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试报告';

-- ============================================================
-- 七、AI 次数
-- ============================================================

CREATE TABLE ai_quota_daily (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id        BIGINT UNSIGNED NOT NULL COMMENT '学员',
  quota_date        DATE NOT NULL COMMENT '日期，北京时间',
  interview_used    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已用面试次数，当日初始为 0',
  scoring_used      INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已用评分次数，当日初始为 0',
  scoring_reserved  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '模拟考试预留的评分次数，当日初始为 0',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quota_student_date (student_id, quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员每日 AI 次数使用量';

CREATE TABLE ai_quota_ledger (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id     BIGINT UNSIGNED NOT NULL COMMENT '学员',
  quota_date     DATE        NOT NULL COMMENT '次数归属日期，北京时间',
  quota_type     VARCHAR(10) NOT NULL COMMENT '次数类型：interview 面试、scoring 评分',
  action         VARCHAR(10) NOT NULL COMMENT '动作：charge 扣减、reserve 预留、consume 预留转扣减、release 释放、refund 退还',
  business_type  VARCHAR(20) NOT NULL COMMENT '业务类型：interview_session、exam_session、practice_answer',
  business_id    BIGINT UNSIGNED NOT NULL COMMENT '业务记录 ID',
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_business_action (business_type, business_id, quota_type, action),
  KEY idx_ledger_student_date (student_id, quota_date),
  KEY idx_ledger_date_type (quota_date, quota_type, action),
  CONSTRAINT chk_ledger_type CHECK (quota_type IN ('interview', 'scoring')),
  CONSTRAINT chk_ledger_action CHECK (action IN ('charge', 'reserve', 'consume', 'release', 'refund')),
  CONSTRAINT chk_ledger_business CHECK (business_type IN ('interview_session', 'exam_session', 'practice_answer'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 次数变动流水，同时用于防止重复扣减';

-- ============================================================
-- 八、运营与服务
-- ============================================================

CREATE TABLE feedback (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  feedback_type           VARCHAR(30)   NOT NULL COMMENT '反馈类型：question_correction 题目纠错、video_invalid 视频失效',
  student_id              BIGINT UNSIGNED NOT NULL COMMENT '提交学员；注销后关联不到时显示已注销用户',
  course_id               BIGINT UNSIGNED NOT NULL COMMENT '相关课程；题目纠错时为题目所属课程',
  practice_question_id    BIGINT UNSIGNED NULL COMMENT '被纠错的题目，题目纠错使用',
  error_type              VARCHAR(20)   NULL COMMENT '错误类型：stem_error 题干有误、answer_error 答案有误、analysis_error 解析有误、other 其他，题目纠错使用',
  description             VARCHAR(1000) NULL COMMENT '学员填写的说明；题目纠错必填',
  video_url               VARCHAR(1000) NULL COMMENT '反馈时的视频链接，视频失效反馈使用',
  status                  VARCHAR(20)   NOT NULL COMMENT '状态：pending 待处理、corrected 已修正、rejected 已驳回',
  handling_note           VARCHAR(1000) NULL COMMENT '处理说明；处理后必填',
  handled_by_sys_user_id  BIGINT UNSIGNED NULL COMMENT '处理人',
  handled_at              DATETIME(3)   NULL COMMENT '处理时间',
  created_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  updated_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_feedback_status_type (status, feedback_type, created_at),
  KEY idx_feedback_question_status (practice_question_id, status),
  KEY idx_feedback_course_status (course_id, status),
  KEY idx_feedback_created (created_at),
  CONSTRAINT chk_feedback_type CHECK (feedback_type IN ('question_correction', 'video_invalid')),
  CONSTRAINT chk_feedback_error_type CHECK (error_type IS NULL OR error_type IN ('stem_error', 'answer_error', 'analysis_error', 'other')),
  CONSTRAINT chk_feedback_status CHECK (status IN ('pending', 'corrected', 'rejected')),
  CONSTRAINT chk_feedback_correction CHECK (feedback_type <> 'question_correction' OR (practice_question_id IS NOT NULL AND error_type IS NOT NULL AND description IS NOT NULL)),
  CONSTRAINT chk_feedback_handled CHECK (status = 'pending' OR (handling_note IS NOT NULL AND handled_by_sys_user_id IS NOT NULL AND handled_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目纠错与视频失效反馈';

CREATE TABLE student_message (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id    BIGINT UNSIGNED NOT NULL COMMENT '接收学员',
  message_type  VARCHAR(30)   NOT NULL COMMENT '类型：agreement_updated 协议已更新、interview_report_generated 面试报告已生成、feedback_result 纠错与反馈处理结果',
  title         VARCHAR(100)  NOT NULL COMMENT '标题',
  content       VARCHAR(2000) NOT NULL COMMENT '正文',
  business_id   BIGINT UNSIGNED NULL COMMENT '关联业务记录 ID，用于跳转',
  is_read       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读：1 是、0 否；默认未读',
  read_at       DATETIME(3)   NULL COMMENT '已读时间',
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
  PRIMARY KEY (id),
  KEY idx_message_student_read (student_id, is_read, created_at),
  CONSTRAINT chk_student_message_type CHECK (message_type IN ('agreement_updated', 'interview_report_generated', 'feedback_result')),
  CONSTRAINT chk_student_message_read CHECK (is_read IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内信';

-- ============================================================
-- 九、数据看板汇总（不含学员个人信息，注销时不删除）
-- ============================================================

CREATE TABLE stat_daily_overview (
  id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  stat_date                     DATE NOT NULL COMMENT '统计日期，北京时间',
  new_student_count             INT UNSIGNED NOT NULL COMMENT '新增注册数',
  cumulative_student_count      INT UNSIGNED NOT NULL COMMENT '累计学员数',
  daily_active_student_count    INT UNSIGNED NOT NULL COMMENT '日活跃学员数',
  monthly_active_student_count  INT UNSIGNED NOT NULL COMMENT '月活跃学员数：含当天在内近 30 天有学习行为的学员数',
  next_day_retained_count       INT UNSIGNED NULL COMMENT '当天注册的学员中，注册后第 1 天有学习行为的人数，用于计算次日留存率；未到统计时间时为空',
  seven_day_retained_count      INT UNSIGNED NULL COMMENT '当天注册的学员中，注册后第 7 天有学习行为的人数，用于计算 7 日留存率；未到统计时间时为空',
  answer_count                  INT UNSIGNED NOT NULL COMMENT '刷题量',
  correct_answer_count          INT UNSIGNED NOT NULL COMMENT '答对题数，用于计算正确率',
  interview_count               INT UNSIGNED NOT NULL COMMENT '面试场次，不含 AI 服务异常结束的场次',
  interview_scored_count        INT UNSIGNED NOT NULL COMMENT '已生成总分的面试场次，用于计算平均分',
  interview_score_sum           DECIMAL(12,1) NOT NULL COMMENT '面试总分之和，用于计算平均分',
  ai_interview_call_count       INT UNSIGNED NOT NULL COMMENT 'AI 调用次数：面试，按场统计',
  ai_scoring_call_count         INT UNSIGNED NOT NULL COMMENT 'AI 调用次数：简答题评分，按实际扣减次数统计',
  refreshed_at                  DATETIME(3)  NOT NULL COMMENT '最近更新时间，用于页面展示',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_daily_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日总览指标';

CREATE TABLE stat_route_daily (
  id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  stat_date                  DATE NOT NULL COMMENT '统计日期，北京时间',
  learning_route_id          BIGINT UNSIGNED NOT NULL COMMENT '路线',
  route_name                 VARCHAR(100) NOT NULL COMMENT '路线名称快照',
  learner_count              INT UNSIGNED NOT NULL COMMENT '学过该路线的学员数',
  average_learned_progress   DECIMAL(5,2) NOT NULL COMMENT '平均已学进度，百分比',
  average_mastered_progress  DECIMAL(5,2) NOT NULL COMMENT '平均已掌握进度，百分比',
  refreshed_at               DATETIME(3)  NOT NULL COMMENT '最近更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_route_date (stat_date, learning_route_id),
  KEY idx_stat_route_route (learning_route_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='各路线每日指标';

CREATE TABLE stat_stage_interview_daily (
  id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  stat_date                DATE NOT NULL COMMENT '统计日期，北京时间',
  route_stage_id           BIGINT UNSIGNED NOT NULL COMMENT '阶段',
  stage_name               VARCHAR(100) NOT NULL COMMENT '阶段名称快照',
  stage_interview_count    INT UNSIGNED NOT NULL COMMENT '有该阶段得分的面试场次',
  stage_score_sum          DECIMAL(12,1) NOT NULL COMMENT '阶段得分之和，用于计算平均得分',
  refreshed_at             DATETIME(3)  NOT NULL COMMENT '最近更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_stage_date (stat_date, route_stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='各阶段每日面试得分';

CREATE TABLE stat_question_daily (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  stat_date             DATE NOT NULL COMMENT '统计日期，北京时间',
  practice_question_id  BIGINT UNSIGNED NOT NULL COMMENT '题目',
  stem_excerpt          VARCHAR(200) NOT NULL COMMENT '题干摘要快照，用于排行展示',
  answer_count          INT UNSIGNED NOT NULL COMMENT '作答次数',
  wrong_count           INT UNSIGNED NOT NULL COMMENT '答错次数',
  refreshed_at          DATETIME(3)  NOT NULL COMMENT '最近更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stat_question_date (stat_date, practice_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目每日作答指标，用于错误率排行';
