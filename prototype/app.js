/* 家庭健康 · 可交互原型（纯内存状态，无后端） */
const state = {
  tab: 'home', memberId: 'yeye', stack: [],
  recordsSeg: 'checkup', mType: 'bp', mMode: 'month', mMonth: '2026-09',
  trendsMode: 'table', trendDays: 30,
  importStep: 1, importText: '', importData: null,
};
const REMINDERS = {
  yeye: { med: ['08:00', '20:00'], measure: ['19:00'], advance: ['提前7天', '提前1天', '当天'] },
  nainai: { med: ['08:00'], measure: [], advance: ['提前1天', '当天'] },
  baba: { med: [], measure: [], advance: ['当天'] },
};

/* ---------- 工具 ---------- */
const $ = (s) => document.querySelector(s);
const member = () => DB.members.find((m) => m.id === state.memberId);
const CTX = { fasting: '空腹', before_meal: '餐前', after_meal_2h: '餐后2h', bedtime: '睡前', random: '随机' };
const SLOTS = [['morning', '早'], ['noon', '中'], ['evening', '晚'], ['bedtime', '睡前']];
const slotName = (k) => (SLOTS.find((s) => s[0] === k) || [])[1] || k;
const slotTags = (x) => (x.slots || []).map((k) => `<span class="slotchip">${slotName(k)}</span>`).join('');
const stockTxt = (x) => x.stock != null ? `剩 ${x.stock} ${esc(x.stockUnit || '')}` : '余量未记 · 点按更新';
const todayStr = () => new Date().toISOString().slice(0, 10);
const daysTo = (d) => Math.ceil((new Date(d) - new Date(todayStr())) / 86400000);
const mmdd = (d) => d.slice(5).replace('-', '/');
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 1600);
}
function fmtM(m) {
  if (m.type === 'bp') return `<b class="num">${m.sys}/${m.dia}</b> <span class="tiny">mmHg${m.hr ? ' · 心率' + m.hr : ''}</span>`;
  if (m.type === 'glucose') return `<b class="num">${m.glu}</b> <span class="tiny">mmol/L · ${CTX[m.ctx] || ''}</span>`;
  return `<b class="num">${m.hr}</b> <span class="tiny">次/分</span>`;
}
function typeName(t) { return { bp: '血压', glucose: '血糖', hr: '心率' }[t]; }
function esc(s) { return String(s ?? '').replace(/</g, '&lt;'); }

/* ---------- 导航 ---------- */
function go(page, arg) { if (page === 'medChange') state.mc = null; state.stack.push({ page, arg }); render(); }
function back() { state.stack.pop(); render(); }
function setTab(tab) { state.tab = tab; state.stack = []; render(); }
function switchMember(id) { state.memberId = id; state.stack = []; closeModal(); render(); }
function openModal(html) { $('#modal-root').innerHTML = `<div class="mask" onclick="closeModal()"></div>${html}`; }
function closeModal() { $('#modal-root').innerHTML = ''; }

/* ---------- 图表 ---------- */
function svgLine(series, w = 340, h = 120) {
  const all = series.flatMap((s) => s.points.map((p) => p.y));
  if (!all.length) return '';
  const min = Math.min(...all), max = Math.max(...all), pad = (max - min || 1) * 0.2;
  const lo = min - pad, hi = max + pad;
  const n = Math.max(...series.map((s) => s.points.length));
  const X = (i) => 10 + (w - 20) * (n === 1 ? 0.5 : i / (n - 1));
  const Y = (v) => h - 14 - ((v - lo) / (hi - lo)) * (h - 30);
  const colors = ['#0F766E', '#B45309', '#5B6DC8'];
  let inner = '';
  series.forEach((s, si) => {
    const pts = s.points.map((p, i) => `${X(i)},${Y(p.y)}`).join(' ');
    inner += `<polyline points="${pts}" fill="none" stroke="${colors[si % 3]}" stroke-width="2"/>`;
    s.points.forEach((p, i) => { inner += `<circle cx="${X(i)}" cy="${Y(p.y)}" r="3" fill="${colors[si % 3]}"/>`; });
  });
  inner += `<text x="10" y="12" font-size="9" fill="#9AA19A">${max}</text><text x="10" y="${h - 4}" font-size="9" fill="#9AA19A">${min}</text>`;
  return `<svg viewBox="0 0 ${w} ${h}" style="width:100%">${inner}</svg>`;
}
function legend(items) {
  const colors = ['#0F766E', '#B45309', '#5B6DC8'];
  return `<div style="display:flex;gap:14px;font-size:12px;color:var(--text-2);margin:2px 4px 6px">${items.map((t, i) => `<span><i style="display:inline-block;width:10px;height:3px;background:${colors[i % 3]};vertical-align:middle;margin-right:4px"></i>${t}</span>`).join('')}</div>`;
}

/* ---------- 指标匹配（原文名精确 + 清单别名归并） ---------- */
function indicatorPoints(name) {
  const wl = member().watchlist.find((w) => w.name === name || w.aliases.includes(name));
  const names = wl ? wl.aliases : [name];
  const pts = [];
  member().events.forEach((e) => e.reports.forEach((r) => r.indicators.forEach((it) => {
    if (names.includes(it.n)) pts.push({ date: e.date, dept: e.dept, v: it.v, u: it.u || '' });
  })));
  pts.sort((a, b) => a.date.localeCompare(b.date));
  return { pts, wl };
}

/* ---------- 历史用药分段 ---------- */
function medHistory(meds) {
  const pts = [...new Set(meds.flatMap((m) => [m.start, m.end]).filter(Boolean))].sort().reverse();
  return pts.map((from, i) => {
    const segEnd = i === 0 ? '9999' : pts[i - 1];
    const active = meds.filter((m) => m.start < segEnd && (m.end === null || m.end > from));
    return { from, to: i === 0 ? '至今' : pts[i - 1], active };
  }).filter((s) => s.active.length);
}

/* ================= 页面 ================= */
function dailyHtml(m) {
  const dl = m.daily || [];
  if (!dl.length) return '<div class="muted">还没设置今日用药，点右上角"管理"添加</div>';
  const west = dl.filter((x) => !x.tcm), tcm = dl.filter((x) => x.tcm);
  const daysLeft = (x) => (x.stock != null && x.daily) ? ` · 约${Math.floor(x.stock / x.daily)}天` : '';
  return SLOTS.map(([k, label]) => [label, west.filter((x) => (x.slots || []).includes(k))]).filter(([, a]) => a.length).map(([label, arr]) => `
    <div class="dose-row"><span class="dose-slot">${label}</span><div class="dose-items">
      ${arr.map((x) => `<div class="dose-item" onclick="f.dailyEdit('${x.id}')"><b>${esc(x.name)}</b><span class="muted">${esc(x.dose)}</span><span class="dose-stock num">${x.stock != null ? `剩${x.stock}${esc(x.unit || '')}${daysLeft(x)}` : '余量未记'}</span></div>`).join('')}
    </div></div>`).join('')
    + tcm.map((x) => `
    <div class="dose-row"><span class="dose-slot" style="background:#F5EBD7;color:#8A6D1F">药</span><div class="dose-items">
      <div class="dose-item" onclick="f.dailyEdit('${x.id}')"><b>${esc(x.name)}</b><span class="muted">剩 ${x.packs} 副</span><span class="dose-stock">这副第 ${x.usedDays} 天 / 共 ${x.daysPerPack} 天</span></div>
    </div></div>`).join('');
}

