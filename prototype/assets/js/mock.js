/* ==========================================================
   云枢智学 原型模拟数据（仅用于演示交互）
   ========================================================== */
(function () {
  const DB = {};

  /* 系统配置默认值（需求第 7 章） */
  DB.config = {
    interviewPerDay: 2, scorePerDay: 20,
    masteryLine: 80, shortPassLine: 60,
    randomCount: 10, randomMin: 5,
    exam: { judge: [5, 3], single: [8, 5], multi: [5, 5], short: [2, 10] }, examMinutes: 30, examMin: 10,
    mainQuestions: 5, maxFollowups: 2, interviewMinutes: 30, interviewMin: 3,
    pauseSeconds: 3, voiceConcurrency: 20, grade: { excellent: 80, good: 60 },
  };

  DB.platforms = [
    { id: 1, name: 'B 站', domains: ['bilibili.com', 'b23.tv'], enabled: true, builtin: true },
  ];

  DB.subjects = [
    { id: 1, name: 'Python', desc: 'Python 语言基础与进阶', sort: 1 },
    { id: 2, name: 'Java', desc: 'Java 语言与生态', sort: 2 },
    { id: 3, name: '前端开发', desc: 'HTML / CSS / JavaScript / 框架', sort: 3 },
    { id: 4, name: '大模型应用', desc: '大模型、RAG、Agent', sort: 4 },
    { id: 5, name: '数据库', desc: '关系型与缓存数据库', sort: 5 },
    { id: 6, name: '工程化与部署', desc: '容器化、CI/CD 与上线', sort: 6 },
  ];

  const c = (id, subjectId, title, duration, q, iq, extra = {}) => ({ id, subjectId, title, duration, q, iq, platformId: 1, url: `https://www.bilibili.com/video/BV1xx${id}411c7mD`, desc: `${title}：从概念到实战的系统讲解。`, status: 'on', learners: U.rand(80, 900), ...extra });
  DB.courses = [
    c(1, 1, 'Python 基础语法', '6 小时', 8, 3),
    c(2, 1, 'Python 面向对象与模块', '5 小时', 6, 2),
    c(3, 1, 'Python 异步编程', '4 小时', 6, 2),
    c(4, 3, 'HTML 与 CSS 基础', '8 小时', 6, 2),
    c(5, 3, 'JavaScript 核心', '10 小时', 6, 3),
    c(6, 3, 'Vue 3 实战', '12 小时', 6, 2),
    c(7, 1, 'FastAPI 后端开发', '7 小时', 6, 2),
    c(8, 5, 'MySQL 入门与 SQL 优化', '9 小时', 6, 3),
    c(9, 5, 'Redis 缓存实战', '5 小时', 6, 2),
    c(10, 4, '大模型基础与 Prompt 工程', '6 小时', 8, 3),
    c(11, 4, 'RAG 检索增强生成', '7 小时', 6, 3),
    c(12, 4, 'LangChain 应用开发', '8 小时', 6, 2),
    c(13, 4, 'Agent 架构与工具调用', '6 小时', 8, 3),
    c(14, 4, '多 Agent 协作', '5 小时', 6, 0),
    c(15, 6, 'Docker 与部署上线', '4 小时', 6, 2),
    c(16, 2, 'Java 基础语法', '10 小时', 8, 3),
    c(17, 2, 'Java 集合与并发', '9 小时', 6, 3),
    c(18, 2, 'Spring Boot 实战', '12 小时', 6, 3),
    c(19, 2, 'MyBatis 与数据访问', '5 小时', 6, 2),
    c(20, 2, '微服务与 Spring Cloud', '10 小时', 6, 2),
    c(21, 2, 'JVM 原理与调优', '6 小时', 0, 2),
    c(22, 4, 'MCP 协议与工具生态', '3 小时', 3, 0),
    c(23, 3, 'TypeScript 入门', '4 小时', 0, 0, { status: 'off' }),
  ];

  DB.routes = [
    { id: 1, name: 'AI 全栈开发', desc: '从 Python 与前端基础出发，掌握后端、数据库与大模型应用开发，独立完成 AI 产品全栈交付。', status: 'on', learners: 2386,
      stages: [{ id: 101, name: '阶段一：Python 基础', courses: [1, 2] }, { id: 102, name: '阶段二：前端基础', courses: [4, 5, 6] }, { id: 103, name: '阶段三：后端与数据', courses: [7, 8, 9] }, { id: 104, name: '阶段四：大模型应用', courses: [10, 11, 12] }, { id: 105, name: '阶段五：部署上线', courses: [15] }] },
    { id: 2, name: 'Agent 开发', desc: '系统学习大模型原理、RAG 与 Agent 架构，具备设计和落地智能体应用的能力。', status: 'on', learners: 1742,
      stages: [{ id: 201, name: '阶段一：Python 与异步', courses: [1, 3] }, { id: 202, name: '阶段二：大模型基础', courses: [10, 11] }, { id: 203, name: '阶段三：Agent 开发', courses: [12, 13, 22] }, { id: 204, name: '阶段四：多 Agent 进阶', courses: [14] }] },
    { id: 3, name: 'Java 开发', desc: '覆盖 Java 基础、主流框架、数据与缓存、分布式架构，对标企业 Java 后端岗位要求。', status: 'on', learners: 3105,
      stages: [{ id: 301, name: '阶段一：Java 基础', courses: [16, 17] }, { id: 302, name: '阶段二：主流框架', courses: [18, 19] }, { id: 303, name: '阶段三：数据与缓存', courses: [8, 9] }, { id: 304, name: '阶段四：分布式与性能', courses: [20, 21] }] },
  ];

  /* ---------- 刷题题目 ---------- */
  const hand = {
    1: [
      { type: 'judge', stem: 'Python 中的列表（list）是可变类型，元组（tuple）是不可变类型。', answer: true, analysis: 'list 支持增删改，tuple 创建后元素不可修改。' },
      { type: 'single', stem: '下列哪个语句可以正确地定义一个函数？', options: ['function f():', 'def f():', 'func f():', 'define f():'], answer: 1, analysis: 'Python 使用 def 关键字定义函数。' },
      { type: 'single', stem: '执行 print(type(3 / 2)) 的输出结果是？', options: ["<class 'int'>", "<class 'float'>", "<class 'double'>", "<class 'number'>"], answer: 1, analysis: 'Python 3 中 / 为真除法，结果为 float。' },
      { type: 'multi', stem: '以下哪些属于 Python 的内置数据类型？', options: ['dict', 'set', 'array', 'tuple'], answer: [0, 1, 3], analysis: 'array 需要从 array 模块导入，不是内置类型。' },
      { type: 'short', stem: '请简述 Python 中深拷贝与浅拷贝的区别。', ref: '浅拷贝只复制最外层对象，内部的可变对象仍与原对象共享引用；深拷贝会递归复制所有层级的对象，新旧对象完全独立。可通过 copy.copy() 与 copy.deepcopy() 实现。', points: ['浅拷贝只复制外层', '内部可变对象共享引用', '深拷贝递归复制', 'copy 模块的两个函数'], analysis: '关键在于是否递归复制嵌套的可变对象。' },
      { type: 'single', stem: '下列哪个方法可以向列表末尾添加一个元素？', options: ['add()', 'push()', 'append()', 'insert_end()'], answer: 2, analysis: 'list.append(x) 在末尾添加元素。' },
      { type: 'judge', stem: 'Python 的缩进只是代码风格，不影响程序语义。', answer: false, analysis: 'Python 以缩进划分代码块，缩进错误会导致语法错误或逻辑错误。' },
      { type: 'short', stem: '请说明 Python 中 *args 和 **kwargs 的作用。', ref: '*args 用于接收任意数量的位置参数，在函数内是一个元组；**kwargs 用于接收任意数量的关键字参数，在函数内是一个字典。两者常用于编写参数灵活的函数或装饰器。', points: ['*args 接收位置参数', '元组', '**kwargs 接收关键字参数', '字典'], analysis: '注意二者在函数内部的数据类型。' },
    ],
    10: [
      { type: 'single', stem: '大模型产生"幻觉"指的是？', options: ['模型输出速度变慢', '模型生成看似合理但与事实不符的内容', '模型拒绝回答问题', '模型输出乱码'], answer: 1, analysis: '幻觉是指模型自信地输出错误或虚构的信息。' },
      { type: 'judge', stem: 'Temperature 参数越高，模型输出通常越随机、越有创造性。', answer: true, analysis: 'Temperature 控制采样的随机程度。' },
      { type: 'multi', stem: '以下哪些属于常见的 Prompt 工程技巧？', options: ['少样本示例（Few-shot）', '思维链（Chain of Thought）', '提高显卡频率', '明确角色与输出格式'], answer: [0, 1, 3], analysis: '提高显卡频率与 Prompt 设计无关。' },
      { type: 'short', stem: '请简述什么是 Token，以及它对大模型应用开发的影响。', ref: 'Token 是模型处理文本的基本单位，一个 Token 可能是一个字、词或词的一部分。模型的上下文长度、调用费用和响应速度都按 Token 计算，因此开发时需要控制提示词和输出长度、做好上下文截断与摘要。', points: ['文本的基本处理单位', '上下文长度限制', '按 Token 计费', '需要控制长度'], analysis: '从定义、限制、成本三方面回答。' },
      { type: 'single', stem: '下列哪一项最适合用来降低大模型回答私有知识时的幻觉？', options: ['增大 Temperature', '检索增强生成（RAG）', '减少系统提示词', '使用更短的问题'], answer: 1, analysis: 'RAG 通过检索外部知识为模型提供依据。' },
      { type: 'single', stem: 'System Prompt 的主要作用是？', options: ['提升推理速度', '设定模型的角色、规则与行为边界', '压缩模型参数', '统计 Token 数量'], answer: 1, analysis: 'System Prompt 用于设定全局行为。' },
      { type: 'judge', stem: '同一个 Prompt 在 Temperature 为 0 时，多次调用的输出一定完全相同。', answer: false, analysis: '一般会非常接近，但受实现细节影响不能保证完全一致。' },
      { type: 'multi', stem: '评估一个大模型应用效果时，可以关注哪些方面？', options: ['回答准确性', '响应时延', '调用成本', '代码行数'], answer: [0, 1, 2], analysis: '代码行数不是效果指标。' },
    ],
    13: [
      { type: 'single', stem: 'Agent 中"工具调用（Function Calling）"的作用是？', options: ['让模型直接修改自身参数', '让模型按约定格式请求外部函数并利用返回结果', '提升模型训练速度', '替代向量数据库'], answer: 1, analysis: '模型输出结构化的调用请求，由程序执行后把结果返回给模型。' },
      { type: 'judge', stem: 'ReAct 模式让模型交替进行"推理"与"行动"。', answer: true, analysis: 'ReAct = Reasoning + Acting。' },
      { type: 'multi', stem: '一个典型的 Agent 通常包含哪些部分？', options: ['规划（Planning）', '记忆（Memory）', '工具（Tools）', '显示器驱动'], answer: [0, 1, 2], analysis: '规划、记忆、工具是 Agent 的常见组成。' },
      { type: 'short', stem: '请说明在 Agent 中如何防止工具被循环调用、陷入死循环。', ref: '可以设置最大迭代步数和超时时间；记录调用历史，检测重复的调用与参数；为每一步设定明确的终止条件；在关键操作前加入人工确认或校验；出现异常时降级为直接回答。', points: ['最大步数或超时', '检测重复调用', '明确终止条件', '异常降级'], analysis: '从限制、检测、终止、兜底四方面作答。' },
      { type: 'single', stem: '以下哪种场景最适合使用多步 Agent 而不是单次调用？', options: ['把一句话翻译成英文', '根据用户需求检索资料、汇总并生成报告', '判断一句话的情感倾向', '统计一段文字的字数'], answer: 1, analysis: '需要多步规划与工具协作的任务适合 Agent。' },
      { type: 'judge', stem: 'Agent 的记忆只能通过增加模型上下文长度来实现。', answer: false, analysis: '还可以借助向量库、摘要等外部记忆。' },
      { type: 'single', stem: '为工具编写描述（description）时，最重要的是？', options: ['越短越好', '清楚说明用途、参数含义和适用场景', '使用英文缩写', '加入大量示例代码'], answer: 1, analysis: '模型依赖工具描述决定何时、如何调用。' },
      { type: 'multi', stem: '上线 Agent 应用前，应考虑哪些安全措施？', options: ['限制工具的权限范围', '对敏感操作二次确认', '记录调用日志', '关闭所有日志以提升性能'], answer: [0, 1, 2], analysis: '日志是排查问题和审计的重要依据。' },
    ],
    16: [
      { type: 'single', stem: 'Java 中用于定义常量的关键字组合是？', options: ['static const', 'final static', 'const final', 'readonly'], answer: 1, analysis: 'Java 使用 static final 定义常量，const 是保留字未使用。' },
      { type: 'judge', stem: 'Java 中 String 是不可变类。', answer: true, analysis: 'String 对象创建后内容不可改变。' },
      { type: 'multi', stem: '以下哪些是 Java 的基本数据类型？', options: ['int', 'String', 'boolean', 'char'], answer: [0, 2, 3], analysis: 'String 是引用类型。' },
      { type: 'short', stem: '请简述 == 与 equals() 的区别。', ref: '== 对基本类型比较值，对引用类型比较内存地址；equals() 默认也比较地址，但很多类（如 String）重写了它以比较内容。自定义类重写 equals() 时通常还要重写 hashCode()。', points: ['== 比较地址或值', 'equals 可被重写比较内容', 'String 重写了 equals', '同时重写 hashCode'], analysis: '注意基本类型与引用类型的差别。' },
      { type: 'single', stem: '下列哪个集合不允许出现重复元素？', options: ['ArrayList', 'LinkedList', 'HashSet', 'Vector'], answer: 2, analysis: 'Set 不允许重复元素。' },
      { type: 'single', stem: 'Java 程序的入口方法签名是？', options: ['public void main()', 'public static void main(String[] args)', 'static int main(String args)', 'void start()'], answer: 1, analysis: '固定签名为 public static void main(String[] args)。' },
      { type: 'judge', stem: 'Java 支持类的多继承。', answer: false, analysis: 'Java 类只支持单继承，可通过接口实现多实现。' },
      { type: 'short', stem: '请说明重载（Overload）与重写（Override）的区别。', ref: '重载发生在同一个类中，方法名相同、参数列表不同，与返回值无关；重写发生在父子类之间，方法签名相同，子类重新实现父类方法，访问权限不能更严格。', points: ['重载：同类、参数不同', '重写：父子类、签名相同', '与返回值无关', '访问权限要求'], analysis: '从发生位置、签名要求两方面区分。' },
    ],
  };
  const generic = (course) => [
    { type: 'judge', stem: `学习「${course.title}」时，理解核心概念比死记 API 更重要。`, answer: true, analysis: '理解原理才能灵活运用。' },
    { type: 'single', stem: `关于「${course.title}」，下列说法最准确的是？`, options: ['只需要看文档，不需要动手实践', '应结合官方文档与项目实践学习', '只要背下所有命令即可', '与其他技术完全无关'], answer: 1, analysis: '理论结合实践是最有效的学习方式。' },
    { type: 'multi', stem: `在实际项目中使用「${course.title}」时，通常需要关注哪些方面？`, options: ['可维护性', '性能', '安全性', '代码字体颜色'], answer: [0, 1, 2], analysis: '代码字体颜色与工程质量无关。' },
    { type: 'short', stem: `请结合一个你做过或了解的场景，说明「${course.title}」解决了什么问题。`, ref: `回答应包含：业务场景说明；「${course.title}」在其中承担的作用；与不使用它相比的改进（如效率、性能、可维护性）；实施中遇到的问题与解决办法。`, points: ['场景描述', '承担的作用', '带来的改进', '问题与解决'], analysis: '按"场景—作用—效果—问题"的结构回答。' },
    { type: 'single', stem: `下列哪种做法最有助于掌握「${course.title}」？`, options: ['只看视频不做笔记', '边学边做小项目并复盘', '跳过基础直接看源码', '只做选择题'], answer: 1, analysis: '项目驱动 + 复盘效果最好。' },
    { type: 'judge', stem: `「${course.title}」的知识点学完一遍后就不需要再复习。`, answer: false, analysis: '需要通过练习和错题复盘巩固。' },
  ];
  DB.questions = [];
  let qid = 1;
  DB.courses.forEach((co) => {
    if (!co.q) return;
    const list = (hand[co.id] || generic(co)).slice(0, co.q);
    while (list.length < co.q) list.push(...generic(co).slice(0, co.q - list.length));
    list.forEach((q, i) => DB.questions.push({ id: qid++, courseId: co.id, sort: i + 1, status: 'on', answered: U.rand(0, 3) > 0, ...q }));
  });

  /* ---------- 面试题目 ---------- */
  const ivHand = {
    1: ['请介绍一下 Python 中的装饰器，以及你在项目中是怎么使用它的？', '列表推导式和生成器表达式有什么区别？分别适合什么场景？', '说说 Python 的 GIL，它对多线程程序有什么影响？'],
    10: ['你如何设计一个稳定、可维护的 Prompt？请结合实际例子说明。', '怎么评估一个大模型应用的回答质量？', '遇到模型输出格式不稳定时，你会怎么处理？'],
    11: ['请描述一个 RAG 系统的完整流程。', '文档切分的粒度如何选择？会带来什么影响？', '如何提升 RAG 的召回准确率？'],
    13: ['请介绍你理解的 Agent 架构，各模块分别负责什么？', '工具调用失败时，Agent 应该如何处理？', '如何评估一个 Agent 的任务完成质量？'],
    16: ['请说说 Java 中 HashMap 的底层实现原理。', '解释一下 Java 的垃圾回收机制。', '接口和抽象类有什么区别？什么时候用哪个？'],
    18: ['Spring Boot 的自动配置是如何实现的？', '说说你对 Spring 事务传播机制的理解。', '如何排查一个 Spring Boot 接口响应慢的问题？'],
  };
  DB.interviewQuestions = [];
  let iid = 1;
  DB.courses.forEach((co) => {
    for (let i = 0; i < co.iq; i++) {
      const stem = (ivHand[co.id] || [])[i] || [`请结合项目经验，谈谈你对「${co.title}」中最核心概念的理解。`, `在使用「${co.title}」相关技术时，你遇到过最棘手的问题是什么？如何解决的？`, `如果让你向新同事讲解「${co.title}」，你会如何组织内容？`][i];
      DB.interviewQuestions.push({ id: iid++, courseId: co.id, stem, ref: '参考答案：先说明核心概念与原理，再结合具体项目场景说明用法与取舍，最后补充常见问题与优化思路。', points: '概念准确；结合实际场景；能说明取舍；有优化思路', follow: '追问方向：实现细节、边界情况、性能与替代方案', status: 'on', used: iid % 3 !== 0 });
    }
  });

  /* ---------- 学员（后台） ---------- */
  const names = ['张同学', '李明', '王小雨', '赵一鸣', '陈思远', '刘洋', '周可', '吴桐', '郑好', '孙悦', '马超', '朱琳', '胡杨', '郭靖', '何夕', '高远', '林夕', '罗丹', '梁晨', '宋佳', '谢天', '韩梅', '唐果', '冯晓', '曹宇', '许诺', '邓雨', '萧然'];
  const edus = ['大专', '本科', '硕士', '本科', '本科'];
  const intents = ['AI 全栈开发', 'Agent 开发', 'Java 开发'];
  const years = ['在校生', '1 年以内', '1~3 年', '3~5 年', '5 年以上'];
  DB.students = names.map((n, i) => ({
    id: 10001 + i, nickname: n, phone: `13${U.rand(0, 9)}${String(U.rand(10000000, 99999999))}`,
    regTime: `2026-${U.pad(U.rand(8, 11))}-${U.pad(U.rand(1, 28))} ${U.pad(U.rand(8, 22))}:${U.pad(U.rand(0, 59))}`,
    lastActive: `2026-11-${U.pad(U.rand(1, 8))} ${U.pad(U.rand(8, 23))}:${U.pad(U.rand(0, 59))}`,
    edu: i % 6 === 5 ? '' : edus[i % edus.length], intent: i % 7 === 6 ? '' : intents[i % 3], years: i % 5 === 4 ? '' : years[i % years.length],
    routes: U.rand(0, 3), interviews: U.rand(0, 12), status: i === 3 ? 'banned' : i === 8 ? 'cancelling' : 'normal', banReason: i === 3 ? '多次发布广告内容' : '',
  }));

  DB.sysUsers = [
    { id: 1, account: 'admin', name: '王主管', phone: '13800001111', role: 'super', status: 'on' },
    { id: 2, account: 'content01', name: '李内容', phone: '13800002222', role: 'content', status: 'on' },
    { id: 3, account: 'content02', name: '赵编辑', phone: '13800003333', role: 'content', status: 'off' },
    { id: 4, account: 'ops01', name: '陈运营', phone: '13800004444', role: 'student', status: 'on' },
  ];
  DB.roleNames = { super: '超级管理员', content: '内容运营', student: '学员运营' };

  DB.feedback = [
    { id: 1, type: 'question', targetId: 3, target: '执行 print(type(3 / 2)) 的输出结果是？', course: 'Python 基础语法', errType: '解析有误', desc: '解析里最好补充 // 整除的说明。', user: '李明', time: '2026-11-08 09:12', status: 'pending', count: 1 },
    { id: 2, type: 'video', targetId: 9, target: 'Redis 缓存实战', course: 'Redis 缓存实战', errType: '', desc: '点进去提示视频已失效。', user: '王小雨', time: '2026-11-08 08:40', status: 'pending', count: 3 },
    { id: 3, type: 'question', targetId: 20, target: '大模型产生"幻觉"指的是？', course: '大模型基础与 Prompt 工程', errType: '其他', desc: '选项 D 描述不太严谨。', user: '陈思远', time: '2026-11-07 21:03', status: 'pending', count: 2 },
    { id: 4, type: 'question', targetId: 33, target: 'Java 中用于定义常量的关键字组合是？', course: 'Java 基础语法', errType: '答案有误', desc: '应该是 static final 而不是 final static？', user: '刘洋', time: '2026-11-06 15:20', status: 'rejected', count: 1, reply: '两种写法等价，答案无误。' },
    { id: 5, type: 'video', targetId: 15, target: 'Docker 与部署上线', course: 'Docker 与部署上线', errType: '', desc: '第 3 集打不开。', user: '周可', time: '2026-11-05 10:11', status: 'fixed', count: 1, reply: '已更换为新的视频链接。' },
  ];

  DB.logs = [
    { time: '2026-11-08 10:21', user: '王主管', role: '超级管理员', module: '系统配置', action: '修改', target: 'AI 用量：每人每日 AI 面试次数由 3 改为 2', ip: '10.0.3.21' },
    { time: '2026-11-08 09:58', user: '陈运营', role: '学员运营', module: '学员管理', action: '导出', target: '学员名单 236 条（筛选：求职意向=Java 开发）', ip: '10.0.3.45' },
    { time: '2026-11-08 09:40', user: '陈运营', role: '学员运营', module: '学员管理', action: '查看完整手机号', target: '学员 赵一鸣（10004）', ip: '10.0.3.45' },
    { time: '2026-11-08 09:30', user: '李内容', role: '内容运营', module: '刷题题库', action: '批量导入', target: '成功 48 条，失败 2 条', ip: '10.0.3.18' },
    { time: '2026-11-07 18:12', user: '李内容', role: '内容运营', module: '纠错与反馈', action: '处理', target: '视频失效反馈：Docker 与部署上线，已修正', ip: '10.0.3.18' },
    { time: '2026-11-07 17:05', user: '陈运营', role: '学员运营', module: '学员管理', action: '封禁', target: '学员 赵一鸣（10004），原因：多次发布广告内容', ip: '10.0.3.45' },
    { time: '2026-11-07 15:44', user: '李内容', role: '内容运营', module: '学习路线', action: '编辑', target: 'Agent 开发：阶段三新增课程「MCP 协议与工具生态」', ip: '10.0.3.18' },
    { time: '2026-11-07 14:02', user: '王主管', role: '超级管理员', module: '系统用户', action: '停用', target: '账号 content02（赵编辑）', ip: '10.0.3.21' },
    { time: '2026-11-07 09:00', user: '王主管', role: '超级管理员', module: '登录', action: '后台登录', target: '账号 admin', ip: '10.0.3.21' },
  ];

  DB.faq = [
    { q: 'AI 面试每天可以参加几次？', a: '每人每天可参加 2 次 AI 面试（文字与语音共用），北京时间每天 0 点重置。因系统原因中断的面试会退还次数。' },
    { q: '语音面试需要什么设备？', a: '需要可用的麦克风与扬声器（或耳机），建议使用电脑上的 Chrome / Edge，或手机上的 Safari、Chrome；微信内置浏览器暂不支持，请在浏览器中打开。' },
    { q: '课程视频无法播放怎么办？', a: '可点击课程旁的"视频无法播放？"提交反馈，运营人员会尽快处理并通过站内信通知你。' },
    { q: '什么情况下课程会显示"已掌握"？', a: '在"按课程顺序练习"中完成该课程全部题目，且正确率达到 80%，即可标记为已掌握。' },
  ];
  DB.contact = { wechat: 'yunshu-kefu', email: 'help@yunshu.example.com', phone: '400-000-0000' };
  DB.sensitiveWords = ['违禁词', '赌博', '诈骗', '代考'];
  DB.agreement = { user: '《云枢智学用户协议》\n\n一、服务说明\n……\n二、用户行为规范\n……', privacy: '《云枢智学隐私政策》\n\n一、我们收集的信息：手机号、学习记录……\n二、麦克风权限：仅在语音面试时使用，录音转写为文字后即删除……\n三、数据保留：……' };

  window.DB = DB;
})();
