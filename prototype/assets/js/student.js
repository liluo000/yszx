/* ==========================================================
   云枢智学 学员前台原型
   需求依据：docs/需求文档.md 第 2、3、5、8 章
   ========================================================== */
(function () {
  const { $, $$, esc, toast, modal, confirm, go } = U;
  const CFG = DB.config;

  /* ---------------- 学员端状态（内存模拟） ---------------- */
  const S = {
    loggedIn: true,
    user: { nickname: '小云同学', phone: '13812345678', hasPassword: false, edu: '本科', intent: 'Agent 开发', years: '1~3 年' },
    interviewLeft: CFG.interviewPerDay,
    scoreLeft: CFG.scorePerDay,
    scoreReserved: 0,
    progress: { 1: { s: 'mastered', opened: '2026-11-02 20:11' }, 3: { s: 'learned', opened: '2026-11-06 21:40' }, 10: { s: 'learned', opened: '2026-11-07 20:05' }, 11: { s: 'opened', opened: '2026-11-07 21:30' } },
    last: { routeId: 2, courseId: 11 },
    wrong: [],
    practiceLog: [
      { title: 'Python 基础语法，顺序练习', score: '7/8', time: '2026-11-02 20:40' },
      { title: 'Agent 开发，阶段二，随机抽题', score: '6/10', time: '2026-11-07 21:52' },
    ],
    interviews: [],
    messages: [
      { id: 1, title: '面试报告已生成', body: '你在 2026-11-06 参加的「Agent 开发」文字面试报告已生成，点击查看。', time: '2026-11-06 21:20', read: false, link: '/report/1' },
      { id: 2, title: '纠错与反馈处理结果', body: '你反馈的题目「执行 print(type(3 / 2)) 的输出结果是？」已处理：已修正。处理说明：已补充 // 整除的说明。', time: '2026-11-05 10:02', read: false },
      { id: 3, title: '用户协议已更新', body: '《用户协议》《隐私政策》已于 2026-11-01 更新，请知悉。', time: '2026-11-01 09:00', read: true },
    ],
    cancelPending: false,
    demo: { queue: false, aiFail: false, reportFail: false, banned: false },
    docRef: '',
    timers: [],
    exam: null, examResult: null, iv: null, practice: null,
  };
  const setTimer = (fn, ms) => { const t = setTimeout(fn, ms); S.timers.push(t); return t; };
  const setTick = (fn, ms) => { const t = setInterval(fn, ms); S.timers.push(t); return t; };
  const clearTimers = () => { S.timers.forEach((t) => { clearTimeout(t); clearInterval(t); }); S.timers = []; if (S.stopDictation) S.stopDictation(); };

  /* ---------------- 数据工具 ---------------- */
  const course = (id) => DB.courses.find((c) => c.id === +id);
  const route = (id) => DB.routes.find((r) => r.id === +id);
  const subject = (id) => DB.subjects.find((s) => s.id === +id);
  const platform = (id) => DB.platforms.find((p) => p.id === +id);
  const onCourses = (ids) => ids.map(course).filter((c) => c && c.status === 'on');
  const qOf = (cid) => DB.questions.filter((q) => q.courseId === +cid && q.status === 'on').sort((a, b) => a.sort - b.sort);
  const ivOf = (cid) => DB.interviewQuestions.filter((q) => q.courseId === +cid && q.status === 'on');
  const stageQ = (st) => onCourses(st.courses).flatMap((c) => qOf(c.id));
  const stageIv = (st) => onCourses(st.courses).flatMap((c) => ivOf(c.id).map((q) => ({ ...q, stageName: st.name })));
  const status = (cid) => (S.progress[cid] && S.progress[cid].s) || 'none';
  const learned = (cid) => ['learned', 'mastered'].includes(status(cid));
  const routeProgress = (r) => {
    const cs = r.stages.flatMap((s) => onCourses(s.courses));
    const conf = cs.filter((c) => qOf(c.id).length);
    return { total: cs.length, learned: cs.filter((c) => learned(c.id)).length, conf: conf.length, mastered: conf.filter((c) => status(c.id) === 'mastered').length };
  };
  const stageProgress = (st) => {
    const cs = onCourses(st.courses);
    const conf = cs.filter((c) => qOf(c.id).length);
    return { total: cs.length, learned: cs.filter((c) => learned(c.id)).length, conf: conf.length, mastered: conf.filter((c) => status(c.id) === 'mastered').length };
  };
  const routesOfCourse = (cid) => DB.routes.filter((r) => r.status === 'on' && r.stages.some((s) => s.courses.includes(+cid)));
  const findStage = (sid) => { for (const r of DB.routes) for (const s of r.stages) if (s.id === +sid) return { r, s }; return {}; };
  const pct = (a, b) => (b ? Math.round((a / b) * 100) : 0);
  const typeName = { judge: '判断题', single: '单选题', multi: '多选题', short: '简答题' };
  const letters = 'ABCDEFGH';
  const hasSensitive = (txt) => DB.sensitiveWords.find((w) => txt.includes(w));
  const unread = () => S.messages.filter((m) => !m.read).length;
  const pushMsg = (title, body, link) => S.messages.unshift({ id: U.uid(), title, body, time: U.now(), read: false, link });
  const progressBar = (label, a, b, cls = '') => `<div><div class="progress-label"><span>${label}</span><span class="fig">${a}/${b}</span></div><div class="progress ${cls}" role="progressbar" aria-label="${label}" aria-valuemin="0" aria-valuemax="${b}" aria-valuenow="${a}"><span style="--p:${b ? (a / b).toFixed(3) : 0}"></span></div></div>`;
  /* 已掌握是本产品"已落定"的状态：用 LOGO 的青绿色标记，其余状态只用墨线 */
  const statusTag = (cid) => ({ mastered: '<span class="stamp">已掌握</span>', learned: '<span class="tag tag-primary">已学</span>' }[status(cid)] || '<span class="tag">未学</span>');
  const icon = U.icon;
  const stageShort = (n) => n.replace(/^阶段.：/, '');

  /* ---------------- 外壳 ---------------- */
  const app = $('#app');
  function renderShell() {
    const path = (location.hash.slice(1) || '/').split('?')[0];
    const nav = [['/', '首页'], ['/routes', '学习路线'], ['/wrong', '错题本'], ['/interview', 'AI 面试'], ['/records', '学习记录']];
    const isActive = (p) => (p === '/' ? path === '/' : path.startsWith(p));
    app.innerHTML = `
      <header class="s-header"><div class="s-header-inner">
        <button class="menu-btn" id="menuBtn" type="button" aria-label="打开菜单" aria-expanded="false">${icon('menu')}</button>
        <a class="logo" href="#/" aria-label="云枢智学首页"><picture><source media="(max-width: 40rem)" srcset="assets/img/logo-mark.png" width="276" height="183"><img src="assets/img/logo.png" alt="云枢智学" width="836" height="183"></picture></a>
        <nav class="s-nav" id="sNav" aria-label="主导航">${nav.map(([p, t]) => `<a href="#${p}" class="${isActive(p) ? 'active' : ''}" ${isActive(p) ? 'aria-current="page"' : ''}>${t}</a>`).join('')}</nav>
        <div class="s-search" role="search">${icon('search')}<input class="input" id="topSearch" type="search" aria-label="搜索路线和课程" placeholder="搜索路线、课程"></div>
        <div class="s-user">${S.loggedIn ? `
          <button class="bell" id="bell" type="button" aria-label="消息中心，${unread()} 条未读">${icon('bell')}${unread() ? `<span class="badge">${unread()}</span>` : ''}</button>
          <div class="dropdown" id="userDrop"><button class="avatar" id="avatarBtn" type="button" aria-label="账户菜单" aria-haspopup="menu">${esc(S.user.nickname.slice(0, 1))}</button>
            <div class="dropdown-menu" role="menu">
              <div class="who"><div class="strong">${esc(S.user.nickname)}</div><div class="small muted fig">${U.maskPhone(S.user.phone)}</div></div>
              <a href="#/profile" role="menuitem">个人中心</a><a href="#/interviews" role="menuitem">面试记录</a><a href="#/messages" role="menuitem">消息中心</a><a href="#/help" role="menuitem">帮助中心</a><a id="logoutBtn" role="menuitem" tabindex="0">退出登录</a>
            </div></div>` : `<button class="btn btn-ghost btn-sm" id="loginBtn" type="button">登录</button><button class="btn btn-primary btn-sm" id="regBtn" type="button">注册</button>`}
        </div></div></header>
      <main class="s-main" id="view"></main>
      <footer class="s-footer"><div class="s-footer-inner"><span>云枢智学</span><a href="#/help">帮助中心</a><a id="ftUser" tabindex="0">用户协议</a><a id="ftPrivacy" tabindex="0">隐私政策</a><span class="grow"></span><span>页面原型，仅演示交互，数据为模拟数据</span></div></footer>`;
    $('#menuBtn').onclick = (e) => { const open = $('#sNav').classList.toggle('open'); e.currentTarget.setAttribute('aria-expanded', open); };
    $('#topSearch').onkeydown = (e) => { if (e.key === 'Enter' && e.target.value.trim()) go('/search?q=' + encodeURIComponent(e.target.value.trim())); };
    if (S.loggedIn) {
      $('#bell').onclick = () => go('/messages');
      $('#avatarBtn').onclick = (e) => { e.stopPropagation(); $('#userDrop').classList.toggle('open'); };
      document.addEventListener('click', () => $('#userDrop') && $('#userDrop').classList.remove('open'), { once: true });
      $('#logoutBtn').onclick = () => { S.loggedIn = false; toast('已退出登录'); go('/'); };
    } else {
      $('#loginBtn').onclick = () => openLogin('code');
      $('#regBtn').onclick = () => openLogin('register');
    }
    $('#ftUser').onclick = () => showAgreement('user');
    $('#ftPrivacy').onclick = () => showAgreement('privacy');
    renderProtoPanel();
    return $('#view');
  }
  const showAgreement = (k) => modal({ title: k === 'user' ? '用户协议' : '隐私政策', width: 560, body: `<p class="text-2" style="white-space:pre-wrap">${esc(DB.agreement[k])}</p>` });

  /* ---------------- 原型控制台 ---------------- */
  let panelOpen = false;
  function renderProtoPanel() {
    $$('.proto-fab,.proto-panel').forEach((e) => e.remove());
    const fab = document.createElement('button');
    fab.className = 'btn proto-fab';
    fab.type = 'button';
    fab.setAttribute('aria-expanded', panelOpen);
    fab.textContent = panelOpen ? '收起原型控制台' : '原型控制台';
    fab.onclick = () => { panelOpen = !panelOpen; renderProtoPanel(); };
    document.body.appendChild(fab);
    if (!panelOpen) return;
    const p = document.createElement('div');
    p.className = 'proto-panel';
    const sw = (key, on) => `<button type="button" class="switch ${on ? 'on' : ''}" role="switch" aria-checked="${on}" data-k="${key}" aria-label="切换"></button>`;
    p.innerHTML = `<h4>原型控制台</h4>
      ${S.docRef ? `<div class="proto-ref">本页对应需求：${S.docRef}</div>` : ''}
      <div class="item"><span>登录状态（学员 / 游客）</span>${sw('login', S.loggedIn)}</div>
      <div class="item"><span>今日 AI 面试剩余 <b class="fig">${S.interviewLeft}</b> 次</span><span class="row"><button type="button" class="btn btn-sm" data-a="iv0">设为 0</button><button type="button" class="btn btn-sm" data-a="ivr">重置</button></span></div>
      <div class="item"><span>今日 AI 评分剩余 <b class="fig">${S.scoreLeft}</b> 次</span><span class="row"><button type="button" class="btn btn-sm" data-a="sc0">设为 0</button><button type="button" class="btn btn-sm" data-a="scr">重置</button></span></div>
      <div class="item"><span>模拟语音面试排队</span>${sw('queue', S.demo.queue)}</div>
      <div class="item"><span>模拟 AI 服务异常</span>${sw('aiFail', S.demo.aiFail)}</div>
      <div class="item"><span>模拟报告生成失败</span>${sw('reportFail', S.demo.reportFail)}</div>
      <div class="item"><span>模拟账号被封禁（登录时）</span>${sw('banned', S.demo.banned)}</div>
      <div class="item"><span>弹出协议更新提示</span><button type="button" class="btn btn-sm" data-a="agree">触发</button></div>
      <div class="item"><span>管理后台原型</span><a href="admin.html" target="_blank" rel="noopener">在新窗口打开</a></div>`;
    document.body.appendChild(p);
    $$('.switch', p).forEach((b) => (b.onclick = () => {
      const k = b.dataset.k;
      if (k === 'login') { S.loggedIn = !S.loggedIn; S.cancelPending = false; toast(S.loggedIn ? '已切换为学员（已登录）' : '已切换为游客'); rerender(); return; }
      S.demo[k] = !S.demo[k];
      renderProtoPanel();
    }));
    $$('[data-a]', p).forEach((b) => (b.onclick = () => {
      const a = b.dataset.a;
      if (a === 'iv0') S.interviewLeft = 0;
      if (a === 'ivr') S.interviewLeft = CFG.interviewPerDay;
      if (a === 'sc0') { S.scoreLeft = 0; S.scoreReserved = 0; }
      if (a === 'scr') S.scoreLeft = CFG.scorePerDay;
      if (a === 'agree') return agreementUpdated();
      rerender();
    }));
  }
  const rerender = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

  function agreementUpdated() {
    if (!S.loggedIn) return toast('游客无需确认，下次登录时勾选同意即可', 'warn');
    modal({ title: '用户协议和隐私政策已更新', closable: false, width: 460,
      body: `<p class="text-2">为了更好地保护你的权益，我们更新了 <a id="agU">《用户协议》</a> 和 <a id="agP">《隐私政策》</a>。请阅读并同意后继续使用；如不同意，将退出登录。</p>`,
      onMount: (root) => { $('#agU', root).onclick = () => showAgreement('user'); $('#agP', root).onclick = () => showAgreement('privacy'); },
      footer: [{ text: '不同意并退出', onClick: (c) => { c(); S.loggedIn = false; toast('已退出登录'); go('/'); } }, { text: '同意', type: 'primary', onClick: (c) => { c(); toast('感谢你的确认', 'success'); } }] });
  }

  /* ---------------- 登录 / 注册（需求 5.1） ---------------- */
  function needLogin(fn) { if (S.loggedIn) return fn(); toast('请先登录', 'warn'); openLogin('code', fn); }
  function openLogin(mode = 'code', after) {
    const st = { mode, captcha: false, codeSent: false, wrong: 0, pwdWrong: 0, countdown: 0, timer: null };
    const m = modal({ title: '', width: 420, footer: null, onClose: () => clearInterval(st.timer) });
    const body = $('.modal-body', m.root);
    const draw = () => {
      const isReg = st.mode === 'register';
      $('.modal-head h3', m.root).textContent = isReg ? '注册云枢智学' : '登录云枢智学';
      body.innerHTML = `
        ${isReg ? '' : `<div class="tabs" role="tablist"><button type="button" role="tab" class="tab btn-link" style="text-decoration:none" aria-selected="${st.mode === 'code'}" data-m="code">验证码登录</button><button type="button" role="tab" class="tab btn-link" style="text-decoration:none" aria-selected="${st.mode === 'pwd'}" data-m="pwd">密码登录</button></div>`}
        <div class="form-item"><label for="lgPhone">手机号</label><input class="input fig" id="lgPhone" name="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="请输入 11 位手机号" value="${S.user.phone}"></div>
        ${st.mode === 'pwd' ? `
          <div class="form-item"><label for="lgPwd">密码</label><input class="input" id="lgPwd" name="password" type="password" autocomplete="current-password" placeholder="请输入密码"><div class="form-hint">原型演示密码 abc123，需先在个人中心设置过密码。</div></div>` : `
          <div class="form-item"><label>图形验证</label><button type="button" class="captcha ${st.captcha ? 'ok' : ''}" id="cap" aria-pressed="${st.captcha}"><span class="box" aria-hidden="true">${st.captcha ? '对' : ''}</span>${st.captcha ? '验证通过' : '点击完成图形验证'}</button></div>
          <div class="form-item"><label for="lgCode">短信验证码</label><div class="input-group"><input class="input fig" id="lgCode" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6 位验证码"><button type="button" class="btn" id="sendCode" ${st.countdown ? 'disabled' : ''}>${st.countdown ? st.countdown + ' 秒后重发' : st.codeSent ? '重新获取' : '获取验证码'}</button></div>
          <div class="form-hint">原型演示验证码为 123456。验证码 5 分钟内有效，输错 5 次即失效。</div></div>`}
        ${isReg ? `<label class="check mb-16"><input type="checkbox" id="agree"> 我已阅读并同意 <a id="aU">《用户协议》</a> 和 <a id="aP">《隐私政策》</a></label>` : ''}
        <div id="lgErr" class="form-error mb-8"></div>
        <button type="button" class="btn btn-primary btn-block btn-lg" id="lgSubmit">${isReg ? '注册' : '登录'}</button>
        <div class="row-between mt-16 small">
          ${isReg ? '<span class="muted">已有账号？</span><a data-m="code">去登录</a>' : '<a id="forgot" tabindex="0">忘记密码</a><span>没有账号？<a data-m="register">立即注册</a></span>'}
        </div>`;
      $$('[data-m]', body).forEach((t) => (t.onclick = () => { st.mode = t.dataset.m; draw(); }));
      const err = (t) => ($('#lgErr', body).textContent = t);
      const phoneOk = () => { const v = $('#lgPhone', body).value.trim(); if (!/^1\d{10}$/.test(v)) { err('请输入正确的 11 位手机号'); return false; } return v; };
      if ($('#cap', body)) $('#cap', body).onclick = () => { st.captcha = true; draw(); };
      if ($('#sendCode', body)) $('#sendCode', body).onclick = () => {
        if (!phoneOk()) return;
        if (!st.captcha) return err('请先完成图形验证');
        st.codeSent = true; st.wrong = 0; st.countdown = 60; toast('验证码已发送（演示：123456）', 'success');
        clearInterval(st.timer);
        st.timer = setInterval(() => { st.countdown--; const b = $('#sendCode', body); if (b) { b.textContent = st.countdown > 0 ? st.countdown + ' 秒后重发' : '重新获取'; b.disabled = st.countdown > 0; } if (st.countdown <= 0) clearInterval(st.timer); }, 1000);
        draw();
      };
      if ($('#aU', body)) { $('#aU', body).onclick = () => showAgreement('user'); $('#aP', body).onclick = () => showAgreement('privacy'); }
      if ($('#forgot', body)) $('#forgot', body).onclick = () => { m.close(); resetPwdModal(); };
      $('#lgSubmit', body).onclick = () => {
        const phone = phoneOk(); if (!phone) return;
        if (st.mode === 'pwd') {
          if (st.pwdWrong >= 5) return err('密码连续输错 5 次，密码登录已锁定 15 分钟，可改用验证码登录');
          if (!S.user.hasPassword) return err('该账号尚未设置密码，请使用验证码登录');
          if ($('#lgPwd', body).value !== S.user.password) { st.pwdWrong++; return err(st.pwdWrong >= 5 ? '密码连续输错 5 次，密码登录已锁定 15 分钟，可改用验证码登录' : `密码错误，还可尝试 ${5 - st.pwdWrong} 次`); }
        } else {
          if (!st.codeSent) return err('请先获取验证码');
          if (st.wrong >= 5) return err('验证码已失效，请重新获取');
          if ($('#lgCode', body).value.trim() !== '123456') { st.wrong++; return err(st.wrong >= 5 ? '验证码已失效，请重新获取' : `验证码错误，还可尝试 ${5 - st.wrong} 次`); }
          if (st.mode === 'register' && !$('#agree', body).checked) return err('请先阅读并勾选同意《用户协议》和《隐私政策》');
        }
        if (S.demo.banned) {
          m.close();
          return modal({ title: '账号已被封禁', width: 420, body: `<div class="alert alert-error">你的账号因违反平台规则已被封禁，暂时无法登录。</div><dl class="detail-dl"><div><dt>客服微信</dt><dd>${DB.contact.wechat}</dd></div><div><dt>邮箱</dt><dd>${DB.contact.email}</dd></div><div><dt>电话</dt><dd class="fig">${DB.contact.phone}</dd></div></dl>` });
        }
        m.close();
        S.user.phone = phone;
        if (S.cancelPending) return cancelPendingPrompt(after);
        S.loggedIn = true;
        if (st.mode === 'register') {
          toast('注册成功，已自动登录', 'success');
          rerender();
          return portraitGuide();
        }
        toast('登录成功', 'success');
        rerender();
        after && setTimeout(after, 50);
      };
    };
    draw();
  }
  function portraitGuide() {
    modal({ title: '完善求职画像', width: 440, body: `<p class="text-2 mb-16">填写后我们能更好地为你推荐学习路线（均为选填，可跳过）。</p>${portraitForm()}`,
      footer: [{ text: '跳过' }, { text: '保存', type: 'primary', onClick: (c, root) => { savePortrait(root); c(); toast('已保存', 'success'); } }] });
  }
  const portraitForm = () => {
    const sel = (id, label, opts, v) => `<div class="form-item"><label>${label}</label><select class="select" id="${id}"><option value="">请选择</option>${opts.map((o) => `<option ${o === v ? 'selected' : ''}>${o}</option>`).join('')}</select></div>`;
    return sel('pfEdu', '学历', ['大专', '本科', '硕士', '博士', '其他'], S.user.edu) + sel('pfIntent', '求职意向', DB.routes.map((r) => r.name), S.user.intent) + sel('pfYears', '工作年限', ['在校生', '1 年以内', '1~3 年', '3~5 年', '5 年以上'], S.user.years);
  };
  const savePortrait = (root) => { S.user.edu = $('#pfEdu', root).value; S.user.intent = $('#pfIntent', root).value; S.user.years = $('#pfYears', root).value; };
  function cancelPendingPrompt(after) {
    modal({ title: '账号注销中', closable: false, width: 440,
      body: `<div class="alert alert-warn">你的账号已申请注销，将于 <b>${S.cancelAt}</b> 正式注销。注销后学习、刷题、面试记录将全部删除且不可恢复。</div>`,
      footer: [{ text: '继续注销并退出', onClick: (c) => { c(); toast('已退出登录，账号仍处于注销冷静期'); } },
        { text: '撤销注销', type: 'primary', onClick: (c) => { c(); S.cancelPending = false; S.loggedIn = true; toast('已撤销注销，账号恢复正常', 'success'); rerender(); after && setTimeout(after, 50); } }] });
  }
  function resetPwdModal() {
    modal({ title: '忘记密码', width: 420, body: `
      <div class="form-item"><label>手机号</label><input class="input" value="${S.user.phone}"></div>
      <div class="form-item"><label>短信验证码</label><div class="input-group"><input class="input" placeholder="演示：123456" id="rpCode"><button type="button" class="btn" onclick="this.disabled=true;this.textContent='60 秒后重发'">获取验证码</button></div></div>
      <div class="form-item"><label>新密码</label><input class="input" type="password" id="rpPwd" placeholder="6~20 位"><div id="rpBar" class="mt-4"></div></div>`,
      onMount: (root) => { $('#rpPwd', root).oninput = (e) => ($('#rpBar', root).innerHTML = U.strengthBar(e.target.value)); },
      footer: [{ text: '取消' }, { text: '重置密码', type: 'primary', onClick: (c, root) => {
        const p = $('#rpPwd', root).value;
        if ($('#rpCode', root).value !== '123456') return toast('验证码错误', 'error');
        if (p.length < 6 || p.length > 20) return toast('密码长度需为 6~20 位', 'error');
        S.user.hasPassword = true; S.user.password = p; c(); toast('密码已重置，所有设备已退出登录，请重新登录', 'success'); S.loggedIn = false; go('/');
      } }] });
  }

  /* ---------------- 首页（需求 5.2） ---------------- */
  function routeCard(r) {
    const p = routeProgress(r);
    return `<article class="card hoverable route-card" data-route="${r.id}" tabindex="0" role="link" aria-label="${esc(r.name)}">
      <div class="row-between"><h3>${esc(r.name)}</h3>${S.loggedIn && p.learned ? `<span class="fig small muted">${p.learned}/${p.total}</span>` : ''}</div>
      <p>${esc(r.desc)}</p>
      <ol>${r.stages.map((s) => `<li><span>${esc(s.name)}</span><span class="fig">${onCourses(s.courses).length} 门</span></li>`).join('')}</ol>
      ${S.loggedIn && p.learned ? progressBar('已学进度', p.learned, p.total) : `<div class="small muted"><span class="fig">${r.learners.toLocaleString()}</span> 人在学</div>`}
    </article>`;
  }
  const bindRouteCards = (el) => $$('[data-route]', el).forEach((c) => {
    c.onclick = () => go('/route/' + c.dataset.route);
    c.onkeydown = (e) => { if (e.key === 'Enter') go('/route/' + c.dataset.route); };
  });

  function pageHome(el) {
    S.docRef = '5.2 首页';
    const routes = DB.routes.filter((r) => r.status === 'on');
    if (!S.loggedIn) {
      const r = routes[1];
      el.innerHTML = `
        <section class="board" aria-labelledby="heroTitle">
          <div><h1 id="heroTitle">学完一个阶段，就面一次</h1>
            <p>按路线看课、刷题，再用文字或语音和 AI 面试官过一遍，报告按阶段指出短板。</p>
            <div class="row"><button type="button" class="btn btn-lg" id="heroReg">免费注册</button><button type="button" class="btn btn-ghost btn-lg" id="heroRoutes">浏览学习路线</button></div></div>
          <article class="card route-card" aria-label="${esc(r.name)} 路线">
            <div class="row-between"><h3>${esc(r.name)}</h3><span class="small muted">${r.stages.length} 个阶段</span></div>
            <ol>${r.stages.map((s) => `<li><span>${esc(s.name)}</span><span class="fig">${onCourses(s.courses).length} 门</span></li>`).join('')}</ol>
            <div class="row-between small"><span class="muted">课程看完，刷题正确率达到 ${CFG.masteryLine}%</span><span class="stamp">已掌握</span></div>
          </article>
        </section>
        <dl class="features">
          <div><dt>学习路线</dt><dd>AI 全栈、Agent、Java 三个方向，按阶段编排视频课程。</dd></div>
          <div><dt>刷题练习</dt><dd>判断、单选、多选、简答四种题型，简答题由 AI 评分。</dd></div>
          <div><dt>AI 面试</dt><dd>文字或语音实时对话，结束后生成分阶段的面试报告。</dd></div>
        </dl>
        <div class="section-title"><h2>全部学习路线</h2><a href="#/routes">查看全部</a></div>
        <div class="grid grid-3">${routes.map(routeCard).join('')}</div>`;
      $('#heroReg').onclick = () => openLogin('register');
      $('#heroRoutes').onclick = () => go('/routes');
      return bindRouteCards(el);
    }
    const lc = course(S.last.courseId), lr = route(S.last.routeId);
    const ls = lr.stages.find((s) => s.courses.includes(lc.id));
    const lp = routeProgress(lr);
    const myRoutes = routes.filter((r) => routeProgress(r).learned || r.id === S.last.routeId);
    el.innerHTML = `
      <div class="page-title"><div><h1>${esc(S.user.nickname)}，继续上次的课程</h1><p>${esc(lr.name)}，${esc(ls.name)}</p></div></div>
      <div class="home-top">
        <article class="card continue">
          <div class="row-between wrap"><h2>${esc(lc.title)}</h2><div class="row">${statusTag(lc.id)}<button type="button" class="btn btn-primary" id="contBtn">${icon('play')}继续学习</button></div></div>
          <div class="mt-16">${progressBar(lr.name + ' 已学进度', lp.learned, lp.total)}</div>
          <div class="ledger">${ls.courses.map(course).filter((c) => c.status === 'on').map((c, i) => `<div class="ledger-row ${c.id === lc.id ? 'current' : ''}"><span class="no">${String(i + 1).padStart(2, '0')}</span><span class="grow">${esc(c.title)}</span>${statusTag(c.id)}</div>`).join('')}</div>
        </article>
        <div class="stat-col">
          <button type="button" class="stat-tile" id="tileWrong"><div class="label">待重做的错题</div><div class="value">${S.wrong.length}<small>道</small></div></button>
          <button type="button" class="stat-tile" id="tileIv"><div class="label">今日 AI 面试剩余</div><div class="value">${S.interviewLeft}<small>/ ${CFG.interviewPerDay} 次</small></div><div class="small muted mt-4">北京时间 0 点重置</div></button>
        </div>
      </div>
      <div class="section-title"><h2>我的路线进度</h2></div>
      <div class="grid grid-3">${myRoutes.map((r) => { const p = routeProgress(r); return `<article class="card hoverable" data-route="${r.id}" tabindex="0" role="link"><div class="row-between mb-12"><h3>${esc(r.name)}</h3><span class="small muted">${r.stages.length} 个阶段</span></div><div class="col" style="gap:var(--space-sm)">${progressBar('已学进度', p.learned, p.total)}${progressBar('已掌握进度', p.mastered, p.conf, 'teal')}</div></article>`; }).join('')}</div>
      <div class="section-title"><h2>全部学习路线</h2><a href="#/routes">查看全部</a></div>
      <div class="grid grid-3">${routes.map(routeCard).join('')}</div>`;
    $('#contBtn').onclick = () => go(`/route/${lr.id}?hl=${lc.id}`);
    $('#tileWrong').onclick = () => go('/wrong');
    $('#tileIv').onclick = () => go('/interview');
    bindRouteCards(el);
  }

  function pageRoutes(el) {
    S.docRef = '5.3.1 路线列表';
    const list = DB.routes.filter((r) => r.status === 'on');
    el.innerHTML = `<div class="page-title"><div><h1>学习路线</h1><p>${list.length} 条路线，所有阶段都开放，可以从任意阶段开始</p></div></div>
      <div class="grid grid-3">${list.map(routeCard).join('')}</div>`;
    bindRouteCards(el);
  }

  /* ---------------- 路线详情（需求 5.3.2 / 5.3.3） ---------------- */
  function pageRoute(el, { id }, { hl }) {
    S.docRef = '5.3.2 路线详情，5.3.3 课程';
    const r = route(id);
    if (!r) return (el.innerHTML = '<div class="empty">没有找到这条路线。<a href="#/routes">返回学习路线</a></div>');
    const p = routeProgress(r);
    el.innerHTML = `
      <a class="back" href="#/routes">返回学习路线</a>
      <section class="route-head">
        <div><h1>${esc(r.name)}</h1><p>${esc(r.desc)}</p>
          <div class="meta mt-12"><span>${r.stages.length} 个阶段</span><span>${p.total} 门课程</span><span>所有阶段都开放</span></div></div>
        <div class="col" style="gap:var(--space-sm)">${S.loggedIn ? progressBar('已学进度', p.learned, p.total) + progressBar('已掌握进度（已配题课程）', p.mastered, p.conf, 'teal') : '<button type="button" class="btn btn-primary" id="heroLogin">登录后记录进度</button>'}
          <button type="button" class="btn" id="goIv">${icon('mic')}针对本路线 AI 面试</button></div>
      </section>
      ${r.stages.map((st) => {
        const sp = stageProgress(st), sq = stageQ(st).length, siv = stageIv(st).length;
        const cs = onCourses(st.courses);
        return `<section class="card stage" aria-label="${esc(st.name)}">
          <div class="stage-head"><h3>${esc(st.name)}</h3>
            <span class="meta">${S.loggedIn ? `<span>已学 <span class="fig">${sp.learned}/${sp.total}</span></span><span>已掌握 <span class="fig">${sp.mastered}/${sp.conf}</span></span>` : `<span>${cs.length} 门课程</span><span>刷题 <span class="fig">${sq}</span> 道</span>`}</span>
            <button type="button" class="btn btn-sm" data-random="${st.id}" ${sq < CFG.randomMin ? 'disabled' : ''}>${sq < CFG.randomMin ? '随机抽题（题目建设中）' : '随机抽题'}</button>
            <button type="button" class="btn btn-sm" data-exam="${st.id}" ${sq < CFG.examMin ? 'disabled' : ''}>${sq < CFG.examMin ? '模拟考试（题目建设中）' : '模拟考试'}</button>
            <button type="button" class="btn btn-sm" data-ivstage="${st.id}" ${!siv ? 'disabled' : ''}>${!siv ? '面试本阶段（暂无面试题）' : '面试本阶段'}</button>
          </div>
          ${cs.map((co, i) => { const qn = qOf(co.id).length; return `<div class="course-row ${+hl === co.id ? 'highlight' : ''}" id="c${co.id}">
            <span class="course-no">${String(i + 1).padStart(2, '0')}</span>
            <div class="course-main"><button type="button" class="title" data-watch="${co.id}">${esc(co.title)}</button>
              <div class="course-meta meta"><span>${esc(platform(co.platformId).name)}</span><span>${co.duration}</span><span>${subject(co.subjectId).name}</span><span>${qn ? `刷题 ${qn} 道` : '未配题'}</span><a data-broken="${co.id}" tabindex="0">视频无法播放？</a></div></div>
            <div class="course-ops">${S.loggedIn ? statusTag(co.id) : ''}
              <button type="button" class="btn btn-sm btn-primary" data-watch="${co.id}">${icon('play')}观看</button>
              ${status(co.id) === 'mastered' ? '' : `<button type="button" class="btn btn-sm" data-learn="${co.id}">${learned(co.id) ? '取消已学' : '标记已学'}</button>`}
              ${qn ? `<button type="button" class="btn btn-sm" data-practice="${co.id}">去练习</button>` : ''}
            </div></div>`; }).join('') || '<div class="empty">本阶段还没有课程</div>'}
        </section>`;
      }).join('')}`;
    if ($('#heroLogin')) $('#heroLogin').onclick = () => openLogin('code');
    $('#goIv').onclick = () => needLogin(() => go('/interview?route=' + r.id));
    $$('[data-watch]', el).forEach((b) => (b.onclick = () => needLogin(() => watch(r, +b.dataset.watch))));
    $$('[data-learn]', el).forEach((b) => (b.onclick = () => needLogin(() => {
      const cid = +b.dataset.learn;
      S.progress[cid] = S.progress[cid] || {};
      S.progress[cid].s = learned(cid) ? 'opened' : 'learned';
      S.last = { routeId: r.id, courseId: cid };
      toast(learned(cid) ? '已标记为已学' : '已取消已学标记', 'success');
      rerender();
    })));
    $$('[data-practice]', el).forEach((b) => (b.onclick = () => needLogin(() => go('/practice/seq/' + b.dataset.practice))));
    $$('[data-random]', el).forEach((b) => (b.onclick = () => needLogin(() => go('/practice/random/' + b.dataset.random))));
    $$('[data-exam]', el).forEach((b) => (b.onclick = () => needLogin(() => examIntro(+b.dataset.exam))));
    $$('[data-ivstage]', el).forEach((b) => (b.onclick = () => needLogin(() => go(`/interview?route=${r.id}&stage=${b.dataset.ivstage}`))));
    $$('[data-broken]', el).forEach((b) => (b.onclick = (e) => { e.stopPropagation(); needLogin(() => videoFeedback(+b.dataset.broken)); }));
    if (hl && $('#c' + hl)) setTimeout(() => $('#c' + hl).scrollIntoView({ behavior: 'smooth', block: 'center' }), 100);
  }
  function watch(r, cid) {
    const co = course(cid);
    S.progress[cid] = S.progress[cid] || { s: 'opened' };
    S.progress[cid].opened = U.now();
    S.last = { routeId: r.id, courseId: cid };
    const mobile = window.innerWidth < 640;
    toast(mobile && co.platformId === 1 ? '正在唤起 B 站 App（未安装将打开网页）…' : `已在新窗口打开「${platform(co.platformId).name}」观看（原型不实际跳转）`);
  }
  function videoFeedback(cid) {
    const co = course(cid);
    modal({ title: '反馈视频无法播放', width: 440, body: `<p class="text-2 mb-12">课程：<b>${esc(co.title)}</b></p><div class="form-item"><label for="vfDesc">问题说明（选填）</label><textarea class="textarea" id="vfDesc" placeholder="例如：提示视频已失效，第 3 集打不开"></textarea></div><p class="small muted">运营人员处理后，会通过站内信告诉你结果。</p>`,
      footer: [{ text: '取消' }, { text: '提交反馈', type: 'primary', onClick: (c, root) => { DB.feedback.unshift({ id: U.uid(), type: 'video', targetId: cid, target: co.title, course: co.title, errType: '', desc: $('#vfDesc', root).value || '视频无法播放', user: S.user.nickname, time: U.now(), status: 'pending', count: 1 }); c(); toast('反馈已提交，感谢！', 'success'); } }] });
  }

  /* ---------------- 搜索（需求 5.3.4） ---------------- */
  function pageSearch(el, _, { q = '' }) {
    S.docRef = '5.3.4 搜索';
    const kw = q.trim();
    const rs = DB.routes.filter((r) => r.status === 'on' && (r.name + r.desc).includes(kw));
    const cs = DB.courses.filter((c) => c.status === 'on' && (c.title + c.desc).includes(kw));
    el.innerHTML = `<div class="page-title"><div><h1>搜索「${esc(kw)}」</h1><p>找到 ${rs.length} 条路线、${cs.length} 门课程。搜索不包含题目。</p></div>
      <div class="input-group" role="search" style="min-width:320px"><input class="input" id="sInput" type="search" aria-label="搜索关键词" value="${esc(kw)}" placeholder="输入关键词"><button type="button" class="btn btn-primary" id="sBtn">搜索</button></div></div>
      <h2 class="mb-12">路线</h2>
      ${rs.length ? `<div class="grid grid-3">${rs.map(routeCard).join('')}</div>` : '<div class="card empty">没有匹配的路线，换个关键词试试</div>'}
      <h2 class="mt-24 mb-12">课程</h2>
      <div class="card" style="padding-block:0">${cs.map((co) => `<div class="course-row" style="padding-inline:0"><div class="course-main"><button type="button" class="title" data-sc="${co.id}">${esc(co.title)}</button><div class="course-meta meta"><span>${subject(co.subjectId).name}</span><span>${co.duration}</span><span>${routesOfCourse(co.id).map((r) => r.name).join('、') || '暂未加入路线'}</span></div></div><button type="button" class="btn btn-sm" data-sc="${co.id}">查看课程</button></div>`).join('') || '<div class="empty">没有匹配的课程</div>'}</div>`;
    const doSearch = () => go('/search?q=' + encodeURIComponent($('#sInput').value.trim()));
    $('#sBtn').onclick = doSearch;
    $('#sInput').onkeydown = (e) => e.key === 'Enter' && doSearch();
    bindRouteCards(el);
    $$('[data-sc]', el).forEach((b) => (b.onclick = () => {
      const cid = +b.dataset.sc, rs2 = routesOfCourse(cid);
      if (!rs2.length) return toast('该课程暂未加入学习路线', 'warn');
      if (rs2.length === 1) return go(`/route/${rs2[0].id}?hl=${cid}`);
      modal({ title: '这门课属于多条路线，选择一条进入', width: 420, body: rs2.map((r) => `<button type="button" class="choice mb-8" data-pick="${r.id}"><h4>${esc(r.name)}</h4><p>${esc(r.stages.find((s) => s.courses.includes(cid)).name)}</p></button>`).join(''), footer: [{ text: '取消' }],
        onMount: (root, close) => $$('[data-pick]', root).forEach((p) => (p.onclick = () => { close(); go(`/route/${p.dataset.pick}?hl=${cid}`); })) });
    }));
  }

  /* ---------------- 刷题引擎（需求 5.4） ---------------- */
  const shuffle = (a) => a.map((x) => [Math.random(), x]).sort((m, n) => m[0] - n[0]).map((x) => x[1]);
  const answerText = (q, a) => {
    if (a === undefined || a === null || a === '') return '未作答';
    if (q.type === 'judge') return a === 0 ? '正确' : '错误';
    if (q.type === 'single') return letters[a];
    if (q.type === 'multi') return a.length ? a.slice().sort().map((i) => letters[i]).join('') : '未作答';
    return a;
  };
  const correctText = (q) => (q.type === 'judge' ? (q.answer ? '正确' : '错误') : q.type === 'single' ? letters[q.answer] : q.type === 'multi' ? q.answer.map((i) => letters[i]).join('') : '见参考答案');
  const isRight = (q, a) => (q.type === 'judge' ? (a === 0) === q.answer : q.type === 'single' ? a === q.answer : q.type === 'multi' ? Array.isArray(a) && a.length === q.answer.length && q.answer.every((i) => a.includes(i)) : null);
  const aiScore = (q, txt) => {
    const len = txt.trim().length;
    const score = len < 8 ? U.rand(15, 35) : Math.min(96, 55 + Math.round(len / 3) + U.rand(-8, 8));
    const miss = (q.points || []).filter(() => Math.random() < (score > 80 ? 0.15 : 0.45));
    return { score, comment: score >= CFG.shortPassLine ? '回答抓住了主要概念，表述较清晰。' : '回答较为简略，核心要点覆盖不足。', missing: miss };
  };
  const addWrong = (q, a) => {
    const w = S.wrong.find((x) => x.qid === q.id);
    if (w) { w.count++; w.my = answerText(q, a); w.time = U.now(); } else S.wrong.unshift({ qid: q.id, my: answerText(q, a), count: 1, time: U.now() });
  };

  function renderOptions(q, st) {
    if (q.type === 'short') return `<textarea class="textarea" id="shortAns" aria-label="你的答案" placeholder="写下你的答案" ${st.submitted ? 'disabled' : ''}>${esc(st.answer || '')}</textarea>`;
    const opts = q.type === 'judge' ? ['正确', '错误'] : q.options;
    return opts.map((o, i) => {
      let cls = 'opt' + (q.type === 'multi' ? ' multi' : '');
      const chosen = q.type === 'multi' ? (st.answer || []).includes(i) : st.answer === i;
      if (chosen) cls += ' sel';
      if (st.submitted) {
        cls += ' locked';
        const right = q.type === 'judge' ? (i === 0) === q.answer : q.type === 'single' ? i === q.answer : q.answer.includes(i);
        if (right) cls += ' right'; else if (chosen) cls += ' wrong';
      }
      return `<div class="${cls}" data-opt="${i}" role="${q.type === 'multi' ? 'checkbox' : 'radio'}" aria-checked="${chosen}" tabindex="${st.submitted ? -1 : 0}"><span class="key" aria-hidden="true">${q.type === 'judge' ? (i === 0 ? '对' : '错') : letters[i]}</span><span>${esc(o)}</span></div>`;
    }).join('');
  }
  function resultBox(q, st, examMode) {
    if (!st.submitted) return '';
    const dl = (rows) => `<dl>${rows.filter(Boolean).map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}</dl>`;
    if (q.type !== 'short') {
      const ok = isRight(q, st.answer);
      return `<div class="result" role="status"><div class="verdict ${ok ? 'ok' : 'bad'}">${ok ? '回答正确' : '回答错误'}</div>${dl([['正确答案', `<b>${correctText(q)}</b>`], !examMode && ['你的答案', answerText(q, st.answer)], ['解析', esc(q.analysis)]])}</div>`;
    }
    if (st.pending) return `<div class="result" role="status"><span class="spinner"></span> <span class="text-2">AI 评分中</span></div>`;
    if (st.self === null) return `<div class="result" role="status"><div class="alert alert-warn">今日 AI 评分次数已用完，请对照参考答案自评。</div>${dl([['参考答案', esc(q.ref)]])}<div class="row mt-12"><span>你觉得自己答得怎么样？</span><button type="button" class="btn btn-sm" data-self="1">会</button><button type="button" class="btn btn-sm btn-danger" data-self="0">不会</button></div></div>`;
    if (st.self !== undefined) return `<div class="result" role="status"><div class="verdict ${st.self ? 'ok' : 'bad'}">自评：${st.self ? '会' : '不会'}</div>${dl([['参考答案', esc(q.ref)], ['解析', esc(q.analysis)]])}</div>`;
    const ai = st.ai, ok = ai.score >= CFG.shortPassLine;
    return `<div class="result" role="status"><div class="ai-score"><div class="num">${ai.score}<span class="small muted"> 分</span></div><div><div class="verdict ${ok ? 'ok' : 'bad'}">${ok ? '达到及格线' : `低于及格线 ${CFG.shortPassLine} 分，已记入错题本`}</div><span class="tag tag-ai mt-4">AI 生成</span></div></div>
      ${dl([['AI 点评', ai.comment + (ai.missing.length ? `缺失要点：${ai.missing.join('、')}。` : '')], ['参考答案', esc(q.ref)], ['解析', esc(q.analysis)]])}</div>`;
  }
  function correctionModal(q) {
    modal({ title: '题目纠错', width: 440, body: `<p class="text-2 mb-12">题目：${esc(q.stem)}</p><div class="form-item"><label><span class="req">*</span>错误类型</label><div class="radio-group">${['题干有误', '答案有误', '解析有误', '其他'].map((t, i) => `<label class="check"><input type="radio" name="et" value="${t}" ${i === 0 ? 'checked' : ''}>${t}</label>`).join('')}</div></div><div class="form-item"><label for="cDesc"><span class="req">*</span>说明</label><textarea class="textarea" id="cDesc" placeholder="请描述你发现的问题"></textarea></div>`,
      footer: [{ text: '取消' }, { text: '提交', type: 'primary', onClick: (c, root) => {
        const d = $('#cDesc', root).value.trim(); if (!d) return toast('请填写说明', 'error');
        DB.feedback.unshift({ id: U.uid(), type: 'question', targetId: q.id, target: q.stem, course: course(q.courseId).title, errType: $('input[name=et]:checked', root).value, desc: d, user: S.user.nickname, time: U.now(), status: 'pending', count: 1 });
        c(); toast('纠错已提交，处理结果将通过站内信通知你', 'success');
      } }] });
  }

  function pagePractice(el, { mode, key }, query) {
    S.docRef = { seq: '5.4.2 按课程顺序练习', random: '5.4.3 按阶段随机抽题', wrong: '5.5 错题重做' }[mode] + '，5.4.1 判分';
    let list = [], title = '', back = '#/';
    if (mode === 'seq') { const co = course(key); list = qOf(key); title = `${co.title}，顺序练习`; const r = routesOfCourse(key)[0]; back = r ? `#/route/${r.id}?hl=${key}` : '#/'; }
    if (mode === 'random') { const { r, s } = findStage(key); const all = stageQ(s); list = shuffle(all).slice(0, CFG.randomCount); title = `${r.name}，${stageShort(s.name)}，随机抽题`; back = `#/route/${r.id}`; }
    if (mode === 'wrong') { const ids = query.ids ? query.ids.split(',').map(Number) : S.wrong.map((w) => w.qid); list = ids.map((i) => DB.questions.find((q) => q.id === i)).filter((q) => q && q.status === 'on'); title = '错题重做'; back = '#/wrong'; }
    if (!list.length) return (el.innerHTML = `<div class="card empty">这里暂时没有可练习的题目。<div class="mt-12"><a href="#/routes">返回学习路线</a></div></div>`);
    S.practice = { mode, key, title, list, idx: 0, states: list.map(() => ({ answer: null, submitted: false })), back };
    drawPractice(el);
  }
  function drawPractice(el) {
    const P = S.practice, q = P.list[P.idx], st = P.states[P.idx];
    const hasShort = P.list.some((x) => x.type === 'short');
    el.innerHTML = `<div class="quiz-wrap">
      <a class="back" href="${P.back}">返回</a>
      <div class="quiz-top"><div><h1>${esc(P.title)}</h1>${P.mode === 'seq' ? `<p>按顺序作答，每题提交后立即显示结果。正确率达到 ${CFG.masteryLine}% 即为已掌握。</p>` : ''}</div>
        <div class="meta">${hasShort ? `<span>今日 AI 评分剩余 <span class="fig">${S.scoreLeft}</span> 次</span>` : ''}<span>第 <span class="fig">${P.idx + 1}/${P.list.length}</span> 题</span></div></div>
      <div class="progress mb-16" role="progressbar" aria-label="答题进度" aria-valuemin="0" aria-valuemax="${P.list.length}" aria-valuenow="${P.states.filter((s) => s.submitted).length}"><span style="--p:${(P.states.filter((s) => s.submitted).length / P.list.length).toFixed(3)}"></span></div>
      <div class="card">
        <div class="meta"><span class="tag tag-primary">${typeName[q.type]}</span><span>${esc(course(q.courseId).title)}</span></div>
        <div class="q-stem">${esc(q.stem)}</div>
        ${q.type === 'short' && !st.submitted && S.scoreLeft <= 0 ? '<div class="alert alert-warn mb-12">今日 AI 评分次数已用完，提交后将展示参考答案，由你自评"会 / 不会"。</div>' : ''}
        ${q.type === 'short' && !st.submitted && S.scoreLeft > 0 ? '<p class="small muted mb-8">提交后由 AI 评分，消耗 1 次 AI 评分次数。</p>' : ''}
        <div id="opts">${renderOptions(q, st)}</div>
        <div id="failBox"></div>
        ${resultBox(q, st)}
        <div class="quiz-foot">
          <button type="button" class="btn btn-ghost btn-sm" id="corr">${icon('flag')}题目纠错</button>
          <div class="row">${P.idx > 0 ? '<button type="button" class="btn" id="prev">上一题</button>' : ''}
            ${!st.submitted ? `<button type="button" class="btn btn-primary" id="submit">提交答案</button>` : st.self === null || st.pending ? '' : `<button type="button" class="btn btn-primary" id="next">${P.idx < P.list.length - 1 ? '下一题' : '完成练习'}</button>`}</div>
        </div>
      </div></div>`;
    $$('[data-opt]', el).forEach((o) => {
      o.onclick = () => {
        const i = +o.dataset.opt;
        if (q.type === 'multi') { st.answer = st.answer || []; st.answer = st.answer.includes(i) ? st.answer.filter((x) => x !== i) : [...st.answer, i]; } else st.answer = i;
        drawPractice(el);
        const again = $(`[data-opt="${i}"]`, el); if (again) again.focus();
      };
      o.onkeydown = (ev) => { if (ev.key === ' ' || ev.key === 'Enter') { ev.preventDefault(); o.click(); } };
    });
    $('#corr').onclick = () => correctionModal(q);
    if ($('#prev')) $('#prev').onclick = () => { P.idx--; drawPractice(el); };
    if ($('#next')) $('#next').onclick = () => { if (P.idx < P.list.length - 1) { P.idx++; drawPractice(el); } else finishPractice(el); };
    $$('[data-self]', el).forEach((b) => (b.onclick = () => { st.self = b.dataset.self === '1'; st.correct = st.self; if (!st.correct) addWrong(q, st.answer); afterJudge(q, st); drawPractice(el); }));
    if ($('#submit')) $('#submit').onclick = () => {
      if (q.type === 'short') {
        const txt = $('#shortAns').value.trim();
        if (!txt) return toast('请先作答', 'warn');
        const bad = hasSensitive(txt);
        if (bad) return toast(`内容包含敏感词「${bad}」，请修改后提交（不消耗次数）`, 'error');
        st.answer = txt;
        if (S.scoreLeft <= 0) { st.submitted = true; st.self = null; return drawPractice(el); }
        st.submitted = true; st.pending = true; drawPractice(el);
        setTimer(() => {
          st.pending = false;
          if (S.demo.aiFail) {
            st.submitted = false; drawPractice(el);
            $('#failBox').innerHTML = '<div class="alert alert-error mt-12" role="alert">评分服务暂时不可用，已自动重试 1 次。请稍后重新提交，本次不扣次数。</div>';
            return;
          }
          st.ai = aiScore(q, txt); S.scoreLeft--; st.correct = st.ai.score >= CFG.shortPassLine;
          if (!st.correct) addWrong(q, txt);
          afterJudge(q, st); drawPractice(el);
        }, 1200);
        return;
      }
      if (st.answer === null || (Array.isArray(st.answer) && !st.answer.length)) return toast('请先选择答案', 'warn');
      st.submitted = true; st.correct = isRight(q, st.answer);
      if (!st.correct) addWrong(q, st.answer);
      afterJudge(q, st); drawPractice(el);
    };
  }
  function afterJudge(q, st) {
    if (S.practice.mode === 'wrong' && st.correct) { S.wrong = S.wrong.filter((w) => w.qid !== q.id); toast('回答正确，已移出错题本', 'success'); }
  }
  function finishPractice(el) {
    const P = S.practice;
    const right = P.states.filter((s) => s.correct).length, total = P.list.length, acc = pct(right, total);
    let note = '';
    if (P.mode === 'seq') {
      const cid = +P.key;
      if (status(cid) === 'mastered') note = `<div class="row-between alert alert-info"><span>这门课已经达成掌握，状态会一直保留。</span><span class="stamp">已掌握</span></div>`;
      else if (acc >= CFG.masteryLine) { S.progress[cid] = { ...(S.progress[cid] || {}), s: 'mastered' }; note = `<div class="row-between alert alert-info"><span>正确率 ${acc}%，达到 ${CFG.masteryLine}%。「${esc(course(cid).title)}」记为已掌握。</span><span class="stamp">已掌握</span></div>`; }
      else note = `<div class="alert alert-info">正确率 ${acc}%，还差 ${CFG.masteryLine - acc} 个百分点。正确率按每题最近一次作答计算，重新练习可以提升。</div>`;
    }
    S.practiceLog.unshift({ title: P.title, score: `${right}/${total}`, time: U.now() });
    el.innerHTML = `<div class="quiz-wrap"><div class="card">
      <h1>练习完成</h1><p class="muted mt-4">${esc(P.title)}</p>
      <div class="row mt-24" style="gap:var(--space-lg);align-items:baseline"><div class="big-score">${acc}<small>%</small></div><div class="text-2">答对 <span class="fig">${right}/${total}</span> 题</div></div>
      <div class="mt-24">${note}</div>
      <div class="row mt-24 wrap"><a class="btn btn-primary" href="${P.back}">返回路线</a><button type="button" class="btn" id="again">重新练习</button><a class="btn btn-ghost" href="#/wrong">查看错题本（${S.wrong.length}）</a></div></div></div>`;
    $('#again').onclick = rerender;
  }

  /* ---------------- 模拟考试（需求 5.4.4、8.5） ---------------- */
  function examIntro(sid) {
    const { r, s } = findStage(sid);
    if (S.exam && S.exam.stageId === sid) return go('/exam/' + sid);
    const pool = stageQ(s);
    const cfg = CFG.exam;
    const plan = Object.keys(cfg).map((t) => ({ t, n: Math.min(cfg[t][0], pool.filter((q) => q.type === t).length), pt: cfg[t][1] }));
    const hasShort = plan.find((p) => p.t === 'short').n > 0;
    const avail = S.scoreLeft - S.scoreReserved;
    modal({ title: '模拟考试', width: 480, body: `
      <p class="text-2">${esc(r.name)}，${esc(s.name)}</p>
      <div class="table-wrap mt-12"><table class="table"><thead><tr><th>题型</th><th>题数</th><th>单题分值</th></tr></thead><tbody>${plan.map((p) => `<tr><td>${typeName[p.t]}</td><td class="fig">${p.n}${p.n < cfg[p.t][0] ? `<span class="small muted">（配置 ${cfg[p.t][0]}，题量不足）</span>` : ''}</td><td class="fig">${p.pt} 分</td></tr>`).join('')}</tbody></table></div>
      <ul class="text-2 mt-12" style="padding-left:var(--space-md);margin-bottom:0">
        <li>限时 ${CFG.examMinutes} 分钟，中途离开倒计时继续，到时自动交卷；总分换算为百分制。</li>
        <li>考试过程中不显示对错，交卷后统一判分；成绩不计入"已掌握"判定。</li>
        ${hasShort ? `<li>试卷含简答题：开考时预留 1 次 AI 评分次数，交卷评分成功后扣减，主动放弃则释放。当前可用 <b>${avail}</b> 次。</li>` : '<li>试卷不含简答题，不消耗 AI 评分次数。</li>'}
      </ul>${hasShort && avail <= 0 ? '<div class="alert alert-error mt-12">今日 AI 评分次数已用完，无法开始含简答题的考试。</div>' : ''}`,
      footer: [{ text: '取消' }, { text: '开始考试', type: 'primary', onClick: (c) => {
        if (hasShort && avail <= 0) return toast('今日 AI 评分次数已用完', 'error');
        c();
        const qs = plan.flatMap((p) => shuffle(pool.filter((q) => q.type === p.t)).slice(0, p.n).map((q) => ({ ...q, pt: p.pt })));
        if (S.exam && S.exam.hasShort) S.scoreReserved = Math.max(0, S.scoreReserved - 1);
        if (hasShort) S.scoreReserved++;
        S.exam = { routeId: r.id, stageId: sid, title: `${r.name}，${s.name}`, qs, answers: {}, start: Date.now(), duration: CFG.examMinutes * 60, idx: 0, hasShort };
        go('/exam/' + sid);
      } }] });
  }
  function pageExam(el, { sid }) {
    S.docRef = '5.4.4 模拟考试，8.5 状态流转';
    const E = S.exam;
    if (!E || E.stageId !== +sid) { el.innerHTML = '<div class="card empty">没有进行中的考试</div>'; return; }
    const left = () => E.duration - Math.floor((Date.now() - E.start) / 1000);
    const draw = () => {
      const q = E.qs[E.idx], a = E.answers[E.idx];
      const st = { answer: a === undefined ? null : a, submitted: false };
      el.innerHTML = `<a class="back" href="#/route/${E.routeId}">返回路线，倒计时继续</a>
        <div class="page-title"><div><h1>模拟考试</h1><p>${esc(E.title)}</p></div></div>
        <div class="grid exam-grid">
          <div class="card"><div class="row-between"><div class="meta"><span class="tag tag-primary">${typeName[q.type]}</span><span class="fig">${q.pt} 分</span></div><span class="small muted">第 <span class="fig">${E.idx + 1}/${E.qs.length}</span> 题</span></div>
            <div class="q-stem">${esc(q.stem)}</div><div id="opts">${renderOptions(q, st)}</div>
            <div class="quiz-foot"><span></span><div class="row">${E.idx > 0 ? '<button type="button" class="btn" id="prev">上一题</button>' : ''}${E.idx < E.qs.length - 1 ? '<button type="button" class="btn btn-primary" id="next">下一题</button>' : '<button type="button" class="btn btn-primary" id="sub2">交卷</button>'}</div></div></div>
          <aside class="card exam-side" aria-label="答题卡">
            <div class="row-between"><span class="muted">剩余时间</span><span class="timer" id="timer" role="timer" aria-live="off">${U.mmss(left())}</span></div>
            ${E.hasShort ? '<p class="small muted mt-8">已预留 1 次 AI 评分次数，交卷评分成功后扣减。</p>' : ''}
            <h4 class="mt-16 mb-8">答题卡</h4>
            <div class="qnav">${E.qs.map((_, i) => `<button type="button" class="${E.answers[i] !== undefined && E.answers[i] !== '' && !(Array.isArray(E.answers[i]) && !E.answers[i].length) ? 'done' : ''} ${i === E.idx ? 'cur' : ''}" data-go="${i}" aria-label="第 ${i + 1} 题">${i + 1}</button>`).join('')}</div>
            <button type="button" class="btn btn-primary btn-block mt-16" id="sub">交卷</button>
            <button type="button" class="btn btn-ghost btn-block mt-8" id="quit">放弃考试</button></aside></div>`;
      $$('[data-opt]', el).forEach((o) => (o.onclick = () => {
        const i = +o.dataset.opt;
        if (q.type === 'multi') { const cur = E.answers[E.idx] || []; E.answers[E.idx] = cur.includes(i) ? cur.filter((x) => x !== i) : [...cur, i]; } else E.answers[E.idx] = i;
        draw();
      }));
      if ($('#shortAns')) $('#shortAns').oninput = (e) => (E.answers[E.idx] = e.target.value);
      $$('[data-go]', el).forEach((s) => (s.onclick = () => { E.idx = +s.dataset.go; draw(); }));
      if ($('#prev')) $('#prev').onclick = () => { E.idx--; draw(); };
      if ($('#next')) $('#next').onclick = () => { E.idx++; draw(); };
      const askSubmit = () => {
        const un = E.qs.filter((_, i) => E.answers[i] === undefined || E.answers[i] === '' || (Array.isArray(E.answers[i]) && !E.answers[i].length)).length;
        confirm('确认交卷', un ? `还有 <b>${un}</b> 道题未作答，交卷后未作答的题计 0 分。` : '确认提交试卷吗？', () => submitExam(false), '交卷');
      };
      $('#sub').onclick = askSubmit;
      if ($('#sub2')) $('#sub2').onclick = askSubmit;
      $('#quit').onclick = () => confirm('放弃考试', '放弃后本场考试不出成绩，预留的 AI 评分次数将释放。', () => { if (E.hasShort) S.scoreReserved--; S.exam = null; toast('已放弃考试'); go('/route/' + E.routeId); }, '放弃考试', true);
    };
    draw();
    setTick(() => {
      const l = left(), t = $('#timer');
      if (t) { t.textContent = U.mmss(l); t.classList.toggle('warn', l < 300); }
      if (l <= 0) { clearTimers(); toast('考试时间到，已自动交卷', 'warn'); submitExam(true); }
    }, 1000);
  }
  function submitExam() {
    const E = S.exam;
    const items = E.qs.map((q, i) => {
      const a = E.answers[i];
      if (q.type === 'short') return { q, a, pending: true };
      const ok = isRight(q, a);
      if (!ok) addWrong(q, a);
      return { q, a, ok, got: ok ? q.pt : 0 };
    });
    S.examResult = { title: E.title, routeId: E.routeId, items, hasShort: E.hasShort, time: U.now() };
    S.exam = null;
    scoreExamShort();
    go('/exam-result');
  }
  function scoreExamShort() {
    const R = S.examResult;
    const shorts = R.items.filter((it) => it.pending);
    if (!shorts.length) { R.shortPending = false; return; }
    if (S.demo.aiFail) { R.shortPending = true; return; }
    shorts.forEach((it) => {
      const txt = (it.a || '').trim();
      it.ai = txt ? aiScore(it.q, txt) : { score: 0, comment: '未作答。', missing: [] };
      it.ok = it.ai.score >= CFG.shortPassLine; it.got = Math.round((it.q.pt * it.ai.score) / 100); it.pending = false;
      if (!it.ok) addWrong(it.q, txt);
    });
    if (R.hasShort) { S.scoreReserved = Math.max(0, S.scoreReserved - 1); S.scoreLeft = Math.max(0, S.scoreLeft - 1); }
    R.shortPending = false;
  }
  function pageExamResult(el) {
    S.docRef = '5.4.4 模拟考试，8.6 评分失败处理';
    const R = S.examResult;
    if (!R) return (el.innerHTML = '<div class="card empty">暂无考试结果</div>');
    const full = R.items.reduce((s, it) => s + it.q.pt, 0);
    const got = R.items.reduce((s, it) => s + (it.got || 0), 0);
    const score = Math.round((got / full) * 100);
    const byType = Object.keys(typeName).map((t) => { const its = R.items.filter((i) => i.q.type === t); return { t, n: its.length, ok: its.filter((i) => i.ok).length, got: its.reduce((s, i) => s + (i.got || 0), 0), full: its.reduce((s, i) => s + i.q.pt, 0) }; }).filter((x) => x.n);
    el.innerHTML = `<div class="quiz-wrap"><a class="back" href="#/route/${R.routeId}">返回路线</a>
      <div class="card"><h1>模拟考试成绩</h1><p class="muted mt-4">${esc(R.title)}</p>
        <div class="row mt-24" style="gap:var(--space-lg);align-items:baseline"><div class="big-score">${R.shortPending ? '--' : score}<small> / 100</small></div>${R.shortPending ? '' : `<p class="text-2">原始得分 <span class="fig">${got}/${full}</span>，已换算为百分制。成绩不计入"已掌握"。</p>`}</div>
        ${R.shortPending ? '<div class="alert alert-warn mt-16" role="alert"><p>简答题评分中。评分服务暂时不可用，已自动重试 1 次。客观题已经判分，评分成功后才会扣减预留的 AI 评分次数。</p><button type="button" class="btn btn-sm mt-8" id="rescore">重新评分</button></div>' : ''}
        <h2 class="mt-24 mb-8">各题型得分</h2>
        <div class="table-wrap"><table class="table"><thead><tr><th>题型</th><th>题数</th><th>答对</th><th>得分</th></tr></thead><tbody>${byType.map((b) => `<tr><td>${typeName[b.t]}</td><td class="fig">${b.n}</td><td class="fig">${b.t === 'short' && R.shortPending ? '评分中' : b.ok}</td><td class="fig">${b.t === 'short' && R.shortPending ? '评分中' : `${b.got}/${b.full}`}</td></tr>`).join('')}</tbody></table></div>
        <h2 class="mt-24 mb-8">逐题解析</h2>${R.items.map((it, i) => `<div class="result"><div class="row-between"><b>${i + 1}. ${esc(it.q.stem)}</b>${it.pending ? '<span class="tag tag-orange">评分中</span>' : it.ok ? '<span class="tag tag-green">正确</span>' : '<span class="tag tag-red">错误</span>'}</div>
          <dl><dt>你的答案</dt><dd>${esc(answerText(it.q, it.a))}</dd>${it.q.type !== 'short' ? `<dt>正确答案</dt><dd>${correctText(it.q)}</dd>` : ''}${it.ai ? `<dt>AI 评分</dt><dd><span class="fig">${it.ai.score}</span> 分，${it.ai.comment} <span class="tag tag-ai">AI 生成</span></dd>` : ''}<dt>解析</dt><dd>${esc(it.q.analysis)}</dd></dl>
          <button type="button" class="btn btn-ghost btn-sm mt-8" data-cor="${it.q.id}">${icon('flag')}题目纠错</button></div>`).join('')}</div></div>`;
    if ($('#rescore')) $('#rescore').onclick = () => { if (S.demo.aiFail) return toast('评分服务仍不可用，请稍后再试（可在原型控制台关闭"模拟 AI 服务异常"）', 'error'); scoreExamShort(); toast('简答题评分完成', 'success'); rerender(); };
    $$('[data-cor]', el).forEach((a) => (a.onclick = () => correctionModal(DB.questions.find((q) => q.id === +a.dataset.cor))));
  }

  /* ---------------- 错题本（需求 5.5） ---------------- */
  function pageWrong(el, _, query) {
    S.docRef = '5.5 错题本，8.3 错题规则';
    const f = { course: query.course || '', subject: query.subject || '', type: query.type || '', route: query.route || '' };
    const items = S.wrong.map((w) => ({ w, q: DB.questions.find((q) => q.id === w.qid) })).filter(({ q }) => {
      if (f.course && q.courseId !== +f.course) return false;
      if (f.subject && course(q.courseId).subjectId !== +f.subject) return false;
      if (f.type && q.type !== f.type) return false;
      if (f.route && !route(f.route).stages.some((s) => s.courses.includes(q.courseId))) return false;
      return true;
    });
    const sel = (k, label, opts) => `<select class="select" data-f="${k}" aria-label="${label}"><option value="">${label}</option>${opts.map(([v, t]) => `<option value="${v}" ${String(f[k]) === String(v) ? 'selected' : ''}>${t}</option>`).join('')}</select>`;
    const usedCourses = [...new Set(S.wrong.map((w) => DB.questions.find((q) => q.id === w.qid).courseId))];
    const redoable = items.filter((x) => x.q.status === 'on');
    el.innerHTML = `<div class="page-title"><div><h1>错题本</h1><p>收录刷题中答错的题目，在这里重做答对后自动移出。</p></div>
      <button type="button" class="btn btn-primary" id="redoAll" ${redoable.length ? '' : 'disabled'}>重做筛选出的 ${redoable.length} 道题</button></div>
      <div class="filter-row" role="group" aria-label="筛选错题">${sel('course', '全部课程', usedCourses.map((id) => [id, course(id).title]))}${sel('subject', '全部学科', DB.subjects.map((s) => [s.id, s.name]))}${sel('type', '全部题型', Object.entries(typeName))}${sel('route', '全部路线', DB.routes.map((r) => [r.id, r.name]))}</div>
      ${items.length ? `<div class="card" style="padding-block:0">${items.map(({ w, q }) => `<article class="wrong-item">
        <div class="row-between wrap"><div class="meta"><span class="tag tag-primary">${typeName[q.type]}</span><span>${esc(course(q.courseId).title)}</span>${q.status !== 'on' ? '<span class="tag tag-orange">已下架</span>' : ''}</div><div class="meta"><span>累计答错 <span class="fig">${w.count}</span> 次</span><span class="fig">${w.time}</span></div></div>
        <h3 class="mt-8">${esc(q.stem)}</h3>
        ${q.options ? `<ol class="opt-list">${q.options.map((o, i) => `<li><span class="fig">${letters[i]}</span>${esc(o)}</li>`).join('')}</ol>` : ''}
        <dl class="answer-dl"><div><dt>你上次的答案</dt><dd class="bad">${esc(w.my)}</dd></div><div><dt>正确答案</dt><dd class="ok">${q.type === 'short' ? '见参考答案' : correctText(q)}</dd></div></dl>
        <details class="mt-8"><summary>查看解析</summary><p class="text-2 mt-4">${q.ref ? '参考答案：' + esc(q.ref) + '<br>' : ''}解析：${esc(q.analysis)}</p></details>
        <div class="row mt-12"><button type="button" class="btn btn-sm btn-primary" data-redo="${q.id}" ${q.status !== 'on' ? 'disabled title="题目已下架，不能重做"' : ''}>重做</button><button type="button" class="btn btn-sm" data-rm="${q.id}">移出错题本</button><button type="button" class="btn btn-sm btn-ghost" data-cor="${q.id}">${icon('flag')}题目纠错</button></div></article>`).join('')}</div>` : `<div class="card empty">${S.wrong.length ? '没有符合筛选条件的错题。' : '错题本是空的。答错的题会自动收进来。'}<div class="mt-12"><a href="#/routes">去学习路线刷题</a></div></div>`}`;
    $$('[data-f]', el).forEach((s) => (s.onchange = () => { const nq = { ...f, [s.dataset.f]: s.value }; go('/wrong?' + new URLSearchParams(Object.entries(nq).filter(([, v]) => v)).toString()); }));
    $$('[data-redo]', el).forEach((b) => (b.onclick = () => go('/practice/wrong/x?ids=' + b.dataset.redo)));
    $('#redoAll').onclick = () => go('/practice/wrong/x?ids=' + items.filter((x) => x.q.status === 'on').map((x) => x.q.id).join(','));
    $$('[data-rm]', el).forEach((b) => (b.onclick = () => confirm('移除错题', '确定把这道题移出错题本吗？', () => { S.wrong = S.wrong.filter((w) => w.qid !== +b.dataset.rm); toast('已移除', 'success'); rerender(); })));
    $$('[data-cor]', el).forEach((b) => (b.onclick = () => correctionModal(DB.questions.find((q) => q.id === +b.dataset.cor))));
  }

  /* ---------------- AI 面试：发起（需求 5.6.1） ---------------- */
  const ivState = { routeId: null, scope: 'route', stages: [], mode: 'text' };
  function pageInterview(el, _, query) {
    S.docRef = '5.6.1 发起面试';
    if (query.route) { ivState.routeId = +query.route; ivState.scope = query.stage ? 'stage' : 'route'; ivState.stages = query.stage ? [+query.stage] : []; }
    if (!ivState.routeId) ivState.routeId = DB.routes[0].id;
    const draw = () => {
      const r = route(ivState.routeId);
      const ivStages = r.stages.filter((s) => stageIv(s).length);
      const chosen = ivState.scope === 'route' ? ivStages : r.stages.filter((s) => ivState.stages.includes(s.id));
      const avail = chosen.reduce((n, s) => n + stageIv(s).length, 0);
      const mainN = Math.min(CFG.mainQuestions, avail);
      const tooFew = avail < CFG.interviewMin;
      const noLeft = S.interviewLeft <= 0;
      const wechat = /MicroMessenger/i.test(navigator.userAgent);
      el.innerHTML = `<div class="page-title"><div><h1>AI 面试</h1><p>选好路线、范围和模式，和 AI 面试官过一遍，结束后生成面试报告。</p></div><a class="btn" href="#/interviews">面试记录</a></div>
        <div class="grid iv-setup">
          <div class="card">
            <fieldset class="fs"><legend><h2>选择学习路线</h2></legend><div class="grid grid-3" role="radiogroup" aria-label="学习路线">${DB.routes.filter((x) => x.status === 'on').map((x) => `<button type="button" role="radio" aria-checked="${x.id === r.id}" class="choice ${x.id === r.id ? 'sel' : ''}" data-r="${x.id}"><h4>${esc(x.name)}</h4><p>${x.stages.length} 个阶段</p></button>`).join('')}</div></fieldset>
            <fieldset class="fs panel"><legend><h2>选择面试范围</h2></legend>
              <div class="seg mb-16" role="radiogroup" aria-label="面试范围"><button type="button" role="radio" aria-checked="${ivState.scope === 'route'}" class="seg-item ${ivState.scope === 'route' ? 'active' : ''}" data-scope="route">整条路线</button><button type="button" role="radio" aria-checked="${ivState.scope === 'stage'}" class="seg-item ${ivState.scope === 'stage' ? 'active' : ''}" data-scope="stage">选择阶段</button></div>
              ${ivState.scope === 'stage' ? `<p class="small muted mb-8">最多选 ${CFG.mainQuestions} 个阶段，和主问题数量一致。没有面试题的阶段不能选。</p><div>${r.stages.map((s) => { const n = stageIv(s).length; return `<label class="stage-check ${n ? '' : 'disabled'}"><span class="check"><input type="checkbox" data-st="${s.id}" ${ivState.stages.includes(s.id) ? 'checked' : ''} ${n ? '' : 'disabled'}> ${esc(s.name)}</span><span class="small muted">${n ? `面试题 ${n} 道` : '暂无面试题'}</span></label>`; }).join('')}</div>` : `<p class="text-2">覆盖本路线有面试题的 ${ivStages.length} 个阶段${ivStages.length > CFG.mainQuestions ? `，多于 ${CFG.mainQuestions} 个时随机选取 ${CFG.mainQuestions} 个` : ''}。主问题平均分到各阶段，每个阶段至少 1 题。</p>`}
            </fieldset>
            <fieldset class="fs panel"><legend><h2>选择面试模式</h2></legend><div class="grid grid-2" role="radiogroup" aria-label="面试模式">
              <button type="button" role="radio" aria-checked="${ivState.mode === 'text'}" class="choice ${ivState.mode === 'text' ? 'sel' : ''}" data-mode="text"><h4 class="row">${icon('chat')}文字面试</h4><p>AI 用文字提问，你用文字作答，适合不方便说话的时候。</p></button>
              <button type="button" role="radio" aria-checked="${ivState.mode === 'voice'}" class="choice ${ivState.mode === 'voice' ? 'sel' : ''}" data-mode="voice"><h4 class="row">${icon('mic')}语音面试</h4><p>实时语音对话，更接近真实面试。开始前先做设备检测，通过后才扣次数。</p></button></div>
              ${ivState.mode === 'voice' && wechat ? '<div class="alert alert-warn mt-12">微信内置浏览器暂不支持语音面试，请在浏览器中打开，或改用文字面试。</div>' : ''}
              <p class="small muted mt-12">整场面试保持所选模式，中途不能切换。</p></fieldset>
          </div>
          <aside class="card summary iv-side" aria-label="本场面试"><h2 class="mb-8">本场面试</h2>
            <dl><div><dt>路线</dt><dd>${esc(r.name)}</dd></div><div><dt>范围</dt><dd>${ivState.scope === 'route' ? '整条路线' : `${chosen.length} 个阶段`}</dd></div><div><dt>主问题</dt><dd><span class="fig">${mainN}</span> 道，每题最多追问 <span class="fig">${CFG.maxFollowups}</span> 次</dd></div><div><dt>时长上限</dt><dd><span class="fig">${CFG.interviewMinutes}</span> 分钟</dd></div><div><dt>今日剩余</dt><dd><span class="fig">${S.interviewLeft}/${CFG.interviewPerDay}</span> 次，文字和语音共用</dd></div></dl>
            ${tooFew && chosen.length ? '<div class="alert alert-warn mt-12">所选范围的面试题还不够，题目建设中。</div>' : ''}
            ${!chosen.length ? '<div class="alert alert-warn mt-12">请至少选择 1 个阶段。</div>' : ''}
            ${noLeft ? '<div class="alert alert-error mt-12">今日面试次数已用完，北京时间 0 点重置。</div>' : ''}
            <button type="button" class="btn btn-primary btn-block btn-lg mt-16" id="startIv" ${tooFew || noLeft || !chosen.length ? 'disabled' : ''}>${ivState.mode === 'voice' ? '进入设备检测' : '开始面试，扣除 1 次'}</button>
            <p class="small muted mt-8">${ivState.mode === 'voice' ? '设备检测通过、正式开始时扣除 1 次。' : '发起即扣除 1 次，因系统原因中断会退还。'}</p></aside>
        </div>`;
      $$('[data-r]', el).forEach((b) => (b.onclick = () => { ivState.routeId = +b.dataset.r; ivState.stages = []; draw(); }));
      $$('[data-scope]', el).forEach((b) => (b.onclick = () => { ivState.scope = b.dataset.scope; draw(); }));
      $$('[data-mode]', el).forEach((b) => (b.onclick = () => { ivState.mode = b.dataset.mode; draw(); }));
      $$('[data-st]', el).forEach((c) => (c.onchange = () => {
        const id = +c.dataset.st;
        if (c.checked && ivState.stages.length >= CFG.mainQuestions) { c.checked = false; return toast(`最多选择 ${CFG.mainQuestions} 个阶段`, 'warn'); }
        ivState.stages = c.checked ? [...ivState.stages, id] : ivState.stages.filter((x) => x !== id);
        draw();
      }));
      $('#startIv').onclick = () => {
        let stages = chosen;
        if (ivState.scope === 'route' && stages.length > CFG.mainQuestions) stages = shuffle(stages).slice(0, CFG.mainQuestions);
        S.iv = buildInterview(r, stages, ivState.mode);
        if (ivState.mode === 'text') { S.interviewLeft--; S.iv.charged = true; go('/interview/text'); }
        else go(S.demo.queue ? '/interview/queue' : '/interview/device');
      };
    };
    draw();
  }
  function buildInterview(r, stages, mode) {
    const n = Math.min(CFG.mainQuestions, stages.reduce((s, st) => s + stageIv(st).length, 0));
    const pools = stages.map((st) => ({ st, qs: shuffle(stageIv(st)) }));
    const alloc = pools.map((p, i) => Math.min(p.qs.length, Math.floor(n / pools.length) + (i < n % pools.length ? 1 : 0)));
    let deficit = n - alloc.reduce((a, b) => a + b, 0);
    pools.forEach((p, i) => { while (deficit > 0 && alloc[i] < p.qs.length) { alloc[i]++; deficit--; } });
    const items = pools.flatMap((p, i) => p.qs.slice(0, alloc[i]).map((q) => ({ stageName: p.st.name, courseId: q.courseId, qid: q.id, stem: q.stem, ref: q.ref, answers: [], follows: [] })));
    return { id: U.uid(), routeId: r.id, routeName: r.name, stageNames: stages.map((s) => s.name), mode, items, cur: 0, follow: 0, start: Date.now(), duration: CFG.interviewMinutes * 60, pausedMs: 0, log: [], ended: false, charged: false };
  }
  const followPool = ['你刚才提到的方案，能具体说说在项目中是怎么落地的吗？', '如果数据量或访问量扩大 10 倍，这个方案还适用吗？需要做哪些调整？', '这个做法有什么缺点或风险？你会如何改进？', '能举一个你实际遇到过的问题，说说是怎么排查和解决的吗？'];
  const ivLeft = (iv) => iv.duration - Math.floor((Date.now() - iv.start - iv.pausedMs) / 1000);
  /* 根据回答决定追问或进入下一题，返回面试官下一句话；null 表示面试结束 */
  function ivNext(iv, answer) {
    const it = iv.items[iv.cur];
    it.answers.push(answer);
    if (answer !== '（未作答）' && iv.follow < CFG.maxFollowups && answer.length >= 6 && Math.random() < 0.6) {
      iv.follow++;
      const f = followPool[(iv.cur + iv.follow) % followPool.length];
      it.follows.push(f);
      return f;
    }
    iv.cur++; iv.follow = 0;
    if (iv.cur < iv.items.length) return `好的，我们进入下一个问题。（第 ${iv.cur + 1} 题，${iv.items[iv.cur].stageName}）\n${iv.items[iv.cur].stem}`;
    return null;
  }
  const ivIntro = (iv) => `你好，我是本场的 AI 面试官。今天我们围绕「${iv.routeName}」进行一场模拟面试，共 ${iv.items.length} 道主问题，可能会有追问，时长上限 ${CFG.interviewMinutes} 分钟。准备好了我们就开始。\n\n第 1 题（${iv.items[0].stageName}）：${iv.items[0].stem}`;
  const ivClosing = '今天的面试就到这里，感谢你的参与！面试报告生成后会通过站内信通知你。';

  function finishInterview(iv, early) {
    iv.ended = true;
    clearTimers();
    iv.items.forEach((it, i) => { if (i >= iv.cur && !it.answers.length) it.answers = []; });
    const rec = { id: iv.id, routeName: iv.routeName, stageNames: iv.stageNames, mode: iv.mode, time: U.now(), status: S.demo.reportFail ? 'failed' : 'done', iv };
    rec.report = buildReport(iv);
    S.interviews.unshift(rec);
    S.iv = null;
    go('/interview/generating/' + rec.id + (early ? '?early=1' : ''));
  }
  function buildReport(iv) {
    const items = iv.items.map((it) => {
      const real = it.answers.filter((a) => a && a !== '（未作答）');
      const len = real.join('').length;
      const score = real.length ? Math.max(30, Math.min(95, 52 + Math.round(len / 4) + U.rand(-8, 8))) : 0;
      return { ...it, score, answered: !!real.length, comment: !real.length ? '本题未作答。' : score >= 80 ? '回答结构清晰，能够结合实际场景说明，追问中也体现了较好的思考深度。' : score >= 60 ? '基本概念掌握正确，但对实现细节和取舍的阐述还不够深入。' : '回答较为笼统，缺少原理层面的说明和实际案例支撑。', missing: !real.length ? [] : score >= 80 ? ['性能优化思路'] : ['原理说明', '具体项目案例', '方案取舍'] };
    });
    const stageMap = {};
    items.forEach((it) => { (stageMap[it.stageName] = stageMap[it.stageName] || []).push(it.score); });
    const stages = Object.entries(stageMap).map(([name, arr]) => ({ name, score: Math.round(arr.reduce((a, b) => a + b, 0) / arr.length) }));
    const total = Math.round(items.reduce((s, it) => s + it.score, 0) / items.length);
    return { total, stages, items };
  }
  const gradeOf = (s) => (s >= CFG.grade.excellent ? ['优秀', 'tag-green'] : s >= CFG.grade.good ? ['良好', 'tag-primary'] : ['待提升', 'tag-orange']);

  /* 排队（需求 5.6.3 并发与排队） */
  function pageQueue(el) {
    S.docRef = '5.6.3 语音面试，并发与排队';
    if (!S.iv) return go('/interview');
    let pos = 3;
    const draw = () => (el.innerHTML = `<div class="card narrow">
      <h1>正在排队</h1><p class="text-2 mt-8">同时进行的语音面试已达上限 ${CFG.voiceConcurrency} 场。排队期间不扣次数，轮到你后进入设备检测。</p>
      <div class="row mt-24" style="gap:var(--space-md);align-items:baseline"><div class="big-score" role="status" aria-live="polite">${pos}</div><span class="muted">人在你前面</span></div>
      <div class="row mt-24"><button type="button" class="btn btn-primary" id="qText">改用文字面试</button><button type="button" class="btn btn-ghost" id="qCancel">取消排队</button></div></div>`);
    const bind = () => { $('#qCancel').onclick = () => { S.iv = null; go('/interview'); }; $('#qText').onclick = () => { S.iv.mode = 'text'; S.interviewLeft--; S.iv.charged = true; go('/interview/text'); }; };
    draw(); bind();
    setTick(() => { pos--; if (pos <= 0) { clearTimers(); toast('轮到你了，请完成设备检测', 'success'); go('/interview/device'); } else { draw(); bind(); } }, 1800);
  }

  /* 设备检测（需求 5.6.3） */
  function pageDevice(el) {
    S.docRef = '5.6.3 语音面试，设备检测';
    if (!S.iv) return go('/interview');
    const d = { step: 1, res: {}, fail: null };
    const draw = () => {
      const stepCls = (n) => (d.res[n] === true ? 'ok' : d.res[n] === false ? 'fail' : d.step === n ? 'cur' : '');
      const mark = (n) => (d.res[n] ? '通过' : d.res[n] === false ? '未通过' : n);
      el.innerHTML = `<div class="narrow"><a class="back" href="#/interview">返回 AI 面试</a>
        <h1>设备检测</h1><p class="muted mt-4 mb-16">三项都通过后正式开始语音面试，这时才扣除 1 次。检测未通过不扣次数。</p>
        <div class="card"><div class="steps">
          <div class="step ${stepCls(1)}" style="border-top:0"><span class="no" aria-label="第 1 项 ${mark(1)}">${d.res[1] ? '对' : 1}</span><div><h4>麦克风权限</h4><p class="text-2">用来录制你的回答。录音转成文字后即删除，不保存。</p>
            ${d.step === 1 ? '<div class="row mt-12"><button type="button" class="btn btn-primary btn-sm" id="s1ok">允许使用麦克风</button><button type="button" class="btn btn-sm" id="s1no">模拟拒绝授权</button></div>' : ''}</div></div>
          <div class="step ${stepCls(2)}"><span class="no" aria-label="第 2 项 ${mark(2)}">${d.res[2] ? '对' : 2}</span><div><h4>录音试听</h4><p class="text-2">说一句话，比如"你好，我准备好了"，确认能被正确识别。</p>
            ${d.step === 2 ? `<div id="s2box" class="mt-12" role="status">${d.recording ? '<div class="row"><div class="wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i></div><span class="small muted">正在录音</span></div>' : d.heard ? `<p>识别结果：<b>${d.heard}</b></p><div class="row mt-8 wrap"><button type="button" class="btn btn-primary btn-sm" id="s2ok">识别正确</button><button type="button" class="btn btn-sm" id="s2re">重新录制</button><button type="button" class="btn btn-sm btn-ghost" id="s2no">模拟识别不到声音</button></div>` : '<button type="button" class="btn btn-primary btn-sm" id="s2rec">开始试录</button>'}</div>` : ''}</div></div>
          <div class="step ${stepCls(3)}"><span class="no" aria-label="第 3 项 ${mark(3)}">${d.res[3] ? '对' : 3}</span><div><h4>播放测试音</h4><p class="text-2">确认能听到测试音，建议戴耳机。</p>
            ${d.step === 3 ? `<div class="row mt-12"><button type="button" class="btn btn-sm" id="s3play">${d.played ? '再播放一次' : '播放测试音'}</button>${d.played ? '<button type="button" class="btn btn-primary btn-sm" id="s3ok">我听到了</button>' : ''}</div>` : ''}</div></div>
        </div>
        ${d.fail ? `<div class="panel" role="alert"><h3>检测未通过：${d.fail}</h3><p class="text-2 mt-8">检查浏览器地址栏里的麦克风权限，或换一台设备再试。本次没有扣除面试次数。</p>
          <div class="row mt-12 wrap"><button type="button" class="btn btn-primary" id="toText">改用文字面试</button><button type="button" class="btn" id="retry">重新检测</button><a class="btn btn-ghost" href="#/help">帮助中心</a></div></div>` : ''}
        ${d.res[1] && d.res[2] && d.res[3] ? `<div class="panel row-between wrap"><span>三项检测都已通过。</span><button type="button" class="btn btn-primary" id="goVoice">开始面试，扣除 1 次</button></div>` : ''}</div></div>`;
      const on = (id, fn) => $('#' + id) && ($('#' + id).onclick = fn);
      on('s1ok', () => { d.res[1] = true; d.step = 2; draw(); });
      on('s1no', () => { d.res[1] = false; d.fail = '未授权使用麦克风'; draw(); });
      on('s2rec', () => { d.recording = true; draw(); setTimer(() => { d.recording = false; d.heard = '你好，我准备好了'; draw(); }, 1600); });
      on('s2re', () => { d.heard = null; draw(); });
      on('s2no', () => { d.res[2] = false; d.fail = '识别不到声音，请检查麦克风是否正常'; draw(); });
      on('s2ok', () => { d.res[2] = true; d.step = 3; draw(); });
      on('s3play', () => { d.played = true; toast('正在播放测试音'); draw(); });
      on('s3ok', () => { d.res[3] = true; d.step = 4; draw(); });
      on('retry', () => { d.step = 1; d.res = {}; d.fail = null; d.heard = null; draw(); });
      on('toText', () => { S.iv.mode = 'text'; S.interviewLeft--; S.iv.charged = true; go('/interview/text'); });
      on('goVoice', () => { S.interviewLeft--; S.iv.charged = true; go('/interview/voice'); });
    };
    draw();
  }

  /* AI 服务异常（需求 8.6） */
  function aiFailPanel(iv, onRecover) {
    const t0 = Date.now();
    modal({ title: 'AI 服务异常', closable: false, width: 440, body: '<div class="row" role="status"><span class="spinner"></span><span>正在重新连接 AI 面试官。1 分钟内恢复就继续面试，异常期间不计入面试时长。</span></div>',
      footer: [{ text: '模拟无法恢复', onClick: (c) => { c(); iv.pausedMs += Date.now() - t0; S.interviewLeft++; toast('AI 服务无法恢复，面试已结束，已退还本场面试次数', 'warn'); finishInterview(iv, true); } },
        { text: '模拟 1 分钟内恢复', type: 'primary', onClick: (c) => { c(); iv.pausedMs += Date.now() - t0; S.demo.aiFail = false; renderProtoPanel(); toast('已恢复连接，继续面试', 'success'); onRecover(); } }] });
  }
  const ivProg = (iv) => `第 ${Math.min(iv.cur + 1, iv.items.length)}/${iv.items.length} 题${iv.follow ? `，追问 ${iv.follow}` : ''}`;
  const ivTopBar = (iv, extra = '') => `<div class="iv-bar"><div><b>${esc(iv.routeName)}</b><div class="meta"><span>${iv.mode === 'voice' ? '语音面试' : '文字面试'}</span>${extra}</div></div>
    <div class="row wrap"><span class="tag" id="ivProg">${ivProg(iv)}</span><span class="small muted">剩余</span><span class="timer" id="ivTimer" role="timer">${U.mmss(ivLeft(iv))}</span><button type="button" class="btn btn-sm btn-danger" id="ivEnd">结束面试</button></div></div>`;
  function bindIvCommon(iv) {
    $('#ivEnd').onclick = () => confirm('结束面试', '确定提前结束吗？未作答、未问到的主问题将计 0 分，已扣次数不退还。', () => finishInterview(iv, true), '结束面试', true);
    setTick(() => {
      if (iv.paused) return;
      const l = ivLeft(iv), t = $('#ivTimer');
      if (t) { t.textContent = U.mmss(l); t.classList.toggle('warn', l < 180); }
      if (l <= 0) { toast('已到面试时长上限，面试自动结束', 'warn'); finishInterview(iv, true); }
    }, 1000);
  }

  /* 文字面试的语音输入：浏览器能识别时转写真实语音，不能识别时用示例文字演示并说明原因 */
  function bindDictation() {
    const btn = $('#dictate'), ta = $('#ans'), status = $('#dictStatus'), wave = $('#dictWave');
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    let rec = null, simT = null, on = false, base = '';
    const paint = (listening, msg = '') => {
      on = listening;
      btn.classList.toggle('on', listening);
      btn.setAttribute('aria-pressed', listening);
      $('.label', btn).textContent = listening ? '结束语音输入' : '语音输入';
      wave.classList.toggle('hidden', !listening);
      status.textContent = msg;
      ta.readOnly = listening;
    };
    const release = () => {
      if (simT) { clearInterval(simT); simT = null; }
      if (rec) { rec.onresult = rec.onerror = rec.onend = null; try { rec.abort(); } catch (err) { /* 已结束 */ } rec = null; }
    };
    const stop = () => { release(); paint(false); };
    const finish = (msg) => { release(); paint(false, msg); ta.focus(); };
    const doneMsg = () => (ta.value.trim() ? '转写完成，可以修改后发送' : '');
    const simulate = (note) => {
      const text = canned[0];
      let i = 0;
      paint(true, note);
      simT = setInterval(() => {
        i += 3;
        ta.value = base + text.slice(0, i);
        ta.scrollTop = ta.scrollHeight;
        if (i >= text.length) finish(doneMsg());
      }, 60);
    };
    btn.onclick = () => {
      if (on) {
        if (rec) { status.textContent = '正在转写最后一句'; rec.stop(); } else finish(doneMsg());
        return;
      }
      base = ta.value;
      if (!SR) return simulate('当前浏览器不支持语音识别，原型用示例文字演示');
      rec = new SR();
      rec.lang = 'zh-CN';
      rec.continuous = true;
      rec.interimResults = true;
      rec.onresult = (e) => {
        let interim = '';
        for (let i = e.resultIndex; i < e.results.length; i++) {
          const t = e.results[i][0].transcript;
          if (e.results[i].isFinal) base += t; else interim += t;
        }
        ta.value = base + interim;
        ta.scrollTop = ta.scrollHeight;
      };
      rec.onerror = (e) => {
        release();
        if (e.error === 'no-speech') return paint(false, '没有听到声音，可以再试一次');
        if (e.error === 'aborted') return paint(false);
        if (e.error === 'not-allowed' && location.protocol !== 'file:') return paint(false, '无法使用麦克风，请在浏览器里允许麦克风权限，也可以直接打字');
        simulate(e.error === 'not-allowed' ? '本地文件打开时浏览器不允许使用麦克风，原型用示例文字演示' : '语音识别服务暂不可用，原型用示例文字演示');
      };
      rec.onend = () => finish(doneMsg());
      try { rec.start(); paint(true, '正在听，说完点结束'); } catch (err) { release(); simulate('语音识别服务暂不可用，原型用示例文字演示'); }
    };
    S.stopDictation = stop;
    return { stop };
  }

  /* 文字面试 */
  function pageIvText(el) {
    S.docRef = '5.6.2 面试过程，文字面试，5.6.4 中途退出';
    const iv = S.iv;
    if (!iv) return go('/interviews');
    if (!iv.log.length) iv.log.push({ who: 'ai', text: ivIntro(iv) });
    el.innerHTML = `<div class="iv-shell">${ivTopBar(iv, '<span>AI 面试官</span>')}
      <div class="chat" id="chat" role="log" aria-live="polite"></div>
      <div class="composer"><div class="composer-box"><textarea class="textarea" id="ans" aria-label="你的回答" placeholder="输入你的回答，Ctrl + Enter 发送"></textarea>
        <div class="composer-tools"><button type="button" class="btn btn-ghost btn-sm dictate" id="dictate" aria-pressed="false">${icon('mic')}<span class="label">语音输入</span></button><div class="wave hidden" id="dictWave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div><span class="small muted" id="dictStatus" role="status" aria-live="polite"></span></div></div>
        <button type="button" class="btn btn-primary" id="send">发送</button></div></div>
      <p class="small muted mt-8 center">中途离开或断网，可以在面试时长内从面试记录继续作答，倒计时按真实时间计算。</p>`;
    const chat = $('#chat');
    const drawLog = (typing) => {
      chat.innerHTML = iv.log.map((m) => `<div class="msg ${m.who === 'me' ? 'me' : m.who === 'sys' ? 'sys' : ''}">${m.who === 'sys' ? '' : `<div class="who">${m.who === 'me' ? esc(S.user.nickname.slice(0, 1)) : 'AI'}</div>`}<div><div class="bubble">${esc(m.text)}</div>${m.who === 'ai' ? '<div class="ai-flag">AI 生成</div>' : ''}</div></div>`).join('') + (typing ? '<div class="msg"><div class="who">AI</div><div class="bubble typing"><span></span><span></span><span></span></div></div>' : '');
      chat.scrollTop = chat.scrollHeight;
    };
    drawLog();
    bindIvCommon(iv);
    const dictation = bindDictation();
    const send = () => {
      if (iv.ended || iv.busy) return;
      dictation.stop();
      const v = $('#ans').value.trim();
      if (!v) return toast('请输入回答', 'warn');
      const bad = hasSensitive(v);
      if (bad) return toast(`内容包含敏感词「${bad}」，请修改后提交（不消耗次数）`, 'error');
      iv.log.push({ who: 'me', text: v }); $('#ans').value = ''; drawLog(true); iv.busy = true;
      const proceed = () => setTimer(() => {
        iv.busy = false;
        const nx = ivNext(iv, v);
        if (nx) { iv.log.push({ who: 'ai', text: nx }); drawLog(); }
        else { iv.log.push({ who: 'ai', text: ivClosing }); drawLog(); setTimer(() => finishInterview(iv), 1400); }
      }, 1300);
      if (S.demo.aiFail) { iv.busy = false; drawLog(); return aiFailPanel(iv, () => { iv.busy = true; drawLog(true); proceed(); }); }
      proceed();
    };
    $('#send').onclick = send;
    $('#ans').onkeydown = (e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) send(); };
  }

  /* 语音面试 */
  const canned = ['我理解这个问题的核心在于先把原理讲清楚。在之前的项目里，我们是先做了方案调研，然后结合业务场景做了取舍，比如在性能和可维护性之间优先保证可维护性，后面再针对瓶颈做优化。', '具体来说，我们把流程拆成了几个步骤，每一步都加了监控和日志，出现问题时可以快速定位。上线后效果还不错，响应时间下降了大约百分之三十。', '如果量级扩大，我会考虑引入缓存和异步处理，同时做好限流和降级，避免单点故障影响整体服务。'];
  function pageIvVoice(el) {
    S.docRef = '5.6.3 语音面试，对话方式与静默处理';
    const iv = S.iv;
    if (!iv) return go('/interviews');
    const V = iv.v || (iv.v = { phase: 'idle', sub: true, subText: '', tr: '', remind: false, said: 0 });
    el.innerHTML = `<div class="iv-shell">${ivTopBar(iv, '<span>AI 面试官，合成语音</span>')}
      <div class="voice-stage"><div class="orb" id="orb" aria-hidden="true">AI</div><div id="vStatus" class="strong" role="status" aria-live="polite"></div>
        <label class="row small muted"><span>字幕</span><button type="button" class="switch ${V.sub ? 'on' : ''}" id="subSw" role="switch" aria-checked="${V.sub}" aria-label="显示字幕"></button></label>
        <div class="subtitle ${V.sub ? '' : 'hidden'}" id="subBox"></div>
        <div class="transcript" id="trBox"><span class="muted small">你的回答会实时转成文字显示在这里</span></div>
        <div class="small muted" id="hint"></div></div>
      <div class="voice-ctrl" id="vCtrl"></div></div>
      <p class="small muted mt-8 center">原型没有接真实麦克风，用"模拟开口回答"演示识别。开口后停顿 ${CFG.pauseSeconds} 秒判定说完，15 秒不开口会提醒。</p>`;
    bindIvCommon(iv);
    let silence = null, pauseT = null, streamT = null, sayT = null;
    const clearLocal = () => [silence, pauseT, streamT, sayT].forEach((t) => { clearTimeout(t); clearInterval(t); });
    const setStatus = (t) => ($('#vStatus').textContent = t);
    const ctrl = (html) => ($('#vCtrl').innerHTML = html);
    $('#subSw').onclick = () => { V.sub = !V.sub; $('#subSw').classList.toggle('on', V.sub); $('#subSw').setAttribute('aria-checked', V.sub); $('#subBox').classList.toggle('hidden', !V.sub); };
    const aiSay = (text, then) => {
      clearLocal(); V.phase = 'ai'; $('#orb').className = 'orb speaking'; setStatus('AI 面试官正在说话…'); $('#hint').textContent = '';
      ctrl('<button type="button" class="btn" id="interrupt">打断面试官</button>');
      let i = 0; const box = $('#subBox'); box.textContent = '';
      sayT = setInterval(() => { i += 2; box.textContent = text.slice(0, i); if (i >= text.length) { clearInterval(sayT); sayT = setTimer(then, 700); } }, 45);
      S.timers.push(sayT);
      $('#interrupt').onclick = () => { clearLocal(); box.textContent = text; then(); };
    };
    const listen = (second) => {
      clearLocal(); V.phase = 'listen'; $('#orb').className = 'orb listening'; setStatus('请开始回答'); V.tr = '';
      $('#trBox').innerHTML = '<span class="muted small">正在聆听…</span>';
      $('#hint').textContent = second ? '已提醒一次，再过 15 秒仍未开口将记为"未作答"' : '15 秒未开口将收到提醒';
      ctrl(`<button type="button" class="btn btn-primary" id="speak">${icon('mic')}模拟开口回答</button><button type="button" class="btn" id="mute">模拟 15 秒不说话</button>`);
      const onSilence = () => {
        if (!second) aiSay('可以先说说你的思路。', () => listen(true));
        else { toast('本题记为"未作答"，进入下一题', 'warn'); respond('（未作答）'); }
      };
      silence = setTimeout(onSilence, 15000); S.timers.push(silence);
      $('#speak').onclick = speak;
      $('#mute').onclick = () => { clearTimeout(silence); onSilence(); };
    };
    const speak = () => {
      clearLocal(); V.phase = 'speaking'; setStatus('正在聆听你的回答…');
      const text = canned[V.said++ % canned.length]; let i = 0;
      $('#hint').textContent = '';
      ctrl('<div class="wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div><button type="button" class="btn btn-primary" id="done">我答完了</button>');
      $('#done').onclick = () => { clearLocal(); respond(text.slice(0, Math.max(i, 12))); };
      streamT = setInterval(() => {
        i += 3; $('#trBox').textContent = text.slice(0, i);
        if (i >= text.length) {
          clearInterval(streamT); let n = CFG.pauseSeconds;
          $('#hint').textContent = `检测到停顿，${n} 秒后视为说完…`;
          pauseT = setInterval(() => { n--; $('#hint').textContent = `检测到停顿，${n} 秒后视为说完…`; if (n <= 0) { clearInterval(pauseT); respond(text); } }, 1000);
          S.timers.push(pauseT);
        }
      }, 60);
      S.timers.push(streamT);
    };
    const respond = (answer) => {
      clearLocal(); V.phase = 'think'; $('#orb').className = 'orb'; setStatus('面试官思考中…'); $('#hint').textContent = ''; ctrl('<span class="spinner"></span>');
      const go2 = () => setTimer(() => {
        const nx = ivNext(iv, answer);
        if ($('#ivProg')) $('#ivProg').textContent = ivProg(iv);
        if (nx) aiSay(nx, () => listen(false));
        else aiSay(ivClosing, () => finishInterview(iv));
      }, 1500);
      if (S.demo.aiFail) return aiFailPanel(iv, go2);
      go2();
    };
    ctrl('<span class="spinner"></span>');
    setStatus('正在连接 AI 面试官…');
    const opening = V.started ? `欢迎回来，我们继续。第 ${iv.cur + 1} 题（${iv.items[iv.cur].stageName}）：${iv.items[iv.cur].stem}` : ivIntro(iv);
    V.started = true;
    setTimer(() => aiSay(opening, () => listen(false)), 800);
  }

  /* 报告生成中 / 报告（需求 5.6.5） */
  function pageGenerating(el, { id }, { early }) {
    S.docRef = '5.6.5 面试报告，8.6 报告生成失败';
    const rec = S.interviews.find((x) => x.id === +id);
    el.innerHTML = `<div class="card narrow" role="status"><div class="row"><span class="spinner"></span><h1>正在生成面试报告</h1></div><p class="text-2 mt-8">${early ? '面试已结束，未作答和未问到的主问题计 0 分。' : '感谢参加本场面试。'}报告一般在 1 分钟内生成。</p>
      <div class="col mt-24"><div class="skeleton" style="width:60%"></div><div class="skeleton" style="width:90%"></div><div class="skeleton" style="width:75%"></div></div></div>`;
    setTimer(() => {
      if (rec.status === 'failed') {
        el.innerHTML = `<div class="card narrow" role="alert"><h1>报告生成失败</h1><p class="text-2 mt-8">已经自动重试 3 次。你可以稍后重新生成，不会额外扣除次数。</p><div class="row mt-24"><button type="button" class="btn btn-primary" id="regen">重新生成</button><a class="btn btn-ghost" href="#/interviews">回到面试记录</a></div></div>`;
        $('#regen').onclick = () => { rec.status = 'done'; S.demo.reportFail = false; renderProtoPanel(); pushMsg('面试报告已生成', `你的「${rec.routeName}」${rec.mode === 'voice' ? '语音' : '文字'}面试报告已生成。`, '/report/' + rec.id); go('/report/' + rec.id); };
        return;
      }
      pushMsg('面试报告已生成', `你的「${rec.routeName}」${rec.mode === 'voice' ? '语音' : '文字'}面试报告已生成，点击查看。`, '/report/' + rec.id);
      go('/report/' + rec.id);
    }, 1800);
  }
  function pageReport(el, { id }) {
    S.docRef = '5.6.5 面试报告，计分规则与四部分内容';
    const rec = S.interviews.find((x) => x.id === +id);
    if (!rec || rec.status !== 'done') return (el.innerHTML = '<div class="card empty">报告还没有生成。<div class="mt-12"><a href="#/interviews">回到面试记录</a></div></div>');
    const R = rec.report, [g, gc] = gradeOf(R.total);
    const C = 2 * Math.PI * 60;
    const weak = R.stages.filter((s) => s.score < 70);
    el.innerHTML = `<a class="back" href="#/interviews">回到面试记录</a>
      <div class="page-title"><div><h1>面试报告</h1><div class="meta mt-4"><span>${esc(rec.routeName)}</span><span>${rec.mode === 'voice' ? '语音面试' : '文字面试'}</span><span class="fig">${rec.time}</span><span class="tag tag-ai">AI 生成</span></div></div></div>
      <div class="grid report-top">
        <section class="card" aria-labelledby="rTotal"><h2 id="rTotal" class="mb-16">总分与等级</h2><div class="row wrap" style="gap:var(--space-lg)">
          <div class="score-ring"><svg viewBox="0 0 140 140" aria-hidden="true"><circle class="track" cx="70" cy="70" r="60" stroke-width="10"/><circle class="val" cx="70" cy="70" r="60" stroke-width="10" stroke-dasharray="${(C * R.total) / 100} ${C}"/></svg><div class="center"><div><b>${R.total}</b><span class="small muted">/ 100</span></div></div></div>
          <div class="grow"><span class="tag ${gc}">${g}</span><p class="text-2 mt-12">总分是 ${R.items.length} 道主问题得分的平均分，每道题连同追问整体评分。未作答或未问到的主问题计 0 分。</p>
            <dl class="grade-dl mt-12"><div><dt>优秀</dt><dd class="fig">≥${CFG.grade.excellent}</dd></div><div><dt>良好</dt><dd class="fig">${CFG.grade.good}–${CFG.grade.excellent - 1}</dd></div><div><dt>待提升</dt><dd class="fig">&lt;${CFG.grade.good}</dd></div></dl></div></div></section>
        <section class="card" aria-labelledby="rStage"><h2 id="rStage" class="mb-8">分阶段得分</h2><div class="chart" id="stageChart"></div>
          <table class="table mt-12"><caption class="visually-hidden">分阶段得分表</caption><thead><tr><th>阶段</th><th>得分</th></tr></thead><tbody>${R.stages.map((s) => `<tr><td>${esc(s.name)}</td><td class="fig">${s.score}</td></tr>`).join('')}</tbody></table></section>
      </div>
      <section class="card mt-16" aria-labelledby="rItems"><h2 id="rItems" class="mb-8">逐题点评</h2>${R.items.map((it, i) => `<div class="qa-item ${i === 0 ? 'open' : ''}"><button type="button" class="qa-head" aria-expanded="${i === 0}"><span class="course-no">${String(i + 1).padStart(2, '0')}</span><span class="grow"><span class="strong">${esc(it.stem)}</span><span class="meta"><span>${esc(it.stageName)}</span>${it.follows.length ? `<span>追问 ${it.follows.length} 次</span>` : ''}</span></span>${it.answered ? `<span class="fig">${it.score}</span><span class="small muted">分</span>` : '<span class="tag tag-orange">未作答</span>'}</button>
        <div class="qa-body"><dl><dt>你的回答</dt><dd>${it.answered ? esc(it.answers.filter((a) => a !== '（未作答）').join('\n\n追问回答：\n')) : '未作答'}${rec.mode === 'voice' && it.answered ? '\n<span class="small muted">语音转写文字</span>' : ''}</dd>${it.follows.length ? `<dt>追问</dt><dd>${it.follows.map(esc).join('\n')}</dd>` : ''}<dt>AI 点评</dt><dd>${it.comment}</dd>${it.missing.length ? `<dt>缺失要点</dt><dd>${it.missing.join('、')}</dd>` : ''}<dt>参考答案</dt><dd>${esc(it.ref)}</dd></dl></div></div>`).join('')}</section>
      <section class="card mt-16" aria-labelledby="rAdvice"><h2 id="rAdvice" class="mb-8">学习建议</h2>${weak.length ? weak.map((s) => { const r = DB.routes.find((x) => x.stages.some((st) => st.name === s.name)); const st = r && r.stages.find((x) => x.name === s.name); const cs = st ? onCourses(st.courses) : []; return `<div class="advice"><p><b>${esc(s.name)}</b> 得分 <span class="fig">${s.score}</span>。建议先回顾这个阶段的核心原理，再结合项目案例练习表达。</p><div class="row wrap mt-8">${cs.map((co) => `<a class="btn btn-sm" href="#/route/${r.id}?hl=${co.id}">${esc(co.title)}</a>${qOf(co.id).length ? `<a class="btn btn-sm btn-ghost" href="#/practice/seq/${co.id}">练习这门课</a>` : ''}`).join('')}</div></div>`; }).join('') : '<p class="text-2">各阶段表现都不错，可以挑战下一条路线。</p>'}</section>`;
    $$('.qa-head', el).forEach((h) => (h.onclick = () => { const open = h.parentElement.classList.toggle('open'); h.setAttribute('aria-expanded', open); }));
    const box = $('#stageChart');
    if (R.stages.length >= 3) U.radarChart(box, { labels: R.stages.map((s) => stageShort(s.name)), values: R.stages.map((s) => s.score) });
    else box.innerHTML = R.stages.map((s) => `<div class="mb-12">${progressBar(esc(s.name), s.score, 100)}</div>`).join('') + '<p class="small muted">本场只覆盖了不到 3 个阶段，用条形图展示。</p>';
  }

  /* ---------------- 学习记录 / 面试记录（需求 5.7、5.8） ---------------- */
  function pageRecords(el, _, { tab = 'track', route: rf = '' }) {
    S.docRef = '5.7 学习记录';
    const rows = DB.courses.filter((c) => S.progress[c.id]).filter((c) => !rf || route(rf).stages.some((s) => s.courses.includes(c.id)));
    el.innerHTML = `<div class="page-title"><div><h1>学习记录</h1><p>打开过、标记过和掌握了的课程，以及每次刷题的成绩。</p></div><select class="select" id="rf" aria-label="按路线筛选" style="width:auto"><option value="">全部路线</option>${DB.routes.map((r) => `<option value="${r.id}" ${+rf === r.id ? 'selected' : ''}>${r.name}</option>`).join('')}</select></div>
      <div class="grid grid-3 mb-16">${DB.routes.filter((r) => !rf || r.id === +rf).map((r) => { const p = routeProgress(r); return `<div class="card card-sm"><h3>${esc(r.name)}</h3><div class="col mt-8">${progressBar('已学', p.learned, p.total)}${progressBar('已掌握', p.mastered, p.conf, 'teal')}</div></div>`; }).join('')}</div>
      <div class="card"><div class="tabs" role="tablist"><button type="button" role="tab" aria-selected="${tab === 'track'}" class="tab btn-link ${tab === 'track' ? 'active' : ''}" data-t="track">学习轨迹</button><button type="button" role="tab" aria-selected="${tab === 'practice'}" class="tab btn-link ${tab === 'practice' ? 'active' : ''}" data-t="practice">刷题记录</button></div>
      ${tab === 'track' ? `<div class="table-wrap"><table class="table"><thead><tr><th>课程</th><th>学科</th><th>状态</th><th>最近打开</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody>${rows.map((c) => `<tr><td>${esc(c.title)}</td><td>${subject(c.subjectId).name}</td><td>${statusTag(c.id)}</td><td class="fig">${S.progress[c.id].opened || '未打开'}</td><td class="ops"><a class="btn-link" href="#/route/${(routesOfCourse(c.id)[0] || {}).id}?hl=${c.id}">查看</a></td></tr>`).join('') || '<tr><td colspan="5"><div class="empty">还没有学习记录。从一条学习路线开始吧。</div></td></tr>'}</tbody></table></div>`
      : `<div class="table-wrap"><table class="table"><thead><tr><th>练习</th><th>成绩</th><th>时间</th></tr></thead><tbody>${S.practiceLog.map((p) => `<tr><td>${esc(p.title)}</td><td class="fig">${p.score.replace(/\s/g, '')}</td><td class="fig">${p.time}</td></tr>`).join('')}</tbody></table></div><p class="small muted mt-8">历史记录保留作答时的题目，题目之后被修改不影响已出的成绩。</p>`}</div>`;
    $('#rf').onchange = (e) => go(`/records?tab=${tab}${e.target.value ? '&route=' + e.target.value : ''}`);
    $$('[data-t]', el).forEach((t) => (t.onclick = () => go(`/records?tab=${t.dataset.t}${rf ? '&route=' + rf : ''}`)));
  }
  function pageInterviews(el) {
    S.docRef = '5.8 面试记录';
    const ongoing = S.iv && !S.iv.ended && S.iv.charged ? [{ id: 'cur', routeName: S.iv.routeName, stageNames: S.iv.stageNames, mode: S.iv.mode, time: '进行中', status: 'ongoing' }] : [];
    const list = [...ongoing, ...S.interviews];
    el.innerHTML = `<div class="page-title"><div><h1>面试记录</h1><p>今日剩余面试次数 <span class="fig">${S.interviewLeft}/${CFG.interviewPerDay}</span></p></div><a class="btn btn-primary" href="#/interview">发起新面试</a></div>
      <div class="card"><div class="table-wrap"><table class="table"><thead><tr><th>面试时间</th><th>路线</th><th>范围</th><th>模式</th><th>总分</th><th>等级</th><th>状态</th><th><span class="visually-hidden">操作</span></th></tr></thead><tbody>
      ${list.map((x) => { const st = { done: '<span class="tag tag-green">已结束</span>', failed: '<span class="tag tag-red">报告生成失败</span>', ongoing: '<span class="tag tag-orange">进行中</span>' }[x.status];
        const op = x.status === 'done' ? `<a class="btn-link" href="#/report/${x.id}">查看报告</a>` : x.status === 'failed' ? `<button type="button" class="btn-link" data-regen="${x.id}">重新生成</button>` : `<a class="btn-link" href="#/interview/${x.mode}">继续作答</a>`;
        return `<tr><td class="fig">${x.time}</td><td>${esc(x.routeName)}</td><td>${x.stageNames.length > 2 ? x.stageNames.length + ' 个阶段' : x.stageNames.map((n) => esc(stageShort(n))).join('、')}</td><td>${x.mode === 'voice' ? '语音' : '文字'}</td><td class="fig">${x.status === 'done' ? x.report.total : ''}</td><td>${x.status === 'done' ? `<span class="tag ${gradeOf(x.report.total)[1]}">${gradeOf(x.report.total)[0]}</span>` : ''}</td><td>${st}</td><td class="ops">${op}</td></tr>`; }).join('') || '<tr><td colspan="8"><div class="empty">还没有面试记录。<a href="#/interview">发起第一场面试</a></div></td></tr>'}
      </tbody></table></div></div>`;
    $$('[data-regen]', el).forEach((b) => (b.onclick = () => { const r = S.interviews.find((x) => x.id === +b.dataset.regen); b.outerHTML = '<span class="spinner" role="status" aria-label="生成中"></span>'; setTimer(() => { r.status = 'done'; pushMsg('面试报告已生成', `你的「${r.routeName}」面试报告已生成。`, '/report/' + r.id); toast('报告已生成', 'success'); rerender(); }, 1200); }));
  }

  /* ---------------- 个人中心（需求 5.1.3~5.1.5、5.9） ---------------- */
  const smsBtn = '<button type="button" class="btn" onclick="this.disabled=true;this.textContent=\'60 秒后重发\'">获取验证码</button>';
  function pageProfile(el, _, { tab = 'info' }) {
    S.docRef = { info: '5.9 个人中心', portrait: '5.9 求职画像', security: '5.1.3 密码，5.1.4 换绑，5.1.5 注销', agreement: '5.9 协议查看' }[tab];
    const nav = [['info', '个人资料'], ['portrait', '求职画像'], ['security', '账号安全'], ['agreement', '协议查看']];
    let body = '';
    if (tab === 'info') body = `<h2 class="mb-16">个人资料</h2><div class="row mb-16" style="gap:var(--space-md)"><div class="avatar" style="width:64px;height:64px;font-size:var(--text-xl)" aria-hidden="true">${esc(S.user.nickname.slice(0, 1))}</div><button type="button" class="btn btn-sm" onclick="U.toast('原型：上传头像')">更换头像</button></div><div class="form-item" style="max-width:360px"><label for="nick">昵称</label><input class="input" id="nick" name="nickname" autocomplete="nickname" value="${esc(S.user.nickname)}"></div><button type="button" class="btn btn-primary" id="saveInfo">保存</button>`;
    if (tab === 'portrait') body = `<h2>求职画像</h2><p class="small muted mt-4 mb-16">都是选填，求职意向从学习路线方向中选择。</p><div style="max-width:360px">${portraitForm()}</div><button type="button" class="btn btn-primary" id="savePf">保存</button>`;
    if (tab === 'security') body = `<h2 class="mb-8">账号安全</h2>
      <div class="kv"><div><h4>登录密码</h4><p class="small muted">${S.user.hasPassword ? '已设置，可以用手机号加密码登录' : '未设置。设置后可以用手机号加密码登录'}</p></div><button type="button" class="btn" id="pwdBtn">${S.user.hasPassword ? '修改密码' : '设置密码'}</button></div>
      <div class="kv"><div><h4>绑定手机号</h4><p class="small muted fig">${U.maskPhone(S.user.phone)}</p></div><button type="button" class="btn" id="phoneBtn">换绑手机号</button></div>
      <div class="kv"><div><h4>注销账号</h4><p class="small muted">有 7 天冷静期。注销后学习、刷题、面试记录全部删除，无法恢复</p></div><button type="button" class="btn btn-danger" id="cancelBtn">申请注销</button></div>`;
    if (tab === 'agreement') body = `<h2 class="mb-8">协议查看</h2><div class="kv"><span>用户协议</span><button type="button" class="btn btn-sm" id="vU">查看</button></div><div class="kv"><span>隐私政策</span><button type="button" class="btn btn-sm" id="vP">查看</button></div>`;
    el.innerHTML = `<div class="page-title"><h1>个人中心</h1></div><div class="side-layout"><nav class="side-nav" aria-label="个人中心">${nav.map(([k, t]) => `<a href="#/profile?tab=${k}" class="${tab === k ? 'active' : ''}" ${tab === k ? 'aria-current="page"' : ''}>${t}</a>`).join('')}</nav><div class="card">${body}</div></div>`;
    const on = (id, fn) => $('#' + id) && ($('#' + id).onclick = fn);
    on('saveInfo', () => { S.user.nickname = $('#nick').value.trim() || S.user.nickname; toast('已保存', 'success'); rerender(); });
    on('savePf', () => { savePortrait(el); toast('已保存', 'success'); });
    on('vU', () => showAgreement('user')); on('vP', () => showAgreement('privacy'));
    on('pwdBtn', pwdModal); on('phoneBtn', rebindModal); on('cancelBtn', cancelModal);
  }
  function pwdModal() {
    modal({ title: S.user.hasPassword ? '修改密码' : '设置密码', width: 440, body: `
      <div class="form-item"><label for="p1">新密码</label><input class="input" type="password" id="p1" name="new-password" autocomplete="new-password" maxlength="20" placeholder="6 到 20 位，不限字符类型"><div id="bar" class="mt-4">${U.strengthBar('')}</div><div class="form-hint">字符分小写字母、大写字母、数字、特殊符号四类。含 1 类为低，2 类为中，3 类及以上为高。强度低也可以保存。</div></div>
      <div class="form-item"><label for="p2">确认新密码</label><input class="input" type="password" id="p2" autocomplete="new-password" maxlength="20"></div>
      ${S.user.hasPassword ? '<p class="small muted">修改成功后，这个账号在所有设备上退出登录。</p>' : ''}`,
      onMount: (root) => { $('#p1', root).oninput = (e) => ($('#bar', root).innerHTML = U.strengthBar(e.target.value)); },
      footer: [{ text: '取消' }, { text: '保存', type: 'primary', onClick: (c, root) => {
        const a = $('#p1', root).value, b = $('#p2', root).value;
        if (a.length < 6 || a.length > 20) return toast('密码长度需为 6 到 20 位', 'error');
        if (a !== b) return toast('两次输入的密码不一致', 'error');
        const modify = S.user.hasPassword;
        S.user.hasPassword = true; S.user.password = a; c();
        if (modify) { S.loggedIn = false; toast('密码已修改，所有设备已退出登录，请重新登录', 'success'); go('/'); }
        else { toast('密码已设置，强度' + U.pwdStrength(a).text, 'success'); rerender(); }
      } }] });
  }
  function rebindModal() {
    let way = 'sms';
    const m = modal({ title: '换绑手机号', width: 460, footer: [{ text: '取消' }, { text: '确认换绑', type: 'primary', onClick: (c, root) => {
      const np = $('#np', root).value.trim();
      if (way === 'pwd' && !S.user.hasPassword) return toast('请先设置登录密码', 'error');
      if (way === 'sms' && $('#oc', root).value !== '123456') return toast('原手机号验证码错误', 'error');
      if (way === 'pwd' && $('#op', root).value !== S.user.password) return toast('登录密码错误', 'error');
      if (!/^1\d{10}$/.test(np)) return toast('请输入正确的新手机号', 'error');
      if (DB.students.some((s) => s.phone === np)) return toast('该手机号已注册', 'error');
      if ($('#nc', root).value !== '123456') return toast('新手机号验证码错误', 'error');
      S.user.phone = np; c(); S.loggedIn = false; toast('换绑成功，所有设备已退出登录，请用新手机号登录', 'success'); go('/');
    } }] });
    const draw = () => {
      const root = m.root;
      $('.modal-body', root).innerHTML = `<div class="seg mb-16" role="radiogroup" aria-label="验证方式"><button type="button" role="radio" aria-checked="${way === 'sms'}" class="seg-item ${way === 'sms' ? 'active' : ''}" data-w="sms">原手机号验证</button><button type="button" role="radio" aria-checked="${way === 'pwd'}" class="seg-item ${way === 'pwd' ? 'active' : ''}" data-w="pwd">原手机号收不到验证码</button></div>
        ${way === 'sms' ? `<div class="form-item"><label for="oc">原手机号 <span class="fig">${U.maskPhone(S.user.phone)}</span> 的验证码</label><div class="input-group"><input class="input fig" id="oc" inputmode="numeric" autocomplete="one-time-code" placeholder="演示验证码 123456">${smsBtn}</div></div>`
        : S.user.hasPassword ? '<div class="form-item"><label for="op">登录密码，代替原手机号验证</label><input class="input" type="password" id="op" autocomplete="current-password"></div>' : '<div class="alert alert-warn mb-16">你还没有设置登录密码。请先在账号安全里设置密码，再用密码验证换绑。</div><input type="hidden" id="op">'}
        <div class="form-item"><label for="np">新手机号</label><input class="input fig" id="np" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="不能是已注册的手机号"></div>
        <div class="form-item"><label for="nc">新手机号验证码</label><div class="input-group"><input class="input fig" id="nc" inputmode="numeric" autocomplete="one-time-code" placeholder="演示验证码 123456">${smsBtn}</div></div>
        <p class="small muted">换绑成功后，这个账号在所有设备上退出登录。</p>`;
      $$('[data-w]', root).forEach((s) => (s.onclick = () => { way = s.dataset.w; draw(); }));
    };
    draw();
  }
  function cancelModal() {
    modal({ title: '申请注销账号', width: 460, body: `
      <div class="alert alert-error">注销生效后，学习、刷题、面试记录<b>全部删除，无法恢复</b>。</div>
      <ul class="text-2 mt-12" style="padding-left:var(--space-md)"><li>申请后进入 7 天冷静期，账号显示为注销中，并退出登录</li><li>冷静期内登录，可以选择撤销注销</li><li>到期后自动删除个人数据，这个手机号可以重新注册</li></ul>
      <div class="form-item mt-16"><label for="cc">短信验证码，发送到 <span class="fig">${U.maskPhone(S.user.phone)}</span></label><div class="input-group"><input class="input fig" id="cc" inputmode="numeric" autocomplete="one-time-code" placeholder="演示验证码 123456">${smsBtn}</div></div>
      <label class="check"><input type="checkbox" id="ck"> 我已了解注销后果，确认注销</label>`,
      footer: [{ text: '取消' }, { text: '确认注销', type: 'danger-solid', onClick: (c, root) => {
        if ($('#cc', root).value !== '123456') return toast('验证码错误', 'error');
        if (!$('#ck', root).checked) return toast('请勾选确认', 'error');
        S.cancelPending = true; S.cancelAt = U.dateAfter(7); S.loggedIn = false; c();
        toast(`已申请注销，账号将于 ${S.cancelAt} 注销，冷静期内登录可以撤销`, 'success'); go('/');
      } }] });
  }

  /* ---------------- 消息中心 / 帮助中心（需求 5.10、5.11） ---------------- */
  function pageMessages(el) {
    S.docRef = '5.10 消息中心（站内信）';
    el.innerHTML = `<div class="page-title"><div><h1>消息中心</h1><p>${unread() ? `${unread()} 条未读` : '没有未读消息'}</p></div><button type="button" class="btn" id="allRead" ${unread() ? '' : 'disabled'}>全部标为已读</button></div>
      <div class="card" style="padding-block:0">${S.messages.map((m) => `<button type="button" class="msg-item ${m.read ? '' : 'unread'}" data-m="${m.id}"><span class="grow"><span class="row-between"><span class="title">${esc(m.title)}</span><span class="row">${m.read ? '' : '<span class="tag tag-primary">未读</span>'}<span class="small muted fig">${m.time}</span></span></span><span class="small text-2 mt-4" style="display:block">${esc(m.body)}</span></span></button>`).join('') || '<div class="empty">还没有消息。</div>'}</div>`;
    $('#allRead').onclick = () => { S.messages.forEach((m) => (m.read = true)); toast('已全部标为已读'); rerender(); };
    $$('[data-m]', el).forEach((d) => (d.onclick = () => { const m = S.messages.find((x) => x.id === +d.dataset.m); m.read = true; if (m.link) go(m.link); else rerender(); }));
  }
  function pageHelp(el) {
    S.docRef = '5.11 帮助中心';
    const c = DB.contact;
    el.innerHTML = `<div class="page-title"><div><h1>帮助中心</h1><p>常见问题和客服联系方式。</p></div></div><div class="grid help-grid">
      <section class="card" aria-labelledby="faqT"><h2 id="faqT">常见问题</h2>${DB.faq.map((f) => `<details class="faq"><summary>${esc(f.q)}</summary><p>${esc(f.a)}</p></details>`).join('')}</section>
      <section class="card" aria-labelledby="ctT"><h2 id="ctT" class="mb-12">联系客服</h2><div class="qr">客服微信二维码</div>
        <dl class="detail-dl" style="grid-template-columns:minmax(0,1fr)"><div><dt>客服微信</dt><dd>${c.wechat}</dd></div><div><dt>邮箱</dt><dd>${c.email}</dd></div><div><dt>电话</dt><dd class="fig">${c.phone}</dd></div></dl></section></div>`;
  }

  /* ---------------- 初始化示例数据 ---------------- */
  (function seed() {
    [[2, 'C'], [21, 'AB'], [45, '错误']].forEach(([id, my], i) => { const q = DB.questions[id]; if (q) S.wrong.push({ qid: q.id, my, count: i + 1, time: '2026-11-0' + (5 + i) + ' 21:1' + i }); });
    const mk = (r, stageIdx, mode, time, status, answers) => {
      const iv = buildInterview(r, stageIdx.map((i) => r.stages[i]), mode);
      iv.items.forEach((it, i) => { it.answers = answers[i] ? [answers[i]] : []; if (answers[i] && i % 2 === 0) it.follows = [followPool[i % followPool.length]]; });
      const rec = { id: iv.id, routeName: r.name, stageNames: iv.stageNames, mode, time, status, iv };
      rec.report = buildReport(iv);
      return rec;
    };
    const a = canned.concat(canned);
    S.interviews.push(mk(DB.routes[1], [0, 1, 2], 'text', '2026-11-06 21:05', 'done', a));
    S.interviews.push(mk(DB.routes[0], [3], 'voice', '2026-11-04 20:30', 'done', [a[0], '', a[1], a[2], a[0]]));
    S.interviews.push(mk(DB.routes[2], [0, 1], 'text', '2026-11-02 19:40', 'failed', a));
    S.messages[0].link = '/report/' + S.interviews[0].id;
  })();

  /* ---------------- 路由 ---------------- */
  const protectedPrefix = ['/practice', '/exam', '/wrong', '/interview', '/report', '/records', '/profile', '/messages'];
  U.router([
    ['/', pageHome], ['/routes', pageRoutes], ['/route/:id', pageRoute], ['/search', pageSearch],
    ['/practice/:mode/:key', pagePractice], ['/exam/:sid', pageExam], ['/exam-result', pageExamResult], ['/wrong', pageWrong],
    ['/interview', pageInterview], ['/interview/queue', pageQueue], ['/interview/device', pageDevice], ['/interview/text', pageIvText], ['/interview/voice', pageIvVoice],
    ['/interview/generating/:id', pageGenerating], ['/report/:id', pageReport], ['/interviews', pageInterviews], ['/records', pageRecords],
    ['/profile', pageProfile], ['/messages', pageMessages], ['/help', pageHelp],
  ], renderShell, (path) => {
    clearTimers();
    if (!S.loggedIn && protectedPrefix.some((p) => path.startsWith(p))) {
      const target = location.hash.slice(1);
      setTimeout(() => openLogin('code', () => go(target)), 150);
      location.hash = '#/';
      return false;
    }
    return true;
  });
})();