function pageHome() {
  const m = member();
  const undone = m.notes.filter((n) => !n.done);
  const showNotes = (undone.length ? undone : m.notes).slice(0, 2);
  const nextEv = m.events.filter((e) => e.next && daysTo(e.next) >= 0).sort((a, b) => a.next.localeCompare(b.next))[0];
  return `
  <div class="card clickable" onclick="go('notes')">
    <div class="card-head"><span class="card-title">📌 便签</span><span class="card-more">全部 ›</span></div>
    ${showNotes.length ? showNotes.map((n) => `
      <div style="padding:4px 0"><div style="font-size:14px;line-height:1.55">${esc(n.text)}</div>
      ${n.remind ? `<div class="remind-chip">⏰ ${n.remind.at} 提醒 · ${n.remind.target || '全家'}</div>` : ''}</div>`).join('')
      : '<div class="muted">暂无便签，点这里写一条 ›</div>'}
  </div>
  <div class="card">
    <div class="card-head"><span class="card-title">💊 今日用药</span><span class="card-more" onclick="go('daily')">管理 ›</span></div>
    ${dailyHtml(m)}
  </div>
  <div class="card clickable" ${nextEv ? `onclick="go('event','${nextEv.id}')"` : ''}>
    <div class="card-head"><span class="card-title">🏥 下次复查</span>${nextEv ? `<span class="num" style="color:var(--primary);font-weight:700">${daysTo(nextEv.next)} 天后</span>` : ''}</div>
    ${nextEv ? `<div class="line-item"><span class="num" style="font-size:17px;font-weight:700">${nextEv.next}</span><span class="muted">${nextEv.dept} · ${nextEv.hospital}</span></div>` : '<div class="muted">未设置，录入复查事件时填写"下次复查日期"即可</div>'}
  </div>`;
}

function pageRecords() {
  const m = member();
  let inner = `<div class="seg">
    <div class="${state.recordsSeg === 'checkup' ? 'on' : ''}" onclick="f.seg('checkup')">复查</div>
    <div class="${state.recordsSeg === 'measure' ? 'on' : ''}" onclick="f.seg('measure')">测量</div></div>`;
  if (state.recordsSeg === 'checkup') {
    inner += m.events.length ? m.events.map((e) => `
      <div class="row" onclick="go('event','${e.id}')">
        <div class="r1"><b class="num">${e.date}</b><span class="tiny">${e.dept}</span></div>
        <div class="r2">${e.hospital} · ${e.reports.length} 份报告${e.medChange ? ' · 用药变化' : ''}${e.next ? ` · 下次 ${mmdd(e.next)}` : ''}</div>
        ${e.note ? `<div class="r2" style="color:#8A9089">${esc(e.note)}</div>` : ''}
      </div>`).join('') : '<div class="empty">还没有复查记录<br>从最近一次复查报告开始，历史可以慢慢补</div>';
  } else {
    inner += `<div class="chips">${[['bp', '血压'], ['glucose', '血糖']].map(([v, t]) => `<div class="chip ${state.mType === v ? 'on' : ''}" onclick="f.mtype('${v}')">${t}</div>`).join('')}</div>`;
    inner += f.pnavHtml();
    const inPeriod = (x) => state.mMode === 'month' ? x.at.startsWith(state.mMonth) : daysTo(x.at.slice(0, 10)) >= -state.mMode;
    const list = m.measurements.filter((x) => x.type === state.mType && inPeriod(x));
    const days = {};
    list.forEach((x) => { (days[x.at.slice(0, 10)] = days[x.at.slice(0, 10)] || []).push(x); });
    const keys = Object.keys(days).sort().reverse();
    if (!keys.length) inner += '<div class="empty">该周期内暂无记录，点底部 ＋ 记一条</div>';
    else if (state.mType === 'bp') {
      inner += keys.map((k) => `
        <div class="drow" onclick="f.dayDetail('${k}')">
          <span class="dd"><b>${mmdd(k)}</b><i>周${'日一二三四五六'[new Date(k + 'T00:00:00').getDay()]}</i></span>
          <span class="rds">${days[k].sort((a, b) => a.at.localeCompare(b.at)).map((x) => `<span class="rd"><span><b class="num">${x.sys}/${x.dia}</b>${x.hr ? ` <i class="num">·${x.hr}</i>` : ''}</span><u>${x.at.slice(11)}</u></span>`).join('')}</span>
        </div>`).join('');
    } else {
      const scenes = [['fasting', '空腹'], ['before_meal', '餐前'], ['after_meal_2h', '餐后'], ['bedtime', '睡前'], ['random', '随机']];
      inner += `<div class="card" style="padding:4px 10px"><table class="it bgrid"><tr><th>日期</th>${scenes.map((s) => `<th>${s[1]}</th>`).join('')}</tr>
      ${keys.map((k) => { const byCtx = {}; days[k].forEach((x) => { (byCtx[x.ctx] = byCtx[x.ctx] || []).push(x); });
        return `<tr class="clickable" onclick="f.dayDetail('${k}')"><td class="num">${mmdd(k)}</td>${scenes.map((s) => `<td>${byCtx[s[0]] ? byCtx[s[0]].map((e) => `<div class="gc"><b class="num">${e.glu}</b><u>${e.at.slice(11)}</u></div>`).join('') : '<span class="num">·</span>'}</td>`).join('')}</tr>`; }).join('')}
      </table></div>`;
    }
  }
  return inner;
}

function pageEvent(id) {
  const e = member().events.find((x) => x.id === id);
  if (!e) return '<div class="empty">事件不存在</div>';
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> ${e.date} 复查</div>
  <div class="card">
    <div class="kv"><b>医院</b><span>${esc(e.hospital)}</span></div>
    <div class="kv"><b>科室</b><span>${esc(e.dept)}</span></div>
    ${e.note ? `<div class="kv"><b>备注</b><span>${esc(e.note)}</span></div>` : ''}
    ${e.next ? `<div class="kv"><b>下次复查</b><span class="num">${e.next}</span></div>` : ''}
  </div>
  ${e.medChange ? `<div class="card"><div class="card-title" style="margin-bottom:6px">本次用药变化</div><div style="font-size:14px;line-height:1.6">${esc(e.medChange)}</div></div>` : ''}
  <div class="card-head" style="margin:4px 2px 8px"><span class="card-title">报告（${e.reports.length}）</span></div>
  ${e.reports.map((r, i) => `
    <div class="row" onclick="go('report','${e.id}|${i}')">
      <div class="r1"><b>${esc(r.title)}</b><span class="tiny">${r.indicators.length ? r.indicators.length + ' 项指标' : '结论文字'}${r.attachments ? ' · 📎' + r.attachments : ''}</span></div>
      ${r.conclusion ? `<div class="r2">${esc(r.conclusion.slice(0, 30))}…</div>` : ''}
    </div>`).join('')}
  <div class="tiny" style="text-align:center;margin-top:8px">由 爸爸 通过导入录入 · ${e.date}</div>`;
}

function pageReport(arg) {
  const [eid, ri] = arg.split('|');
  const e = member().events.find((x) => x.id === eid);
  const r = e.reports[+ri];
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> ${esc(r.title)}</div>
  <div class="sub">${e.date} · ${e.hospital} ${e.dept}</div>
  ${r.indicators.length ? `<div class="card" style="padding:6px 12px">
    <table class="it"><tr><th>项目</th><th>结果</th><th>参考区间</th></tr>
    ${r.indicators.map((it) => `<tr class="clickable" onclick="go('indicator','${esc(it.n)}')">
      <td>${esc(it.n)}</td><td class="num">${it.v}${it.u ? ' <span class="tiny">' + esc(it.u) + '</span>' : ''}</td><td class="muted">${esc(it.r) || '—'}</td></tr>`).join('')}
    </table></div>
    <div class="tiny" style="margin:-4px 4px 12px">点任一指标行可查看该项历次变化 · 原样展示，无异常标注</div>` : ''}
  ${r.conclusion ? `<div class="card"><div class="card-title" style="margin-bottom:6px">结论</div><div style="font-size:14px;line-height:1.7">${esc(r.conclusion)}</div></div>` : ''}
  ${r.attachments ? `<div class="card"><div class="card-title" style="margin-bottom:4px">原件照片 ${r.attachments} 张</div>
    <div class="attach">${Array.from({ length: r.attachments }, () => '<div class="ph">🧾</div>').join('')}</div>
    <div class="tiny" style="margin-top:8px">点击查看 / 下载原件（演示）</div></div>` : ''}`;
}

