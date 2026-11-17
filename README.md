# 云枢智学

在线学习、刷题与 AI 面试平台。需求和设计见 `docs/`，架构见 `docs/design/02.整体架构设计.md`。

## 目录

| 目录 | 内容 |
|---|---|
| `docs/` | 需求文档、设计文档、数据库与接口规范 |
| `prototype/` | 页面原型，仅作交互参考，开发以需求文档为准 |
| `server/` | 后端：JDK 17、Spring Boot 4.1、MyBatis-Plus、Sa-Token、Flyway，单 Maven 工程 |
| `web/` | 前端：Vue 3、TypeScript、Vite，pnpm 工作区（学员端 `apps/student`、管理后台 `apps/admin`） |
| `deploy/dev/` | 本地开发依赖服务：MySQL 8.4、Redis 7.4、SeaweedFS |
| `scripts/sql/` | 初始建表脚本 `schema.sql`，与 Flyway 的 V1 迁移内容一致 |

## 本地环境要求

| 工具 | 版本 |
|---|---|
| JDK | 17（Eclipse Temurin） |
| Maven | 3.9 及以上 |
| Node.js | 22.12 及以上 |
| pnpm | 12 |
| Docker Desktop | 用于启动本地依赖服务 |

## 启动顺序

**1. 启动本地依赖服务**（MySQL、Redis、对象存储），详见 `deploy/dev/README.md`：

```bash
cd deploy/dev && docker compose up -d
```

**2. 启动后端**，端口 8080。第一次启动时 Flyway 会自动建表；账号密码自动读取 `deploy/dev/.env`：

```bash
cd server && mvn spring-boot:run
```

在 IntelliJ IDEA 中也可以直接运行 `com.yunshuzhixue.YunshuApplication`。健康检查地址：<http://127.0.0.1:8080/actuator/health>

**3. 安装前端依赖**（只需一次）：

```bash
cd web && pnpm install
```

**4. 启动前端**，学员端 <http://127.0.0.1:5173/>，管理后台 <http://127.0.0.1:5174/>：

```bash
cd web && pnpm dev:student
```

```bash
cd web && pnpm dev:admin
```

前端开发服务器会把 `/api` 和 `/ws` 请求转发到本地后端。

**5. 首次登录管理后台**：账号和初始密码见 `deploy/dev/.env` 中的 `ADMIN_INIT_ACCOUNT`、`ADMIN_INIT_PASSWORD`。第一次登录后必须修改密码，之后用新密码登录。这个账号由后端在库中没有任何后台账号时自动创建，只创建一次。

## 服务管理脚本

每天开工不必手动拼命令，用 `scripts/dev-up.sh` 统一管理：

```bash
scripts/dev-up.sh status          # 后端、学员端、管理端、MySQL、Redis、对象存储是否就绪
scripts/dev-up.sh up              # 启动后端（jar 不存在时自动打包），等到健康检查通过再返回
scripts/dev-up.sh up --web        # 同时启动学员端 5173 与管理端 5174
scripts/dev-up.sh up --build      # 强制重新打包后启动
scripts/dev-up.sh down            # 停止后端（down --web 连前端一起停）
scripts/dev-up.sh restart         # 重启后端
scripts/dev-up.sh logs -f         # 跟随后端日志（web-logs 看前端日志）
```

脚本做的事情：检查端口占用 → 按需打包 → 用 `setsid` 在**新会话**里启动进程并把 PID 写进 `.run-logs/*.pid` → 轮询健康检查。因为进程不在当前终端的进程组里，关掉终端、会话结束都不会把后端带走；日志统一写在 `.run-logs/server.log`、`.run-logs/web-student.log`、`.run-logs/web-admin.log`。

**后端“莫名被关掉”怎么判断**：如果日志只停在 `Started YunshuApplication` 那一行、没有任何 `Shutting down` / `Closing`，说明是被外层进程组强杀的（常见于把服务挂在某个会话里后台运行，会话结束时被回收），不是应用崩溃。先 `scripts/dev-up.sh status` 确认，再 `scripts/dev-up.sh up` 拉起；反复出现就用脚本启动，或在自己的终端里跑 `cd server && mvn spring-boot:run`。

## 常用命令

| 位置 | 命令 | 作用 |
|---|---|---|
| `server/` | `mvn -DskipTests package` | 打包后端 |
| `web/` | `pnpm typecheck` | 类型检查 |
| `web/` | `pnpm lint` | ESLint 检查 |
| `web/` | `pnpm format` | Prettier 格式化 |
| `web/` | `pnpm build` | 构建学员端和管理后台 |

## 数据库变更

