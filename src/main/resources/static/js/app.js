// ===== 記帳與目標：共用前端小工具 =====
(function () {
    'use strict';

    const fmtMoney = (n, d = 0) => n.toLocaleString('zh-TW', { minimumFractionDigits: d, maximumFractionDigits: d });

    // ---- 送出前確認（刪除等不可逆動作） ----
    document.addEventListener('submit', (e) => {
        const msg = e.target.dataset.confirm;
        if (msg && !window.confirm(msg)) e.preventDefault();
    });

    // ---- 工作時間換算：和後端 PayRate.describe 同一套規則 ----
    function describeWork(amount, perMinute, hoursPerDay) {
        if (!(perMinute > 0) || !(amount > 0)) return '';
        const minutes = Math.round(amount / perMinute);
        if (minutes < 1) return '工作不到 1 分鐘';
        if (minutes < 60) return `工作 ${minutes} 分鐘`;
        const perDay = hoursPerDay * 60;
        if (minutes >= perDay * 2) return `工作 ${(minutes / perDay).toFixed(1).replace(/\.0$/, '')} 天`;
        const h = Math.floor(minutes / 60), m = minutes % 60;
        return m === 0 ? `工作 ${h} 小時` : `工作 ${h} 小時 ${m} 分`;
    }

    // ---- 記帳表單：依收入 / 支出切換分類選項、即時顯示工作時間 ----
    document.querySelectorAll('form.entry-form').forEach((form) => {
        const select = form.querySelector('select[name=categoryId]');
        const amount = form.querySelector('input[name=amount]');
        const preview = form.querySelector('.work-preview');
        const perMinute = parseFloat(form.dataset.perMinute || '0');
        const hoursPerDay = parseFloat(form.dataset.hoursPerDay || '8');

        const syncCategories = () => {
            const type = form.querySelector('input[name=type]:checked')?.value;
            let firstVisible = null;
            select.querySelectorAll('optgroup').forEach((g) => {
                const show = g.dataset.type === type;
                g.hidden = show ? false : true;
                g.querySelectorAll('option').forEach((o) => {
                    o.disabled = !show;
                    if (show && !firstVisible) firstVisible = o;
                });
            });
            const current = select.selectedOptions[0];
            if (!current || current.disabled) select.value = firstVisible ? firstVisible.value : '';
        };
        const syncPreview = () => {
            if (!preview) return;
            const text = describeWork(parseFloat(amount.value), perMinute, hoursPerDay);
            const isExpense = form.querySelector('input[name=type]:checked')?.value === 'EXPENSE';
            preview.textContent = text ? `= ${text}${isExpense ? '才賺得到' : ''}` : '';
        };
        form.querySelectorAll('input[name=type]').forEach((r) => r.addEventListener('change', () => { syncCategories(); syncPreview(); }));
        amount?.addEventListener('input', syncPreview);
        syncCategories();
        syncPreview();
    });

    // ---- 目標表單：依「目標內容」「計算模式」顯示需要的欄位 ----
    document.querySelectorAll('form.goal-form').forEach((form) => {
        const sync = () => {
            const type = form.querySelector('select[name=targetType]').value;
            const mode = form.querySelector('input[name=mode]:checked')?.value;
            form.querySelectorAll('[data-show-type]').forEach((el) => { el.hidden = el.dataset.showType !== type; });
            form.querySelectorAll('[data-show-mode]').forEach((el) => { el.hidden = !el.dataset.showMode.split(' ').includes(mode); });
            const principal = form.querySelector('select[name=principalMode]')?.value;
            form.querySelectorAll('[data-show-principal]').forEach((el) => { el.hidden = el.dataset.showPrincipal !== principal; });
            form.querySelectorAll('.mode-option').forEach((el) => el.classList.toggle('selected', el.querySelector('input').checked));
        };
        form.addEventListener('change', sync);
        sync();
    });

    // ---- 股票買賣表單：輸入代號自動查名稱，即時試算手續費、證交稅 ----
    document.querySelectorAll('form.trade-form').forEach((form) => {
        const codeInput = form.querySelector('input[name=code]');
        const nameInput = form.querySelector('input[name=name]');
        const result = form.querySelector('.lookup-result');
        const preview = form.querySelector('.trade-preview');
        const discount = parseFloat(form.dataset.feeDiscount || '1');
        const minFee = parseInt(form.dataset.minFee || '20', 10);
        let lastCode = '';

        const lookup = async () => {
            const code = codeInput.value.trim().toUpperCase();
            if (code.length < 4 || code === lastCode) return;
            lastCode = code;
            result.textContent = '查詢中…（上櫃股票第一次查詢約需 30 秒）';
            try {
                const r = await fetch(`/stocks/lookup?code=${encodeURIComponent(code)}`).then((x) => x.json());
                if (r.found) {
                    result.textContent = `${r.market}・${r.name}・${r.date.slice(5).replace('-', '/')} 收盤 ${r.close}`;
                    nameInput.value = r.name;
                    const price = form.querySelector('input[name=price]');
                    if (!price.value) price.value = r.close;
                    sync();
                } else {
                    result.textContent = '查不到這個代號，請手動填名稱';
                }
            } catch (e) {
                result.textContent = '查詢失敗，可以手動填名稱';
            }
        };

        const sync = () => {
            const shares = parseInt(form.querySelector('input[name=shares]').value, 10);
            const price = parseFloat(form.querySelector('input[name=price]').value);
            const sell = form.querySelector('input[name=type]:checked')?.value === 'SELL';
            if (!(shares > 0) || !(price > 0)) { preview.textContent = ''; return; }
            const gross = shares * price;
            const fee = Math.max(Math.floor(gross * 0.001425 * discount), shares % 1000 === 0 ? minFee : 1);
            const etf = codeInput.value.trim().startsWith('00');
            const tax = sell ? Math.floor(gross * (etf ? 0.001 : 0.003)) : 0;
            const total = sell ? gross - fee - tax : gross + fee;
            preview.innerHTML = `成交金額 <strong>$${fmtMoney(gross)}</strong>　手續費 $${fmtMoney(fee)}`
                + (sell ? `　證交稅 $${fmtMoney(tax)}（${etf ? 'ETF 0.1%' : '0.3%'}）` : '')
                + `　→ ${sell ? '入帳' : '扣款'} <strong>$${fmtMoney(total)}</strong>`;
        };

        codeInput.addEventListener('blur', lookup);
        codeInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); lookup(); } });
        form.addEventListener('input', sync);
        form.addEventListener('change', sync);
        sync();
    });

    // 更新股價可能要等 30 秒，按下去先給提示
    document.querySelectorAll('form.refresh-form').forEach((f) => f.addEventListener('submit', () => {
        const b = f.querySelector('button');
        b.disabled = true;
        b.textContent = '⏳ 更新中…（上櫃資料較大，最多約 30 秒）';
    }));

    // ---- 表格排序：<th data-sort="num|text">，儲存格可用 data-value 指定排序值 ----
    document.querySelectorAll('table.sortable').forEach((table) => {
        table.querySelectorAll('th[data-sort]').forEach((th, idx) => {
            th.addEventListener('click', () => {
                const col = Array.from(th.parentNode.children).indexOf(th);
                const asc = !th.classList.contains('sorted-asc');
                table.querySelectorAll('th').forEach((h) => h.classList.remove('sorted-asc', 'sorted-desc'));
                th.classList.add(asc ? 'sorted-asc' : 'sorted-desc');
                const tbody = table.tBodies[0];
                const rows = Array.from(tbody.rows).filter((r) => r.cells.length > col);
                const val = (r) => {
                    const c = r.cells[col];
                    const v = c.dataset.value ?? c.textContent.trim();
                    return th.dataset.sort === 'num' ? parseFloat(v) || 0 : v;
                };
                rows.sort((a, b) => {
                    const x = val(a), y = val(b);
                    const cmp = typeof x === 'number' ? x - y : String(x).localeCompare(String(y), 'zh-Hant');
                    return asc ? cmp : -cmp;
                });
                rows.forEach((r) => tbody.appendChild(r));
            });
        });
    });

    // ---- 今天已經賺了多少（上班時間內每 0.1 秒跳動，午休暫停） ----
    const earn = document.getElementById('earn-today');
    if (earn) {
        const perSecond = parseFloat(earn.dataset.perSecond);
        const ds = earn.dataset;
        const status = document.getElementById('earn-status');
        // "08:30" → 當天 0 點起算的秒數
        const toSec = (hhmm) => { const [h, m] = hhmm.split(':').map(Number); return h * 3600 + m * 60; };
        const start = toSec(ds.start), end = toSec(ds.end);
        const hasBreak = !!(ds.breakStart && ds.breakEnd);
        const bStart = hasBreak ? toSec(ds.breakStart) : 0, bEnd = hasBreak ? toSec(ds.breakEnd) : 0;
        const overlap = (a1, a2, b1, b2) => Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
        const fmtLeft = (sec) => {
            const m = Math.ceil(sec / 60);
            return m >= 60 ? `${Math.floor(m / 60)} 小時 ${m % 60} 分` : `${m} 分鐘`;
        };

        const tick = () => {
            const now = new Date();
            const t = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds() + now.getMilliseconds() / 1000;
            const weekend = now.getDay() === 0 || now.getDay() === 6;
            // 已工作秒數 = [上班, 現在] 的長度 − 其中落在午休的部分
            const until = Math.min(Math.max(t, start), end);
            const worked = weekend ? 0 : (until - start) - (hasBreak ? overlap(start, until, bStart, bEnd) : 0);

            let text;
            if (weekend) text = '今天是週末，好好休息 🌴';
            else if (t < start) text = `還沒上班（${ds.start} 開始計算）`;
            else if (t >= end) text = '今天已下班，辛苦了 🎉';
            else if (hasBreak && t >= bStart && t < bEnd) text = `午休中 🍱 暫停計算，${ds.breakEnd} 繼續（還有 ${fmtLeft(bEnd - t)}）`;
            else if (hasBreak && t < bStart) text = `上班中…距離午休還有 ${fmtLeft(bStart - t)}`;
            else text = `上班中…距離下班還有 ${fmtLeft(end - t)}`;

            earn.textContent = '$' + fmtMoney(Math.max(worked, 0) * perSecond, 2);
            status.textContent = text;
        };
        tick();
        setInterval(tick, 100);
    }

    // ---- 圖表（Chart.js） ----
    // 糖果色：天空藍、珊瑚粉、薄荷綠、陽光黃、薰衣草紫…
    const palette = ['#2fa6ff', '#ff6b8b', '#12c39a', '#ffb020', '#9b6bff', '#ff8a3d', '#00c2d1', '#f45bd0', '#8bd346', '#6c7cff'];
    const textColor = () => getComputedStyle(document.body).color;

    window.fgCharts = {
        doughnut(id, labels, values) {
            const el = document.getElementById(id);
            if (!el || typeof Chart === 'undefined') return;
            new Chart(el, {
                type: 'doughnut',
                data: { labels, datasets: [{ data: values, backgroundColor: palette, borderWidth: 3, borderColor: getComputedStyle(document.body).getPropertyValue('--surface') || '#fff', hoverOffset: 10, borderRadius: 6 }] },
                options: {
                    maintainAspectRatio: false,
                    cutout: '62%',
                    plugins: {
                        legend: { position: 'right', labels: { color: textColor(), boxWidth: 12, usePointStyle: true, pointStyle: 'circle', font: { weight: 700 } } },
                        tooltip: { callbacks: { label: (c) => {
                            const total = c.dataset.data.reduce((a, b) => a + b, 0);
                            return ` ${c.label}：$${fmtMoney(c.parsed)}（${total ? (c.parsed * 100 / total).toFixed(1) : 0}%）`;
                        } } }
                    }
                }
            });
        },
        /** 目標試算：總資產、本金+累積存入、目標線 */
        projection(id, years, totals, contributed, target) {
            const el = document.getElementById(id);
            if (!el || typeof Chart === 'undefined') return;
            const labels = years.map((y) => (y === 0 ? '現在' : `${y} 年`));
            const datasets = [
                { label: '總資產', data: totals, borderColor: '#7c4dff', backgroundColor: 'rgba(124,77,255,.14)', fill: true, tension: .35, pointRadius: 3, pointBackgroundColor: '#ff5fa2', borderWidth: 3 },
                { label: '本金 + 累積存入', data: contributed, borderColor: '#2fa6ff', borderDash: [5, 5], fill: false, tension: 0, pointRadius: 0, borderWidth: 2 }
            ];
            if (target) {
                datasets.push({ label: '目標', data: years.map(() => Number(target)), borderColor: '#12c39a', borderDash: [10, 5], fill: false, pointRadius: 0, borderWidth: 2 });
            }
            new Chart(el, {
                type: 'line',
                data: { labels, datasets },
                options: {
                    maintainAspectRatio: false,
                    interaction: { mode: 'index', intersect: false },
                    plugins: {
                        legend: { position: 'bottom', labels: { color: textColor(), boxWidth: 14, usePointStyle: true, font: { weight: 700 } } },
                        tooltip: { callbacks: { label: (c) => ` ${c.dataset.label}：$${fmtMoney(c.parsed.y)}` } }
                    },
                    scales: {
                        x: { ticks: { color: textColor(), maxTicksLimit: 12 }, grid: { display: false } },
                        y: { ticks: { color: textColor(), callback: (v) => '$' + (Math.abs(v) >= 10000 ? fmtMoney(v / 10000, 0) + ' 萬' : fmtMoney(v)) } }
                    }
                }
            });
        },
        line(id, labels, values) {
            const el = document.getElementById(id);
            if (!el || typeof Chart === 'undefined') return;
            new Chart(el, {
                type: 'line',
                data: { labels, datasets: [{ data: values, borderColor: '#ff5fa2', backgroundColor: 'rgba(255,95,162,.14)', fill: true, tension: .35, pointRadius: 4, pointBackgroundColor: '#7c4dff', borderWidth: 3 }] },
                options: {
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` $${fmtMoney(c.parsed.y)}` } } },
                    scales: {
                        x: { ticks: { color: textColor() }, grid: { display: false } },
                        y: { ticks: { color: textColor(), callback: (v) => '$' + fmtMoney(v) } }
                    }
                }
            });
        }
    };
})();