function pageIndicator(name) {
  const { pts, wl } = indicatorPoints(name);
  const numeric = pts.filter((p) => typeof p.v === 'number');
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> ${esc(name)}</div>
  <div class="sub">共 ${pts.length} 次记录${wl ? ` · 已归并别名：${wl.aliases.join('、')}` : ' · 按报告原文名精确匹配'}</div>
  ${pts.length === 1 ? `<div class="warn-box">仅匹配到 1 次记录。若其他报告中该指标用了不同名称（如 ACR 与 ACR(尿)），加入重点清单并配置别名后可合并历史。<br><b style="cursor:pointer" onclick="f.addWatch('${esc(name)}')">＋ 加入重点清单</b></div>` : ''}
  ${numeric.length >= 2 ? `<div class="chart">${svgLine([{ points: numeric.map((p) => ({ y: p.v })) }])}</div>` : ''}
  ${pts.slice().reverse().map((p) => `<div class="row"><div class="r1"><b class="num">${p.v} <span class="tiny">${esc(p.u)}</span></b><span class="tiny num">${p.date} · ${p.dept}</span></div></div>`).join('')}
  ${!pts.length ? '<div class="empty">暂无记录</div>' : ''}`;
}

function pageMeds() {
  const m = member();
  const cur = m.meds.filter((x) => x.cat === 'long' && !x.end);
  const temp = m.meds.filter((x) => x.cat === 'temp' && !x.end);
  return `
  <div class="card-head" style="margin:2px 2px 8px"><span class="card-title">当前方案 · 长期 ${cur.length} 种</span><span class="tiny">点卡片编辑详情</span></div>
  ${cur.length ? cur.map((x) => `
    <div class="medcard" onclick="go('medEdit','${x.id}')">
      <div class="mc1"><b style="font-size:16px">${esc(x.name)}</b>${x.kind === 'tcm' ? '<span class="tag">中药</span>' : ''}${slotTags(x)}</div>
      <div class="mc2">${esc(x.dosage)}</div>
    </div>`).join('') : '<div class="card"><span class="muted">暂无进行中的长期用药</span></div>'}
  ${temp.length ? `<div class="card"><div class="card-head"><span class="card-title">临时用药</span><span class="tiny">辅助记录</span></div>
    ${temp.map((x) => `<div class="line-item"><span>${esc(x.name)}${x.kind === 'tcm' ? '<span class="tag">中药</span>' : ''}</span><span class="muted">${esc(x.dosage)} · 至 ${x.end || '未定'}</span></div>`).join('')}</div>` : ''}
  <div class="mlist" style="margin-top:14px"><div class="mi" onclick="go('medHistory')"><span>历史用药<div class="desc">按阶段查看每个时期在用什么药</div></span><span class="arrow">›</span></div></div>
  <button class="btn" onclick="go('medChange')">＋ 记用药变化</button>`;
}

function mcNewRowHtml(n, i) {
  return `<div class="card" style="padding:10px 12px">
    <div class="field" style="margin-bottom:8px"><input placeholder="药品 / 方名" value="${esc(n.name)}" oninput="f.mcNewField(${i},'name',this.value)"></div>
    <div class="field" style="margin-bottom:8px"><input placeholder="用法用量，如 每次 50mg" value="${esc(n.dosage)}" oninput="f.mcNewField(${i},'dosage',this.value)"></div>
    <div class="frow">
      <select onchange="f.mcNewField(${i},'kind',this.value)"><option value="western" ${n.kind === 'western' ? 'selected' : ''}>西药</option><option value="tcm" ${n.kind === 'tcm' ? 'selected' : ''}>中药</option></select>
      <select onchange="f.mcNewField(${i},'cat',this.value)"><option value="long" ${n.cat === 'long' ? 'selected' : ''}>长期</option><option value="temp" ${n.cat === 'temp' ? 'selected' : ''}>临时</option></select>
    </div>
    <div class="chips" style="margin:8px 0 0">${SLOTS.map(([k, label]) => `<span class="chip ${n.slots.includes(k) ? 'on' : ''}" onclick="f.mcNewSlot(${i},'${k}',this)">${label}</span>`).join('')}</div>
    ${n.cat === 'temp' ? `<div class="field" style="margin:8px 0 0"><label>止日（临时药）</label><input type="date" value="${n.end}" onchange="f.mcNewField(${i},'end',this.value)"></div>` : ''}
  </div>`;
}

function pageMedChange() {
  const m = member();
  if (!state.mc) state.mc = { stops: {}, adj: {}, news: [] };
  const st = state.mc;
  const active = m.meds.filter((x) => !x.end);
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 记用药变化</div>
  <div class="frow">
    <div class="field"><label>生效日期</label><input type="date" id="mc-date" value="${todayStr()}"></div>
    <div class="field"><label>事由（备注，自由填写）</label><input id="mc-reason" placeholder="如：复查后调整、感冒"></div>
  </div>
  <div class="field"><label>关联复查（可空）</label><select id="mc-event"><option value="">不关联</option>
    ${m.events.map((e) => `<option value="${e.id}">${e.date} ${e.dept}</option>`).join('')}</select></div>
  <div class="card-head" style="margin:2px 2px 8px"><span class="card-title">当前用药 · 逐条标记</span><span class="tiny">可叠加，再点取消</span></div>
  ${active.length ? active.map((x) => `
    <div class="mcrow ${st.stops[x.id] ? 'stopped' : ''}">
      <div class="mcr-info"><b>${esc(x.name)}</b>${x.kind === 'tcm' ? '<span class="tag">中药</span>' : ''}<span class="muted">${esc(x.dosage)}</span>${slotTags(x)}</div>
      <div class="mcr-ops">
        <span class="op ${st.adj[x.id] ? 'on' : ''}" onclick="f.mcAdj('${x.id}')">改量</span>
        <span class="op ${st.stops[x.id] ? 'on' : ''}" onclick="f.mcStop('${x.id}')">停用</span>
      </div>
      ${st.adj[x.id] ? `<div class="adj-edit">
        <div class="field" style="margin-bottom:8px"><label>改后用法用量</label><input value="${esc(st.adj[x.id].dosage)}" oninput="f.mcAdjDos('${x.id}',this.value)"></div>
        <div class="chips" style="margin:0">${SLOTS.map(([k, label]) => `<span class="chip ${st.adj[x.id].slots.includes(k) ? 'on' : ''}" onclick="f.mcAdjSlot('${x.id}','${k}',this)">${label}</span>`).join('')}</div>
      </div>` : ''}
    </div>`).join('') : '<div class="card"><span class="muted">当前无进行中用药，直接新增</span></div>'}
  <div class="card-head" style="margin:14px 2px 8px"><span class="card-title">新增药品 / 药方</span></div>
  <div id="mc-news">${st.news.map((n, i) => mcNewRowHtml(n, i)).join('')}</div>
  <button class="btn ghost small" onclick="f.mcNew()">＋ 新增一条</button>
  <button class="btn" onclick="f.mcSave()">保 存（停 ${Object.keys(st.stops).length} · 改 ${Object.keys(st.adj).length} · 增 ${st.news.filter((n) => n.name.trim()).length}）</button>`;
}

