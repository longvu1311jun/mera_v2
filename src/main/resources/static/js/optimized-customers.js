/*
 * Trang "Khách đã tối ưu" (/khach-hang-da-toi-uu).
 *
 * Nạp toàn bộ danh sách 1 lần từ /api/khach-hang-da-toi-uu/data, cache phía client,
 * rồi phân trang / tìm kiếm / sắp xếp hoàn toàn trong trình duyệt. Polling 60s làm mới.
 */
(function () {
    const DATA_URL = '/api/khach-hang-da-toi-uu/data';
    const POLL_INTERVAL_MS = 60000;

    const $ = (id) => document.getElementById(id);
    const body = $('ocBody');
    if (!body) return;

    let allRows = [];
    let errorState = false;
    let inFlight = false;
    let currentAbort = null;
    let pollTimer = null;
    let searchDebounce = null;

    const pageSizeEl = $('ocPageSize');
    const state = {
        page: 1,
        pageSize: (pageSizeEl && parseInt(pageSizeEl.value, 10)) || 50,
        search: '',
        sortKey: null,
        sortDir: null
    };

    function escapeHtml(s) {
        if (s === null || s === undefined) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function normDigits(s) { return (s || '').replace(/\D/g, ''); }
    function toggle(el, show) { if (el) el.style.display = show ? '' : 'none'; }

    function setLive(cls, text) {
        const wrap = $('ocLiveStatus'), label = $('ocLiveText');
        if (wrap) { wrap.classList.remove('is-live', 'is-down', 'is-refresh'); if (cls) wrap.classList.add(cls); }
        if (label && text) label.textContent = text;
    }

    // ---- Tìm kiếm (tên / SĐT) ----
    function filteredRows() {
        const term = state.search.trim().toLowerCase();
        const digits = normDigits(state.search);
        if (!term) return allRows.slice();
        return allRows.filter(r => {
            const nameMatch = (r.name || '').toLowerCase().indexOf(term) > -1;
            const phoneMatch = digits.length > 0 && normDigits(r.phone).indexOf(digits) > -1;
            return nameMatch || phoneMatch;
        });
    }

    // ---- Sắp xếp ----
    /** "dd/MM/yyyy HH:mm" -> số so sánh được; "—"/rỗng -> null (đẩy xuống cuối). */
    function dateSortVal(s) {
        if (!s || s === '—') return null;
        const m = s.match(/(\d{2})\/(\d{2})\/(\d{4})\s+(\d{2}):(\d{2})/);
        if (!m) return null;
        return +(m[3] + m[2] + m[1] + m[4] + m[5]);
    }

    const SORT_COLS = {
        noteCount: r => r.noteCount,
        customerCreatedText: r => dateSortVal(r.customerCreatedText),
        firstSeenAt: r => dateSortVal(r.firstSeenAt),
        optimizedAt: r => dateSortVal(r.optimizedAt)
    };

    function sortedRows(rows) {
        const getter = state.sortKey && SORT_COLS[state.sortKey];
        if (!getter || !state.sortDir) return rows;
        const dir = state.sortDir === 'asc' ? 1 : -1;
        return rows.slice().sort((a, b) => {
            const va = getter(a), vb = getter(b);
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            return va < vb ? -dir : (va > vb ? dir : 0);
        });
    }

    function updateSortIndicators() {
        document.querySelectorAll('th.oc-sortable').forEach(th => {
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
            state.sortKey = null;
            state.sortDir = null;
        }
        state.page = 1;
        updateSortIndicators();
        applyView();
    }

    function orderCell(before, after) {
        const grew = (after || 0) > (before || 0);
        const afterHtml = grew ? '<span class="oc-after">' + after + '</span>' : String(after);
        return before + '<span class="oc-arrow">→</span>' + afterHtml;
    }

    function renderRows(rows, offset) {
        if (!rows.length) { body.innerHTML = ''; return; }
        let html = '';
        for (let i = 0; i < rows.length; i++) {
            const r = rows[i];
            html += '<tr>'
                + '<td>' + (offset + i + 1) + '</td>'
                + '<td>' + escapeHtml(r.name) + '</td>'
                + '<td>' + escapeHtml(r.phone != null ? r.phone : '—') + '</td>'
                + '<td class="customer-id">' + escapeHtml(r.customerId) + '</td>'
                + '<td class="num">' + r.noteCount + '</td>'
                + '<td class="num">' + orderCell(r.orderCountBefore, r.orderCountAfter) + '</td>'
                + '<td class="num">' + orderCell(r.succeedBefore, r.succeedAfter) + '</td>'
                + '<td>' + escapeHtml(r.customerCreatedText) + '</td>'
                + '<td>' + escapeHtml(r.firstSeenAt) + '</td>'
                + '<td><div class="reason-text">' + escapeHtml(r.reason) + '</div></td>'
                + '<td>' + escapeHtml(r.optimizedAt) + '</td>'
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
        const pager = $('ocPager');
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

    function applyView() {
        const rows = sortedRows(filteredRows());
        const total = rows.length;
        const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
        if (state.page > totalPages) state.page = totalPages;
        if (state.page < 1) state.page = 1;

        const offset = (state.page - 1) * state.pageSize;
        const slice = rows.slice(offset, offset + state.pageSize);
        renderRows(slice, offset);
        toggle($('ocEmpty'), total === 0 && !errorState);

        const fullTotal = allRows.length;
        let info;
        if (total === 0) {
            info = 'Không có kết quả';
        } else {
            const filteredNote = (total !== fullTotal) ? (' (lọc từ ' + fullTotal + ')') : '';
            info = 'Hiển thị ' + (offset + 1) + '–' + (offset + slice.length) + ' / ' + total + ' KH' + filteredNote
                 + ' · Trang ' + state.page + '/' + totalPages;
        }
        const infoEl = $('ocPageInfo');
        if (infoEl) infoEl.textContent = info;
        renderPagination(state.page, totalPages);
    }

    function goToPage(page) {
        if (page < 1) return;
        state.page = page;
        applyView();
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
            body.innerHTML = '<tr><td colspan="11" class="mr-empty">⏳ Đang tải dữ liệu…</td></tr>';
            toggle($('ocEmpty'), false);
        }
        try {
            const res = await fetch(DATA_URL, { signal: ac.signal });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            allRows = data.rows || [];
            errorState = !!data.errorMessage;

            const set = (id, v) => { const el = $(id); if (el) el.textContent = v; };
            set('ocTotal', data.total);
            set('ocToday', data.optimizedToday);
            set('ocGeneratedAt', data.generatedAt);

            const errAlert = $('ocErrorAlert');
            if (errAlert) { errAlert.textContent = errorState ? data.errorMessage : ''; toggle(errAlert, errorState); }

            applyView();
            setLive('is-live', 'Cập nhật ' + new Date().toLocaleTimeString('vi-VN'));
        } catch (e) {
            if (e.name === 'AbortError') return;
            console.error('Load khách tối ưu lỗi:', e);
            setLive('is-down', 'Lỗi tải dữ liệu');
            if (!silent) applyView();
        } finally {
            if (currentAbort === ac) { inFlight = false; currentAbort = null; }
        }
    }

    // ---- Sự kiện ----
    const refreshBtn = $('ocRefreshBtn');
    if (refreshBtn) refreshBtn.addEventListener('click', () => loadData(false));

    if (pageSizeEl) {
        pageSizeEl.addEventListener('change', function () {
            state.pageSize = parseInt(this.value, 10) || state.pageSize;
            state.page = 1;
            applyView();
        });
    }

    document.querySelectorAll('th.oc-sortable').forEach(th => {
        th.addEventListener('click', () => toggleSort(th.getAttribute('data-sort')));
    });

    const searchEl = $('ocSearch');
    if (searchEl) {
        searchEl.addEventListener('input', function () {
            const v = this.value;
            clearTimeout(searchDebounce);
            searchDebounce = setTimeout(() => { state.search = v; state.page = 1; applyView(); }, 200);
        });
    }

    function startPolling() { stopPolling(); pollTimer = setInterval(() => loadData(true), POLL_INTERVAL_MS); }
    function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }
    const auto = $('ocAutoRefresh');
    if (auto) auto.addEventListener('change', function () { this.checked ? startPolling() : stopPolling(); });

    // Khởi động
    loadData(false);
    if (!auto || auto.checked) startPolling();
})();