表结构由 Flyway 管理，迁移脚本在 `server/src/main/resources/db/migration/`。已执行过的迁移脚本不要修改；修改表结构时新增 `V2__说明.sql` 等脚本，并同步更新 `docs/design/03.数据库设计.md`。

## 开发进度

管理端已按模块交付；学员端在建，按模块分批交付。

| 范围 | 状态 |
|---|---|
| 管理端：登录与账号、内容管理、题库管理、纠错反馈、系统用户与角色、系统配置、操作日志 | 已交付 |
| 管理端：学员管理（A-STU-01~06）、数据看板（A-DSH-01~02）、工作台（A-DSH-03），含导出与操作日志 | 已交付 |
| 学员端：全局框架、首页（S-PTL-01）、学习路线列表/详情/搜索（S-PTL-02~04） | 已交付 |
| 学员端：登录与注册（S-ACC-01~05、07）、协议查看（S-HLP-01），含登录守卫与账号菜单 | 已交付 |
| 学员端：错题本（S-WRG-01~02），含四项筛选、解析查看与手动移除 | 已交付 |
| 学员端：AI 次数管理（S-QTA-01）、按课程顺序练习（S-PRC-01~05），含自动判分、作答事件与刷题记录 | 已交付 |
| 学员端：课程视频打开与记录（S-LRN-01~03）、视频失效反馈（S-FDB-01）、课程已学状态在路线详情生效 | 已交付 |
| 学员端：首页「继续学习」接上次学习位置，含路线进度、本阶段课程清单与跳回定位 | 已交付 |
| 学员端：首页「我的路线进度」按课程状态汇总各路线已学、已掌握进度 | 已交付 |
| 学员端：账号剩余能力（S-ACC-06、08~13）、个人中心页（资料与画像、头像、密码、换绑、注销与撤销、协议查看） | 已交付 |
| 学员端：帮助中心（S-HLP-03）、协议更新后重新同意（S-HLP-02）与 `AGREEMENT_CONSENT_REQUIRED` 拦截 | 已交付 |
| 学员端：错题重做（S-PRC-01 wrong_redo，答对自动移出） | 已交付 |
| 学员端：随机抽题（S-PRC-01 random）、学习轨迹与各路线进度（S-LRN-04/05） | 已交付 |
| 学员端：消息中心（S-MSG-01~04），含未读角标、已读标记与协议更新通知 | 已交付 |
| 学员端：模拟考试（S-EXM-01~08，组卷、限时、设备接管、成绩与重评分） | 已交付 |
| 学员端：AI 面试（S-IVW-01~08、WS-01/02）、面试记录与报告（S-RPT-01~03） | 已交付 |

**开发中的简化点**（`infra` 适配器就绪后替换）：