function pageDaily() {
  const m = member();
  const dl = m.daily || [];
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 今日用药 · 管理</div>
  <div class="sub">家里实际执行的服药单，自由填写，与用药页的"当前方案"是两回事</div>
  ${dl.length ? dl.map((x) => `<div class="row" onclick="f.dailyEdit('${x.id}')">
    <div class="r1"><b>${esc(x.name) || '（未命名）'}</b>${x.tcm ? '<span class="tag">中药计数</span>' : `<span class="tiny">${(x.slots || []).map(slotName).join(' · ') || '未设时段'}</span>`}</div>
    <div class="r2">${x.tcm ? `剩 ${x.packs} 副 · 每副 ${x.daysPerPack} 天 · 当前第 ${x.usedDays} 天` : `${esc(x.dose)} · ${x.stock != null ? '剩 ' + x.stock + esc(x.unit || '') + (x.daily ? '（约' + Math.floor(x.stock / x.daily) + '天）' : '') : '余量未记'}`}</div>
  </div>`).join('') : '<div class="empty">还没添加，从下方添加</div>'}
  <button class="btn" onclick="f.dailyAdd(0)">＋ 添加一项</button>
  <button class="btn ghost" onclick="f.dailyAdd(1)">＋ 添加中药计数</button>`;
}

function pageMedHistory() {
  const m = member();
  const segs = medHistory(m.meds);
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 历史用药</div>
  <div class="sub">按变化节点分段 · 每个阶段在用什么药</div>
  ${segs.length ? segs.map((s) => {
    const ch = m.changes.find((c) => c.date === s.from);
    const ev = ch && ch.eventId ? m.events.find((e) => e.id === ch.eventId) : null;
    return `<div class="card" style="margin-bottom:10px">
      <div style="font-size:14px;font-weight:700">${s.from} 起${s.to === '至今' ? ' · 至今' : ' ~ ' + s.to}</div>
      ${ch && ch.note ? `<div class="tiny" style="margin-top:2px">${esc(ch.note)}${ev ? ` · 关联 <span style="color:var(--primary);cursor:pointer" onclick="go('event','${ev.id}')">${ev.date} 复查</span>` : ''}</div>` : ''}
      <div style="font-size:13.5px;margin-top:6px">西药：${s.active.filter((a) => a.kind === 'western').map((a) => esc(a.name)).join('、') || '无'}</div>
      <div style="font-size:13.5px;margin-top:2px">中药：${s.active.filter((a) => a.kind === 'tcm').map((a) => esc(a.name)).join('、') || '无'}</div>
    </div>`; }).join('') : '<div class="empty">暂无用药记录</div>'}`;
}

function pageMedEdit(id) {
  const x = member().meds.find((m) => m.id === id);
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 编辑用药</div>
  <div class="field"><label>名称</label><input id="me-name" value="${esc(x.name)}"></div>
  <div class="field"><label>用法用量</label><input id="me-dosage" value="${esc(x.dosage)}"></div>
  <div class="frow">
    <div class="field"><label>类别</label><select id="me-kind"><option value="western" ${x.kind === 'western' ? 'selected' : ''}>西药</option><option value="tcm" ${x.kind === 'tcm' ? 'selected' : ''}>中药</option></select></div>
    <div class="field"><label>性质</label><select id="me-cat"><option value="long" ${x.cat === 'long' ? 'selected' : ''}>长期</option><option value="temp" ${x.cat === 'temp' ? 'selected' : ''}>临时</option></select></div>
  </div>
  <div class="field"><label>服用时段</label><div class="chips" style="margin:0">${SLOTS.map(([k, label]) => `<span class="chip me-slot ${(x.slots || []).includes(k) ? 'on' : ''}" data-k="${k}" onclick="this.classList.toggle('on')">${label}</span>`).join('')}</div></div>
  <div class="frow">
    <div class="field"><label>开始日期</label><input id="me-start" type="date" value="${x.start}"></div>
    <div class="field"><label>结束日期（空=进行中）</label><input id="me-end" type="date" value="${x.end || ''}"></div>
  </div>
  <button class="btn" onclick="f.medEditSave('${id}')">保 存</button>`;
}

function pageMeasureForm(type) {
  const titles = { bp: '记血压', glucose: '记血糖', hr: '记心率' };
  let fields = '';
  if (type === 'bp') fields = `<div class="frow"><div class="field"><label>高压 (mmHg)</label><input id="mf-sys" type="number" inputmode="numeric" placeholder="138"></div>
    <div class="field"><label>低压 (mmHg)</label><input id="mf-dia" type="number" inputmode="numeric" placeholder="86"></div></div>
    <div class="field"><label>心率（可空）</label><input id="mf-hr" type="number" inputmode="numeric" placeholder="72"></div>`;
  if (type === 'glucose') fields = `<div class="field"><label>血糖 (mmol/L)</label><input id="mf-glu" type="number" step="0.1" inputmode="decimal" placeholder="6.1"></div>
    <div class="field"><label>测量场景</label><select id="mf-ctx"><option value="fasting">空腹</option><option value="before_meal">餐前</option><option value="after_meal_2h">餐后2小时</option><option value="bedtime">睡前</option><option value="random">随机</option></select></div>`;
  if (type === 'hr') fields = `<div class="field"><label>心率 (次/分)</label><input id="mf-hr" type="number" inputmode="numeric" placeholder="68"></div>`;
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> ${titles[type]}</div>
  <div class="sub">${member().name} · 时间默认现在，保存后可改（演示固定为现在）</div>
  ${fields}
  <div class="field"><label>备注（可空）</label><input id="mf-note" placeholder=""></div>
  <button class="btn" onclick="f.mSave('${type}')">保 存</button>
  <button class="btn ghost" onclick="f.mSave('${type}',1)">保存并再记一条</button>`;
}

