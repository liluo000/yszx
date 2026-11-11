import type { QuestionType } from '@yunshu/api'

/** 配置项在页面上的展示方式；取值范围与后端 ConfigDefinitions 一致 */
export type ConfigField =
  | { kind: 'int'; key: string; label: string; desc: string; unit: string; min: number; max: number }
  | { kind: 'paper_rule'; key: string; label: string; desc: string }
  | { kind: 'grade'; key: string; label: string; desc: string }

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  true_false: '判断题',
  single_choice: '单选题',
  multiple_choice: '多选题',
  short_answer: '简答题',
}

/** 参数类分组（需求第 7 章），对应系统配置页左侧前四项 */
export const PARAMETER_TABS: Record<string, { title: string; note?: string; fields: ConfigField[] }> = {
  ai: {
    title: 'AI 用量',
    note: '每日次数按北京时间 0 点重置；不设置全平台 AI 用量总上限。',
    fields: [
      {
        kind: 'int',
        key: 'ai.interview_daily_limit',
        label: '每人每日 AI 面试次数',
        desc: '文字面试与语音面试共用，每场扣 1 次',
        unit: '次',
        min: 0,
        max: 100,
      },
      {
        kind: 'int',
        key: 'ai.scoring_daily_limit',
        label: '每人每日简答题 AI 评分次数',
        desc: '练习时每题扣 1 次，模拟考试整场扣 1 次',
        unit: '次',
        min: 0,
        max: 1000,
      },
    ],
  },
  learn: {
    title: '学习判定',
    note: '修改后只影响之后开始的练习和考试。',
    fields: [
      {
        kind: 'int',
        key: 'learning.mastery_threshold',
        label: '"已掌握"达标线',
        desc: '顺序练习的正确率达到此值即判定为已掌握',
        unit: '%',
        min: 1,
        max: 100,
      },
      {
        kind: 'int',
        key: 'learning.short_answer_pass_score',
        label: '简答题及格线',
        desc: 'AI 评分（百分制）低于此值视为答错，收录到错题本',
        unit: '分',
        min: 0,
        max: 100,
      },
    ],
  },
  exam: {
    title: '刷题与考试',
    note: '修改后只影响之后开始的考试。',
    fields: [
      {
        kind: 'int',
        key: 'practice.random_question_count',
        label: '随机抽题数量',
        desc: '按阶段随机抽题时每次抽取的题目数',
        unit: '道',
        min: 1,
        max: 100,
      },
      {
        kind: 'int',
        key: 'practice.random_min_question_count',
        label: '随机抽题最低题量',
        desc: '阶段题量低于此值时随机抽题入口置灰',
        unit: '道',
        min: 1,
        max: 100,
      },
      { kind: 'paper_rule', key: 'exam.paper_rule', label: '模拟考试组卷规则', desc: '各题型的题目数量与单题分值' },
      {
        kind: 'int',
        key: 'exam.duration_minutes',
        label: '模拟考试时长',
        desc: '单场考试的时长上限',
        unit: '分钟',
        min: 1,
        max: 300,
      },
      {
        kind: 'int',
        key: 'exam.min_question_count',
        label: '模拟考试最低题量',
        desc: '阶段题量低于此值时模拟考试入口置灰',
        unit: '道',
        min: 1,
        max: 200,
      },
    ],
  },
  iv: {
    title: 'AI 面试',
    note: '修改后只影响之后开始的面试。',
    fields: [
      {
        kind: 'int',
        key: 'interview.main_question_count',
        label: '主问题数量',
        desc: '单场面试从题库中抽取的主问题数',
        unit: '道',
        min: 1,
        max: 20,
      },
      {
        kind: 'int',
        key: 'interview.max_followup_count',
        label: '每题最多追问次数',
        desc: 'AI 对单道主问题的追问上限',
        unit: '次',
        min: 0,
        max: 10,
      },
      {
        kind: 'int',
        key: 'interview.duration_minutes',
        label: '面试时长上限',
        desc: '超时自动结束并生成报告',
        unit: '分钟',
        min: 5,
        max: 180,
      },
      {
        kind: 'int',
        key: 'interview.min_question_count',
        label: '面试最低题量',
        desc: '所选范围内面试题低于此值时不能发起',
        unit: '道',
        min: 1,
        max: 20,
      },
      {
        kind: 'int',
        key: 'interview.voice_end_pause_seconds',
        label: '语音"说完"判定停顿',
        desc: '学员开口后停顿超过此时长，视为本次回答说完',
        unit: '秒',
        min: 1,
        max: 10,
      },
      {
        kind: 'int',
        key: 'interview.voice_concurrency_limit',
        label: '语音面试同时进行上限',
        desc: '全平台同时进行的语音面试场数，超出后排队',
        unit: '场',
        min: 1,
        max: 1000,
      },
      { kind: 'grade', key: 'interview.grade_thresholds', label: '面试等级分数线', desc: '优秀、良好、待提升三档' },
    ],
  },
}
