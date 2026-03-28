// Shared search-info JS for demo.html and searchInfo.html
(function () {
  // Tailwind config
  tailwind.config = {
    theme: {
      extend: {
        colors: {
          brand: { 50: '#f0fdfa', 100: '#ccfbf1', 500: '#14b8a6', 600: '#0d9488', 700: '#0f766e' }
        }
      }
    }
  };

  const phoneInput = document.getElementById('phoneInput');
  const searchBtn = document.getElementById('searchBtn');
  const customerCard = document.getElementById('customerCard');
  const customerInfo = document.getElementById('customerInfo');
  const ordersWrap = document.getElementById('ordersWrap') || document.getElementById('ordersList');
  const notesWrap = document.getElementById('notesWrap') || document.getElementById('notesList');
  const loadingOverlay = document.getElementById('loadingOverlay');
  let lastOrders = [];
  let lastCustomer = null;
  const emptyStateEl = document.getElementById('emptyState');
  const DEFAULT_EMPTY_TEXT = emptyStateEl ? emptyStateEl.textContent.trim() : 'Nhập số điện thoại để tìm kiếm thông tin khách hàng';
  const STATUS_MAP = {
    0: 'Mới',
    17: 'Chờ xác nhận',
    11: 'Chờ hàng',
    12: 'Chờ in',
    13: 'Đã in',
    20: 'Đã đặt hàng',
    1: 'Đã xác nhận',
    8: 'Đang đóng hàng',
    9: 'Chờ chuyển hàng',
    2: 'Đã gửi hàng',
    3: 'Đã nhận',
    16: 'Đã thu tiền',
    4: 'Đang hoàn',
    15: 'Hoàn một phần',
    5: 'Đã hoàn',
    6: 'Đã hủy',
    7: 'Đã xóa'
  };

  function showLoading(show) { if (loadingOverlay) loadingOverlay.style.display = show ? 'flex' : 'none'; }

  function sanitizePhone(value) { return (value || '').replace(/\D/g, '').trim(); }

  function formatDate(str) {
    if (!str) return '-';
    const date = new Date(str);
    if (isNaN(date.getTime())) return str;
    // Adjust to +7 hours (UTC+7) before formatting
    const adjusted = new Date(date.getTime() + 7 * 60 * 60 * 1000);
    return adjusted.toLocaleString('vi-VN');
  }

  function escapeHtml(s) { return (s == null) ? '' : String(s).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m])); }

  function renderCustomer(customer) {
    if (!customer) { if (customerCard) customerCard.style.display = 'none'; return; }
    lastCustomer = customer;
    if (customerInfo) {
      const succeedCount = customer.succeedOrderCount || 0;
      const customerStatus = succeedCount === 0 ? 'Khách mới' : 'Khách cũ';
      customerInfo.innerHTML = `
            <div class="w-20 h-20 mx-auto -mt-10 bg-white rounded-full p-1 shadow-md">
                <img src="https://api.dicebear.com/9.x/avataaars/svg?seed=Amaya&backgroundColor=65c9ff,b6e3f4&backgroundType=solid,gradientLinear&clothingGraphic=diamond&eyebrows=default,defaultNatural&eyes=default&facialHair[]&facialHairColor[]"
                    alt="Avatar" class="w-full h-full rounded-full">
            </div>
            <h2 class="mt-2 text-xl font-bold text-slate-800" >${escapeHtml(customer.name || '-')}</h2>
            <div class="flex justify-center items-center gap-2 text-slate-500 mt-1 mb-4">
                <span class="font-bold text-slate-700 text-lg">${escapeHtml(customer.phone || '-')}</span>
                <button class="text-brand-600 bg-brand-50 px-2 py-0.5 rounded text-xs hover:bg-brand-100"><i class="fa-regular fa-copy"></i></button>
            </div>
            <div class="grid grid-cols-2 gap-3 mb-4">
                <div class="bg-slate-50 p-2 rounded border border-slate-100">
                    <span class="block text-[10px] text-slate-400 uppercase font-bold">Thành công</span>
                    <span class="block text-xl font-bold text-emerald-600">${escapeHtml(String(customer.succeedOrderCount || 0))}</span>
                </div>
                <div class="bg-slate-50 p-2 rounded border border-slate-100">
                    <span class="block text-[10px] text-slate-400 uppercase font-bold">Trạng thái</span>
                    <span class="block text-base font-bold text-slate-700 mt-0.5">${escapeHtml(customerStatus)}</span>
                </div>
            </div>
            <div class="text-left space-y-3 border-t border-slate-100 pt-3">
                <div>
                    <span class="text-[10px] text-slate-400 font-bold uppercase">Customer ID</span>
                    <div class="bg-slate-100 text-slate-600 text-[11px] p-1.5 rounded font-mono truncate mt-1">${escapeHtml(customer.customerId || '-')}</div>
                </div>
                <div>
                    <span class="text-[10px] text-slate-400 font-bold uppercase">Địa chỉ</span>
                    <p class="text-sm text-slate-500 italic mt-0.5">${escapeHtml(customer.fullAddress || '-')}</p>
                </div>
            </div>
      `;
      if (customerCard) customerCard.style.display = 'block';
      // copy button handler: copy phone to clipboard
      try {
        const copyBtn = customerInfo.querySelector('button');
        if (copyBtn) {
          copyBtn.addEventListener('click', () => {
            try {
              const phoneEl = customerInfo.querySelector('.font-bold.text-slate-700');
              const phoneText = phoneEl ? phoneEl.textContent.trim() : (customer.phone || '');
              if (!phoneText) { alert('Không có số điện thoại để copy'); return; }
              if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(phoneText).then(() => {
                  // small feedback: change button temporarily
                  const original = copyBtn.innerHTML;
                  copyBtn.innerHTML = '<i class="fa-regular fa-copy"></i> Đã copy';
                  setTimeout(() => { copyBtn.innerHTML = original; }, 1200);
                }).catch(() => { alert('Không thể copy số điện thoại'); });
              } else {
                // fallback
                const ta = document.createElement('textarea');
                ta.value = phoneText;
                document.body.appendChild(ta);
                ta.select();
                try { document.execCommand('copy'); alert('Đã copy'); } catch (e) { alert('Không thể copy số điện thoại'); }
                document.body.removeChild(ta);
              }
            } catch (e) { console.warn('Copy failed', e); alert('Không thể copy số điện thoại'); }
          });
        }
      } catch (e) {}
    }
  }

  // function renderSummary(summary) {
  //   const summaryInfo = document.getElementById('summaryInfo');
  //   if(!summary) { if (summaryInfo) summaryInfo.innerHTML = '<span class="muted">Không có dữ liệu</span>'; return; }
  //   if (summaryInfo) summaryInfo.innerHTML = `
  //     <div><strong>Tổng đơn:</strong> ${summary.totalOrders ?? 0}</div>
  //     <div><strong>Giao thành công:</strong> ${summary.deliveredOrders ?? 0}</div>
  //     <div><strong>Hoàn/Trả:</strong> ${summary.returnedOrders ?? 0}</div>
  //     <div><strong>Đã chi:</strong> ${summary.totalSpent ?? 0}</div>
  //     <div><strong>COD:</strong> ${summary.totalCOD ?? 0}</div>
  //     <div><strong>COD đã đối soát:</strong> ${summary.reconciledCOD ?? 0}</div>
  //   `;
  // }

  function itemsHtml(items) {
    if (!items || !items.length) return `<span class="text-slate-400 text-sm">Không có sản phẩm</span>`;
    return items.map(item => `<span class="bg-blue-50 text-blue-700 text-sm font-medium px-2 py-0.5 rounded border border-blue-100">${escapeHtml(item.name)} (${escapeHtml(String(item.quantity||0))})</span>`).join('');
  }

  function renderOrders(orders) {
    console.log('render order');
    lastOrders = orders || [];
    // populate left product summary element
    try {
      const leftEl = document.getElementById('leftProductSummaryContent');
      if (leftEl) {
        // Aggregate purchased product counts for orders with status = 3 (Đã nhận)
        const receivedOrders = (orders || []).filter(o => {
          const s = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
          return s === 3;
        });
        const productCounts = {};
        receivedOrders.forEach(o => {
          (o.items || []).forEach(it => {
            const name = it.name || it.product_name || it.productName || 'Unknown';
            const qty = Number(it.quantity || 0);
            productCounts[name] = (productCounts[name] || 0) + qty;
          });
        });
        const keys = Object.keys(productCounts);
        if (keys.length === 0) {
          leftEl.innerHTML = '<span class=\"text-slate-400\">Không có sản phẩm đã nhận</span>';
        } else {
          leftEl.innerHTML = '<ul class=\"list-disc pl-5\">' + keys.map(k => `<li>${escapeHtml(k)} — <strong>${productCounts[k]}</strong></li>`).join('') + '</ul>';
        }
      }
    } catch (e) {
      console.warn('Could not render left product summary', e);
    }
    if (!orders || orders.length === 0) {
      if (ordersWrap) ordersWrap.innerHTML = '<span class="text-slate-400 text-sm">Không có dữ liệu</span>';
      return;
    }
    // Count orders excluding canceled (status === 6)
    const visibleOrdersCount = (orders || []).filter(o => {
      const s = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
      return s !== 6;
    }).length;

    let html = `
    <div class="flex flex-col bg-white rounded-xl shadow-sm border border-slate-200 h-full overflow-hidden">
        <div class="px-5 py-3 border-b border-slate-100 bg-white sticky top-0 z-10 flex justify-between items-center">
            <h3 class="font-bold text-lg text-slate-800">
                <i class="fa-solid fa-box-open text-brand-500 mr-2"></i>Lịch sử mua hàng
            </h3>
            <span class="text-sm text-slate-400 font-medium">${visibleOrdersCount} đơn hàng</span>
        </div>
        <div class="p-3 space-y-3 overflow-y-auto custom-scrollbar flex-1 bg-slate-50/30">
    `;
    orders.forEach(o => {
      // ==== Status mapping ====
      const status = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
      // Skip canceled orders (status = 6)
      if (status === 6) return;
      let statusText = status != null ? (STATUS_MAP[status] || String(status)) : '-';
      let statusClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (status === 3) {
        statusClass = 'bg-emerald-50 text-emerald-600 border-emerald-100';
      } else if (status === 6) {
        statusClass = 'bg-red-50 text-white-600 border-red-100';
      } else if (status === 1) {
        statusClass = 'bg-blue-100 text-white-600 border-blue-100';
      }else if (status === 11) {
        statusClass = 'bg-yellow-50 text-white-600 border-yellow-100';
      }else if (status === 9) {
        statusClass = 'bg-pink-50 text-white-600 border-yellow-100';
      }else if (status === 5) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else if (status === 8) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else if (status === 4) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else{
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }

      // ==== Items ====
      let itemsHtmlStr = '';
      if (o.items && o.items.length > 0) {
        itemsHtmlStr = o.items.map(item => `
          <span class="bg-blue-50 text-blue-700 text-sm font-medium px-2 py-0.5 rounded border border-blue-100">
              ${escapeHtml(item.name)} (${escapeHtml(String(item.quantity))})
          </span>
        `).join('');
      } else {
        itemsHtmlStr = `<span class="text-slate-400 text-sm">Không có sản phẩm</span>`;
      }

      // ==== Time ====
      const dateTime = formatDate(o.timeAssignSeller);
      const orderId = o.orderId ?? o.systemId ?? '-';
      // determine sale and cskh per rules (uses orderSourcesName and assigningCareName)
      let saleName = o.assigningSellerName || '-';
      let cskhName = o.assigningCareName || '-';
      if (o.orderSourcesName && typeof o.orderSourcesName === 'string') {
        const src = o.orderSourcesName.toLowerCase();
        if (src.includes('facebook')) {
          saleName = o.assigningCareName || saleName;
          cskhName = o.assigningSellerName || '-';
        } else if (src.includes('zalo')) {
          saleName = o.assigningSellerName || '-';
          cskhName = saleName;
        }
      }

      html += `
        <div class="bg-white border border-slate-200 rounded-lg p-3 hover:border-brand-300 hover:shadow-md transition">
            <div class="flex justify-between items-start mb-2">
                <div>
                    <div class="text-xs font-bold text-slate-400 uppercase">#${o.orderId ? (o.orderLink ?
                                `<a href="${o.orderLink}" target="_blank" rel="noopener noreferrer">${escapeHtml(o.orderId)}</a>`
                                :
                                `<a href="https://pos.pages.fm/shop/1546758/order?order_id=${encodeURIComponent(o.orderId)}" target="_blank" rel="noopener noreferrer">${escapeHtml(o.orderId)}</a>`) : '-'}</div>
                    <h4 class="font-bold text-slate-800 text-base">${dateTime}</h4>
                    <div class="mt-1">
<!--                      <button data-order-id="${escapeHtml(o.orderId)}" class="order-open text-sm text-brand-600 hover:underline">Chi tiết</button>-->
                    </div>
                </div>
                <span class="text-xs px-2 py-0.5 rounded border font-bold ${statusClass}">
                    ${statusText}
                </span>
            </div>
            <div class="flex flex-wrap gap-1.5 mb-2">
                ${itemsHtmlStr}
            </div>
            <div class="text-sm text-slate-400 pt-2 border-t border-slate-50">
                <div><span class="font-medium">CSKH:</span> ${escapeHtml(saleName)}</div>
                <div class="mt-1"><span class="font-medium">Sale:</span> ${escapeHtml(cskhName)}</div>
            </div>
        </div>
        `;
    });
    html += `
        </div>
    </div>
    `;
    if (ordersWrap) ordersWrap.innerHTML = html;
  }

  // Notes section removed per UI request (we only display 'Trao đổi' now)

  function renderConversations(conversations) {
    const list = document.getElementById('conversationsList') || document.getElementById('conversationsWrap') || document.getElementById('conversationsList');
    if (!list) return;
    if (!conversations || conversations.length === 0) {
      list.innerHTML = '<span class="text-slate-400">Chưa có trao đổi</span>';
      return;
    }
    let html = `<div class="flex flex-col gap-3">`;
    conversations.forEach(c => {
      html += `
        <div class="bg-white p-3 rounded border border-slate-100">
          <div class="text-sm text-slate-700 mb-1">${escapeHtml(c.content || '')}</div>
          <div class="text-xs text-slate-400">Người: ${escapeHtml(c.name || '-')} • ${escapeHtml(c.createdAt == null ? '' : String(c.createdAt))}</div>
        </div>
      `;
    });
    html += `</div>`;
    list.innerHTML = html;
  }

  // Render POS notes (from SearchService -> customer.notes)
  function renderPosNotes(notes) {
    const wrap = document.getElementById('posNotesList');
    if (!wrap) return;
    try {
      if (!notes || !Array.isArray(notes) || notes.length === 0) {
        wrap.innerHTML = '<span class="text-slate-400">Chưa có ghi chú</span>';
        return;
      }
      const html = notes.map(n => {
        const message = (n.message || n.msg || n.content || '').toString();
        const orderId = (n.order_id || n.orderId || '') ? `<div class="text-xs text-slate-400 mt-1">${escapeHtml(String(n.order_id || n.orderId || ''))}</div>` : '';
        const created = n.created_at || n.createdAt || n.Ngày || '';
        const createdText = created ? ` • ${escapeHtml(String(created))}` : '';
        return `<div class="bg-white p-2 rounded border border-slate-100 mb-2">
            <div class="text-sm text-slate-700">${escapeHtml(message)}</div>
            <div class="text-xs text-slate-400 mt-1"> ${escapeHtml(n.createdBy || n.created_by || '')}${createdText}</div>
            ${orderId}
          </div>`;
      }).join('');
      wrap.innerHTML = `<div class="flex flex-col">${html}</div>`;
    } catch (e) {
      wrap.innerHTML = '<span class="text-red-500 text-sm">Lỗi khi hiển thị ghi chú</span>';
    }
  }

  async function doSearch() {
    console.log(phoneInput ? phoneInput.value : 'no input');
    const phone = sanitizePhone(phoneInput ? phoneInput.value : '');
    if (!phone) {
      alert('Vui lòng nhập số điện thoại hợp lệ');
      return;
    }
    // Hide main panels while searching; we'll show them only after customer/orders are loaded
    try {
      const mainGrid = document.getElementById('mainGrid');
      const emptyState = document.getElementById('emptyState');
      if (mainGrid) mainGrid.classList.add('hidden');
      if (emptyState) emptyState.style.display = 'none';
    } catch (e) {}
    if (phoneInput) phoneInput.value = phone;
    // show loading overlay
    showLoading(true);
    showLoading(true);
    if (searchBtn) searchBtn.disabled = true;
    try {
      console.log('Clicked search button');
      const res = await fetch(`/api/search-info?phone=${encodeURIComponent(phone)}`);
      const data = await res.json();
      if (!res.ok || data.error || data.message) {
        // show single-line not found message and hide panels
        try {
          const mainGrid = document.getElementById('mainGrid');
          const emptyState = document.getElementById('emptyState');
          if (mainGrid) mainGrid.classList.add('hidden');
          if (emptyState) {
            emptyState.textContent = 'Không tìm thấy thông tin khách hàng';
            emptyState.style.display = 'flex';
          }
        } catch (e) {}
        // clear panels
        try { if (customerCard) customerCard.style.display = 'none'; } catch(e) {}
        try { if (ordersWrap) ordersWrap.innerHTML = ''; } catch(e) {}
        try { const convListEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap'); if (convListEl) convListEl.innerHTML = ''; } catch(e) {}
        return;
      }
      // If API returned but no customer data or customer fields empty -> treat as not found
      if (!data || !data.customer) {
        try {
          const mainGrid = document.getElementById('mainGrid');
          const emptyState = document.getElementById('emptyState');
          if (mainGrid) mainGrid.classList.add('hidden');
          if (emptyState) {
            emptyState.textContent = 'Không tìm thấy thông tin khách hàng';
            emptyState.style.display = 'flex';
          }
        } catch (e) {}
        try { if (customerCard) customerCard.style.display = 'none'; } catch(e) {}
        try { if (ordersWrap) ordersWrap.innerHTML = ''; } catch(e) {}
        try { const convListEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap'); if (convListEl) convListEl.innerHTML = ''; } catch(e) {}
        return;
      }
      // also consider customer empty if no id/name/phone present
      try {
        const cust = data.customer || {};
        const hasCustomerInfo = (cust.customerId && String(cust.customerId).trim()) || (cust.name && String(cust.name).trim()) || (cust.phone && String(cust.phone).trim());
        if (!hasCustomerInfo) {
          const mainGrid = document.getElementById('mainGrid');
          const emptyState = document.getElementById('emptyState');
          if (mainGrid) mainGrid.classList.add('hidden');
          if (emptyState) {
            emptyState.textContent = 'Không tìm thấy thông tin khách hàng';
            emptyState.style.display = 'flex';
          }
          try { if (customerCard) customerCard.style.display = 'none'; } catch(e) {}
          try { if (ordersWrap) ordersWrap.innerHTML = ''; } catch(e) {}
          try { const convListEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap'); if (convListEl) convListEl.innerHTML = ''; } catch(e) {}
          return;
        }
      } catch (e) {}
      // restore default emptyState text
      try { if (emptyStateEl) emptyStateEl.textContent = DEFAULT_EMPTY_TEXT; } catch(e) {}
      renderCustomer(data.customer);
      renderOrders(data.orders);
      // render POS notes from customer (if any)
      renderPosNotes(data.customer ? data.customer.notes : []);
      // show main grid now that customer & orders are rendered (ensure emptyState hidden)
      try { const mainGrid = document.getElementById('mainGrid'); const emptyState = document.getElementById('emptyState'); if (mainGrid) mainGrid.classList.remove('hidden'); if (emptyState) emptyState.style.display = 'none'; } catch(e) {}
      // show loading indicator for exchanges area while we fetch them
      try {
        const convListEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap');
        if (convListEl) convListEl.innerHTML = `<div class="p-4 flex items-center justify-center text-sm text-slate-500"><i class="fa-solid fa-circle-notch fa-spin mr-2"></i>Đang tải trao đổi...</div>`;
      } catch (e) {}
      // Hide global overlay now that customer/orders are visible;
      // exchanges will show their own inline spinner.
      try { showLoading(false); } catch (e) {}
      // sequentially fetch exchanges after customer/orders are rendered
      try {
        const urlParams = new URLSearchParams(window.location.search);
        const baseIdParam = urlParams.get('baseId') || window._preferredBaseId || '';
        const tableIdParam = urlParams.get('tableId') || window._preferredTableId || '';
        if (baseIdParam && tableIdParam) {
          const convRes = await fetch(`/api/lark/search-by-table?baseId=${encodeURIComponent(baseIdParam)}&tableId=${encodeURIComponent(tableIdParam)}&phone=${encodeURIComponent(phone)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
          });
          const convJson = await convRes.json();
          console.log('[/api/lark/search-by-table] response:', { ok: convRes.ok, status: convRes.status, body: convJson });
          if (convRes.ok && convJson && convJson.data && convJson.data.length > 0) {
            const mapped = convJson.data.map(c => ({
              content: c.content,
              name: c.customerName || '',
              createdAt: c.createdAt,
              baseId: c.baseId || baseIdParam,
              tableId: c.tableId || tableIdParam,
              linkRecordIds: c.linkRecordIds || []
            }));
            // remember context (useful when creating new record)
            window._lastExchangeContext = mapped.length ? mapped[0] : { baseId: baseIdParam, tableId: tableIdParam, linkRecordIds: [] };
            renderConversations(mapped);
          } else {
            // fallback to aggregate endpoint if table-specific call returned nothing
            const convRes2 = await fetch(`/api/exchanges?phone=${encodeURIComponent(phone)}&customerName=${encodeURIComponent(data.customer ? data.customer.name : '')}`);
            const convJson2 = await convRes2.json();
            console.log('[/api/exchanges] fallback response:', { ok: convRes2.ok, status: convRes2.status, body: convJson2 });
            if (convRes2.ok) {
              if (convJson2 && convJson2.data && convJson2.data.length > 0) {
                const mapped2 = convJson2.data.map(c => ({ content: c.content, name: c.customerName || '', createdAt: c.createdAt, baseId: c.baseId, tableId: c.tableId, linkRecordIds: c.linkRecordIds || [] }));
                window._lastExchangeContext = mapped2.length ? mapped2[0] : null;
                renderConversations(mapped2);
              } else {
                // no exchanges found
                window._lastExchangeContext = null;
                renderConversations([]);
              }
            } else {
              // server error when fetching aggregate exchanges
              const listEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap');
              if (listEl) listEl.innerHTML = `<span class="text-red-500 text-sm">Lỗi khi tải trao đổi: ${convRes2.status}</span>`;
            }
          }
        } else {
          // no specific table context -> use existing aggregate endpoint
          const convRes = await fetch(`/api/exchanges?phone=${encodeURIComponent(phone)}&customerName=${encodeURIComponent(data.customer ? data.customer.name : '')}`);
          const convJson = await convRes.json();
          console.log('[/api/exchanges] response:', { ok: convRes.ok, status: convRes.status, body: convJson });
          if (convRes.ok && convJson && convJson.data) {
            try { convJson.data.forEach((c, idx) => console.log(`[exchange][${idx}] createdAt raw:`, c.createdAt, 'type:', typeof c.createdAt, 'full:', c)); } catch (e) {}
            const mapped = convJson.data.map(c => ({ content: c.content, name: c.customerName || '', createdAt: c.createdAt, baseId: c.baseId, tableId: c.tableId, linkRecordIds: c.linkRecordIds || [] }));
            window._lastExchangeContext = mapped.length ? mapped[0] : null;
            renderConversations(mapped);
          } else {
            if (convRes.ok) {
              // ok but no data
              window._lastExchangeContext = null;
              renderConversations([]);
            } else {
              const listEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap');
              if (listEl) listEl.innerHTML = `<span class="text-red-500 text-sm">Lỗi khi tải trao đổi: ${convRes.status}</span>`;
            }
          }
        }
      } catch (e) {
        console.warn('Exchange fetch error', e);
        const listEl = document.getElementById('conversationsList') || document.getElementById('conversationsWrap');
        if (listEl) listEl.innerHTML = `<span class="text-red-500 text-sm">Lỗi khi tải trao đổi</span>`;
      }
    } catch (err) {
      console.error(err);
      alert('Lỗi khi tra cứu: ' + err.message);
    } finally {
      showLoading(false);
      if (searchBtn) searchBtn.disabled = false;
    }
  }

  function init() {
    if (searchBtn) searchBtn.addEventListener('click', doSearch);
    if (phoneInput) phoneInput.addEventListener('keydown', e => { if (e.key === 'Enter') doSearch(); });
    window._searchLoadPhone = doSearch;
    // initial empty state handling
    try {
      const mainGrid = document.getElementById('mainGrid');
      const emptyState = document.getElementById('emptyState');
      const q = (new URLSearchParams(window.location.search)).get('phone') || '';
      if ((phoneInput && phoneInput.value && phoneInput.value.trim()) || q.trim()) {
        if (mainGrid) mainGrid.classList.remove('hidden');
        if (emptyState) emptyState.style.display = 'none';
      } else {
        if (mainGrid) mainGrid.classList.add('hidden');
        if (emptyState) emptyState.style.display = 'flex';
      }
    } catch (e) {}
    // Modal handlers
    const orderModal = document.getElementById('orderModal');
    const orderModalOverlay = document.getElementById('orderModalOverlay');
    const orderModalClose = document.getElementById('orderModalClose');
    const orderDetails = document.getElementById('orderDetails');
    const orderNoteInput = document.getElementById('orderNoteInput');
    const orderNoteSubmit = document.getElementById('orderNoteSubmit');
    const orderNoteCancel = document.getElementById('orderNoteCancel');

    function closeModal() {
      if (orderModal) {
        orderModal.classList.add('hidden');
        orderModal.classList.remove('flex');
      }
      if (orderNoteInput) orderNoteInput.value = '';
    }

    function openModalForOrderId(ordId) {
      if (!ordId) return;
      const ord = lastOrders.find(o => String(o.orderId) === String(ordId) || String(o.systemId) === String(ordId));
      if (!ord) {
        alert('Không tìm thấy đơn hàng chi tiết');
        return;
      }
      // render details
      const items = (ord.items || []).map(it => `<li>${escapeHtml(it.name)} — ${escapeHtml(String(it.quantity))}</li>`).join('');
      orderDetails.innerHTML = `
        <div><strong>Mã đơn:</strong> ${escapeHtml(ord.orderId || ord.systemId || '-')}</div>
        <div class="mt-2"><strong>Thời gian:</strong> ${formatDate(ord.timeAssignSeller)}</div>
        <div class="mt-2"><strong>Trạng thái:</strong> ${escapeHtml(STATUS_MAP[Number(ord.status)] || String(ord.status || '-'))}</div>
        <div class="mt-2"><strong>Sản phẩm:</strong><ul class="list-disc pl-5 mt-1">${items}</ul></div>
        <div class="mt-2"><strong>Sale:</strong> ${escapeHtml(ord.assigningSellerName || '-')}</div>
        <div class="mt-1"><strong>CSKH:</strong> ${escapeHtml(ord.assigningCareName || '-')}</div>
      `;
      if (orderModal) {
        orderModal.classList.remove('hidden');
        orderModal.classList.add('flex');
      }
      // focus textarea
      if (orderNoteInput) orderNoteInput.focus();

      // submit handler
      if (orderNoteSubmit) {
        orderNoteSubmit.onclick = () => {
          const msg = orderNoteInput.value && orderNoteInput.value.trim();
          if (!msg) { alert('Nhập nội dung note'); return; }
          // only log locally (no server call)
          console.log('Note submitted (local only):', {
            message: msg,
            customerId: lastCustomer ? lastCustomer.customerId : null,
            orderId: ord.orderId || ord.systemId
          });
          closeModal();
          // refresh data view
          doSearch();
        };
      }
    }

    if (orderModalClose) orderModalClose.addEventListener('click', closeModal);
    if (orderModalOverlay) orderModalOverlay.addEventListener('click', closeModal);
    if (orderNoteCancel) orderNoteCancel.addEventListener('click', closeModal);

    // delegate clicks from ordersWrap
    if (ordersWrap) {
      ordersWrap.addEventListener('click', (e) => {
        const btn = e.target.closest && e.target.closest('.order-open');
        if (btn) {
          e.preventDefault();
          const id = btn.getAttribute('data-order-id');
          openModalForOrderId(id);
        }
      });
    }
    // Notes section removed from UI by request; only "Trao đổi" remains.
  // Exchange add modal handlers
  try {
    const exchangeModal = document.getElementById('exchangeModal');
    const exchangeModalOverlay = document.getElementById('exchangeModalOverlay');
    const exchangeModalClose = document.getElementById('exchangeModalClose');
    const exchangeInput = document.getElementById('exchangeInput');
    const exchangeSubmit = document.getElementById('exchangeSubmit');
    const exchangeCancel = document.getElementById('exchangeCancel');
    const exchangeAddBtn = document.getElementById('exchangeAddBtn');
    function openExchangeModal() {
      if (exchangeModal) {
        exchangeModal.classList.remove('hidden');
        exchangeModal.classList.add('flex');
      }
      if (exchangeInput) exchangeInput.value = '';
      if (exchangeInput) exchangeInput.focus();
    }
    function closeExchangeModal() {
      if (exchangeModal) {
        exchangeModal.classList.add('hidden');
        exchangeModal.classList.remove('flex');
      }
    }
    if (exchangeAddBtn) exchangeAddBtn.addEventListener('click', (e) => { e.preventDefault(); openExchangeModal(); });
    if (exchangeModalClose) exchangeModalClose.addEventListener('click', closeExchangeModal);
    if (exchangeModalOverlay) exchangeModalOverlay.addEventListener('click', closeExchangeModal);
    if (exchangeCancel) exchangeCancel.addEventListener('click', closeExchangeModal);
    if (exchangeSubmit) {
      exchangeSubmit.addEventListener('click', () => {
        const msg = exchangeInput ? exchangeInput.value && exchangeInput.value.trim() : '';
        if (!msg) { alert('Nhập nội dung trao đổi'); return; }
        // Use current timestamp for createdAt
        const dateVal = Date.now();
        const newRec = { content: 'PK: ' + msg, customerName: lastCustomer ? lastCustomer.name : '', createdAt: dateVal, Ngày: dateVal };
        // Try to create via backend into Lark if we have table context
        try {
          const ctx = window._lastExchangeContext || {};
          // prepare POS payload (always call POS)
          const customerId = lastCustomer ? lastCustomer.customerId : null;
          let latestOrderId = null;
          try { if (lastOrders && lastOrders.length>0) latestOrderId = lastOrders[0].orderId || lastOrders[0].systemId || null; } catch(e){}
          const posPayload = { customerId: customerId, message: 'PK: ' + msg, orderId: latestOrderId };
          console.log('[pos] create-note payload:', posPayload);
          console.log('[pos] calling backend endpoint: /api/pos/create-note');
          const posFetch = fetch('/api/pos/create-note', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(posPayload)
          }).then(r => r.json()).catch(e => ({ error: e }));

          // prepare Lark fetch (may be skipped if no context)
          let larkFetch = Promise.resolve({ skipped: true });
          if (ctx.baseId && ctx.tableId) {
            const payload = { content: 'PK: ' + msg, ngay: dateVal, linkRecordIds: ctx.linkRecordIds || [] };
            larkFetch = fetch(`/api/lark/create-record?baseId=${encodeURIComponent(ctx.baseId)}&tableId=${encodeURIComponent(ctx.tableId)}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(payload)
            }).then(r => r.json()).catch(e => ({ error: e }));
          }

          Promise.allSettled([larkFetch, posFetch]).then(results => {
            console.log('[pos] create-note results:', results);
            const larkRes = results[0].status === 'fulfilled' ? results[0].value : { error: 'request failed' };
            const posRes = results[1].status === 'fulfilled' ? results[1].value : { error: 'request failed' };
            // handle lark result (prepend if success)
            if (larkRes && larkRes.code === 0) {
              try {
                const list = document.getElementById('conversationsList');
                if (list) {
                  const node = document.createElement('div');
                  node.className = 'bg-white p-3 rounded border border-slate-100 mb-3';
                  const createdRaw = newRec.createdAt == null ? '' : String(newRec.createdAt);
                  node.innerHTML = `<div class="text-sm text-slate-700 mb-1">${escapeHtml(newRec.content)}</div><div class="text-xs text-slate-400">Người: ${escapeHtml(newRec.customerName || '-')} • ${escapeHtml(createdRaw)}</div>`;
                  list.insertBefore(node, list.firstChild);
                }
              } catch (e) { console.warn('Could not append exchange locally', e); }
            } else if (larkRes && larkRes.skipped) {
              // no-op
            } else {
              console.warn('Lark create failed', larkRes);
              // fallback: append locally
              try {
                const list = document.getElementById('conversationsList');
                if (list) {
                  const node = document.createElement('div');
                  node.className = 'bg-white p-3 rounded border border-slate-100 mb-3';
                  const createdRaw = newRec.createdAt == null ? '' : String(newRec.createdAt);
                  node.innerHTML = `<div class="text-sm text-slate-700 mb-1">${escapeHtml(newRec.content)}</div><div class="text-xs text-slate-400">Người: ${escapeHtml(newRec.customerName || '-')} • ${escapeHtml(createdRaw)}</div>`;
                  list.insertBefore(node, list.firstChild);
                }
              } catch (e) {}
            }
            if (posRes && posRes.code === 0) {
              console.log('POS note created', posRes.data);
            } else {
              console.warn('POS create-note failed', posRes);
            }
          });
        } catch (e) { console.warn('Error creating exchange', e); }
        closeExchangeModal();
      });
    }
  } catch (e) {}
  // POS notes header toggle (collapse/expand)
  try {
    const posHeader = document.getElementById('posNotesHeader');
    const posList = document.getElementById('posNotesList');
    const posIcon = document.getElementById('posNotesToggleIcon');
    if (posHeader && posList && posIcon) {
      posHeader.addEventListener('click', () => {
        try {
          posList.classList.toggle('hidden');
          posIcon.classList.toggle('rotate-90');
        } catch (e) {}
      });
    }
  } catch (e) {}
    // render fake conversations immediately for UI testing
    try {
      const sampleConvosInitial = [
        // { content: 'Khách hỏi về chương trình khuyến mãi, đã tư vấn', name: 'Trần A', createdAt: Date.now() - 1000 * 60 * 60 * 24 },
        // { content: 'CSKH gọi nhắc lịch lấy thuốc', name: 'CSKH B', createdAt: Date.now() - 1000 * 60 * 60 * 4 },
        // { content: 'Khách xác nhận nhận thuốc', name: 'Khách C', createdAt: Date.now() - 1000 * 60 * 30 }
      ];
      renderConversations(sampleConvosInitial);
    } catch (e) {}
    // replace initial sample with server-driven later; keep samples for UI when no server
  }

  init();
})();