function pageImport() {
  const s = state;
  let body = '';
  if (s.importStep === 1) body = `
    <div class="field"><label>粘贴外部 AI 整理好的文本（JSON）</label>
    <textarea id="imp-text" style="min-height:220px" placeholder='{"format":"family-health-import",...}'>${esc(s.importText)}</textarea></div>
    <button class="btn ghost" onclick="f.impSample()">填入示例文本</button>
    <button class="btn" onclick="f.impParse()">解 析</button>`;
  if (s.importStep === 2) {
    const ev = s.importData.payload.event;
    const cnt = ev.reports.reduce((a, r) => a + (r.indicators ? r.indicators.length : 0), 0);
    body = `
    <div class="ok-box">✓ 校验通过：识别到 <b class="num">${ev.checkup_date}</b> 复查 · 报告 ${ev.reports.length} 份 · 指标 ${cnt} 项<br>医院：${esc(ev.hospital || '—')}　科室：${esc(ev.department || '—')}<br>下次复查：${ev.next_checkup_date || '—'}</div>
    <button class="btn ghost" onclick="state.importStep=3;render()">展开逐项核对</button>
    <button class="btn" onclick="f.impConfirm()">确认入库</button>
    <button class="btn ghost" onclick="state.importStep=1;render()">返回修改</button>`;
  }
  if (s.importStep === 3) {
    const ev = s.importData.payload.event;
    body = ev.reports.map((r) => `<div class="card"><div class="card-title" style="margin-bottom:6px">${esc(r.title)}</div>
      <table class="it"><tr><th>项目</th><th>结果</th><th>参考区间</th></tr>
      ${(r.indicators || []).map((it) => `<tr><td>${esc(it.item_name)}</td><td class="num">${it.value}${it.unit ? ' <span class="tiny">' + esc(it.unit) + '</span>' : ''}</td><td class="muted">${esc(it.reference_range) || '—'}</td></tr>`).join('')}</table></div>`).join('')
      + `<button class="btn" onclick="f.impConfirm()">确认入库</button><button class="btn ghost" onclick="state.importStep=2;render()">返回</button>`;
  }
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 记复查（导入）</div>
  <div class="steps"><i class="${s.importStep >= 1 ? 'on' : ''}"></i><i class="${s.importStep >= 2 ? 'on' : ''}"></i><i class="${s.importStep >= 3 ? 'on' : ''}"></i><i></i></div>
  ${body}`;
}

function pageTrends() {
  const m = member();
  const evs = m.events.slice().sort((a, b) => a.date.localeCompare(b.date));
  let wlHtml = '';
  if (!m.watchlist.length) wlHtml = '<div class="empty">重点清单为空<br>在报告里点某个指标即可"加入重点清单"</div>';
  else if (state.trendsMode === 'table') {
    wlHtml = `<table class="it"><tr><th></th>${evs.map((e) => `<th class="num">${mmdd(e.date)}</th>`).join('')}</tr>
      ${m.watchlist.map((w) => { const { pts } = indicatorPoints(w.name); const map = {}; pts.forEach((p) => map[p.date] = p.v);
        return `<tr class="clickable" onclick="go('indicator','${esc(w.name)}')"><td>${esc(w.name)}</td>${evs.map((e) => `<td class="num">${map[e.date] ?? '—'}</td>`).join('')}</tr>`; }).join('')}</table>`;
  } else {
    wlHtml = m.watchlist.map((w) => { const { pts } = indicatorPoints(w.name); const numeric = pts.filter((p) => typeof p.v === 'number');
      return `<div style="margin-bottom:6px"><div style="font-size:13px;font-weight:700;margin:0 2px 4px">${esc(w.name)} <span class="tiny">${esc(w.unit)}</span></div>
      ${numeric.length >= 2 ? svgLine([{ points: numeric.map((p) => ({ y: p.v })) }]) : '<div class="tiny" style="padding:6px">记录不足两次，暂无折线</div>'}</div>`; }).join('');
  }
  const bp = m.measurements.filter((x) => x.type === 'bp' && daysTo(x.at.slice(0, 10)) >= -state.trendDays).sort((a, b) => a.at.localeCompare(b.at));
  const glu = m.measurements.filter((x) => x.type === 'glucose' && daysTo(x.at.slice(0, 10)) >= -state.trendDays).sort((a, b) => a.at.localeCompare(b.at));
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 趋势与对比</div>
  <div class="card-head"><span class="card-title">重点指标对比</span>
    <span class="card-more" onclick="f.tmode()">${state.trendsMode === 'table' ? '折线 ›' : '表格 ›'}</span></div>
  <div class="card" style="padding:8px 12px">${wlHtml}</div>
  <div class="tiny" style="margin:-4px 4px 12px">仅展示数值与日期，无异常标注 · 清单行可点进单项历史</div>
  <div class="card-head"><span class="card-title">日常测量趋势</span>
    <span class="chips" style="margin:0">${[7, 30, 90].map((d) => `<span class="chip ${state.trendDays === d ? 'on' : ''}" onclick="f.tdays(${d})">${d}天</span>`).join('')}</span></div>
  <div class="chart"><div class="ct"><span>血压</span><span class="tiny">${bp.length} 条 · 流水见记录 Tab</span></div>
    ${bp.length >= 2 ? legend(['高压', '低压']) + svgLine([{ points: bp.map((x) => ({ y: x.sys })) }, { points: bp.map((x) => ({ y: x.dia })) }]) : '<div class="empty">近' + state.trendDays + '天记录不足</div>'}</div>
  <div class="chart"><div class="ct"><span>血糖（空腹）</span><span class="tiny">${glu.length} 条</span></div>
    ${glu.length >= 2 ? svgLine([{ points: glu.map((x) => ({ y: x.glu })) }]) : '<div class="empty">近' + state.trendDays + '天记录不足</div>'}</div>`;
}

function pageNotes() {
  const m = member();
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 便签
    <span style="flex:1"></span><span class="card-more" onclick="go('noteForm')">＋ 新建</span></div>
  ${m.notes.length ? m.notes.map((n) => `
    <div class="note ${n.done ? 'done' : ''}">
      <input type="checkbox" ${n.done ? 'checked' : ''} onclick="f.noteToggle('${n.id}')" style="width:20px;height:20px;accent-color:var(--primary);margin-top:2px">
      <div class="nt">${esc(n.text)}
        ${n.remind ? `<div class="remind-chip">⏰ ${n.remind.at} · 提醒 ${n.remind.target || '全家'}</div>` : ''}
        <div class="nmeta">${n.by} · ${n.at}</div></div>
    </div>`).join('') : '<div class="empty">暂无便签</div>'}`;
}

function pageNoteForm() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 新建便签</div>
  <div class="field"><label>内容</label><textarea id="nf-text" placeholder="如：下周三上午去取药"></textarea></div>
  <div class="field"><label>提醒时刻（可空）</label><input id="nf-remind" type="datetime-local"></div>
  <div class="field"><label>提醒对象</label><select id="nf-target"><option value="">全家</option>
    ${DB.devices.map((d) => `<option>${d.name}</option>`).join('')}</select></div>
  <button class="btn" onclick="f.noteSave()">保 存</button>`;
}

