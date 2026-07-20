/*
 * Trang "Số thả nổi" — logic dùng chung cho bản admin (/khach-hang-canh-bao)
 * và bản chia sẻ cho team sale (/so-tha-noi).
 *
 * PHÂN TRANG SERVER-SIDE: mỗi lần đổi trang / nhóm / tìm kiếm / sắp xếp / bộ lọc đều gọi
 * /api/khach-hang-canh-bao/data với tham số tương ứng; server đọc bảng precompute
 * problem_customer_facts (đã đánh index) và trả về ĐÚNG 1 trang + số đếm nhóm. Không còn
 * tải toàn bộ tập / cap 5000. Polling 60s làm mới trang hiện tại.
 */
(function () {
    const DATA_URL = '/api/khach-hang-canh-bao/data';
    const POLL_INTERVAL_MS = 60000;
    const SERVER_FILTER_KEYS = ['minNotes', 'hours', 'maxDays', 'months', 'fromDate', 'toDate'];

    const $ = (id) => document.getElementById(id);
    const body = $('pcBody');
    if (!body) return; // không phải trang số nổi

    // Cột "Thao tác" (nút xóa nhóm) chỉ có ở bản admin /khach-hang-canh-bao.
    const isAdmin = body.dataset.admin === '1';
    const COLSPAN = isAdmin ? 12 : 11;

    let lastData = null;
    let errorState = false;
    let inFlight = false;
    let currentAbort = null;
    let pollTimer = null;
    let searchDebounce = null;

    const pageSizeEl = $('pcPageSize');
    const state = {
        page: 1,
        pageSize: (pageSizeEl && parseInt(pageSizeEl.value, 10)) || 50,
        group: 'all',        // all | A | B | C | D
        search: '',
        sortKey: null,       // null = thứ tự mặc định server
        sortDir: null        // 'asc' | 'desc' | null
    };

    function escapeHtml(s) {
        if (s === null || s === undefined) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function toggle(el, show) { if (el) el.style.display = show ? '' : 'none'; }
    function badge(cls, txt) { return '<span class="mr-badge ' + cls + '">' + txt + '</span>'; }

    function setLive(cls, text) {
        const wrap = $('pcLiveStatus'), label = $('pcLiveText');
        if (wrap) { wrap.classList.remove('is-live', 'is-down', 'is-refresh'); if (cls) wrap.classList.add(cls); }
        if (label && text) label.textContent = text;
    }

    // ---- Render bảng (dữ liệu đã là 1 trang từ server) ----
    function renderRows(rows, offset) {
        if (!rows || !rows.length) { body.innerHTML = ''; return; }
        const minNotes = lastData ? lastData.minNotes : 0;
        let html = '';
        for (let i = 0; i < rows.length; i++) {
            const r = rows[i];
            const noteCls = r.noteCount >= minNotes ? ' class="note-hot"' : '';
            let badges = '';
            if (r.groupA) badges += badge('mr-badge--warn', 'Nhóm A');
            if (r.groupB) badges += badge('mr-badge--danger', 'Nhóm B');
            if (r.groupC) badges += badge('mr-badge--info', 'Nhóm C');
            if (r.groupD) badges += badge('mr-badge--neutral', 'Nhóm D');
            let actionCell = '';
            if (isAdmin) {
                const groups = rowGroups(r);
                actionCell = '<td class="pc-actions">'
                    + (groups.length
                        ? '<button type="button" class="pc-remove-btn" data-cid="' + escapeHtml(r.customerId)
                            + '" data-groups="' + groups.join(',') + '" data-name="' + escapeHtml(r.name || r.customerId)
                            + '">✕ Xóa nhóm</button>'
                        : '')
                    + '</td>';
            }
            html += '<tr>'
                + '<td>' + (offset + i + 1) + '</td>'
                + '<td>' + escapeHtml(r.name) + '</td>'
                + '<td>' + escapeHtml(r.phone != null ? r.phone : '—') + '</td>'
                + '<td class="customer-id">' + escapeHtml(r.customerId) + '</td>'
                + '<td class="num"><span' + noteCls + '>' + r.noteCount + '</span></td>'
                + '<td class="num">' + r.orderCount + '</td>'
                + '<td class="num">' + r.succeedOrderCount + '</td>'
                + '<td>' + escapeHtml(r.insertedAt) + '</td>'
                + '<td>' + escapeHtml(r.lastNoteAt) + '</td>'
                + '<td>' + escapeHtml(r.lastReceivedAt) + '</td>'
                + '<td>' + badges + '<div class="reason-text">' + escapeHtml(r.reason) + '</div></td>'
                + actionCell
                + '</tr>';
        }
        body.innerHTML = html;
    }

    // Danh sách nhóm mà 1 dòng đang thuộc (thứ tự A→D).
    function rowGroups(r) {
        const g = [];
        if (r.groupA) g.push('A');
        if (r.groupB) g.push('B');
        if (r.groupC) g.push('C');
        if (r.groupD) g.push('D');
        return g;
    }

    function updateSortIndicators() {
        document.querySelectorAll('th.pc-sortable').forEach(th => {
            if (th.getAttribute('data-sort') === state.sortKey && state.sortDir) {
                th.setAttribute('data-dir', state.sortDir);
            } else {
                th.removeAttribute('data-dir');
            }
        });
    }

    function toggleSort(key) {
        if (state.sortKey !== key) {
            state.sortKey = key;
            state.sortDir = 'asc';
        } else if (state.sortDir === 'asc') {
            state.sortDir = 'desc';
        } else {
            state.sortKey = null;   // lần 3: bỏ sort, về mặc định server
            state.sortDir = null;
        }
        state.page = 1;
        updateSortIndicators();
        loadData(false);
    }

    // ---- Phân trang ----
    function pageButton(label, page, opts) {
        opts = opts || {};
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'mr-page-btn' + (opts.active ? ' active' : '');
        btn.textContent = label;
        if (opts.disabled) btn.disabled = true;
        else btn.addEventListener('click', () => goToPage(page));
        return btn;
    }

    function pageList(current, totalPages) {
        const pages = [];
        const add = (p) => { if (!pages.includes(p)) pages.push(p); };
        add(1);
        for (let p = current - 2; p <= current + 2; p++) if (p >= 1 && p <= totalPages) add(p);
        add(totalPages);
        pages.sort((a, b) => a - b);
        const out = [];
        for (let i = 0; i < pages.length; i++) {
            if (i > 0 && pages[i] - pages[i - 1] > 1) out.push('…');
            out.push(pages[i]);
        }
        return out;
    }

    function renderPagination(current, totalPages) {
        const pager = $('pcPager');
        if (!pager) return;
        pager.innerHTML = '';
        pager.appendChild(pageButton('‹', current - 1, { disabled: current <= 1 }));
        pageList(current, totalPages).forEach(item => {
            if (item === '…') {
                const span = document.createElement('span');
                span.className = 'pc-ellipsis';
                span.textContent = '…';
                pager.appendChild(span);
            } else {
                pager.appendChild(pageButton(String(item), item, { active: item === current }));
            }
        });
        pager.appendChild(pageButton('›', current + 1, { disabled: current >= totalPages }));
    }

    function renderView(data) {
        const page = data.page || 1;
        const pageSize = data.pageSize || state.pageSize;
        const totalPages = Math.max(1, data.totalPages || 1);
        const matched = data.matched || 0;
        const offset = (page - 1) * pageSize;

        renderRows(data.rows || [], offset);
        toggle($('pcEmpty'), matched === 0 && !errorState);

        const total = data.total || 0;
        let info;
        if (matched === 0) {
            info = 'Không có kết quả';
        } else {
            const shown = (data.rows || []).length;
            const filteredNote = (matched !== total) ? (' (lọc từ ' + total + ')') : '';
            info = 'Hiển thị ' + (offset + 1) + '–' + (offset + shown) + ' / ' + matched + ' KH' + filteredNote
                 + ' · Trang ' + page + '/' + totalPages;
        }
        const infoEl = $('pcPageInfo');
        if (infoEl) infoEl.textContent = info;
        renderPagination(page, totalPages);
    }

    function goToPage(page) {
        if (page < 1) return;
        state.page = page;
        loadData(false);
    }

    function setGroup(group) {
        state.group = group;
        state.page = 1;
        const sel = $('pcGroupFilter');
        if (sel) sel.value = group;
        document.querySelectorAll('.mr-stat[data-group]').forEach(el => {
            el.classList.toggle('is-active', el.getAttribute('data-group') === group);
        });
        loadData(false);
    }

    // ---- Nạp 1 trang từ server ----
    function serverParams() {
        const p = new URLSearchParams();
        SERVER_FILTER_KEYS.forEach(k => { const el = $(k); if (el && el.value !== '') p.set(k, el.value); });
        p.set('group', state.group);
        if (state.search) p.set('search', state.search);
        if (state.sortKey && state.sortDir) { p.set('sort', state.sortKey); p.set('dir', state.sortDir); }
        p.set('page', state.page);
        p.set('pageSize', state.pageSize);
        return p;
    }

    async function loadData(silent) {
        if (inFlight) {
            if (silent) return;
            if (currentAbort) currentAbort.abort();
        }
        inFlight = true;
        const ac = new AbortController();
        currentAbort = ac;
        if (!silent) {
            setLive('is-refresh', 'Đang cập nhật…');
            body.innerHTML = '<tr><td colspan="' + COLSPAN + '" class="mr-empty">⏳ Đang tải…</td></tr>';
            toggle($('pcEmpty'), false);
        }
        try {
            const params = serverParams();
            const res = await fetch(DATA_URL + '?' + params.toString(), { signal: ac.signal });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            lastData = data;
            errorState = !!data.errorMessage;

            // Server có thể clamp trang → đồng bộ lại state
            if (data.page) state.page = data.page;

            // Stat card (toàn tập theo khoảng ngày)
            const set = (id, v) => { const el = $(id); if (el) el.textContent = v; };
            set('pcTotal', data.total);
            set('pcGroupA', data.groupACount);
            set('pcGroupB', data.groupBCount);
            set('pcGroupC', data.groupCCount);
            set('pcGroupD', data.groupDCount);

            const errAlert = $('pcErrorAlert');
            if (errAlert) { errAlert.textContent = errorState ? data.errorMessage : ''; toggle(errAlert, errorState); }
            toggle($('pcCappedAlert'), false); // không còn cap ở server-side

            set('pcGeneratedAt', data.generatedAt);
            set('pcMaxDays', data.maxDays);
            set('pcMonths', data.months);
            SERVER_FILTER_KEYS.forEach(k => { const el = $(k); if (el && data[k] != null) el.value = data[k]; });
            updateDateLabel();

            if ($('pcFilterForm')) {
                try { history.replaceState(null, '', location.pathname + '?' + params.toString()); } catch (e) {}
            }

            renderView(data);
            setLive('is-live', 'Cập nhật ' + new Date().toLocaleTimeString('vi-VN'));
        } catch (e) {
            if (e.name === 'AbortError') return;
            console.error('Load số nổi lỗi:', e);
            setLive('is-down', 'Lỗi tải dữ liệu');
            if (!silent && lastData) renderView(lastData); // khôi phục trang trước
        } finally {
            if (currentAbort === ac) { inFlight = false; currentAbort = null; }
        }
    }

    // ---- Sự kiện ----
    const form = $('pcFilterForm');
    if (form) {
        form.addEventListener('submit', function (ev) {
            ev.preventDefault();
            state.page = 1;
            loadData(false);
        });
    }

    const refreshBtn = $('pcRefreshBtn');
    if (refreshBtn) refreshBtn.addEventListener('click', () => loadData(false));

    if (pageSizeEl) {
        pageSizeEl.addEventListener('change', function () {
            state.pageSize = parseInt(this.value, 10) || state.pageSize;
            state.page = 1;
            loadData(false);
        });
    }

    const groupSel = $('pcGroupFilter');
    if (groupSel) groupSel.addEventListener('change', function () { setGroup(this.value); });

    document.querySelectorAll('.mr-stat[data-group]').forEach(el => {
        el.addEventListener('click', () => setGroup(el.getAttribute('data-group')));
    });

    document.querySelectorAll('th.pc-sortable').forEach(th => {
        th.addEventListener('click', () => toggleSort(th.getAttribute('data-sort')));
    });

    // ---- Xóa nhóm (chỉ admin) ----
    const GROUP_LABEL = { A: 'Nhóm A', B: 'Nhóm B', C: 'Nhóm C', D: 'Nhóm D (Từ chối chăm)' };
    let removeMenu = null;

    function closeRemoveMenu() {
        if (removeMenu) { removeMenu.remove(); removeMenu = null; }
        document.removeEventListener('mousedown', onRemoveOutside);
        document.removeEventListener('keydown', onRemoveEsc);
    }
    function onRemoveOutside(e) { if (removeMenu && !removeMenu.contains(e.target)) closeRemoveMenu(); }
    function onRemoveEsc(e) { if (e.key === 'Escape') closeRemoveMenu(); }

    function openRemoveMenu(btn, cid, name, groups) {
        closeRemoveMenu();
        const menu = document.createElement('div');
        menu.className = 'pc-remove-menu';
        let html = '<div class="pc-remove-menu-title">Xóa "' + escapeHtml(name) + '" khỏi:</div>';
        groups.forEach(g => { html += '<button type="button" data-g="' + g + '">' + GROUP_LABEL[g] + '</button>'; });
        if (groups.length > 1) html += '<button type="button" class="pc-remove-all" data-g="*">Tất cả nhóm trên</button>';
        menu.innerHTML = html;
        document.body.appendChild(menu);
        const rect = btn.getBoundingClientRect();
        menu.style.top = (window.scrollY + rect.bottom + 4) + 'px';
        // Canh phải theo nút, không tràn mép trái
        menu.style.left = Math.max(8, window.scrollX + rect.right - menu.offsetWidth) + 'px';
        menu.querySelectorAll('button').forEach(b => {
            b.addEventListener('click', () => {
                const g = b.getAttribute('data-g');
                closeRemoveMenu();
                if (g === '*') removeGroups(cid, groups);
                else removeGroups(cid, [g]);
            });
        });
        setTimeout(() => {
            document.addEventListener('mousedown', onRemoveOutside);
            document.addEventListener('keydown', onRemoveEsc);
        }, 0);
    }

    async function removeGroups(cid, groups) {
        const names = groups.map(g => GROUP_LABEL[g]).join(', ');
        if (!confirm('Xóa khách khỏi ' + names + '?')) return;
        try {
            for (const g of groups) {
                const res = await fetch('/api/khach-hang-canh-bao/remove-group', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: 'customerId=' + encodeURIComponent(cid) + '&group=' + encodeURIComponent(g)
                });
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                if (!data.success) throw new Error(data.errorMessage || 'Lỗi không xác định');
            }
            loadData(false);
        } catch (e) {
            console.error('Xóa nhóm lỗi:', e);
            alert('Xóa nhóm thất bại: ' + e.message);
        }
    }

    if (isAdmin) {
        body.addEventListener('click', function (e) {
            const btn = e.target.closest('.pc-remove-btn');
            if (!btn) return;
            const cid = btn.getAttribute('data-cid');
            const name = btn.getAttribute('data-name') || cid;
            const groups = (btn.getAttribute('data-groups') || '').split(',').filter(Boolean);
            if (!cid || !groups.length) return;
            if (groups.length === 1) removeGroups(cid, groups);
            else openRemoveMenu(btn, cid, name, groups);
        });
    }

    const searchEl = $('pcSearch');
    if (searchEl) {
        searchEl.addEventListener('input', function () {
            const v = this.value;
            clearTimeout(searchDebounce);
            searchDebounce = setTimeout(() => { state.search = v.trim(); state.page = 1; loadData(false); }, 300);
        });
    }

    function startPolling() { stopPolling(); pollTimer = setInterval(() => loadData(true), POLL_INTERVAL_MS); }
    function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }
    const auto = $('pcAutoRefresh');
    if (auto) auto.addEventListener('change', function () { this.checked ? startPolling() : stopPolling(); });

    // ---- Bộ chọn khoảng Ngày tạo KH (popover + preset + Áp dụng) ----
    const dateBtn = $('pcDateBtn');
    const datePopover = $('pcDatePopover');
    const dateLabel = $('pcDateLabel');
    const fromEl = $('fromDate');
    const toEl = $('toDate');

    function pad2(n) { return String(n).padStart(2, '0'); }
    function fmtISO(d) { return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()); }
    function isoToDisplay(iso) {
        if (!iso) return '';
        const p = iso.split('-');
        return p.length === 3 ? (p[2] + '/' + p[1] + '/' + p[0]) : iso;
    }

    function updateDateLabel() {
        if (!dateLabel) return;
        const f = fromEl ? fromEl.value : '';
        const t = toEl ? toEl.value : '';
        if (f && t) dateLabel.textContent = isoToDisplay(f) + ' – ' + isoToDisplay(t);
        else if (f) dateLabel.textContent = 'Từ ' + isoToDisplay(f);
        else if (t) dateLabel.textContent = 'Đến ' + isoToDisplay(t);
        else dateLabel.textContent = 'Tất cả thời gian';
    }

    function onDateOutside(e) {
        if (!datePopover) return;
        if (!datePopover.contains(e.target) && dateBtn && !dateBtn.contains(e.target)) closeDatePopover();
    }
    function openDatePopover() {
        if (!datePopover) return;
        datePopover.hidden = false;
        document.addEventListener('mousedown', onDateOutside);
    }
    function closeDatePopover() {
        if (!datePopover) return;
        datePopover.hidden = true;
        document.removeEventListener('mousedown', onDateOutside);
    }

    function computePreset(name) {
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const start = new Date(today), end = new Date(today);
        switch (name) {
            case 'today': break;
            case 'yesterday': start.setDate(today.getDate() - 1); end.setDate(today.getDate() - 1); break;
            case 'last7': start.setDate(today.getDate() - 6); break;
            case 'last30': start.setDate(today.getDate() - 29); break;
            case 'last90': start.setDate(today.getDate() - 89); break;
            case 'lastMonth': {
                const s = new Date(today.getFullYear(), today.getMonth() - 1, 1);
                const e = new Date(today.getFullYear(), today.getMonth(), 0);
                return { from: fmtISO(s), to: fmtISO(e) };
            }
            case 'weekToDate': { const dow = (today.getDay() + 6) % 7; start.setDate(today.getDate() - dow); break; }
            case 'monthToDate': start.setDate(1); break;
            default: return { from: '', to: '' };
        }
        return { from: fmtISO(start), to: fmtISO(end) };
    }

    function applyDates() {
        updateDateLabel();
        state.page = 1;
        closeDatePopover();
        loadData(false);
    }

    if (dateBtn && datePopover) {
        dateBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            datePopover.hidden ? openDatePopover() : closeDatePopover();
        });
        document.querySelectorAll('.pc-preset').forEach(btn => {
            btn.addEventListener('click', function () {
                const r = computePreset(this.getAttribute('data-preset'));
                if (fromEl) fromEl.value = r.from;
                if (toEl) toEl.value = r.to;
                applyDates();
            });
        });
        const dateApply = $('pcDateApply');
        if (dateApply) dateApply.addEventListener('click', applyDates);
        const dateClear = $('pcDateClear');
        if (dateClear) dateClear.addEventListener('click', function () {
            if (fromEl) fromEl.value = '';
            if (toEl) toEl.value = '';
            applyDates();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !datePopover.hidden) closeDatePopover();
        });
    }
    updateDateLabel();

    // Khởi động
    loadData(false);
    if (!auto || auto.checked) startPolling();
})();
