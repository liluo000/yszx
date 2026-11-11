/* ==========================================================
   云枢智学 原型公共工具：路由、弹窗、提示、图表
   纯前端原型，不连接服务端，数据均为内存中的模拟数据
   ========================================================== */
(function () {
  const U = {};

  U.$ = (sel, root = document) => root.querySelector(sel);
  U.$$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  U.esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  U.maskPhone = (p) => String(p).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2');
  U.pad = (n) => String(n).padStart(2, '0');
  U.mmss = (sec) => `${U.pad(Math.floor(Math.max(0, sec) / 60))}:${U.pad(Math.max(0, sec) % 60)}`;
  U.now = () => { const d = new Date(); return `${d.getFullYear()}-${U.pad(d.getMonth() + 1)}-${U.pad(d.getDate())} ${U.pad(d.getHours())}:${U.pad(d.getMinutes())}`; };
  U.dateAfter = (days) => { const d = new Date(Date.now() + days * 864e5); return `${d.getMonth() + 1} 月 ${d.getDate()} 日`; };
  U.rand = (a, b) => Math.floor(Math.random() * (b - a + 1)) + a;
  U.sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  U.uid = (() => { let i = 1000; return () => ++i; })();

  /* 密码强度：小写、大写、数字、特殊符号四类，1 类低 / 2 类中 / 3 类及以上高（需求 5.1.3） */
  U.pwdStrength = (p) => {
    if (!p) return { level: 0, text: '' };
    let n = 0;
    if (/[a-z]/.test(p)) n++;
    if (/[A-Z]/.test(p)) n++;
    if (/\d/.test(p)) n++;
    if (/[^a-zA-Z\d]/.test(p)) n++;
    if (n <= 1) return { level: 1, text: '低' };
    if (n === 2) return { level: 2, text: '中' };
    return { level: 3, text: '高' };
  };
  U.strengthBar = (p) => {
    const s = U.pwdStrength(p);
    const segs = [1, 2, 3].map((i) => `<span class="pw-seg ${i <= s.level ? 'on lv' + s.level : ''}"></span>`).join('');
    return `<div class="pw-meter" role="status" aria-live="polite">${segs}<span class="pw-text lv${s.level}">${s.text ? '强度：' + s.text : ''}</span></div>`;
  };

  /* ---------- 图标：一套自绘线性图标 ---------- */
  const ICONS = {
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    play: '<path d="M8 5.5v13l10.5-6.5z"/>',
    flag: '<path d="M6 21V4M6 4h11l-2 4 2 4H6"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    chat: '<path d="M4 5h16v11H9l-5 4z"/>',
  };
  U.icon = (name, cls = 'icon') => `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${ICONS[name] || ''}</svg>`;

  /* ---------- 提示 ---------- */
  U.toast = (msg, type = '') => {
    let wrap = U.$('.toast-wrap');
    if (!wrap) { wrap = document.createElement('div'); wrap.className = 'toast-wrap'; document.body.appendChild(wrap); }
    const t = document.createElement('div');
    t.className = 'toast ' + type;
    t.textContent = msg;
    wrap.appendChild(t);
    setTimeout(() => { t.style.transition = 'opacity .3s'; t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, 2600);
  };

  /* ---------- 弹窗 ----------
     U.modal({ title, body, width, footer: [{ text, type, onClick(close, root) }], drawer, onMount(root, close), closable }) */
  U.modal = (opt) => {
    const mask = document.createElement('div');
    mask.className = 'mask' + (opt.drawer ? ' drawer-mask' : '');
    const footer = opt.footer === null ? '' : `<div class="modal-foot">${(opt.footer || [{ text: '知道了', type: 'primary' }]).map((b, i) => `<button type="button" class="btn ${b.type ? 'btn-' + b.type : ''}" data-i="${i}">${b.text}</button>`).join('')}</div>`;
    mask.innerHTML = `<div class="${opt.drawer ? 'drawer' : 'modal'}" style="${opt.width && !opt.drawer ? 'width:' + opt.width + 'px' : ''}">
      <div class="modal-head"><h3>${opt.title || ''}</h3>${opt.closable === false ? '' : `<button class="modal-close" type="button" aria-label="关闭">${U.icon('close')}</button>`}</div>
      <div class="modal-body">${opt.body || ''}</div>${footer}</div>`;
    document.body.appendChild(mask);
    const root = mask.firstElementChild;
    const close = () => { mask.remove(); opt.onClose && opt.onClose(); };
    const x = U.$('.modal-close', mask);
    if (x) x.onclick = close;
    if (opt.closable !== false) mask.addEventListener('mousedown', (e) => { if (e.target === mask) close(); });
    U.$$('.modal-foot .btn', mask).forEach((b) => {
      const conf = (opt.footer || [{ text: '知道了', type: 'primary' }])[b.dataset.i];
      b.onclick = () => (conf.onClick ? conf.onClick(close, root) : close());
    });
    opt.onMount && opt.onMount(root, close);
    return { close, root };
  };
  U.confirm = (title, text, onOk, okText = '确定', danger = false) =>
    U.modal({ title, width: 420, body: `<p class="text-2">${text}</p>`, footer: [{ text: '取消' }, { text: okText, type: danger ? 'danger-solid' : 'primary', onClick: (c) => { c(); onOk && onOk(); } }] });

  /* ---------- 路由（hash） ---------- */
  U.router = (routes, mount, before) => {
    const run = () => {
      const raw = location.hash.slice(1) || '/';
      const [path, qs] = raw.split('?');
      const query = Object.fromEntries(new URLSearchParams(qs || ''));
      for (const [pattern, handler] of routes) {
        const keys = [];
        const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
        const m = path.match(re);
        if (m) {
          const params = {};
          keys.forEach((k, i) => (params[k] = decodeURIComponent(m[i + 1])));
          U.$$('.mask').forEach((el) => el.remove());
          if (before && before(path, params, query) === false) return;
          handler(mount(), params, query);
          window.scrollTo(0, 0);
          return;
        }
      }
      location.hash = '#/';
    };
    window.addEventListener('hashchange', run);
    run();
    return run;
  };
  U.go = (path) => { if (location.hash === '#' + path) window.dispatchEvent(new HashChangeEvent('hashchange')); else location.hash = '#' + path; };

  /* ---------- 图表（单系列折线 / 分组柱状 / 雷达），均带悬停提示 ---------- */
  const tipFor = (box) => {
    let tip = U.$('.chart-tip', box);
    if (!tip) { tip = document.createElement('div'); tip.className = 'chart-tip hidden'; box.appendChild(tip); }
    return tip;
  };
  const niceMax = (v) => { const p = Math.pow(10, Math.floor(Math.log10(v || 1))); return Math.ceil((v || 1) / p) * p; };

  U.lineChart = (box, { labels, values, unit = '', color = 'var(--series-1)', name = '' }) => {
    const W = 640, H = 220, L = 40, R = 12, T = 12, B = 28;
    const max = niceMax(Math.max(...values));
    const x = (i) => L + (i * (W - L - R)) / Math.max(1, labels.length - 1);
    const y = (v) => T + (H - T - B) * (1 - v / max);
    const ticks = [0, .25, .5, .75, 1].map((t) => Math.round(max * t));
    const path = values.map((v, i) => `${i ? 'L' : 'M'}${x(i)},${y(v)}`).join(' ');
    const step = Math.ceil(labels.length / 8);
    box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${U.esc(name)}">
      <g class="grid">${ticks.map((t) => `<line x1="${L}" x2="${W - R}" y1="${y(t)}" y2="${y(t)}"/>`).join('')}</g>
      <g class="axis">${ticks.map((t) => `<text x="${L - 6}" y="${y(t) + 4}" text-anchor="end">${t}</text>`).join('')}
        ${labels.map((l, i) => (i % step === 0 || i === labels.length - 1 ? `<text x="${x(i)}" y="${H - 8}" text-anchor="middle">${l}</text>` : '')).join('')}</g>
      <path d="${path}" class="line-1" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
      <line class="cross hidden" y1="${T}" y2="${H - B}" stroke-dasharray="3 3"/>
      <circle class="hot dot mark-1 hidden" r="5"/>
      <rect x="${L}" y="${T}" width="${W - L - R}" height="${H - T - B}" fill="transparent" class="hit"/>
    </svg>`;
    const svg = U.$('svg', box), tip = tipFor(box), cross = U.$('.cross', svg), hot = U.$('.hot', svg);
    U.$('.hit', svg).addEventListener('mousemove', (e) => {
      const r = svg.getBoundingClientRect();
      const px = ((e.clientX - r.left) / r.width) * W;
      const i = Math.max(0, Math.min(labels.length - 1, Math.round(((px - L) / (W - L - R)) * (labels.length - 1))));
      cross.setAttribute('x1', x(i)); cross.setAttribute('x2', x(i)); cross.classList.remove('hidden');
      hot.setAttribute('cx', x(i)); hot.setAttribute('cy', y(values[i])); hot.classList.remove('hidden');
      tip.innerHTML = `${labels[i]}：<b>${values[i]}${unit}</b>`;
      tip.style.left = (x(i) / W) * r.width + 'px'; tip.style.top = (y(values[i]) / H) * r.height + 'px';
      tip.classList.remove('hidden');
    });
    U.$('.hit', svg).addEventListener('mouseleave', () => { tip.classList.add('hidden'); cross.classList.add('hidden'); hot.classList.add('hidden'); });
  };

  U.barChart = (box, { labels, series, unit = '', max }) => {
    const W = 640, H = 240, L = 40, R = 12, T = 12, B = 32;
    const top = max || niceMax(Math.max(...series.flatMap((s) => s.values)));
    const y = (v) => T + (H - T - B) * (1 - v / top);
    const ticks = [0, .25, .5, .75, 1].map((t) => Math.round(top * t));
    const gw = (W - L - R) / labels.length, bw = Math.min(28, (gw * 0.6) / series.length);
    let bars = '';
    labels.forEach((lab, i) => {
      series.forEach((s, j) => {
        const v = s.values[i];
        const bx = L + gw * i + gw / 2 - (bw * series.length + 2 * (series.length - 1)) / 2 + j * (bw + 2);
        const by = y(v), bh = H - B - by;
        bars += `<path d="M${bx},${H - B} V${by + 4} Q${bx},${by} ${bx + 4},${by} H${bx + bw - 4} Q${bx + bw},${by} ${bx + bw},${by + 4} V${H - B} Z" class="bar ${s.cls}" data-tip="${U.esc(lab)}，${U.esc(s.name)}：<b>${v}${unit}</b>"/>`;
        bars += `<rect x="${bx - 2}" y="${T}" width="${bw + 4}" height="${H - T - B}" fill="transparent" data-tip="${U.esc(lab)}，${U.esc(s.name)}：<b>${v}${unit}</b>" data-x="${bx + bw / 2}" data-y="${by}" class="hitbar"/>`;
        if (bh < 0) bars += '';
      });
    });
    box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="柱状图">
      <g class="grid">${ticks.map((t) => `<line x1="${L}" x2="${W - R}" y1="${y(t)}" y2="${y(t)}"/>`).join('')}</g>
      <g class="axis">${ticks.map((t) => `<text x="${L - 6}" y="${y(t) + 4}" text-anchor="end">${t}${unit}</text>`).join('')}
      ${labels.map((l, i) => `<text x="${L + gw * i + gw / 2}" y="${H - 10}" text-anchor="middle">${l}</text>`).join('')}</g>${bars}</svg>`;
    const svg = U.$('svg', box), tip = tipFor(box);
    U.$$('.hitbar', svg).forEach((el) => {
      el.addEventListener('mouseenter', () => {
        const r = svg.getBoundingClientRect();
        tip.innerHTML = el.dataset.tip;
        tip.style.left = (el.dataset.x / W) * r.width + 'px'; tip.style.top = (el.dataset.y / H) * r.height + 'px';
        tip.classList.remove('hidden');
      });
      el.addEventListener('mouseleave', () => tip.classList.add('hidden'));
    });
  };

  U.radarChart = (box, { labels, values, color = 'var(--series-1)' }) => {
    const S = 300, C = S / 2, Rr = 100, n = labels.length;
    const pt = (i, r) => [C + r * Math.sin((2 * Math.PI * i) / n), C - r * Math.cos((2 * Math.PI * i) / n)];
    const rings = [0.25, 0.5, 0.75, 1].map((k) => `<polygon class="ring" points="${labels.map((_, i) => pt(i, Rr * k).join(',')).join(' ')}"/>`).join('');
    const spokes = labels.map((_, i) => `<line class="ring" x1="${C}" y1="${C}" x2="${pt(i, Rr)[0]}" y2="${pt(i, Rr)[1]}"/>`).join('');
    const poly = values.map((v, i) => pt(i, (Rr * v) / 100).join(',')).join(' ');
    const dots = values.map((v, i) => { const [px, py] = pt(i, (Rr * v) / 100); return `<circle class="dot mark-1" cx="${px}" cy="${py}" r="5"/><circle cx="${px}" cy="${py}" r="14" fill="transparent" class="hitpt" data-tip="${U.esc(labels[i])}：<b>${v} 分</b>" data-x="${px}" data-y="${py}"/>`; }).join('');
    const texts = labels.map((l, i) => { const [px, py] = pt(i, Rr + 22); return `<text class="label" x="${px}" y="${py + 4}" text-anchor="middle">${U.esc(l)}</text>`; }).join('');
    box.innerHTML = `<svg viewBox="-40 -10 380 320" role="img" aria-label="分阶段得分雷达图">${rings}${spokes}<polygon class="area-1" points="${poly}" stroke-width="2"/>${dots}${texts}</svg>`;
    const svg = U.$('svg', box), tip = tipFor(box);
    U.$$('.hitpt', svg).forEach((el) => {
      el.addEventListener('mouseenter', () => {
        const r = svg.getBoundingClientRect();
        tip.innerHTML = el.dataset.tip;
        tip.style.left = ((+el.dataset.x + 40) / 380) * r.width + 'px'; tip.style.top = ((+el.dataset.y + 10) / 320) * r.height + 'px';
        tip.classList.remove('hidden');
      });
      el.addEventListener('mouseleave', () => tip.classList.add('hidden'));
    });
  };

  window.U = U;
})();