function pageMine() {
  const items = [
    ['members', '成员档案管理', `${DB.members.length} 份档案`],
    ['devices', '家庭口令与设备', `${DB.devices.length} 台设备`],
    ['reminders', '提醒设置', '服药 / 测量 / 复查'],
    ['token', 'API 令牌', '供 HTTP 导入'],
    ['export', '数据导出', 'JSON + 照片包'],
    ['about', '关于与隐私说明', ''],
  ];
  return `
  <div class="card" style="display:flex;align-items:center;gap:12px">
    <div class="avatar" style="width:44px;height:44px;border-radius:50%;background:var(--primary);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700">家</div>
    <div><b style="font-size:16px">${DB.familyName}</b><div class="tiny">${DB.members.length} 位成员 · ${DB.devices.length} 台设备</div></div>
  </div>
  <div class="mlist">${items.map(([p, t, d]) => `<div class="mi" onclick="go('${p}')"><span>${t}${d ? `<div class="desc">${d}</div>` : ''}</span><span class="arrow">›</span></div>`).join('')}</div>`;
}

function pageMembers() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 成员档案
    <span style="flex:1"></span><span class="card-more" onclick="go('memberEdit','')">＋ 添加</span></div>
  ${DB.members.map((m) => `
    <div class="row" onclick="go('memberEdit','${m.id}')">
      <div class="r1"><b>${m.name}</b><span class="tiny">${m.relation} · ${m.gender}</span></div>
      <div class="r2">${esc(m.profileNote) || '未填写档案说明'}</div>
    </div>`).join('')}`;
}

function pageMemberEdit(id) {
  const m = DB.members.find((x) => x.id === id) || { id: '', name: '', relation: '', gender: '男', birth: '', profileNote: '' };
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> ${id ? '编辑档案' : '添加成员'}</div>
  <div class="frow"><div class="field"><label>姓名/称呼</label><input id="pe-name" value="${esc(m.name)}"></div>
  <div class="field"><label>关系</label><input id="pe-rel" value="${esc(m.relation)}" placeholder="爷爷"></div></div>
  <div class="frow"><div class="field"><label>性别</label><select id="pe-gender"><option ${m.gender === '男' ? 'selected' : ''}>男</option><option ${m.gender === '女' ? 'selected' : ''}>女</option></select></div>
  <div class="field"><label>出生日期</label><input id="pe-birth" type="date" value="${m.birth}"></div></div>
  <div class="field"><label>档案说明（自由书写：确诊疾病、过敏史、手术史…）</label><textarea id="pe-note">${esc(m.profileNote)}</textarea></div>
  <button class="btn" onclick="f.peSave('${id}')">保 存</button>`;
}

function pageDevices() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 家庭口令与设备</div>
  <div class="card"><div class="card-head"><span class="card-title">家庭口令</span><span class="card-more" onclick="toast('更换后所有设备需重新输入（演示）')">更换 ›</span></div>
    <div class="muted">新设备首次使用需输入家庭口令完成署名</div></div>
  <div class="card-head" style="margin:4px 2px 8px"><span class="card-title">已接入设备</span></div>
  <div id="dev-list">${DB.devices.map((d) => `
    <div class="row"><div class="r1"><b>${d.name}${d.self ? '<span class="tag">本机</span>' : ''}</b>
    ${d.self ? '<span class="tiny">当前设备</span>' : `<span class="card-more" onclick="f.devRevoke('${d.id}')">吊销</span>`}</div></div>`).join('')}</div>`;
}

function pageReminders() {
  const m = member(), r = REMINDERS[m.id];
  const timeChips = (arr, kind) => arr.map((t, i) => `<span class="chip on" style="cursor:default">${t} <b style="cursor:pointer" onclick="f.rmTime('${kind}',${i})">×</b></span>`).join('');
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 提醒设置</div>
  <div class="sub">当前成员：${m.name}（顶部可切换）· 到点由全家设备本地通知</div>
  <div class="card"><div class="card-title" style="margin-bottom:8px">服药提醒</div>
    <div class="chips">${timeChips(r.med, 'med')}<span class="chip" onclick="f.addTime('med')">＋</span></div>
    <div class="tiny">通知内容自动拼接当前用药方案，用药变动无需改提醒</div></div>
  <div class="card"><div class="card-title" style="margin-bottom:8px">测量提醒</div>
    <div class="chips">${timeChips(r.measure, 'measure')}<span class="chip" onclick="f.addTime('measure')">＋</span></div></div>
  <div class="card"><div class="card-title" style="margin-bottom:8px">复查提醒</div>
    <div class="chips">${r.advance.map((t) => `<span class="chip on" style="cursor:default">${t}</span>`).join('')}</div>
    <div class="tiny">按复查事件的"下次复查日期"触发</div></div>
  <div class="warn-box">首次使用请按引导开启"自启动 / 电池无限制"，避免系统杀后台导致通知延迟。</div>`;
}

function pageToken() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> API 令牌</div>
  <div class="sub">供外部脚本 / HTTP 导入使用，与 App 粘贴导入同一格式</div>
  <div class="card"><div class="kv"><b>当前令牌</b><span class="num">fhk_••••••••3d9a</span></div>
    <div class="kv"><b>署名</b><span>爸爸（导入记录将署名该设备）</span></div></div>
  <button class="btn ghost" onclick="toast('新令牌 fhk_new_x7q2，仅显示一次（演示）')">创建新令牌</button>
  <button class="btn ghost" onclick="toast('已吊销（演示）')">吊销当前令牌</button>`;
}

function pageExport() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 数据导出</div>
  ${DB.members.map((m) => `<div class="row"><div class="r1"><b>${m.name}</b>
    <span class="card-more" onclick="toast('已生成导出包（演示）')">导出 ›</span></div>
    <div class="r2">${m.events.length} 次复查 · ${m.measurements.length} 条测量 · ${m.meds.length} 条用药</div></div>`).join('')}
  <div class="tiny" style="margin:8px 4px">导出格式与导入格式 v1 同字段，导出物可再导入（防锁定保险）。</div>`;
}

function pageAbout() {
  return `
  <div class="page-title"><span class="back" onclick="back()">‹</span> 关于与隐私</div>
  <div class="card" style="font-size:14px;line-height:1.9">
    本应用是家庭内部的健康记录工具：<br>
    · 不做医学判断，不标注异常，不提供健康建议<br>
    · 报告参考区间为原文存档，请以医生解读为准<br>
    · 数据存储于家庭自有的云环境，可随时导出<br>
    · 整理报告文本给外部 AI 前，请先隐去姓名、证件号
  </div>`;
}

/* ================= 渲染 ================= */
const TABS = [
  ['home', '概览', '🏠'], ['records', '记录', '📋'], null, ['meds', '用药', '💊'], ['mine', '我的', '👤'],
];
function renderMemberbar() {
  const m = member();
  $('#memberbar').innerHTML = `
    <div class="who" onclick="openMemberSwitch()"><div class="avatar">${m.name[0]}</div><span>${m.name}</span><span class="caret">▾</span></div>
    ${state.tab === 'home' && !state.stack.length ? `<span class="entry" onclick="go('trends')">趋势与对比 ›</span>` : ''}`;
}
function renderTabbar() {
  $('#tabbar').innerHTML = TABS.map((t) => t ? `
    <div class="tab ${state.tab === t[0] && !state.stack.length ? 'on' : ''}" onclick="setTab('${t[0]}')">
      <span class="ic">${t[2]}</span><span>${t[1]}</span></div>`
    : `<div class="plus-wrap"><div id="plus-btn" onclick="openPlus()">＋</div></div>`).join('');
}
function render() {
  renderMemberbar(); renderTabbar();
  const top = state.stack[state.stack.length - 1];
  const PAGES = {
    home: pageHome, records: pageRecords, meds: pageMeds, mine: pageMine,
    event: pageEvent, report: pageReport, indicator: pageIndicator,
    medChange: pageMedChange, medHistory: pageMedHistory, medEdit: pageMedEdit, daily: pageDaily, measureForm: pageMeasureForm, import: pageImport,
    trends: pageTrends, notes: pageNotes, noteForm: pageNoteForm,
    members: pageMembers, memberEdit: pageMemberEdit, devices: pageDevices,
    reminders: pageReminders, token: pageToken, export: pageExport, about: pageAbout,
  };
  $('#screen').innerHTML = top ? PAGES[top.page](top.arg) : PAGES[state.tab]();
  $('#screen').scrollTop = 0;
}