- 图形验证码与短信验证码在本地开发固定为 `1234` / `123456`（架构 T05 中短信厂商待定）；接口、限流与核销逻辑均已按需求实现。
- 协议正文由后台「系统配置 - 协议」发布（A-CFG-04）。未发布时协议接口返回 404，注册与登录按版本 0 提交、服务端跳过版本校验。
- 注册与验证码自动注册时，M01 发布「学员已注册」事件、M17 订阅后写入 `student_agreement_consent`；协议更新后 `pending_agreements` 返回真实待同意项。为避免 M01 依赖 M17，查询接口 `AgreementConsentGateway` 放在 `common`，由 M17 实现。
- 协议同意状态目前**每次请求实时查库**（未加缓存）；接口量上来后可在 M17 侧按学员缓存并在同意/发布时失效。
- 错题收录与移出均由 M09 的「作答完成」事件驱动：答错累加次数；错题重做（wrong_redo）答对自动移出。发起重做时 M09 通过 `WrongQuestionDirectory`（M10 实现）把错题记录 ID 解析成题目 ID，已下架的题不能重做；错题重做轮次不进入「刷题记录」列表。首页「待重做错题」接 M10 的只读计数（只算题目仍上架的错题），「今日面试剩余」接 M13 的真实剩余次数。
- 练习已支持 `sequential` 顺序练习、`random` 随机抽题、`wrong_redo` 错题重做三种模式；后两者用 `planned_question_ids` 固定本轮题目列表，随机抽题按系统配置的抽题数量与最低题量从阶段抽题，重复发起会把旧的进行中场次标记为 `unfinished`。错题重做场次不进入「刷题记录」。模拟考试已按契约实现八个接口；到时自动交卷由定时任务每分钟扫描。
- 简答题真实 AI 评分已接入厂商适配：模拟考试通过 `ai.api.ShortAnswerScorer` 调用，配置了百炼凭证时走 `DashScopeShortAnswerScorer`（官方 SDK + 版本化提示词），凭证为空时自动回退 `DevShortAnswerScorer`（本地要点重合度估分）；失败路径（`scoring_pending` + 重新评分）按 8.6 实现。练习里仍按需求 5.4.1 降级为学员自评。
- M14 内容安全已落地：`safety.api.SensitiveTextFilter` 提供学员输入拦截与 AI 输出按句替换（命中写 `sensitive_word_hit_log`），词库由 `sysconfig.api.SensitiveWordCatalog` 提供、变更事件后刷新进程内缓存。文字面试、语音面试与简答题评分共用这套规则。
- 模拟考试含简答题时开考预留 1 次评分次数、交卷评分成功才扣减、放弃则释放；次数流水用 `ai_quota_ledger` 保证幂等。
- 「已掌握」自动判定仍待 M08 后续开发；学习轨迹（S-LRN-04，打开/已学/已掌握三类行为）与各路线进度（S-LRN-05）已实现，学习记录页按页签展示；顺序练习已写入 `sequential_question_result`，课程打开与标记已学已可用。首页「继续学习」已接 `student_learning_position`，可跳回路线详情并定位到上次课程；「我的路线进度」按 M08 的课程状态汇总，列出有已学课程的路线（上次学习的路线即使还没标记已学也列出）。
- 消息中心（S-MSG-01~04）已实现列表、未读数、标记已读与全部已读；顶栏铃铛 60 秒轮询未读数。反馈处理结果由 M09/M05 直接调用 `StudentMessageSender` 写入；协议发布时 M17 订阅 `AgreementPublishedEvent` 后给全体未封禁学员写入站内信；面试报告生成后由 M12 调用 `StudentMessageSender` 写入 `interview_report_generated` 站内信。
- 视频链接按契约由 S-LRN-01 返回，不直接写在 `CourseItem` 里；PC 端新窗口打开，手机端 B 站优先唤起 App、未安装时回落网页。视频失效反馈已可提交，后台处理结果通过站内信通知（通知在后台处理时由 M16 发送）。
- 注销已按需求实现申请（7 天冷静期）与撤销，但**冷静期到期后的数据清除尚未开发**：需要 `common` 中的「学员数据清除」扩展接口 + 各模块实现 + 定时任务，到点删除明细、手机号可重新注册。
- 首次设置密码的短信验证码复用 S-ACC-02 的 `reset_password` 场景（场景枚举未新增，避免改动已评审的契约）。
- 注销中的账号当前只拦截账号变更类接口（返回 `STUDENT_CANCELLING`）；进行中的模拟考试与 AI 面试尚未按需求 5.1.5 在到期时自动交卷或超时结束。

**AI 面试的厂商适配层**（架构 T05）：

- 大模型能力统一走 `ai.api.InterviewAi`（开场、主问题、动态追问、过渡、结束语、逐题评分、学习建议）。`infra.ai` 已接入阿里云百炼官方 SDK（`com.alibaba:dashscope-sdk-java`，默认 `qwen-plus`）；配置了 `DASHSCOPE_API_KEY` 时由 `DashScopeInterviewAi` 生成话术与评分，提示词放 `server/src/main/resources/prompts/v1/` 版本化管理，输出按句切分适配 SSE；未配置凭证时 `AiAdapterConfiguration` 自动回退 `DevInterviewAi` 本地占位，离线可运行。模型/温度/maxTokens 可在 `yunshu.ai.dashscope.*` 配置。SDK 调用或解析失败统一转 `UnavailableException`，按需求 8.6 处理。
- 语音识别/合成仍通过 `InterviewAi.transcribe` 抽象，本轮未接 ASR/TTS，SDK 适配器保持返回空串。WS-01 / WS-02 的协议、一次性票据认证、排队与名额（Redis ZSET，断线保留 3 分钟）、设备检测、字幕、敏感词拦截、接管与结束均已实现；**音频帧当前不产生真实转写**，前端用 `dev_transcript` 文本帧走完整对话。真实 ASR/TTS 接好后（`语音链路：服务端中转`，见架构 5.1）无需改业务代码。
- 次数规则：文字面试发起扣 1 次，语音面试设备检测通过才扣；AI 异常结束按 8.6 退还次数、一题未答不生成报告；报告生成失败自动重试 3 次，仍失败可手动重新生成。定时任务负责超时结束与报告重试。

**阶段占位**（接口已在 `docs/design/04.API接口设计.md` 定义，暂不实现）：

- A-STU-07~10：管理端学员详情里的学习记录、刷题记录、面试记录与面试报告四个页签。学员端业务数据已齐（学习轨迹、刷题记录、面试记录/报告），待管理端补齐；在此之前显示「学员端上线后提供」。
