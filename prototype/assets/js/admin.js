/* ==========================================================
   云枢智学 管理后台原型
   需求依据：docs/需求文档.md 第 2.2、6、7、8 章
   ========================================================== */
(function () {
  const { $, $$, esc, toast, modal, confirm, go } = U;
  const CFG = DB.config;
  DB.courses.find((c) => c.id === 23).learners = 0;

  const A = { loggedIn: false, role: 'super', pwdWrong: {}, docRef: '' };
  const roleUser = { super: DB.sysUsers[0], content: DB.sysUsers[1], student: DB.sysUsers[3] };
  const me = () => roleUser[A.role];
  const addLog = (module, action, target) => DB.logs.unshift({ time: U.now(), user: me().name, role: DB.roleNames[A.role], module, action, target, ip: '10.0.3.21' });

  const course = (id) => DB.courses.find((c) => c.id === +id);
  const subject = (id) => DB.subjects.find((s) => s.id === +id);
  const platform = (id) => DB.platforms.find((p) => p.id === +id);
  const route = (id) => DB.routes.find((r) => r.id === +id);
  const routesOfCourse = (cid) => DB.routes.filter((r) => r.stages.some((s) => s.courses.includes(+cid)));
  const qCount = (cid) => DB.questions.filter((q) => q.courseId === +cid).length;
  const qOnCount = (cid) => DB.questions.filter((q) => q.courseId === +cid && q.status === 'on').length;
  const ivCount = (cid) => DB.interviewQuestions.filter((q) => q.courseId === +cid).length;
  const typeName = { judge: '判断题', single: '单选题', multi: '多选题', short: '简答题' };
  const letters = 'ABCDEFGH';
  const statusTag = (s) => (s === 'on' ? '<span class="tag tag-green">已上架</span>' : '<span class="tag">已下架</span>');
  const pending = () => DB.feedback.filter((f) => f.status === 'pending').length;

  /* 表格 */
  const table = (cols, rows, empty = '没有符合条件的数据') => `<div class="table-wrap"><table class="table"><thead><tr>${cols.map((c) => `<th>${c[0]}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map((r) => `<tr>${cols.map((c) => `<td${c[2] ? ` class="${c[2]}"` : ''}>${c[1](r)}</td>`).join('')}</tr>`).join('') : `<tr><td colspan="${cols.length}"><div class="empty">${empty}</div></td></tr>`}</tbody></table></div>
    <div class="pager">共 <span class="fig">${rows.length}</span> 条<span class="page fig" aria-current="page">1</span></div>`;

  /* 菜单与权限（需求 2.2） */
  const MENU = [
    { group: '', items: [['/workbench', '工作台', '', ['super', 'content', 'student']]] },
    { group: '内容管理', items: [['/subjects', '学科管理', '', ['super', 'content']], ['/courses', '课程库', '', ['super', 'content']], ['/routes', '学习路线', '', ['super', 'content']], ['/questions', '刷题题库', '', ['super', 'content']], ['/ivbank', '面试题库', '', ['super', 'content']], ['/feedback', '纠错与反馈', '', ['super', 'content']]] },
    { group: '学员与数据', items: [['/students', '学员管理', '', ['super', 'student']], ['/dashboard', '数据看板', '', ['super', 'student']]] },
    { group: '系统管理', items: [['/sysusers', '系统用户', '', ['super']], ['/roles', '角色管理', '', ['super']], ['/logs', '操作日志', '', ['super']], ['/config', '系统配置', '', ['super']]] },
  ];
  const allowed = (path) => { const it = MENU.flatMap((g) => g.items).find((i) => path.startsWith(i[0])); return !it || it[3].includes(A.role); };
  const titleOf = (path) => { const it = MENU.flatMap((g) => g.items).find((i) => path.startsWith(i[0])); return it ? it[1] : ''; };

  const app = $('#app');
  function shell() {
    const path = (location.hash.slice(1) || '/').split('?')[0];
    if (path === '/login') { app.innerHTML = '<div id="view"></div>'; return $('#view'); }
    app.innerHTML = `
      <aside class="a-side" id="aSide"><div class="a-brand"><span class="brand-lockup"><img src="assets/img/logo-reversed.png?v=2" alt="云枢智学" width="836" height="183"><span>管理后台</span></span><button type="button" class="menu-toggle" id="menuToggle" aria-label="打开菜单" aria-expanded="false">${U.icon('menu')}</button></div>
        <nav class="a-menu" aria-label="后台菜单">${MENU.map((g) => { const its = g.items.filter((i) => i[3].includes(A.role)); return its.length ? `${g.group ? `<div class="a-group">${g.group}</div>` : ''}${its.map((i) => `<a href="#${i[0]}" class="${path.startsWith(i[0]) ? 'active' : ''}" ${path.startsWith(i[0]) ? 'aria-current="page"' : ''}>${i[1]}${i[0] === '/feedback' && pending() ? `<span class="count" aria-label="${pending()} 条待处理">${pending()}</span>` : ''}</a>`).join('')}` : ''; }).join('')}</nav></aside>
      <div class="a-main"><header class="a-top"><span class="crumb">${titleOf(path)}</span>
        <div class="right"><label class="role-switch"><span>演示角色</span><select class="select" id="roleSel">${Object.entries(DB.roleNames).map(([k, v]) => `<option value="${k}" ${k === A.role ? 'selected' : ''}>${v}</option>`).join('')}</select></label>
          <span class="small text-2">${esc(me().name)}</span><a href="student.html" target="_blank" rel="noopener" class="small">打开学员端</a><button type="button" class="btn btn-sm" id="logout">退出</button></div></header>
        <main class="a-content" id="view"></main></div>`;
    $('#roleSel').onchange = (e) => { A.role = e.target.value; toast('已切换为' + DB.roleNames[A.role]); go(allowed(path) ? path : '/workbench'); };
    $('#logout').onclick = () => { A.loggedIn = false; go('/login'); };
    $('#menuToggle').onclick = (e) => { const open = $('#aSide').classList.toggle('open'); e.currentTarget.setAttribute('aria-expanded', open); };
    return $('#view');
  }
  const refTag = (t) => `<span class="proto-ref">需求 ${t}</span>`;

  /* ---------------- 登录（需求 6.9） ---------------- */
  function pageLogin(el) {
    el.innerHTML = `<div class="login-wrap">
      <section class="login-side"><img class="login-logo" src="assets/img/logo-reversed.png?v=2" alt="云枢智学" width="836" height="183">
        <div><h1>管理后台</h1><p>维护学习路线、课程和题库，处理学员反馈，查看学习与面试数据。</p></div><p class="small">页面原型，数据为模拟数据</p></section>
      <main class="login-main"><div class="login-card"><h2>登录</h2><p class="muted mt-4 mb-16">使用后台账号和密码登录</p>
      <div class="form-item"><label for="acc">账号</label><input class="input" id="acc" name="username" autocomplete="username" value="admin"></div>
      <div class="form-item"><label for="pwd">密码</label><input class="input" id="pwd" name="password" type="password" autocomplete="current-password" value="123456"></div>
      <div class="form-error mb-8" id="err" role="alert"></div>
      <button type="button" class="btn btn-primary btn-block btn-lg" id="go">登录</button>
      <div class="panel small text-2"><p>演示账号的密码都是 123456。</p><dl class="detail-dl"><div><dt>admin</dt><dd>超级管理员</dd></div><div><dt>content01</dt><dd>内容运营</dd></div><div><dt>ops01</dt><dd>学员运营</dd></div><div><dt>content02</dt><dd>已停用</dd></div></dl><p class="mt-12 muted">密码连续输错 5 次锁定 15 分钟，8 小时无操作自动退出。</p></div></div></main></div>`;
    const submit = () => {
      const acc = $('#acc').value.trim(), pwd = $('#pwd').value;
      const u = DB.sysUsers.find((x) => x.account === acc);
      const err = (t) => ($('#err').textContent = t);
      if (!u) return err('账号不存在');
      if ((A.pwdWrong[acc] || 0) >= 5) return err('密码连续输错 5 次，账号已锁定 15 分钟，可联系超级管理员重置密码');
      if (pwd !== '123456') { A.pwdWrong[acc] = (A.pwdWrong[acc] || 0) + 1; return err(A.pwdWrong[acc] >= 5 ? '密码连续输错 5 次，账号已锁定 15 分钟，可联系超级管理员重置密码' : `密码错误，还可尝试 ${5 - A.pwdWrong[acc]} 次`); }
      if (u.status === 'off') return err('该账号已停用，无法登录后台');
      A.loggedIn = true; A.role = u.role; roleUser[u.role] = u;
      addLog('登录', '后台登录', '账号 ' + acc);
      toast('登录成功', 'success'); go('/workbench');
    };
    $('#go').onclick = submit;
    $('#pwd').onkeydown = (e) => e.key === 'Enter' && submit();
  }

  /* ---------------- 工作台（需求 6.12） ---------------- */
  function pageWorkbench(el) {
    const qShort = DB.routes.flatMap((r) => r.stages).filter((s) => { const n = s.courses.map(course).filter((c) => c.status === 'on').reduce((a, c) => a + qOnCount(c.id), 0); const iv = s.courses.reduce((a, c) => a + ivCount(c), 0); return n < CFG.examMin || iv < 1; });
    const content = [
      ['待处理的题目纠错', DB.feedback.filter((f) => f.type === 'question' && f.status === 'pending').length, '条', '/feedback?type=question'],
      ['待处理的视频失效反馈', DB.feedback.filter((f) => f.type === 'video' && f.status === 'pending').length, '条', '/feedback?type=video'],
      ['未配题的课程', DB.courses.filter((c) => c.status === 'on' && !qOnCount(c.id)).length, '门', '/courses?conf=no'],
      ['题量不足的阶段', qShort.length, '个', '/routes'],
    ];
    const student = [['今日新增注册', 86, '人', '/dashboard'], ['今日活跃学员', 1204, '人', '/dashboard'], ['今日面试场次', 312, '场', '/dashboard']];
    const tiles = A.role === 'content' ? content : A.role === 'student' ? student : [...content, ...student];
    el.innerHTML = `<div class="a-title"><div><h2>工作台</h2><p>你好，${esc(me().name)}。这里是你的待办和今天的数据，点击进入处理。</p></div>${refTag('6.12 工作台')}</div>
      <div class="kpis">${tiles.map((t) => `<button type="button" class="kpi" data-go="${t[3]}"><div class="label">${t[0]}</div><div class="value">${t[1].toLocaleString()}<span class="small muted"> ${t[2]}</span></div></button>`).join('')}</div>
      ${A.role !== 'content' ? '<p class="sample-note mt-8">学员数据为原型示例数据。</p>' : ''}
      ${A.role !== 'student' ? `<section class="card mt-24" aria-labelledby="wbFb"><div class="card-head"><h3 id="wbFb">最新待处理反馈</h3><a href="#/feedback">查看全部</a></div>${table([['类型', (f) => (f.type === 'video' ? '<span class="tag tag-orange">视频失效</span>' : '<span class="tag tag-primary">题目纠错</span>')], ['对象', (f) => esc(f.target)], ['提交次数', (f) => `<span class="fig">${f.count}</span>`], ['时间', (f) => `<span class="fig">${f.time}</span>`]], DB.feedback.filter((f) => f.status === 'pending').slice(0, 4), '没有待处理的反馈')}</section>` : ''}`;
    $$('[data-go]', el).forEach((k) => (k.onclick = () => go(k.dataset.go)));
  }

  /* ---------------- 学科管理（需求 6.1） ---------------- */
  function pageSubjects(el) {
    el.innerHTML = `<div class="a-title"><h2>学科管理</h2><div class="row">${refTag('6.1 学科管理')}<button type="button" class="btn btn-primary" id="add">+ 新增学科</button></div></div>
      ${table([['排序', (s) => s.sort], ['学科名称', (s) => `<b>${esc(s.name)}</b>`], ['简介', (s) => esc(s.desc)], ['课程数', (s) => DB.courses.filter((c) => c.subjectId === s.id).length], ['操作', (s) => `<button type="button" class="btn-link" data-ed="${s.id}">编辑</button><button type="button" class="btn-link danger" data-del="${s.id}">删除</button>`, 'ops']], DB.subjects.slice().sort((a, b) => a.sort - b.sort))}`;
    const form = (s = {}) => modal({ title: s.id ? '编辑学科' : '新增学科', body: `<div class="form-item"><label><span class="req">*</span>学科名称</label><input class="input" id="n" value="${esc(s.name || '')}"></div><div class="form-item"><label>简介</label><textarea class="textarea" id="d">${esc(s.desc || '')}</textarea></div><div class="form-item"><label>排序号</label><input class="input" id="o" type="number" value="${s.sort || DB.subjects.length + 1}"></div>`,
      footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, r) => {
        const n = $('#n', r).value.trim(); if (!n) return toast('请填写学科名称', 'error');
        if (DB.subjects.some((x) => x.name === n && x.id !== s.id)) return toast('学科名称已存在', 'error');
        if (s.id) Object.assign(s, { name: n, desc: $('#d', r).value, sort: +$('#o', r).value }); else DB.subjects.push({ id: U.uid(), name: n, desc: $('#d', r).value, sort: +$('#o', r).value });
        addLog('学科管理', s.id ? '编辑' : '新增', '学科 ' + n); c(); toast('已保存', 'success'); pageSubjects(el);
      } }] });
    $('#add').onclick = () => form();
    $$('[data-ed]', el).forEach((b) => (b.onclick = () => form(subject(b.dataset.ed))));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => {
      const s = subject(b.dataset.del), n = DB.courses.filter((c) => c.subjectId === s.id).length;
      if (n) return modal({ title: '无法删除', body: `<div class="alert alert-warn">学科「${esc(s.name)}」下仍有 ${n} 门课程，不允许删除。</div>` });
      confirm('删除学科', `确定删除「${esc(s.name)}」吗？`, () => { DB.subjects = DB.subjects.filter((x) => x !== s); addLog('学科管理', '删除', '学科 ' + s.name); toast('已删除', 'success'); pageSubjects(el); }, '删除', true);
    }));
  }

  /* ---------------- 通用：批量导入 ---------------- */
  function importModal(title, tpl, fields, onDone) {
    let picked = false;
    const m = modal({ title, width: 560, body: '', footer: [{ text: '取消' }, { text: '开始导入', type: 'primary', onClick: (c, r) => {
      if (!picked) return toast('请先选择文件', 'error');
      $('.modal-body', r).innerHTML = '<div class="row"><span class="spinner"></span>正在导入…</div>';
      setTimeout(() => {
        $('.modal-body', r).innerHTML = `<div class="alert alert-success">导入完成：成功 <b>18</b> 条，失败 <b>2</b> 条</div>
          <div class="mt-12">${table([['行号', (x) => x[0]], ['失败原因', (x) => x[1]]], [[5, '必填字段为空：' + fields[0]], [12, fields[1]]])}</div><button type="button" class="btn btn-sm mt-8" onclick="U.toast('原型：下载失败明细')">下载失败明细</button>`;
        $('.modal-foot', r).innerHTML = '<button type="button" class="btn btn-primary" id="ok">完成</button>';
        $('#ok', r).onclick = () => { c(); onDone && onDone(18); };
      }, 1000);
    } }] });
    $('.modal-body', m.root).innerHTML = `<ol class="text-2" style="padding-left:var(--space-md);margin-top:0"><li>下载导入模板：<a onclick="U.toast('原型：下载 ${tpl}')">${tpl}</a></li><li>按模板填写后上传（.xlsx）</li></ol>
      <button type="button" class="upload mt-12" id="up">点击选择文件，或把文件拖到这里</button>`;
    $('#up', m.root).onclick = () => { picked = true; $('#up', m.root).textContent = '已选择 导入数据.xlsx，共 20 行'; $('#up', m.root).style.borderStyle = 'solid'; };
  }

  /* ---------------- 课程库（需求 6.2） ---------------- */
  function pageCourses(el, _, q) {
    const f = { subject: q.subject || '', status: q.status || '', conf: q.conf || '', kw: q.kw || '' };
    const rows = DB.courses.filter((c) => (!f.subject || c.subjectId === +f.subject) && (!f.status || c.status === f.status) && (!f.conf || (f.conf === 'yes') === (qOnCount(c.id) > 0)) && (!f.kw || c.title.includes(f.kw)));
    el.innerHTML = `<div class="a-title"><h2>课程库</h2><div class="row">${refTag('6.2 课程库，8.7 删除规则')}<button type="button" class="btn" id="imp">批量导入</button><button type="button" class="btn btn-primary" id="add">+ 新增课程</button></div></div>
      <div class="filter-bar"><input class="input" id="kw" placeholder="搜索课程名称" value="${esc(f.kw)}">
        <select class="select" data-f="subject"><option value="">全部学科</option>${DB.subjects.map((s) => `<option value="${s.id}" ${+f.subject === s.id ? 'selected' : ''}>${s.name}</option>`).join('')}</select>
        <select class="select" data-f="status"><option value="">全部状态</option><option value="on" ${f.status === 'on' ? 'selected' : ''}>已上架</option><option value="off" ${f.status === 'off' ? 'selected' : ''}>已下架</option></select>
        <select class="select" data-f="conf"><option value="">是否已配题</option><option value="yes" ${f.conf === 'yes' ? 'selected' : ''}>已配题</option><option value="no" ${f.conf === 'no' ? 'selected' : ''}>未配题</option></select>
        <button type="button" class="btn btn-sm btn-primary" id="search">查询</button></div>
      ${table([['课程名称', (c) => `<b>${esc(c.title)}</b><div class="small muted">${c.duration}</div>`], ['学科', (c) => subject(c.subjectId).name], ['视频平台', (c) => `<span class="tag tag-teal">${platform(c.platformId).name}</span>`], ['刷题数', (c) => (qOnCount(c.id) ? qOnCount(c.id) : '<span class="tag tag-orange">未配题</span>')], ['面试题数', (c) => ivCount(c.id)], ['引用路线数', (c) => routesOfCourse(c.id).length], ['状态', (c) => statusTag(c.status)],
        ['操作', (c) => `<button type="button" class="btn-link" data-ed="${c.id}">编辑</button><button type="button" class="btn-link" data-tg="${c.id}">${c.status === 'on' ? '下架' : '上架'}</button><button type="button" class="btn-link danger" data-del="${c.id}">删除</button>`, 'ops']], rows)}`;
    const apply = (k, v) => { const nq = { ...f, [k]: v }; go('/courses?' + new URLSearchParams(Object.entries(nq).filter(([, x]) => x)).toString()); };
    $$('[data-f]', el).forEach((s) => (s.onchange = () => apply(s.dataset.f, s.value)));
    $('#search').onclick = () => apply('kw', $('#kw').value.trim());
    $('#imp').onclick = () => importModal('批量导入课程', '课程导入模板.xlsx', ['课程名称', '视频链接域名不属于所选平台（youtube.com）'], (n) => { addLog('课程库', '批量导入', `成功 ${n} 条，失败 2 条`); toast('导入完成', 'success'); });
    $('#add').onclick = () => courseForm();
    $$('[data-ed]', el).forEach((b) => (b.onclick = () => courseForm(course(b.dataset.ed))));
    $$('[data-tg]', el).forEach((b) => (b.onclick = () => {
      const c = course(b.dataset.tg);
      const off = c.status === 'on';
      confirm(off ? '下架课程' : '上架课程', off ? `下架后：引用它的路线中不再显示该课程，也不计入进度；关联的刷题和面试题目停用；学员的历史记录和错题保留。重新上架后全部恢复。` : '上架后课程将在引用它的路线中显示。', () => { c.status = off ? 'off' : 'on'; addLog('课程库', off ? '下架' : '上架', '课程 ' + c.title); toast('操作成功', 'success'); pageCourses(el, _, q); }, off ? '下架' : '上架');
    }));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => {
      const c = course(b.dataset.del), refs = routesOfCourse(c.id), reasons = [];
      if (refs.length) reasons.push(`被 ${refs.length} 条路线引用：${refs.map((r) => r.name).join('、')}`);
      if (qCount(c.id) || ivCount(c.id)) reasons.push(`下面有刷题题目 ${qCount(c.id)} 道、面试题目 ${ivCount(c.id)} 道`);
      if (c.learners) reasons.push(`已有 ${c.learners} 名学员的学习记录`);
      if (reasons.length) return modal({ title: '无法删除，只能下架', body: `<div class="alert alert-warn">课程需同时满足"未被路线引用、下面没有题目、没有学员学习记录"才能删除。</div><ul class="small text-2 mt-12" style="padding-left:18px">${reasons.map((r) => `<li>${esc(r)}</li>`).join('')}</ul>` });
      confirm('删除课程', `确定删除「${esc(c.title)}」吗？删除后不可恢复。`, () => { DB.courses = DB.courses.filter((x) => x !== c); addLog('课程库', '删除', '课程 ' + c.title); toast('已删除', 'success'); pageCourses(el, _, q); }, '删除', true);
    }));
    function courseForm(c = {}) {
      const plats = DB.platforms.filter((p) => p.enabled || p.id === c.platformId);
      modal({ title: c.id ? '编辑课程' : '新增课程', width: 560, body: `
        <div class="form-item"><label><span class="req">*</span>课程名称</label><input class="input" id="t" value="${esc(c.title || '')}"></div>
        <div class="grid grid-2" style="column-gap:var(--space-md);row-gap:0"><div class="form-item"><label><span class="req">*</span>所属学科</label><select class="select" id="s">${DB.subjects.map((s) => `<option value="${s.id}" ${s.id === c.subjectId ? 'selected' : ''}>${s.name}</option>`).join('')}</select></div>
          <div class="form-item"><label><span class="req">*</span>视频平台</label><select class="select" id="p">${plats.map((p) => `<option value="${p.id}" ${p.id === c.platformId ? 'selected' : ''}>${p.name}</option>`).join('')}</select><div class="form-hint">平台列表在「系统配置，视频平台」中维护</div></div></div>
        <div class="form-item"><label><span class="req">*</span>视频链接</label><input class="input" id="u" value="${esc(c.url || '')}" placeholder="https://www.bilibili.com/video/..."><div class="form-hint" id="uh">可以是单个视频，也可以是合集或多 P 视频的入口；域名需属于所选平台</div></div>
        <div class="form-item"><label>简介</label><textarea class="textarea" id="d">${esc(c.desc || '')}</textarea></div>
        <div class="grid grid-3" style="column-gap:var(--space-md);row-gap:0"><div class="form-item"><label>封面</label><button type="button" class="upload" onclick="this.textContent='已选择 cover.png'">上传封面</button></div><div class="form-item"><label>时长</label><input class="input" id="du" value="${esc(c.duration || '')}" placeholder="如 6 小时"></div><div class="form-item"><label>排序号</label><input class="input" type="number" value="${c.id || DB.courses.length + 1}"></div></div>`,
        footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (close, r) => {
          const t = $('#t', r).value.trim(), u = $('#u', r).value.trim(), p = platform($('#p', r).value);
          if (!t) return toast('请填写课程名称', 'error');
          let host = ''; try { host = new URL(u).hostname; } catch (e) { return toast('请填写正确的视频链接', 'error'); }
          if (!p.domains.some((d) => host === d || host.endsWith('.' + d))) { $('#u', r).classList.add('error'); $('#uh', r).innerHTML = `<span class="form-error">链接域名 ${esc(host)} 不属于「${p.name}」（允许：${p.domains.join('、')}）</span>`; return; }
          const data = { title: t, subjectId: +$('#s', r).value, platformId: p.id, url: u, desc: $('#d', r).value, duration: $('#du', r).value || '—' };
          if (c.id) Object.assign(c, data); else DB.courses.push({ id: U.uid(), q: 0, iq: 0, status: 'on', learners: 0, ...data });
          addLog('课程库', c.id ? '编辑' : '新增', '课程 ' + t); close(); toast('已保存', 'success'); pageCourses(el, _, q);
        } }] });
    }
  }

  /* ---------------- 学习路线（需求 6.3） ---------------- */
  function pageRoutes(el) {
    el.innerHTML = `<div class="a-title"><h2>学习路线</h2><div class="row">${refTag('6.3 学习路线管理')}<button type="button" class="btn btn-primary" id="add">+ 新增路线</button></div></div>
      ${table([['路线名称', (r) => `<b>${esc(r.name)}</b>`], ['阶段数', (r) => r.stages.length], ['课程数', (r) => r.stages.reduce((n, s) => n + s.courses.length, 0)], ['学习人数', (r) => r.learners.toLocaleString()], ['状态', (r) => statusTag(r.status)],
        ['操作', (r) => `<a class="btn-link" href="#/routes/${r.id}">编辑阶段与课程</a><a class="btn-link" href="student.html#/route/${r.id}" target="_blank" rel="noopener">预览</a><button type="button" class="btn-link" data-tg="${r.id}">${r.status === 'on' ? '下架' : '上架'}</button><button type="button" class="btn-link danger" data-del="${r.id}">删除</button>`, 'ops']], DB.routes)}`;
    $('#add').onclick = () => { const r = { id: U.uid(), name: '新学习路线', desc: '', status: 'off', learners: 0, stages: [] }; DB.routes.push(r); addLog('学习路线', '新增', r.name); go('/routes/' + r.id); };
    $$('[data-tg]', el).forEach((b) => (b.onclick = () => { const r = route(b.dataset.tg); r.status = r.status === 'on' ? 'off' : 'on'; addLog('学习路线', r.status === 'on' ? '上架' : '下架', r.name); toast('操作成功', 'success'); pageRoutes(el); }));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => {
      const r = route(b.dataset.del);
      if (r.learners) return modal({ title: '无法删除，只能下架', body: `<div class="alert alert-warn">路线「${esc(r.name)}」已有 ${r.learners} 名学员的学习记录（打开过课程、标记已学或作答过题目），不能删除，只能下架。</div>` });
      confirm('删除路线', `确定删除「${esc(r.name)}」吗？`, () => { DB.routes = DB.routes.filter((x) => x !== r); addLog('学习路线', '删除', r.name); toast('已删除', 'success'); pageRoutes(el); }, '删除', true);
    }));
  }
  function pageRouteEdit(el, { id }) {
    const r = route(id);
    if (!r) return (el.innerHTML = '<div class="empty">路线不存在</div>');
    const draw = () => {
      el.innerHTML = `<a class="small muted" href="#/routes">返回学习路线</a>
        <div class="a-title mt-8"><h2>编辑路线：${esc(r.name)}</h2><div class="row">${refTag('6.3，同一路线内一门课程只能出现在一个阶段')}<a class="btn" href="student.html#/route/${r.id}" target="_blank" rel="noopener">在学员端预览</a><button type="button" class="btn btn-primary" id="save">保存</button></div></div>
        <div class="card mb-16"><div class="grid grid-2" style="column-gap:var(--space-md);row-gap:0"><div class="form-item"><label><span class="req">*</span>路线名称</label><input class="input" id="rn" value="${esc(r.name)}"></div><div class="form-item"><label>排序号</label><input class="input" type="number" value="${r.id}"></div></div>
          <div class="form-item"><label>简介</label><textarea class="textarea" id="rd">${esc(r.desc)}</textarea></div><div class="form-item" style="margin:0"><label>封面</label><button type="button" class="upload" style="max-width:260px" onclick="this.textContent='已选择 route-cover.png'">上传封面</button></div></div>
        <div class="row-between mb-12"><h3>阶段与课程</h3><button type="button" class="btn" id="addStage">+ 新增阶段</button></div>
        ${r.stages.map((s, i) => `<div class="stage-box"><div class="stage-box-head"><input class="input" data-sn="${i}" value="${esc(s.name)}"><span class="small muted">${s.courses.length} 门课程</span><span class="grow"></span>
            <button type="button" class="btn btn-sm" data-sup="${i}" ${i === 0 ? 'disabled' : ''}>上移</button><button type="button" class="btn btn-sm" data-sdown="${i}" ${i === r.stages.length - 1 ? 'disabled' : ''}>下移</button><button type="button" class="btn btn-sm btn-primary" data-pick="${i}">+ 引用课程</button><button type="button" class="btn btn-sm btn-danger" data-sdel="${i}">删除阶段</button></div>
          ${s.courses.map((cid, j) => { const c = course(cid); return `<div class="ref-course"><span class="no">${String(j + 1).padStart(2, '0')}</span><b class="grow">${esc(c.title)}</b><span class="small muted">${subject(c.subjectId).name}</span>${c.status === 'off' ? '<span class="tag">已下架</span>' : ''}${qOnCount(cid) ? `<span class="small muted">刷题 ${qOnCount(cid)}</span>` : '<span class="tag tag-orange">未配题</span>'}<button type="button" class="btn-link" data-cup="${i}-${j}" ${j === 0 ? 'disabled' : ''}>↑</button><button type="button" class="btn-link" data-cdown="${i}-${j}" ${j === s.courses.length - 1 ? 'disabled' : ''}>↓</button><button type="button" class="btn-link danger" data-crm="${i}-${j}">移除</button></div>`; }).join('') || '<div class="empty" style="padding:20px">暂无课程，点击"引用课程"从课程库选择</div>'}</div>`).join('') || '<div class="card empty">还没有阶段</div>'}`;
      $('#save').onclick = () => { r.name = $('#rn').value.trim() || r.name; r.desc = $('#rd').value; $$('[data-sn]', el).forEach((i) => (r.stages[+i.dataset.sn].name = i.value)); addLog('学习路线', '编辑', r.name); toast('已保存', 'success'); };
      $('#addStage').onclick = () => { r.stages.push({ id: U.uid(), name: `阶段${'一二三四五六七八九十'[r.stages.length] || r.stages.length + 1}，新阶段`, courses: [] }); draw(); };
      const swap = (arr, a, b) => ([arr[a], arr[b]] = [arr[b], arr[a]]);
      $$('[data-sup]', el).forEach((b) => (b.onclick = () => { swap(r.stages, +b.dataset.sup, +b.dataset.sup - 1); draw(); }));
      $$('[data-sdown]', el).forEach((b) => (b.onclick = () => { swap(r.stages, +b.dataset.sdown, +b.dataset.sdown + 1); draw(); }));
      $$('[data-sdel]', el).forEach((b) => (b.onclick = () => {
        const s = r.stages[+b.dataset.sdel];
        if (s.courses.length && r.learners) return modal({ title: '无法删除，只能下架', body: `<div class="alert alert-warn">阶段「${esc(s.name)}」已有学员学习记录，不能删除。可将整条路线下架。</div>` });
        confirm('删除阶段', `确定删除「${esc(s.name)}」吗？`, () => { r.stages.splice(+b.dataset.sdel, 1); draw(); }, '删除', true);
      }));
      $$('[data-cup]', el).forEach((b) => (b.onclick = () => { const [i, j] = b.dataset.cup.split('-').map(Number); swap(r.stages[i].courses, j, j - 1); draw(); }));
      $$('[data-cdown]', el).forEach((b) => (b.onclick = () => { const [i, j] = b.dataset.cdown.split('-').map(Number); swap(r.stages[i].courses, j, j + 1); draw(); }));
      $$('[data-crm]', el).forEach((b) => (b.onclick = () => { const [i, j] = b.dataset.crm.split('-').map(Number); r.stages[i].courses.splice(j, 1); draw(); }));
      $$('[data-pick]', el).forEach((b) => (b.onclick = () => picker(+b.dataset.pick)));
    };
    const picker = (si) => {
      const inOther = (cid) => r.stages.findIndex((s, i) => i !== si && s.courses.includes(cid));
      let kw = '';
      const m = modal({ title: `引用课程到「${esc(r.stages[si].name)}」`, width: 600, footer: [{ text: '取消' }, { text: '确定', type: 'primary', onClick: (c, root) => { const ids = $$('input[data-c]:checked', root).map((x) => +x.dataset.c); r.stages[si].courses = [...r.stages[si].courses.filter((x) => ids.includes(x) || !$(`input[data-c="${x}"]`, root)), ...ids.filter((x) => !r.stages[si].courses.includes(x))]; c(); draw(); } }] });
      const list = () => {
        $('#plist', m.root).innerHTML = DB.courses.filter((c) => c.title.includes(kw)).map((c) => { const o = inOther(c.id); return `<label class="ref-course" style="${o >= 0 ? 'opacity:.5' : ''}"><input type="checkbox" data-c="${c.id}" ${r.stages[si].courses.includes(c.id) ? 'checked' : ''} ${o >= 0 ? 'disabled' : ''}><span class="grow">${esc(c.title)}</span><span class="small muted">${subject(c.subjectId).name}</span>${o >= 0 ? `<span class="tag">已在「${esc(r.stages[o].name)}」</span>` : ''}${c.status === 'off' ? '<span class="tag">已下架</span>' : ''}</label>`; }).join('');
      };
      $('.modal-body', m.root).innerHTML = `<input class="input mb-12" id="pkw" placeholder="搜索课程"><p class="small muted mb-8">同一条路线内，已被其他阶段引用的课程不能重复引用</p><div id="plist" style="max-height:360px;overflow-y:auto;border:1px solid var(--border);border-radius:8px"></div>`;
      $('#pkw', m.root).oninput = (e) => { kw = e.target.value.trim(); list(); };
      list();
    };
    draw();
  }

  /* ---------------- 刷题题库（需求 6.4） ---------------- */
  function pageQuestions(el, _, q) {
    const f = { subject: q.subject || '', course: q.course || '', type: q.type || '', status: q.status || '' };
    const rows = DB.questions.filter((x) => (!f.course || x.courseId === +f.course) && (!f.subject || course(x.courseId).subjectId === +f.subject) && (!f.type || x.type === f.type) && (!f.status || x.status === f.status)).slice(0, 40);
    const total = DB.questions.filter((x) => (!f.course || x.courseId === +f.course) && (!f.subject || course(x.courseId).subjectId === +f.subject) && (!f.type || x.type === f.type) && (!f.status || x.status === f.status)).length;
    el.innerHTML = `<div class="a-title"><h2>刷题题库</h2><div class="row">${refTag('6.4 刷题题库，8.7 删除与修改规则')}<button type="button" class="btn" id="imp">批量导入</button><button type="button" class="btn btn-primary" id="add">+ 新增题目</button></div></div>
      <div class="filter-bar"><select class="select" data-f="subject"><option value="">全部学科</option>${DB.subjects.map((s) => `<option value="${s.id}" ${+f.subject === s.id ? 'selected' : ''}>${s.name}</option>`).join('')}</select>
        <select class="select" data-f="course"><option value="">全部课程</option>${DB.courses.filter((c) => !f.subject || c.subjectId === +f.subject).map((c) => `<option value="${c.id}" ${+f.course === c.id ? 'selected' : ''}>${c.title}</option>`).join('')}</select>
        <select class="select" data-f="type"><option value="">全部题型</option>${Object.entries(typeName).map(([k, v]) => `<option value="${k}" ${f.type === k ? 'selected' : ''}>${v}</option>`).join('')}</select>
        <select class="select" data-f="status"><option value="">全部状态</option><option value="on" ${f.status === 'on' ? 'selected' : ''}>已上架</option><option value="off" ${f.status === 'off' ? 'selected' : ''}>已下架</option></select>
        <span class="small muted">共 ${total} 道${total > 40 ? '（原型仅展示前 40 道）' : ''}</span></div>
      ${table([['题干', (x) => `<div style="max-width:420px">${esc(x.stem)}</div>`], ['题型', (x) => `<span class="tag tag-primary">${typeName[x.type]}</span>`], ['所属课程', (x) => esc(course(x.courseId).title)], ['排序', (x) => x.sort], ['作答记录', (x) => (x.answered ? '有' : '<span class="muted">无</span>')], ['状态', (x) => statusTag(x.status)],
        ['操作', (x) => `<button type="button" class="btn-link" data-ed="${x.id}">编辑</button><button type="button" class="btn-link" data-tg="${x.id}">${x.status === 'on' ? '下架' : '上架'}</button><button type="button" class="btn-link danger" data-del="${x.id}">删除</button>`, 'ops']], rows)}`;
    const apply = (k, v) => { const nq = { ...f, [k]: v }; if (k === 'subject') nq.course = ''; go('/questions?' + new URLSearchParams(Object.entries(nq).filter(([, x]) => x)).toString()); };
    $$('[data-f]', el).forEach((s) => (s.onchange = () => apply(s.dataset.f, s.value)));
    $('#imp').onclick = () => importModal('批量导入刷题题目', '刷题题目导入模板.xlsx', ['题干', '多选题正确选项少于 2 个'], (n) => { addLog('刷题题库', '批量导入', `成功 ${n} 条，失败 2 条`); toast('导入完成', 'success'); });
    $('#add').onclick = () => qForm();
    $$('[data-ed]', el).forEach((b) => (b.onclick = () => qForm(DB.questions.find((x) => x.id === +b.dataset.ed))));
    $$('[data-tg]', el).forEach((b) => (b.onclick = () => { const x = DB.questions.find((y) => y.id === +b.dataset.tg); x.status = x.status === 'on' ? 'off' : 'on'; addLog('刷题题库', x.status === 'on' ? '上架' : '下架', x.stem.slice(0, 20)); toast('操作成功', 'success'); pageQuestions(el, _, q); }));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => {
      const x = DB.questions.find((y) => y.id === +b.dataset.del);
      if (x.answered) return modal({ title: '无法删除，只能下架', body: '<div class="alert alert-warn">该题目已被学员作答过，不能删除，只能下架。下架后错题本中仍保留该题（可查看、不能重做）。</div>' });
      confirm('删除题目', '确定删除该题目吗？', () => { DB.questions = DB.questions.filter((y) => y !== x); addLog('刷题题库', '删除', x.stem.slice(0, 20)); toast('已删除', 'success'); pageQuestions(el, _, q); }, '删除', true);
    }));
    function qForm(x = { type: 'single', options: ['', '', '', ''], answer: 0, courseId: +f.course || 1 }) {
      const st = { type: x.type, options: x.options ? [...x.options] : ['', '', '', ''], answer: Array.isArray(x.answer) ? [...x.answer] : x.answer };
      const m = modal({ title: x.id ? '编辑题目' : '新增题目', width: 620, footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, r) => {
        const stem = $('#stem', r).value.trim(); if (!stem) return toast('请填写题干', 'error');
        if (['single', 'multi'].includes(st.type)) {
          st.options = $$('[data-o]', r).map((i) => i.value.trim());
          if (st.options.some((o) => !o)) return toast('选项内容不能为空', 'error');
          if (st.type === 'multi' && (!Array.isArray(st.answer) || st.answer.length < 2)) return toast('多选题至少需要 2 个正确选项', 'error');
        }
        if (st.type === 'short' && !$('#ref', r).value.trim()) return toast('请填写参考答案', 'error');
        const data = { courseId: +$('#qc', r).value, type: st.type, stem, analysis: $('#ana', r).value, options: ['single', 'multi'].includes(st.type) ? st.options : undefined, answer: st.answer, ref: st.type === 'short' ? $('#ref', r).value : undefined, points: st.type === 'short' ? $('#pts', r).value.split(/[,，、\n]/).filter(Boolean) : undefined };
        if (x.id) Object.assign(x, data); else DB.questions.unshift({ id: U.uid(), sort: 99, status: 'on', answered: false, ...data });
        addLog('刷题题库', x.id ? '编辑' : '新增', stem.slice(0, 20)); c(); toast(x.id ? '已保存。学员历史作答保留快照，错题本和之后的作答使用新版本' : '已保存', 'success'); pageQuestions(el, _, q);
      } }] });
      const draw = () => {
        const body = $('.modal-body', m.root);
        const keep = { stem: $('#stem', body) ? $('#stem', body).value : x.stem || '', ana: $('#ana', body) ? $('#ana', body).value : x.analysis || '' };
        body.innerHTML = `<div class="grid grid-2" style="column-gap:var(--space-md);row-gap:0"><div class="form-item"><label><span class="req">*</span>所属课程</label><select class="select" id="qc">${DB.courses.map((c) => `<option value="${c.id}" ${c.id === x.courseId ? 'selected' : ''}>${c.title}</option>`).join('')}</select></div>
          <div class="form-item"><label><span class="req">*</span>题型</label><select class="select" id="qt" ${x.id ? 'disabled' : ''}>${Object.entries(typeName).map(([k, v]) => `<option value="${k}" ${st.type === k ? 'selected' : ''}>${v}</option>`).join('')}</select></div></div>
          <div class="form-item"><label><span class="req">*</span>题干</label><textarea class="textarea" id="stem" style="min-height:64px">${esc(keep.stem)}</textarea></div>
          ${st.type === 'judge' ? `<div class="form-item"><label><span class="req">*</span>正确答案</label><div class="radio-group"><label class="check"><input type="radio" name="ja" value="1" ${st.answer !== false ? 'checked' : ''}>对</label><label class="check"><input type="radio" name="ja" value="0" ${st.answer === false ? 'checked' : ''}>错</label></div></div>` : ''}
          ${['single', 'multi'].includes(st.type) ? `<div class="form-item"><label><span class="req">*</span>选项（2~8 个，勾选正确选项${st.type === 'multi' ? '，至少 2 个' : '，仅 1 个'}）</label>${st.options.map((o, i) => `<div class="opt-edit"><span class="k">${letters[i]}</span><input class="input" data-o="${i}" value="${esc(o)}"><label class="check"><input type="${st.type === 'single' ? 'radio' : 'checkbox'}" name="ca" data-ca="${i}" ${(st.type === 'single' ? st.answer === i : (st.answer || []).includes(i)) ? 'checked' : ''}>正确</label>${st.options.length > 2 ? `<button type="button" class="btn-link danger" data-orm="${i}">删除</button>` : ''}</div>`).join('')}${st.options.length < 8 ? '<button type="button" class="btn btn-sm" id="oadd">+ 添加选项</button>' : ''}</div>` : ''}
          ${st.type === 'short' ? `<div class="form-item"><label><span class="req">*</span>参考答案</label><textarea class="textarea" id="ref">${esc(x.ref || '')}</textarea></div><div class="form-item"><label>评分要点（供 AI 评分使用，用顿号分隔）</label><textarea class="textarea" id="pts" style="min-height:64px">${esc((x.points || []).join('、'))}</textarea></div>` : ''}
          <div class="form-item"><label>解析</label><textarea class="textarea" id="ana" style="min-height:64px">${esc(keep.ana)}</textarea></div>
          ${x.id ? '<div class="alert alert-info small">修改后，学员的历史作答记录和成绩保留作答时的快照，不重新判定；错题本和之后的作答使用最新版本。</div>' : ''}`;
        $('#qt', body).onchange = (e) => { st.type = e.target.value; st.answer = st.type === 'multi' ? [] : st.type === 'judge' ? true : 0; draw(); };
        $$('[data-o]', body).forEach((i) => (i.oninput = () => (st.options[+i.dataset.o] = i.value)));
        $$('[data-ca]', body).forEach((i) => (i.onchange = () => { const k = +i.dataset.ca; if (st.type === 'single') st.answer = k; else st.answer = i.checked ? [...(st.answer || []), k] : st.answer.filter((v) => v !== k); }));
        $$('[name=ja]', body).forEach((i) => (i.onchange = () => (st.answer = i.value === '1')));
        if ($('#oadd', body)) $('#oadd', body).onclick = () => { st.options.push(''); draw(); };
        $$('[data-orm]', body).forEach((b) => (b.onclick = () => { const k = +b.dataset.orm; st.options.splice(k, 1); st.answer = st.type === 'single' ? 0 : (st.answer || []).filter((v) => v !== k).map((v) => (v > k ? v - 1 : v)); draw(); }));
      };
      draw();
    }
  }

  /* ---------------- 面试题库（需求 6.5） ---------------- */
  function pageIvBank(el, _, q) {
    const f = { course: q.course || '', status: q.status || '' };
    const rows = DB.interviewQuestions.filter((x) => (!f.course || x.courseId === +f.course) && (!f.status || x.status === f.status));
    el.innerHTML = `<div class="a-title"><h2>面试题库</h2><div class="row">${refTag('6.5 面试题库')}<button type="button" class="btn" id="imp">批量导入</button><button type="button" class="btn btn-primary" id="add">+ 新增面试题</button></div></div>
      <div class="filter-bar"><select class="select" data-f="course"><option value="">全部课程</option>${DB.courses.map((c) => `<option value="${c.id}" ${+f.course === c.id ? 'selected' : ''}>${c.title}</option>`).join('')}</select><select class="select" data-f="status"><option value="">全部状态</option><option value="on">已上架</option><option value="off">已下架</option></select></div>
      ${table([['题干', (x) => `<div style="max-width:460px">${esc(x.stem)}</div>`], ['所属课程', (x) => esc(course(x.courseId).title)], ['是否被抽取过', (x) => (x.used ? '是' : '<span class="muted">否</span>')], ['状态', (x) => statusTag(x.status)], ['操作', (x) => `<button type="button" class="btn-link" data-ed="${x.id}">编辑</button><button type="button" class="btn-link" data-tg="${x.id}">${x.status === 'on' ? '下架' : '上架'}</button><button type="button" class="btn-link danger" data-del="${x.id}">删除</button>`, 'ops']], rows)}`;
    $$('[data-f]', el).forEach((s) => (s.onchange = () => { const nq = { ...f, [s.dataset.f]: s.value }; go('/ivbank?' + new URLSearchParams(Object.entries(nq).filter(([, x]) => x)).toString()); }));
    $('#imp').onclick = () => importModal('批量导入面试题', '面试题导入模板.xlsx', ['题干', '所属课程不存在'], () => toast('导入完成', 'success'));
    const form = (x = { courseId: +f.course || 1 }) => modal({ title: x.id ? '编辑面试题' : '新增面试题', width: 600, body: `
      <div class="form-item"><label><span class="req">*</span>所属课程</label><select class="select" id="c">${DB.courses.map((c) => `<option value="${c.id}" ${c.id === x.courseId ? 'selected' : ''}>${c.title}</option>`).join('')}</select></div>
      <div class="form-item"><label><span class="req">*</span>题干</label><textarea class="textarea" id="s" style="min-height:64px">${esc(x.stem || '')}</textarea></div>
      <div class="form-item"><label><span class="req">*</span>参考答案</label><textarea class="textarea" id="r">${esc(x.ref || '')}</textarea></div>
      <div class="form-item"><label><span class="req">*</span>评分要点</label><textarea class="textarea" id="p" style="min-height:64px">${esc(x.points || '')}</textarea></div>
      <div class="form-item"><label>追问方向</label><textarea class="textarea" id="fw" style="min-height:64px">${esc(x.follow || '')}</textarea><div class="form-hint">供 AI 面试官追问参考；修改题目不影响已生成的面试报告</div></div>`,
      footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, r) => {
        if (!$('#s', r).value.trim() || !$('#r', r).value.trim() || !$('#p', r).value.trim()) return toast('请填写必填项', 'error');
        const data = { courseId: +$('#c', r).value, stem: $('#s', r).value, ref: $('#r', r).value, points: $('#p', r).value, follow: $('#fw', r).value };
        if (x.id) Object.assign(x, data); else DB.interviewQuestions.unshift({ id: U.uid(), status: 'on', used: false, ...data });
        addLog('面试题库', x.id ? '编辑' : '新增', data.stem.slice(0, 20)); c(); toast('已保存', 'success'); pageIvBank(el, _, q);
      } }] });
    $('#add').onclick = () => form();
    $$('[data-ed]', el).forEach((b) => (b.onclick = () => form(DB.interviewQuestions.find((x) => x.id === +b.dataset.ed))));
    $$('[data-tg]', el).forEach((b) => (b.onclick = () => { const x = DB.interviewQuestions.find((y) => y.id === +b.dataset.tg); x.status = x.status === 'on' ? 'off' : 'on'; toast('操作成功', 'success'); pageIvBank(el, _, q); }));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => {
      const x = DB.interviewQuestions.find((y) => y.id === +b.dataset.del);
      if (x.used) return modal({ title: '无法删除，只能下架', body: '<div class="alert alert-warn">该题目已在面试中被抽取过，不能删除，只能下架。</div>' });
      confirm('删除面试题', '确定删除吗？', () => { DB.interviewQuestions = DB.interviewQuestions.filter((y) => y !== x); toast('已删除', 'success'); pageIvBank(el, _, q); }, '删除', true);
    }));
  }

  /* ---------------- 纠错与反馈（需求 6.6） ---------------- */
  function pageFeedback(el, _, q) {
    const f = { type: q.type || '', status: q.status || 'pending' };
    const rows = DB.feedback.filter((x) => (!f.type || x.type === f.type) && (f.status === 'all' || x.status === f.status));
    const st = { pending: '<span class="tag tag-orange">待处理</span>', fixed: '<span class="tag tag-green">已修正</span>', rejected: '<span class="tag">已驳回</span>' };
    el.innerHTML = `<div class="a-title"><h2>纠错与反馈</h2>${refTag('6.6 纠错与反馈，处理结果站内信通知学员')}</div>
      <div class="filter-bar stack"><div class="seg" role="radiogroup" aria-label="反馈类型">${[['', '全部类型'], ['question', '题目纠错'], ['video', '视频失效反馈']].map(([k, t]) => `<button type="button" role="radio" aria-checked="${f.type === k}" class="seg-item ${f.type === k ? 'active' : ''}" data-type="${k}">${t}</button>`).join('')}</div><div class="seg" role="radiogroup" aria-label="处理状态">${[['pending', '待处理'], ['fixed', '已修正'], ['rejected', '已驳回'], ['all', '全部']].map(([k, t]) => `<button type="button" role="radio" aria-checked="${f.status === k}" class="seg-item ${f.status === k ? 'active' : ''}" data-st="${k}">${t}</button>`).join('')}</div></div>
      ${table([['类型', (x) => (x.type === 'video' ? '<span class="tag tag-orange">视频失效</span>' : '<span class="tag tag-primary">题目纠错</span>')], ['对象', (x) => `<div style="max-width:320px"><a href="#/${x.type === 'video' ? 'courses' : 'questions'}">${esc(x.target)}</a></div><div class="small muted">${esc(x.course)}</div>`], ['错误类型', (x) => x.errType || '—'], ['说明', (x) => `<div style="max-width:240px" class="small">${esc(x.desc)}</div>`], ['提交次数', (x) => `<b>${x.count}</b> 次`], ['最近提交', (x) => `${esc(x.user)}<div class="small muted">${x.time}</div>`], ['状态', (x) => st[x.status] + (x.reply ? `<div class="small muted mt-4">${esc(x.reply)}</div>` : '')],
        ['操作', (x) => (x.status === 'pending' ? `<button type="button" class="btn-link" data-do="${x.id}">处理</button>` : '<span class="muted small">已处理</span>'), 'ops']], rows)}`;
    $$('[data-type]', el).forEach((t) => (t.onclick = () => go(`/feedback?status=${f.status}${t.dataset.type ? '&type=' + t.dataset.type : ''}`)));
    $$('[data-st]', el).forEach((t) => (t.onclick = () => go(`/feedback?status=${t.dataset.st}${f.type ? '&type=' + f.type : ''}`)));
    $$('[data-do]', el).forEach((b) => (b.onclick = () => {
      const x = DB.feedback.find((y) => y.id === +b.dataset.do);
      modal({ title: '处理反馈', width: 520, body: `<div class="mb-16"><div class="meta"><span>${x.type === 'video' ? '视频失效反馈' : '题目纠错，' + x.errType}</span>${x.count > 1 ? `<span>共 ${x.count} 条，一次处理全部</span>` : ''}</div><h3 class="mt-4">${esc(x.target)}</h3><p class="text-2 mt-4">${esc(x.desc)}</p><a class="small" href="#/${x.type === 'video' ? 'courses' : 'questions'}">打开${x.type === 'video' ? '课程' : '题目'}编辑</a></div>
        <div class="form-item"><label><span class="req">*</span>处理结果</label><div class="radio-group"><label class="check"><input type="radio" name="rs" value="fixed" checked>已修正</label><label class="check"><input type="radio" name="rs" value="rejected">已驳回</label></div></div>
        <div class="form-item"><label><span class="req">*</span>处理说明</label><textarea class="textarea" id="rp" placeholder="将通过站内信发送给提交的学员"></textarea></div>`,
        footer: [{ text: '取消' }, { text: '提交', type: 'primary', onClick: (c, r) => {
          const rp = $('#rp', r).value.trim(); if (!rp) return toast('请填写处理说明', 'error');
          x.status = $('input[name=rs]:checked', r).value; x.reply = rp;
          addLog('纠错与反馈', '处理', `${x.type === 'video' ? '视频失效反馈' : '题目纠错'}：${x.target.slice(0, 16)}，${x.status === 'fixed' ? '已修正' : '已驳回'}`);
          c(); toast(`已处理，已向 ${x.count} 位学员发送站内信`, 'success'); go(location.hash.slice(1));
        } }] });
    }));
  }

  /* ---------------- 学员管理（需求 6.7） ---------------- */
  function pageStudents(el, _, q) {
    const f = { kw: q.kw || '', intent: q.intent || '', edu: q.edu || '', years: q.years || '', status: q.status || '', iv: q.iv || '' };
    const rows = DB.students.filter((s) => (!f.kw || s.nickname.includes(f.kw) || s.phone.endsWith(f.kw)) && (!f.intent || s.intent === f.intent) && (!f.edu || s.edu === f.edu) && (!f.years || s.years === f.years) && (!f.status || s.status === f.status) && (!f.iv || (f.iv === 'yes') === (s.interviews > 0)));
    const filtered = Object.values(f).some(Boolean);
    const exportCount = filtered ? rows.length * 37 : 8421;
    const stTag = { normal: '<span class="tag tag-green">正常</span>', banned: '<span class="tag tag-red">封禁</span>', cancelling: '<span class="tag tag-orange">注销中</span>' };
    const sel = (k, label, opts) => `<select class="select" data-f="${k}"><option value="">${label}</option>${opts.map(([v, t]) => `<option value="${v}" ${f[k] === v ? 'selected' : ''}>${t}</option>`).join('')}</select>`;
    el.innerHTML = `<div class="a-title"><h2>学员管理</h2><div class="row">${refTag('6.7 学员管理，导出上限 5000 条')}<button type="button" class="btn btn-primary" id="exp">导出学员名单</button></div></div>
      <div class="filter-bar"><input class="input" id="kw" placeholder="昵称 / 手机号后四位" value="${esc(f.kw)}">
        <input class="input" type="date" title="注册时间"><input class="input" type="date" title="最近活跃时间">
        ${sel('intent', '求职意向', DB.routes.map((r) => [r.name, r.name]))}${sel('edu', '学历', ['大专', '本科', '硕士'].map((x) => [x, x]))}${sel('years', '工作年限', ['在校生', '1 年以内', '1~3 年', '3~5 年', '5 年以上'].map((x) => [x, x]))}
        ${sel('iv', '是否参加过面试', [['yes', '参加过'], ['no', '未参加']])}${sel('status', '状态', [['normal', '正常'], ['banned', '封禁'], ['cancelling', '注销中']])}<button type="button" class="btn btn-sm btn-primary" id="search">查询</button></div>
      ${table([['学员', (s) => `<b>${esc(s.nickname)}</b><div class="small muted">ID ${s.id}</div>`], ['手机号', (s) => U.maskPhone(s.phone)], ['注册时间', (s) => s.regTime], ['最近活跃', (s) => s.lastActive], ['学历', (s) => s.edu || '—'], ['求职意向', (s) => s.intent || '—'], ['工作年限', (s) => s.years || '—'], ['学习路线', (s) => s.routes], ['面试场次', (s) => s.interviews], ['状态', (s) => stTag[s.status]],
        ['操作', (s) => `<button type="button" class="btn-link" data-dt="${s.id}">详情</button>${s.status === 'banned' ? `<button type="button" class="btn-link" data-unban="${s.id}">解封</button>` : s.status === 'normal' ? `<button type="button" class="btn-link danger" data-ban="${s.id}">封禁</button>` : ''}`, 'ops']], rows)}`;
    const apply = (k, v) => { const nq = { ...f, [k]: v }; go('/students?' + new URLSearchParams(Object.entries(nq).filter(([, x]) => x)).toString()); };
    $$('[data-f]', el).forEach((s) => (s.onchange = () => apply(s.dataset.f, s.value)));
    $('#search').onclick = () => apply('kw', $('#kw').value.trim());
    $('#exp').onclick = () => {
      const over = exportCount > 5000;
      modal({ title: '导出学员名单', width: 480, body: `<p class="text-2">按当前筛选条件，共 <b>${exportCount.toLocaleString()}</b> 条学员数据。</p>
        ${over ? '<div class="alert alert-error mt-12">单次导出上限为 5000 条，请缩小筛选范围后再导出。</div>' : '<div class="alert alert-warn mt-12">导出内容包含<b>完整手机号</b>与求职画像，请妥善保管。本次导出将记录操作人、时间、筛选条件与导出条数。</div>'}
        <p class="small muted mt-12">导出格式：Excel（.xlsx）</p>`,
        footer: over ? [{ text: '知道了', type: 'primary' }] : [{ text: '取消' }, { text: '确认导出', type: 'primary', onClick: (c) => { addLog('学员管理', '导出', `学员名单 ${exportCount} 条（筛选：${Object.entries(f).filter(([, v]) => v).map(([k, v]) => k + '=' + v).join('，') || '无'}）`); c(); toast('导出成功，已记入操作日志', 'success'); } }] });
    };
    $$('[data-dt]', el).forEach((b) => (b.onclick = () => detail(DB.students.find((s) => s.id === +b.dataset.dt))));
    $$('[data-ban]', el).forEach((b) => (b.onclick = () => {
      const s = DB.students.find((x) => x.id === +b.dataset.ban);
      modal({ title: '封禁学员', width: 440, body: `<p class="text-2 mb-12">学员：<b>${esc(s.nickname)}</b>（${U.maskPhone(s.phone)}）。封禁后该学员在所有设备上立即退出登录，且无法再登录。</p><div class="form-item"><label><span class="req">*</span>封禁原因</label><textarea class="textarea" id="rs"></textarea></div>`,
        footer: [{ text: '取消' }, { text: '确认封禁', type: 'danger-solid', onClick: (c, r) => { const rs = $('#rs', r).value.trim(); if (!rs) return toast('请填写封禁原因', 'error'); s.status = 'banned'; s.banReason = rs; addLog('学员管理', '封禁', `学员 ${s.nickname}（${s.id}），原因：${rs}`); c(); toast('已封禁', 'success'); pageStudents(el, _, q); } }] });
    }));
    $$('[data-unban]', el).forEach((b) => (b.onclick = () => { const s = DB.students.find((x) => x.id === +b.dataset.unban); confirm('解封学员', `确定解封「${esc(s.nickname)}」吗？`, () => { s.status = 'normal'; addLog('学员管理', '解封', `学员 ${s.nickname}（${s.id}）`); toast('已解封', 'success'); pageStudents(el, _, q); }); }));
    function detail(s) {
      let shown = false, tab = 'learn';
      const m = modal({ title: '学员详情', drawer: true, footer: null });
      const draw = () => {
        $('.modal-body', m.root).innerHTML = `<div class="row" style="gap:var(--space-sm)"><div class="avatar-lg" aria-hidden="true">${esc(s.nickname[0])}</div><div><h3>${esc(s.nickname)}</h3><div class="meta"><span>ID <span class="fig">${s.id}</span></span><span>注册于 <span class="fig">${s.regTime}</span></span></div></div></div>
          <div class="grid grid-2 mt-16" style="gap:var(--space-sm) var(--space-md)">
            <div><span class="muted small">手机号</span><div>${shown ? s.phone : U.maskPhone(s.phone)} ${shown ? '' : '<button type="button" class="btn-link" id="show">查看</button>'}</div></div>
            <div><span class="muted small">状态</span><div>${{ normal: '正常', banned: '封禁（' + esc(s.banReason) + '）', cancelling: '注销中' }[s.status]}</div></div>
            <div><span class="muted small">学历</span><div>${s.edu || '—'}</div></div><div><span class="muted small">求职意向</span><div>${s.intent || '—'}</div></div>
            <div><span class="muted small">工作年限</span><div>${s.years || '—'}</div></div><div><span class="muted small">最近活跃</span><div>${s.lastActive}</div></div></div>
          <div class="tabs mt-24">${[['learn', '学习记录'], ['practice', '刷题记录'], ['iv', '面试记录']].map(([k, t]) => `<button type="button" role="tab" aria-selected="${tab === k}" class="tab ${tab === k ? 'active' : ''}" data-t="${k}">${t}</button>`).join('')}</div>
          ${tab === 'learn' ? table([['课程', (x) => x[0]], ['状态', (x) => x[1]], ['最近打开', (x) => x[2]]], [['Python 基础语法', '<span class="tag tag-green">已掌握</span>', '2026-11-02 20:11'], ['大模型基础与 Prompt 工程', '<span class="tag tag-primary">已学</span>', '2026-11-07 20:05'], ['RAG 检索增强生成', '<span class="tag">未学</span>', '2026-11-07 21:30']])
          : tab === 'practice' ? table([['练习', (x) => x[0]], ['成绩', (x) => x[1]], ['时间', (x) => x[2]]], [['Python 基础语法，顺序练习', '7 / 8', '2026-11-02 20:40'], ['Agent 开发，阶段二，模拟考试', '78 分', '2026-11-07 21:52']])
          : table([['时间', (x) => x[0]], ['路线', (x) => x[1]], ['模式', (x) => x[2]], ['总分', (x) => x[3]], ['', () => '<a class="btn-link" onclick="U.toast(\'原型：查看该学员的面试报告\')">查看报告</a>', 'ops']], [['2026-11-06 21:05', 'Agent 开发', '文字', '76，良好'], ['2026-11-04 20:30', 'AI 全栈开发', '语音', '64，良好']])}`;
        if ($('#show', m.root)) $('#show', m.root).onclick = () => { shown = true; addLog('学员管理', '查看完整手机号', `学员 ${s.nickname}（${s.id}）`); toast('已显示完整手机号，本次查看已记入操作日志'); draw(); };
        $$('[data-t]', m.root).forEach((t) => (t.onclick = () => { tab = t.dataset.t; draw(); }));
      };
      draw();
    }
  }

  /* ---------------- 数据看板（需求 6.8） ---------------- */
  function pageDashboard(el, _, q) {
    const range = q.range || 'day';
    const days = { day: 14, week: 12, month: 6, custom: 10 }[range];
    const labels = Array.from({ length: days }, (_, i) => (range === 'month' ? `${6 + i} 月` : range === 'week' ? `第 ${33 + i} 周` : `11-${U.pad(i + 1)}`).replace('11-1', '11-1'));
    const seed = (base, amp) => labels.map((_, i) => Math.round(base + amp * Math.sin(i / 1.7) + i * (base / 40)));
    const reg = seed({ day: 70, week: 480, month: 1900, custom: 70 }[range], { day: 18, week: 90, month: 300, custom: 18 }[range]);
    el.innerHTML = `<div class="a-title"><div><h2>数据看板</h2><p>数据更新于今天 14:00，按北京时间统计。当天数据每小时更新，留存率和完成率每天凌晨汇总前一天。以下为原型示例数据。</p></div>
        <div class="row">${refTag('6.8 数据看板')}<button type="button" class="btn" id="exp">导出 Excel</button></div></div>
      <div class="filter-bar"><div class="seg" role="radiogroup" aria-label="时间粒度">${[['day', '按日'], ['week', '按周'], ['month', '按月'], ['custom', '自定义']].map(([k, t]) => `<button type="button" role="radio" aria-checked="${range === k}" class="seg-item ${range === k ? 'active' : ''}" data-r="${k}">${t}</button>`).join('')}</div>${range === 'custom' ? '<input class="input" type="date" aria-label="开始日期" value="2026-10-30"><span class="muted">至</span><input class="input" type="date" aria-label="结束日期" value="2026-11-08">' : ''}</div>
      <h3 class="mt-16 mb-12">获客与活跃</h3>
      <div class="kpis">${[['新增注册', reg.reduce((a, b) => a + b, 0).toLocaleString(), '所选时间范围'], ['累计学员', '12,486', '截至今日'], ['日活跃学员', '1,204', '今日有学习行为'], ['月活跃学员', '6,932', '近 30 天'], ['次日留存率', '46.2%', '昨日注册'], ['7 日留存率', '28.5%', '7 天前注册'], ['面试场次', '312', '今日，文字和语音'], ['面试平均分', '71.4', '今日']].map((k) => `<div class="kpi"><div class="label">${k[0]}</div><div class="value">${k[1]}</div><div class="sub">${k[2]}</div></div>`).join('')}</div>
      <div class="grid grid-2 mt-16">
        <div class="card"><div class="card-head"><h3>新增注册趋势</h3><span class="small muted">人</span></div><div class="chart" id="c1"></div></div>
        <div class="card"><div class="card-head"><h3>各路线完成率</h3><div class="legend"><span><i class="k1"></i>平均已学进度</span><span><i class="k2"></i>平均已掌握进度</span></div></div><div class="chart" id="c2"></div></div>
      </div>
      <h3 class="mt-24 mb-12">学习、刷题与 AI 用量</h3>
      <div class="grid grid-2">
        <div class="card"><div class="card-head"><h3>AI 调用次数（近 7 天）</h3><div class="legend"><span><i class="k1"></i>面试</span><span><i class="k2"></i>简答题评分</span></div></div><div class="chart" id="c3"></div></div>
        <div class="card"><div class="card-head"><h3>各路线学习人数与刷题</h3></div>${table([['路线', (r) => r[0]], ['学习人数', (r) => r[1]], ['刷题量', (r) => r[2]], ['正确率', (r) => r[3]]], [['AI 全栈开发', '2,386', '18,402', '71%'], ['Agent 开发', '1,742', '12,955', '68%'], ['Java 开发', '3,105', '26,780', '74%']])}</div>
      </div>
      <div class="grid grid-2 mt-16">
        <div class="card"><div class="card-head"><h3>错误率最高的题目</h3></div>${table([['题目', (r) => `<div style="max-width:300px" class="small">${r[0]}</div>`], ['课程', (r) => r[1]], ['错误率', (r) => `<b>${r[2]}</b>`]], [['Python 中深拷贝与浅拷贝的区别', 'Python 基础语法', '64%'], ['ReAct 模式让模型交替进行"推理"与"行动"', 'Agent 架构与工具调用', '58%'], ['以下哪些是 Java 的基本数据类型？', 'Java 基础语法', '52%'], ['Temperature 为 0 时输出一定相同', '大模型基础与 Prompt 工程', '49%']])}</div>
        <div class="card"><div class="card-head"><h3>各阶段面试平均得分</h3></div>${table([['阶段', (r) => r[0]], ['面试次数', (r) => r[1]], ['平均分', (r) => r[2]]], [['Agent 开发，阶段三，Agent 开发', 842, 66.8], ['AI 全栈开发，阶段四，大模型应用', 731, 70.2], ['Java 开发，阶段一，Java 基础', 1205, 75.6], ['Java 开发，阶段二，主流框架', 968, 69.1]])}</div>
      </div>`;
    U.lineChart($('#c1'), { labels, values: reg, unit: ' 人', name: '新增注册趋势' });
    U.barChart($('#c2'), { labels: DB.routes.map((r) => r.name), series: [{ name: '平均已学进度', cls: 'mark-1', values: [58, 52, 63] }, { name: '平均已掌握进度', cls: 'mark-2', values: [34, 29, 41] }], unit: '%', max: 100 });
    U.barChart($('#c3'), { labels: ['11-02', '11-03', '11-04', '11-05', '11-06', '11-07', '11-08'], series: [{ name: '面试', cls: 'mark-1', values: [280, 302, 295, 340, 318, 356, 312] }, { name: '简答题评分', cls: 'mark-2', values: [1840, 1920, 1760, 2105, 1990, 2240, 1630] }] });
    $$('[data-r]', el).forEach((s) => (s.onclick = () => go('/dashboard?range=' + s.dataset.r)));
    $('#exp').onclick = () => { addLog('数据看板', '导出', `看板数据（${{ day: '按日', week: '按周', month: '按月', custom: '自定义' }[range]}）`); toast('已导出 Excel（仅统计数字，不含学员个人信息），已记入操作日志', 'success'); };
  }

  /* ---------------- 系统用户 / 角色 / 日志（需求 6.9~6.11） ---------------- */
  function pageSysUsers(el) {
    el.innerHTML = `<div class="a-title"><h2>系统用户</h2><div class="row">${refTag('6.9 系统用户管理')}<button type="button" class="btn btn-primary" id="add">+ 新增账号</button></div></div>
      ${table([['账号', (u) => `<b>${esc(u.account)}</b>`], ['姓名', (u) => esc(u.name)], ['手机号', (u) => U.maskPhone(u.phone)], ['角色', (u) => `<span class="tag tag-primary">${DB.roleNames[u.role]}</span>`], ['状态', (u) => (u.status === 'on' ? '<span class="tag tag-green">启用</span>' : '<span class="tag">停用</span>')],
        ['操作', (u) => `<button type="button" class="btn-link" data-ed="${u.id}">编辑</button><button type="button" class="btn-link" data-tg="${u.id}">${u.status === 'on' ? '停用' : '启用'}</button><button type="button" class="btn-link" data-rp="${u.id}">重置密码</button>`, 'ops']], DB.sysUsers)}`;
    const form = (u = { role: 'content' }) => modal({ title: u.id ? '编辑账号' : '新增账号', width: 480, body: `
      <div class="form-item"><label><span class="req">*</span>账号</label><input class="input" id="a" value="${esc(u.account || '')}" ${u.id ? 'disabled' : ''}></div>
      <div class="grid grid-2" style="column-gap:var(--space-md);row-gap:0"><div class="form-item"><label><span class="req">*</span>姓名</label><input class="input" id="n" value="${esc(u.name || '')}"></div><div class="form-item"><label>手机号</label><input class="input" id="p" value="${esc(u.phone || '')}"></div></div>
      <div class="form-item"><label><span class="req">*</span>角色（单选）</label><div class="radio-group">${Object.entries(DB.roleNames).map(([k, v]) => `<label class="check"><input type="radio" name="rl" value="${k}" ${u.role === k ? 'checked' : ''}>${v}</label>`).join('')}</div></div>
      ${u.id ? '' : '<div class="form-item"><label><span class="req">*</span>初始密码</label><input class="input" type="password" id="pw" placeholder="6~20 位，不限字符类型"><div id="bar" class="mt-4">' + U.strengthBar('') + '</div></div>'}`,
      onMount: (r) => { if ($('#pw', r)) $('#pw', r).oninput = (e) => ($('#bar', r).innerHTML = U.strengthBar(e.target.value)); },
      footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, r) => {
        const a = $('#a', r).value.trim(), n = $('#n', r).value.trim();
        if (!a || !n) return toast('请填写必填项', 'error');
        if (!u.id) { const pw = $('#pw', r).value; if (pw.length < 6 || pw.length > 20) return toast('密码长度需为 6~20 位', 'error'); if (DB.sysUsers.some((x) => x.account === a)) return toast('账号已存在', 'error'); }
        const data = { name: n, phone: $('#p', r).value, role: $('input[name=rl]:checked', r).value };
        if (u.id) Object.assign(u, data); else DB.sysUsers.push({ id: U.uid(), account: a, status: 'on', ...data });
        addLog('系统用户', u.id ? '编辑' : '新增', '账号 ' + a); c(); toast('已保存', 'success'); pageSysUsers(el);
      } }] });
    $('#add').onclick = () => form();
    $$('[data-ed]', el).forEach((b) => (b.onclick = () => form(DB.sysUsers.find((u) => u.id === +b.dataset.ed))));
    $$('[data-tg]', el).forEach((b) => (b.onclick = () => { const u = DB.sysUsers.find((x) => x.id === +b.dataset.tg); const off = u.status === 'on'; confirm(off ? '停用账号' : '启用账号', off ? `停用后「${esc(u.name)}」将立即退出登录，且无法登录后台。` : '启用后可正常登录后台。', () => { u.status = off ? 'off' : 'on'; addLog('系统用户', off ? '停用' : '启用', '账号 ' + u.account); toast('操作成功', 'success'); pageSysUsers(el); }, off ? '停用' : '启用', off); }));
    $$('[data-rp]', el).forEach((b) => (b.onclick = () => { const u = DB.sysUsers.find((x) => x.id === +b.dataset.rp); confirm('重置密码', `将为「${esc(u.name)}」生成临时密码，重置后该账号立即退出登录（也用于解除密码输错锁定）。`, () => { delete A.pwdWrong[u.account]; addLog('系统用户', '重置密码', '账号 ' + u.account); modal({ title: '密码已重置', body: '<p>临时密码：<b style="font-family:monospace;font-size:16px">Ys#7k2Qa</b></p><p class="small muted mt-8">请通过安全渠道告知该用户</p>' }); }); }));
  }
  function pageRoles(el) {
    const perms = { super: ['全部模块', '操作日志、系统用户、角色管理、系统配置（仅超级管理员）'], content: ['学科管理、课程库、学习路线', '刷题题库、面试题库', '纠错与反馈处理'], student: ['学员管理（查看、封禁 / 解封、导出、查看完整手机号）', '数据看板（含导出）'] };
    el.innerHTML = `<div class="a-title"><h2>角色管理</h2>${refTag('6.10 角色管理（只读，固定三个角色）')}</div>
      <div class="alert alert-info mb-16">系统内置三个固定角色，不可新增、编辑或删除；创建系统用户时指定角色。</div>
      <div class="grid grid-3">${Object.entries(DB.roleNames).map(([k, v]) => `<div class="card"><div class="row-between mb-12"><h3>${v}</h3><span class="small muted">${DB.sysUsers.filter((u) => u.role === k).length} 个账号</span></div><ul class="perm-list">${perms[k].map((p) => `<li>${p}</li>`).join('')}<li>工作台</li></ul></div>`).join('')}</div>`;
  }
  function pageLogs(el, _, q) {
    const f = { user: q.user || '', module: q.module || '' };
    const rows = DB.logs.filter((l) => (!f.user || l.user === f.user) && (!f.module || l.module === f.module));
    el.innerHTML = `<div class="a-title"><h2>操作日志</h2>${refTag('6.11 操作日志，不可修改，保留 1 年')}</div>
      <div class="filter-bar"><select class="select" data-f="user"><option value="">全部操作人</option>${[...new Set(DB.logs.map((l) => l.user))].map((u) => `<option ${f.user === u ? 'selected' : ''}>${u}</option>`).join('')}</select>
        <select class="select" data-f="module"><option value="">全部模块</option>${[...new Set(DB.logs.map((l) => l.module))].map((m) => `<option ${f.module === m ? 'selected' : ''}>${m}</option>`).join('')}</select><input class="input" type="date"> 至 <input class="input" type="date"></div>
      ${table([['时间', (l) => l.time], ['操作人', (l) => `${esc(l.user)}<div class="small muted">${l.role}</div>`], ['模块', (l) => l.module], ['操作类型', (l) => `<span class="tag">${l.action}</span>`], ['操作对象', (l) => `<div style="max-width:420px" class="small">${esc(l.target)}</div>`], ['IP 地址', (l) => l.ip]], rows)}`;
    $$('[data-f]', el).forEach((s) => (s.onchange = () => { const nq = { ...f, [s.dataset.f]: s.value }; go('/logs?' + new URLSearchParams(Object.entries(nq).filter(([, x]) => x)).toString()); }));
  }

  /* ---------------- 系统配置（需求第 7 章） ---------------- */
  function pageConfig(el, _, q) {
    const tab = q.tab || 'ai';
    const tabs = [['ai', 'AI 用量'], ['learn', '学习判定'], ['exam', '刷题与考试'], ['iv', 'AI 面试'], ['agree', '协议文本'], ['words', '敏感词库'], ['help', '帮助中心与联系方式'], ['platform', '视频平台']];
    const num = (label, desc, key, unit, val) => `<div class="cfg-row"><div><b>${label}</b><div class="small muted">${desc}</div></div><div class="unit-input"><input class="input" type="number" data-k="${key}" aria-label="${label}" value="${val}"><span class="small muted">${unit}</span></div></div>`;
    let body = '';
    if (tab === 'ai') body = num('每人每日 AI 面试次数', '文字面试与语音面试共用；每场扣 1 次', 'interviewPerDay', '次', CFG.interviewPerDay) + num('每人每日简答题 AI 评分次数', '练习时每题扣 1 次，模拟考试整场扣 1 次', 'scorePerDay', '次', CFG.scorePerDay) + '<p class="small muted mt-12">每日次数按北京时间 0 点重置；不设置全平台 AI 用量总上限。</p>';
    if (tab === 'learn') body = num('"已掌握"达标线', '顺序练习的正确率达到此值即判定为已掌握', 'masteryLine', '%', CFG.masteryLine) + num('简答题及格线', 'AI 评分（百分制）低于此值视为答错，收录到错题本', 'shortPassLine', '分', CFG.shortPassLine);
    if (tab === 'exam') body = num('随机抽题数量', '按阶段随机抽题时每次抽取的题目数', 'randomCount', '道', CFG.randomCount) + num('随机抽题最低题量', '阶段题量低于此值时随机抽题入口置灰', 'randomMin', '道', CFG.randomMin) +
      `<div class="cfg-row" style="align-items:start"><div><b>模拟考试组卷规则</b><div class="small muted">各题型题目数量与单题分值</div></div><div class="col">${Object.entries(CFG.exam).map(([t, [n, p]]) => `<div class="row"><span style="width:56px">${typeName[t]}</span><input class="input" type="number" value="${n}" style="width:80px"> 题 × <input class="input" type="number" value="${p}" style="width:80px"> 分</div>`).join('')}<div class="small muted">合计 ${Object.values(CFG.exam).reduce((s, [n]) => s + n, 0)} 题 ${Object.values(CFG.exam).reduce((s, [n, p]) => s + n * p, 0)} 分</div></div></div>` +
      num('模拟考试时长', '单场考试的时长上限', 'examMinutes', '分钟', CFG.examMinutes) + num('模拟考试最低题量', '阶段题量低于此值时模拟考试入口置灰', 'examMin', '道', CFG.examMin);
    if (tab === 'iv') body = num('主问题数量', '单场面试从题库中抽取的主问题数', 'mainQuestions', '道', CFG.mainQuestions) + num('每题最多追问次数', 'AI 对单道主问题的追问上限', 'maxFollowups', '次', CFG.maxFollowups) + num('面试时长上限', '超时自动结束并出报告', 'interviewMinutes', '分钟', CFG.interviewMinutes) + num('面试最低题量', '所选范围内面试题低于此值时不能发起', 'interviewMin', '道', CFG.interviewMin) + num('语音"说完"判定停顿时长', '学员开口后停顿超过此时长，视为本次回答说完', 'pauseSeconds', '秒', CFG.pauseSeconds) + num('语音面试同时进行上限', '全平台同时进行的语音面试场数，超出后排队', 'voiceConcurrency', '场', CFG.voiceConcurrency) +
      `<div class="cfg-row"><div><b>面试等级分数线</b><div class="small muted">优秀 / 良好 / 待提升三档</div></div><div class="col"><div class="row wrap">优秀 ≥ <input class="input" type="number" value="${CFG.grade.excellent}" style="width:80px"> 分，良好 ≥ <input class="input" type="number" value="${CFG.grade.good}" style="width:80px"> 分，其余为待提升</div></div></div>`;
    if (tab === 'agree') body = `<div class="form-item"><label>用户协议</label><textarea class="textarea" style="min-height:160px">${esc(DB.agreement.user)}</textarea></div><div class="form-item"><label>隐私政策</label><textarea class="textarea" style="min-height:160px">${esc(DB.agreement.privacy)}</textarea></div><div class="alert alert-warn small">发布更新后：未登录学员在下次登录时勾选同意；已登录学员在下次打开页面时弹窗要求同意，不同意则退出登录；同时发送"协议已更新"站内信。</div>`;
    if (tab === 'words') body = `<p class="small muted mb-12">用于过滤 AI 面试与简答题评分中学员的输入和 AI 的输出。学员输入命中：拦截并提示修改，不消耗次数；AI 输出命中：替换为 *；命中情况均写入日志。</p><div class="row mb-16"><input class="input" id="nw" placeholder="输入敏感词" style="max-width:240px"><button type="button" class="btn" id="addW">添加</button><button type="button" class="btn" id="impW">批量导入</button></div><div id="words">${DB.sensitiveWords.map((w, i) => `<span class="word-tag">${esc(w)}<button data-rw="${i}">×</button></span>`).join('')}</div>`;
    if (tab === 'help') body = `<h4 class="mb-12">常见问题</h4>${DB.faq.map((f, i) => `<div class="faq-row"><div class="row-between"><b>${esc(f.q)}</b><span class="row" style="gap:var(--space-sm)"><button type="button" class="btn-link" onclick="U.toast('原型：编辑常见问题')">编辑</button><button type="button" class="btn-link" ${i === 0 ? 'disabled' : ''}>上移</button><button type="button" class="btn-link danger" onclick="U.toast('原型：删除常见问题')">删除</button></span></div><p class="text-2 mt-4">${esc(f.a)}</p></div>`).join('')}<button type="button" class="btn btn-sm mt-12" onclick="U.toast('原型：新增常见问题')">新增问题</button>
      <h4 class="mt-24 mb-12">客服联系方式（均为选填）</h4><div class="grid grid-3"><div class="form-item"><label>客服微信二维码</label><button type="button" class="upload" onclick="this.textContent='已上传 qrcode.png'">上传图片</button></div><div class="form-item"><label>邮箱</label><input class="input" value="${DB.contact.email}"></div><div class="form-item"><label>电话</label><input class="input" value="${DB.contact.phone}"></div></div><p class="small muted">联系方式在帮助中心、封禁提示、语音面试设备检测失败等处统一引用。</p>`;
    if (tab === 'platform') body = `<div class="row-between mb-12"><p class="small muted">新增平台只需在此配置，不需要修改代码；停用的平台不能用于新建课程，已有课程不受影响。</p><button type="button" class="btn btn-sm btn-primary" id="addP">+ 新增平台</button></div>
      ${table([['平台名称', (p) => `<b>${esc(p.name)}</b>${p.builtin ? ' <span class="tag">内置</span>' : ''}`], ['允许的域名', (p) => p.domains.map((d) => `<span class="tag">${d}</span>`).join(' ')], ['课程数', (p) => DB.courses.filter((c) => c.platformId === p.id).length], ['状态', (p) => (p.enabled ? '<span class="tag tag-green">启用</span>' : '<span class="tag">停用</span>')], ['操作', (p) => `<button type="button" class="btn-link" data-pe="${p.id}">编辑</button><button type="button" class="btn-link" data-pt="${p.id}">${p.enabled ? '停用' : '启用'}</button>`, 'ops']], DB.platforms)}`;
    el.innerHTML = `<div class="a-title"><h2>系统配置</h2>${refTag('第 7 章 系统配置，修改记入操作日志')}</div>
      <div class="cfg-layout"><nav class="cfg-nav">${tabs.map(([k, t]) => `<a href="#/config?tab=${k}" class="${tab === k ? 'active' : ''}">${t}</a>`).join('')}</nav>
        <div class="card"><h3 class="mb-8">${tabs.find((t) => t[0] === tab)[1]}</h3>${body}${['words', 'platform'].includes(tab) ? '' : `<div class="mt-16"><button type="button" class="btn btn-primary" id="save">${tab === 'agree' ? '发布更新' : '保存'}</button></div>`}</div></div>`;
    if ($('#save')) $('#save').onclick = () => {
      const changes = $$('[data-k]', el).filter((i) => +i.value !== CFG[i.dataset.k]).map((i) => { const old = CFG[i.dataset.k]; CFG[i.dataset.k] = +i.value; return `${i.closest('.cfg-row').querySelector('b').textContent}由 ${old} 改为 ${i.value}`; });
      addLog('系统配置', tab === 'agree' ? '发布协议更新' : '修改', `${tabs.find((t) => t[0] === tab)[1]}${changes.length ? '：' + changes.join('；') : ''}`);
      toast(tab === 'agree' ? '协议已发布，学员需重新同意；已记入操作日志' : '已保存，已记入操作日志', 'success');
    };
    if ($('#addW')) { const redraw = () => pageConfig(el, _, q); $('#addW').onclick = () => { const w = $('#nw').value.trim(); if (!w) return; if (DB.sensitiveWords.includes(w)) return toast('已存在', 'warn'); DB.sensitiveWords.push(w); addLog('系统配置', '新增敏感词', w); redraw(); }; $$('[data-rw]', el).forEach((b) => (b.onclick = () => { DB.sensitiveWords.splice(+b.dataset.rw, 1); redraw(); })); $('#impW').onclick = () => importModal('批量导入敏感词', '敏感词导入模板.xlsx', ['敏感词', '与已有敏感词重复'], () => toast('导入完成', 'success')); }
    const pForm = (p = { domains: [] }) => modal({ title: p.id ? '编辑视频平台' : '新增视频平台', width: 460, body: `<div class="form-item"><label><span class="req">*</span>平台名称</label><input class="input" id="pn" value="${esc(p.name || '')}" placeholder="如 腾讯课堂"></div><div class="form-item"><label><span class="req">*</span>允许的域名（每行一个）</label><textarea class="textarea" id="pd" placeholder="example.com">${p.domains.join('\n')}</textarea><div class="form-hint">课程的视频链接域名需属于这里填写的域名（含子域名）</div></div>`,
      footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, r) => { const n = $('#pn', r).value.trim(), d = $('#pd', r).value.split('\n').map((x) => x.trim()).filter(Boolean); if (!n || !d.length) return toast('请填写必填项', 'error'); if (p.id) Object.assign(p, { name: n, domains: d }); else DB.platforms.push({ id: U.uid(), name: n, domains: d, enabled: true }); addLog('系统配置', p.id ? '编辑视频平台' : '新增视频平台', n); c(); toast('已保存', 'success'); pageConfig(el, _, q); } }] });
    if ($('#addP')) $('#addP').onclick = () => pForm();
    $$('[data-pe]', el).forEach((b) => (b.onclick = () => pForm(platform(b.dataset.pe))));
    $$('[data-pt]', el).forEach((b) => (b.onclick = () => { const p = platform(b.dataset.pt); p.enabled = !p.enabled; addLog('系统配置', p.enabled ? '启用视频平台' : '停用视频平台', p.name); toast(p.enabled ? '已启用' : '已停用：不能用于新建课程，已有课程不受影响', 'success'); pageConfig(el, _, q); }));
  }

  /* ---------------- 路由 ---------------- */
  U.router([
    ['/login', pageLogin], ['/workbench', pageWorkbench], ['/subjects', pageSubjects], ['/courses', pageCourses], ['/routes', pageRoutes], ['/routes/:id', pageRouteEdit],
    ['/questions', pageQuestions], ['/ivbank', pageIvBank], ['/feedback', pageFeedback], ['/students', pageStudents], ['/dashboard', pageDashboard],
    ['/sysusers', pageSysUsers], ['/roles', pageRoles], ['/logs', pageLogs], ['/config', pageConfig], ['/', () => go(A.loggedIn ? '/workbench' : '/login')],
  ], shell, (path) => {
    if (path !== '/login' && path !== '/' && !A.loggedIn) { location.hash = '#/login'; return false; }
    if (A.loggedIn && !allowed(path)) { toast('当前角色无权访问该页面', 'warn'); location.hash = '#/workbench'; return false; }
    return true;
  });
})();