/* ================= 弹层 ================= */
function openMemberSwitch() {
  openModal(`<div class="center-modal"><div class="dialog"><h3>切换成员</h3>
    ${DB.members.map((m) => `<div class="mrow" onclick="switchMember('${m.id}')">
      <div class="avatar">${m.name[0]}</div>
      <div class="meta"><b>${m.name}</b><div>${m.events.length ? m.events[0].date + ' 复查过' : '暂无复查记录'}</div></div>
      ${m.id === state.memberId ? '<span class="check">✓</span>' : ''}</div>`).join('')}
    <div class="mrow" onclick="closeModal();setTab('mine');go('memberEdit','')"><div class="avatar" style="background:#9AA19A">＋</div><div class="meta"><b>添加成员</b></div></div>
  </div></div>`);
}
function openPlus() {
  openModal(`<div class="sheet">
    <h3>日常</h3>
    <div class="grid2">
      <div class="act" onclick="closeModal();go('measureForm','bp')">🩺 记血压</div>
      <div class="act" onclick="closeModal();go('measureForm','glucose')">🩸 记血糖</div>
    </div>
    <div class="tiny" style="margin:6px 2px 0">心率是血压的附属可选项，随血压一同记录</div>
    <h3>阶段性</h3>
    <div class="grid2">
      <div class="act" onclick="closeModal();go('medChange')">💊 记用药变化</div>
      <div class="act" onclick="closeModal();state.importStep=1;state.importText='';go('import')">🏥 记复查（导入）</div>
    </div>
  </div>`);
}

