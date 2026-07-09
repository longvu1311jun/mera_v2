/*
 * Trang "Số thả nổi" — logic dùng chung cho bản admin (/khach-hang-canh-bao)
 * và bản chia sẻ cho team sale (/so-tha-noi).
 *
 * Nạp toàn bộ tập cảnh báo 1 lần từ /api/khach-hang-canh-bao/data, cache phía client,
 * rồi phân trang / lọc nhóm / tìm kiếm hoàn toàn trong trình duyệt (tức thời).
 * Polling 60s làm mới cache. Trang share không có form filter server -> tự dùng mặc định.
 */
(function () {
    const DATA_URL = '/api/khach-hang-canh-bao/data';
    const POLL_INTERVAL_MS = 60000;
    const SERVER_FILTER_KEYS = ['minNotes', 'hours', 'maxDays', 'months', 'fromDate', 'toDate'];

    const $ = (id) => document.getElementById(id);
    const body = $('pcBody');
    if (!body) return; // không phải trang số nổi

    // Toàn bộ tập (cache client) + trạng thái xem
    let allRows = [];
    let lastData = null;
    let errorState = false;
    let inFlight = false;
    let pollTimer = null;
    let searchDebounce = null;

    const pageSizeEl = $('pcPageSize');
    const state = {
        page: 1,
        pageSize: (pageSizeEl && parseInt(pageSizeEl.value, 10)) || 50,
        group: 'all',        // all | A | B | C
        search: ''
    };

    function escapeHtml(s) {
        if (s === null || s === undefined) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function normDigits(s) { return (s || '').replace(/\D/g, ''); }
    function toggle(el, show) { if (el) el.style.display = show ? '' : 'none'; }
    function badge(cls, txt) { return '<span class="mr-badge ' + cls + '">' + txt + '</span>'; }

    function setLive(cls, text) {
        const wrap = $('pcLiveStatus'), label = $('pcLiveText');
        if (wrap) { wrap.classList.remove('is-live', 'is-down', 'is-refresh'); if (cls) wrap.classList.add(cls); }
        if (label && text) label.textContent = text;
    }

    // ---- Lọc client (nhóm + tìm kiếm) ----
    function filteredRows() {
        const g = state.group;
        const term = state.search.trim().toLowerCase();
        const digits = normDigits(state.search);
        return allRows.filter(r => {
            if (g === 'A' && !r.groupA) return false;
            if (g === 'B' && !r.groupB) return false;
            if (g === 'C' && !r.groupC) return false;
            if (term) {
                const nameMatch = (r.name || '').toLowerCase().indexOf(term) > -1;
                const phoneMatch = digits.length > 0 && normDigits(r.phone).indexOf(digits) > -1;
                if (!nameMatch && !phoneMatch) return false;
            }
            return true;
        });
    }

    function renderRows(rows, offset) {
        if (!rows.length) { body.innerHTML = ''; return; }
        const minNotes = lastData ? lastData.minNotes : 0;
        let html = '';
        for (let i = 0; i < rows.length; i++) {
            const r = rows[i];
            const noteCls = r.noteCount >= minNotes ? ' class="note-hot"' : '';
            let badges = '';
            if (r.groupA) badges += badge('mr-badge--warn', 'Nhóm A');
            if (r.groupB) badges += badge('mr-badge--danger', 'Nhóm B');
            if (r.groupC) badges += badge('mr-badge--info', 'Nhóm C');
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
                + '</tr>';
        }
        body.innerHTML = html;
    }

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

    // ---- Áp dụng bộ lọc/tìm kiếm + phân trang (tức thời, không gọi server) ----
    function applyView() {
        const rows = filteredRows();
        const total = rows.length;
        const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
        if (state.page > totalPages) state.page = totalPages;
        if (state.page < 1) state.page = 1;

        const offset = (state.page - 1) * state.pageSize;
        const slice = rows.slice(offset, offset + state.pageSize);
        renderRows(slice, offset);
        toggle($('pcEmpty'), total === 0 && !errorState);

        const fullTotal = allRows.length;
        let info;
        if (total === 0) {
            info = 'Không có kết quả';
        } else {
            const filteredNote = (total !== fullTotal) ? (' (lọc từ ' + fullTotal + ')') : '';
            info = 'Hiển thị ' + (offset + 1) + '–' + (offset + slice.length) + ' / ' + total + ' KH' + filteredNote
                 + ' · Trang ' + state.page + '/' + totalPages;
        }
        const infoEl = $('pcPageInfo');
        if (infoEl) infoEl.textContent = info;
        renderPagination(state.page, totalPages);
    }

    function goToPage(page) {
        if (page < 1) return;
        state.page = page;
        applyView();
    }

    function setGroup(group) {
        state.group = group;
        state.page = 1;
        const sel = $('pcGroupFilter');
        if (sel) sel.value = group;
        document.querySelectorAll('.mr-stat[data-group]').forEach(el => {
            el.classList.toggle('is-active', el.getAttribute('data-group') === group);
        });
        applyView();
    }

    // ---- Nạp dữ liệu từ server (fetch toàn bộ) ----
    function serverFilters() {
        const p = new URLSearchParams();
        SERVER_FILTER_KEYS.forEach(k => { const el = $(k); if (el && el.value !== '') p.set(k, el.value); });
        return p;
    }

    async function loadData(silent) {
        if (inFlight) return;
        inFlight = true;
        if (!silent) setLive('is-refresh', 'Đang cập nhật…');
        try {
            const params = serverFilters();
            const res = await fetch(DATA_URL + '?' + params.toString());
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            lastData = data;
            allRows = data.rows || [];
            errorState = !!data.errorMessage;

            // Số liệu tổng (toàn tập)
            const set = (id, v) => { const el = $(id); if (el) el.textContent = v; };
            set('pcTotal', data.total);
            set('pcGroupA', data.groupACount);
            set('pcGroupB', data.groupBCount);
            set('pcGroupC', data.groupCCount);

            // Cảnh báo
            const errAlert = $('pcErrorAlert');
            if (errAlert) { errAlert.textContent = errorState ? data.errorMessage : ''; toggle(errAlert, errorState); }
            toggle($('pcCappedAlert'), data.capped && !errorState);

            // Meta + đồng bộ input (nếu có) đã clamp
            set('pcGeneratedAt', data.generatedAt);
            set('pcMaxDays', data.maxDays);
            set('pcMonths', data.months);
            SERVER_FILTER_KEYS.forEach(k => { const el = $(k); if (el && data[k] != null) el.value = data[k]; });

            // Giữ URL cho các filter server (chỉ khi trang có form)
            if ($('pcFilterForm')) {
                try { history.replaceState(null, '', location.pathname + '?' + params.toString()); } catch (e) {}
            }

            applyView();
            setLive('is-live', 'Cập nhật ' + new Date().toLocaleTimeString('vi-VN'));
        } catch (e) {
            console.error('Load số nổi lỗi:', e);
            setLive('is-down', 'Lỗi tải dữ liệu');
        } finally {
            inFlight = false;
        }
    }

    // ---- Sự kiện ----
    const form = $('pcFilterForm');
    if (form) {
        form.addEventListener('submit', function (ev) {
            ev.preventDefault();
            state.page = 1;
            loadData(false);   // filter server -> fetch lại
        });
    }

    const refreshBtn = $('pcRefreshBtn');
    if (refreshBtn) refreshBtn.addEventListener('click', () => loadData(false));

    if (pageSizeEl) {
        pageSizeEl.addEventListener('change', function () {
            state.pageSize = parseInt(this.value, 10) || state.pageSize;
            state.page = 1;
            applyView();
        });
    }

    const groupSel = $('pcGroupFilter');
    if (groupSel) groupSel.addEventListener('change', function () { setGroup(this.value); });

    // Khoảng Ngày tạo KH là filter server -> đổi ngày thì fetch lại
    ['fromDate', 'toDate'].forEach(id => {
        const el = $(id);
        if (el) el.addEventListener('change', function () { state.page = 1; loadData(false); });
    });

    document.querySelectorAll('.mr-stat[data-group]').forEach(el => {
        el.addEventListener('click', () => setGroup(el.getAttribute('data-group')));
    });

    const searchEl = $('pcSearch');
    if (searchEl) {
        searchEl.addEventListener('input', function () {
            const v = this.value;
            clearTimeout(searchDebounce);
            searchDebounce = setTimeout(() => { state.search = v; state.page = 1; applyView(); }, 200);
        });
    }

    function startPolling() { stopPolling(); pollTimer = setInterval(() => loadData(true), POLL_INTERVAL_MS); }
    function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }
    const auto = $('pcAutoRefresh');
    if (auto) auto.addEventListener('change', function () { this.checked ? startPolling() : stopPolling(); });

    // Khởi động
    loadData(false);
    if (!auto || auto.checked) startPolling();
})();