/* ================= 动作 ================= */
const f = {
  seg: (v) => { state.recordsSeg = v; render(); },
  mtype: (v) => { state.mType = v; render(); },
  mmode: (v) => { state.mMode = v === 'month' ? 'month' : +v; if (v === 'month') state.mMonth = todayStr().slice(0, 7); render(); },
  mshift: (d) => {
    const [y, m] = state.mMonth.split('-').map(Number);
    const nm = m - 1 + d, ny = y + Math.floor(nm / 12), nmo = ((nm % 12) + 12) % 12 + 1;
    state.mMonth = `${ny}-${String(nmo).padStart(2, '0')}`; render();
  },
  pnavHtml: () => {
    if (state.mMode === 'month') {
      const [y, mo] = state.mMonth.split('-');
      return `<div class="pnav"><span class="pa" onclick="f.mshift(-1)">‹</span><span class="pl">${y} 年 ${+mo} 月</span><span class="pa" onclick="f.mshift(1)">›</span>
        <span class="chips" style="margin:0 0 0 auto"><span class="chip" onclick="f.mmode('7')">近7天</span><span class="chip" onclick="f.mmode('30')">近30天</span></span></div>`;
    }
    return `<div class="pnav"><span class="pl">近 ${state.mMode} 天</span>
      <span class="chips" style="margin:0 0 0 auto"><span class="chip" onclick="f.mmode('month')">回到本月</span></span></div>`;
  },
  dayDetail: (k) => {
    const recs = member().measurements.filter((x) => x.at.slice(0, 10) === k).sort((a, b) => a.at.localeCompare(b.at));
    openModal(`<div class="sheet"><h3>${k} · ${recs.length} 条记录</h3>
      ${recs.map((x) => `<div class="row" onclick="toast('编辑 / 删除（演示）')"><div class="r1"><span>${typeName(x.type)} ${fmtM(x)}</span><span class="tiny num">${x.at.slice(11)}</span></div><div class="r2">由 ${x.by} 录入</div></div>`).join('')}
    </div>`);
  },
  tmode: () => { state.trendsMode = state.trendsMode === 'table' ? 'chart' : 'table'; render(); },
  tdays: (d) => { state.trendDays = d; render(); },
  addWatch: (name) => {
    if (!member().watchlist.find((w) => w.name === name)) member().watchlist.push({ name, aliases: [name], unit: '' });
    toast('已加入重点清单'); render();
  },
  noteToggle: (id) => { const n = member().notes.find((x) => x.id === id); n.done = !n.done; render(); },
  noteSave: () => {
    const text = $('#nf-text').value.trim();
    if (!text) return toast('请填写内容');
    const rt = $('#nf-remind').value, tg = $('#nf-target').value;
    member().notes.unshift({ id: 'n' + Date.now(), text, done: false, by: '爸爸', at: '今天',
      remind: rt ? { at: rt.replace('T', ' ').slice(5), target: tg || null } : null });
    toast('已保存'); back();
  },
  mSave: (type, again) => {
    const now = new Date(), pad = (x) => String(x).padStart(2, '0');
    const at = `${todayStr()} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
    let rec = { type, at, by: '爸爸' };
    if (type === 'bp') {
      const sys = +$('#mf-sys').value, dia = +$('#mf-dia').value;
      if (!sys || !dia) return toast('请填写高压和低压');
      rec.sys = sys; rec.dia = dia; rec.hr = +$('#mf-hr').value || undefined;
    } else if (type === 'glucose') {
      if (!$('#mf-glu').value) return toast('请填写血糖');
      rec.glu = +$('#mf-glu').value; rec.ctx = $('#mf-ctx').value;
    } else {
      if (!$('#mf-hr').value) return toast('请填写心率');
      rec.hr = +$('#mf-hr').value;
    }
    member().measurements.unshift(rec);
    toast('已保存');
    if (again) render(); else { state.recordsSeg = 'measure'; state.stack = []; state.tab = 'records'; render(); }
  },
  mcStop: (id) => { const st = state.mc; st.stops[id] ? delete st.stops[id] : (st.stops[id] = true); delete st.adj[id]; render(); },
  mcAdj: (id) => {
    const st = state.mc;
    if (st.adj[id]) delete st.adj[id];
    else { const m = member().meds.find((x) => x.id === id); st.adj[id] = { dosage: m.dosage, slots: (m.slots || []).slice() }; delete st.stops[id]; }
    render();
  },
  mcAdjDos: (id, v) => { state.mc.adj[id].dosage = v; },
  mcAdjSlot: (id, k, el) => { const a = state.mc.adj[id].slots; const i = a.indexOf(k); i >= 0 ? a.splice(i, 1) : a.push(k); el.classList.toggle('on'); },
  mcNew: () => { state.mc.news.push({ name: '', dosage: '', kind: 'western', cat: 'long', slots: ['morning'], end: '' }); render(); },
  mcNewField: (i, k, v) => { state.mc.news[i][k] = v; if (k === 'cat') render(); },
  mcNewSlot: (i, k, el) => { const a = state.mc.news[i].slots; const j = a.indexOf(k); j >= 0 ? a.splice(j, 1) : a.push(k); el.classList.toggle('on'); },
  mcSave: () => {
    const date = $('#mc-date').value || todayStr();
    const note = $('#mc-reason').value.trim();
    const evId = $('#mc-event').value || null;
    const st = state.mc, meds = member().meds;
    Object.keys(st.stops).forEach((id) => { const m = meds.find((x) => x.id === id); if (m) m.end = date; });
    Object.entries(st.adj).forEach(([id, a]) => {
      const m = meds.find((x) => x.id === id); if (!m) return;
      m.end = date;
      meds.push({ id: 'm' + Date.now() + id, name: m.name, dosage: a.dosage || m.dosage, slots: a.slots.length ? a.slots : m.slots,
        kind: m.kind, cat: m.cat, start: date, end: null, supersedes: id });
    });
    st.news.forEach((n, i) => {
      if (!n.name.trim()) return;
      meds.push({ id: 'mn' + Date.now() + i, name: n.name, dosage: n.dosage, slots: n.slots, kind: n.kind, cat: n.cat,
        start: date, end: n.cat === 'temp' ? (n.end || date) : null });
    });
    const c = Object.keys(st.stops).length, a = Object.keys(st.adj).length, n = st.news.filter((x) => x.name.trim()).length;
    if (c + a + n > 0) member().changes.unshift({ id: 'c' + Date.now(), date, note, eventId: evId });
    state.mc = null;
    toast(`已保存：停 ${c} · 改 ${a} · 增 ${n}`);
    state.stack = []; state.tab = 'meds'; render();
  },
  dailyEdit: (id) => {
    const x = member().daily.find((d) => d.id === id);
    const body = x.tcm ? `
      <div class="field"><label>名称</label><input id="dl-name" value="${esc(x.name)}"></div>
      <div class="frow">
        <div class="field"><label>剩余副数</label><input id="dl-packs" type="number" inputmode="numeric" value="${x.packs}"></div>
        <div class="field"><label>每副吃几天</label><input id="dl-dpp" type="number" inputmode="numeric" value="${x.daysPerPack}"></div>
        <div class="field"><label>当前这副已吃</label><input id="dl-used" type="number" inputmode="numeric" value="${x.usedDays}"></div>
      </div>` : `
      <div class="field"><label>名称</label><input id="dl-name" value="${esc(x.name)}"></div>
      <div class="frow">
        <div class="field"><label>每次用量</label><input id="dl-dose" value="${esc(x.dose)}" placeholder="1 片"></div>
        <div class="field"><label>每日用量（/天，算天数用）</label><input id="dl-daily" type="number" inputmode="numeric" value="${x.daily}"></div>
      </div>
      <div class="frow">
        <div class="field"><label>剩余量（可空）</label><input id="dl-stock" type="number" inputmode="numeric" value="${x.stock ?? ''}"></div>
        <div class="field"><label>单位</label><input id="dl-unit" value="${esc(x.unit || '片')}"></div>
      </div>
      <div class="field"><label>服用时段</label><div class="chips" style="margin:0">${SLOTS.map(([k, label]) => `<span class="chip dl-slot ${(x.slots || []).includes(k) ? 'on' : ''}" data-k="${k}" onclick="this.classList.toggle('on')">${label}</span>`).join('')}</div></div>`;
    openModal(`<div class="sheet"><h3>编辑今日用药</h3>${body}
      <button class="btn" onclick="f.dailySave('${id}')">保 存</button>
      <button class="btn ghost" onclick="f.dailyDel('${id}')">删除此项</button></div>`);
  },
  dailySave: (id) => {
    const x = member().daily.find((d) => d.id === id);
    x.name = $('#dl-name').value.trim() || x.name;
    if (x.tcm) {
      x.packs = +$('#dl-packs').value || 0; x.daysPerPack = +$('#dl-dpp').value || 1; x.usedDays = +$('#dl-used').value || 0;
    } else {
      x.dose = $('#dl-dose').value; x.daily = +$('#dl-daily').value || 1;
      const sv = $('#dl-stock').value; x.stock = sv === '' ? null : +sv; x.unit = $('#dl-unit').value;
      x.slots = [...document.querySelectorAll('.dl-slot.on')].map((el) => el.dataset.k);
    }
    closeModal(); toast('已保存'); render();
  },
  dailyDel: (id) => { const m = member(); m.daily = m.daily.filter((d) => d.id !== id); closeModal(); toast('已删除'); render(); },
  dailyAdd: (tcm) => {
    const m = member();
    const id = 'dl' + Date.now();
    m.daily.push(tcm ? { id, name: '中药方', tcm: true, packs: 7, daysPerPack: 1, usedDays: 0 }
                     : { id, name: '', dose: '1 片', slots: ['morning'], stock: null, unit: '片', daily: 1 });
    f.dailyEdit(id);
  },
  medEditSave: (id) => {
    const x = member().meds.find((m) => m.id === id);
    const slots = [...document.querySelectorAll('.me-slot.on')].map((el) => el.dataset.k);
    Object.assign(x, {
      name: $('#me-name').value.trim() || x.name, dosage: $('#me-dosage').value,
      kind: $('#me-kind').value, cat: $('#me-cat').value, slots,
      start: $('#me-start').value || x.start, end: $('#me-end').value || null,
    });
    toast('已保存'); back();
  },
  impSample: () => { state.importText = IMPORT_SAMPLE; render(); },
  impParse: () => {
    state.importText = $('#imp-text').value;
    try {
      const d = JSON.parse(state.importText);
      if (d.format !== 'family-health-import' || !d.payload || !d.payload.event) throw new Error('bad');
      state.importData = d; state.importStep = 2;
    } catch (e) { toast('解析失败：不是合法的导入文本'); return; }
    render();
  },
  impConfirm: () => {
    const ev = state.importData.payload.event;
    member().events.unshift({
      id: 'e' + Date.now(), date: ev.checkup_date, hospital: ev.hospital || '', dept: ev.department || '',
      note: ev.note || '', next: ev.next_checkup_date || '', medChange: '',
      reports: ev.reports.map((r) => ({ title: r.title, date: ev.checkup_date, attachments: 0, conclusion: r.conclusion_text || '',
        indicators: (r.indicators || []).map((i) => ({ n: i.item_name, v: i.value, u: i.unit || '', r: i.reference_range || '' })) })),
    });
    toast('已入库（演示）');
    state.stack = []; state.tab = 'records'; state.recordsSeg = 'checkup'; render();
  },
  peSave: (id) => {
    const name = $('#pe-name').value.trim();
    if (!name) return toast('请填写姓名');
    const data = { name, relation: $('#pe-rel').value, gender: $('#pe-gender').value, birth: $('#pe-birth').value, profileNote: $('#pe-note').value };
    if (id) Object.assign(DB.members.find((x) => x.id === id), data);
    else DB.members.push({ id: 'u' + Date.now(), ...data, meds: [], events: [], measurements: [], notes: [], watchlist: [] });
    toast('已保存'); back();
  },
  devRevoke: (id) => { DB.devices = DB.devices.filter((d) => d.id !== id); toast('已吊销'); render(); },
  rmTime: (kind, i) => { REMINDERS[state.memberId][kind].splice(i, 1); render(); },
  addTime: (kind) => { REMINDERS[state.memberId][kind].push('12:00'); render(); toast('已添加 12:00，可点 × 删除（演示）'); },
};

render();
