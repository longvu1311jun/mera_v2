/**
 * search-info.js - Logic for 360 Customer View
 * Handles API calls to /api/search-info and renders the UI
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Pre-load session configs
    initConfigs();

    const phoneInput = document.getElementById('phoneInput');
    const searchBtn = document.getElementById('searchBtn');

    if (searchBtn) {
        searchBtn.addEventListener('click', () => doSearch());
    }

    if (phoneInput) {
        phoneInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') doSearch();
        });
    }

    // Auto search if phone is in URL
    const urlParams = new URLSearchParams(window.location.search);
    const phone = urlParams.get('phone');
    if (phone) {
        phoneInput.value = phone;
        doSearch();
    }
});

async function initConfigs() {
    showLoading(true, "Đang đồng bộ dữ liệu...", "Kết nối Lark Wiki & Pancake POS");
    try {
        await fetch('/api/init-search');
    } catch (e) {
        console.error("❌ Init configs failed", e);
    } finally {
        showLoading(false);
    }
}

async function doSearch() {
    const phoneInput = document.getElementById('phoneInput');
    const phone = phoneInput.value.trim().replace(/\D/g, '');
    
    if (!phone || phone.length < 9) {
        alert('Vui lòng nhập số điện thoại hợp lệ');
        return;
    }

    showLoading(true, "Đang tổng hợp dữ liệu 360...", "Kết nối Pancake POS & Lark Bitable");

    try {
        const response = await fetch(`/api/search-info?phone=${encodeURIComponent(phone)}`);
        const data = await response.json();

        if (data.error || data.message) {
            alert(data.error || data.message);
            showLoading(false);
            return;
        }

        renderDashboard(data);
    } catch (error) {
        console.error('Search error:', error);
        alert('Lỗi khi tải dữ liệu: ' + error.message);
    } finally {
        showLoading(false);
    }
}

let allOrders = []; // Global to store all orders for filtering

function renderDashboard(data) {
    const { customer, orders, exchanges, productSummary } = data;
    allOrders = orders || []; // Store and reset on dashboard render

    // 1. Render Profile (Left)
    if (customer) {
        document.getElementById('profileName').textContent = customer.name || 'Khách hàng';
        document.getElementById('profilePhone').textContent = formatPhone(customer.phone);
        document.getElementById('profileSucceedCount').textContent = customer.succeedOrderCount || 0;
        document.getElementById('profileStatus').textContent = (customer.succeedOrderCount > 0) ? 'Khách cũ' : 'Khách mới';
        document.getElementById('profileCustomerId').textContent = customer.customerId || 'N/A';
        document.getElementById('profileAddress').textContent = customer.fullAddress || 'Chưa cập nhật';
        
        const avatarImg = document.getElementById('profileAvatar');
        if (avatarImg) {
            const seed = encodeURIComponent(customer.name || 'User');
            avatarImg.src = `https://api.dicebear.com/7.x/initials/svg?seed=${seed}&backgroundColor=e5e7eb&textColor=374151`;
        }
    }

    // 2. Render Product Summary (Left)
    const productSummaryWrap = document.getElementById('productSummaryWrap');
    if (productSummaryWrap) {
        if (productSummary && productSummary.length > 0) {
            productSummaryWrap.innerHTML = productSummary.map(p => `
                <li class="flex justify-between items-center text-sm text-slate-700 py-1 border-b border-slate-50 last:border-0">
                    <span class="font-medium">• ${p.name}</span>
                    <span class="font-bold text-brand-600"> — ${p.quantity}</span>
                </li>
            `).join('');
        } else {
            productSummaryWrap.innerHTML = '<p class="text-xs text-slate-400 italic">Chưa có dữ liệu mua hàng</p>';
        }
    }

    // 3. Render Order History (Center Column, logic logic extracted to helper)
    initStatusFilter(orders);
    renderOrdersList(orders);

    // 4. Render POS Notes & Lark Exchanges (Right)
    const posNotesWrap = document.getElementById('posNotesWrap');
    if (posNotesWrap && customer && customer.posNotes) {
        if (customer.posNotes.length > 0) {
            posNotesWrap.innerHTML = customer.posNotes.map(n => `
                 <div class="bg-slate-50 p-2 rounded border border-slate-100 text-xs text-slate-600 mb-2">
                    <p class="font-medium text-slate-800">${n.message}</p>
                    <div class="flex justify-between items-center mt-1.5 pt-1 border-t border-slate-100/50">
                        <span class="text-[9px] font-bold text-slate-400 italic">${n.userName || 'Hệ thống'}</span>
                        <span class="text-[9px] text-slate-400">${n.createdAt}</span>
                    </div>
                 </div>
            `).join('');
        } else {
            posNotesWrap.innerHTML = '<p class="text-[11px] text-slate-400 italic text-center py-4">Không có ghi chú từ POS</p>';
        }
    }

    const exchangeHistoryWrap = document.getElementById('exchangeHistoryWrap');
    if (exchangeHistoryWrap) {
        // Tách warning đặc biệt và nhật ký thường
        const warnings = (exchanges || []).filter(ex => ex.type === 'special_warning');
        const normalExchanges = (exchanges || []).filter(ex => ex.type !== 'special_warning');

        // Cập nhật tên base vào title
        const baseNameEl = document.getElementById('exchangeBaseName');
        const allItems = [...warnings, ...normalExchanges];
        if (baseNameEl && allItems.length > 0) {
            baseNameEl.textContent = allItems[0].source || 'Base';
        }

        let html = '';

        // Render warning banners (Từ chối chăm / Hoàn / Hủy)
        if (warnings.length > 0) {
            html += warnings.map(w => {
                const tableName = w.tableName || w.content || '';
                let iconClass = 'fa-circle-exclamation';
                let colorClass = 'bg-red-50 border-red-300 text-red-700';
                let badgeClass = 'bg-red-100 text-red-700 border-red-200';
                let dotClass = 'bg-red-500';

                if (tableName.toLowerCase().includes('hoàn')) {
                    iconClass = 'fa-rotate-left';
                    colorClass = 'bg-amber-50 border-amber-300 text-amber-700';
                    badgeClass = 'bg-amber-100 text-amber-700 border-amber-200';
                    dotClass = 'bg-amber-500';
                } else if (tableName.toLowerCase().includes('hủy')) {
                    iconClass = 'fa-ban';
                    colorClass = 'bg-rose-50 border-rose-300 text-rose-700';
                    badgeClass = 'bg-rose-100 text-rose-700 border-rose-200';
                    dotClass = 'bg-rose-500';
                }

                return `
                <div class="flex items-start gap-3 px-1 pb-3 mb-3 border-b border-slate-100 last:border-0">
                    <span class="mt-0.5 w-2 h-2 rounded-full ${dotClass} flex-shrink-0 mt-2"></span>
                    <div class="flex-1 ${colorClass} border rounded-xl px-3 py-2.5 flex items-center gap-2.5 shadow-sm">
                        <i class="fa-solid ${iconClass} text-sm flex-shrink-0"></i>
                        <p class="text-sm font-semibold leading-snug">${w.content || 'Khách hàng nằm trong bảng ' + tableName}</p>
                    </div>
                </div>`;
            }).join('');
        }

        // Render nhật ký trao đổi thường
        if (normalExchanges.length > 0) {
            html += normalExchanges.map((ex, idx) => `
                <div class="relative pl-6 mb-4 last:mb-0">
                        <div class="space-y-0.5">                    
                        <p class="text-sm text-slate-800 leading-snug">${ex.content || ''}</p>                
                        <p class="text-xs text-slate-400">Base: <span class="font-medium text-slate-600">${ex.source || ''}</span></p>
                    </div>
                </div>
            `).join('');
        } else if (warnings.length === 0) {
            html = '<div class="text-center py-4 text-slate-400 text-xs italic">Chưa có nhật ký trao đổi</div>';
        }

        exchangeHistoryWrap.innerHTML = `<div class="relative timeline-line">${html}</div>`;
    }
}

function initStatusFilter(orders) {
    const filter = document.getElementById('statusFilter');
    if (!filter) return;
    
    // Clear and add "All"
    filter.innerHTML = '<option value="all">Tất cả trạng thái</option>';
    filter.value = 'all';
    
    // Extract unique statuses present in current orders
    const statusesInOrders = [...new Set(orders.map(o => o.status))].sort((a, b) => a - b);
    
    statusesInOrders.forEach(s => {
        const option = document.createElement('option');
        option.value = s;
        option.textContent = getStatusText(s);
        filter.appendChild(option);
    });
    
    // Add change listener if not already added
    if (!filter._hasListener) {
        filter.addEventListener('change', (e) => {
            const status = e.target.value;
            const filtered = (status === 'all') 
                ? allOrders 
                : allOrders.filter(o => String(o.status) === status);
            renderOrdersList(filtered);
        });
        filter._hasListener = true;
    }
}

function renderOrdersList(orders) {
    const ordersWrap = document.getElementById('ordersWrap');
    const orderCountText = document.getElementById('orderCountText');
    if (!ordersWrap) return;

    orderCountText.textContent = `${orders.length} đơn hàng`;
    if (orders.length > 0) {
        ordersWrap.innerHTML = orders.map(o => `
            <div class="bg-white border border-slate-200 rounded-lg p-3 hover:border-brand-300 hover:shadow-md transition relative group">
                <div class="flex justify-between items-start mb-2">
                    <div>
                        <div class="text-[10px] font-bold text-slate-400 uppercase">#${o.orderId || o.systemId}</div>
                        <h4 class="font-bold text-slate-800 text-sm">${formatDate(o.timeAssignSeller)}</h4>
                    </div>
                    <span class="text-[10px] ${getStatusClass(o.status)} px-2 py-0.5 rounded border font-bold">${getStatusText(o.status)}</span>
                </div>
                <div class="flex flex-wrap gap-1.5 mb-2">
                    ${o.items.map(it => `
                        <span class="bg-blue-50 text-blue-700 text-[11px] font-medium px-2 py-0.5 rounded border border-blue-100">${it.name} (${it.quantity})</span>
                    `).join('')}
                </div>
                <div class="text-[11px] text-slate-400 pt-2 border-t border-slate-50 flex flex-col gap-0.5">
                    <div class="flex justify-between">
                         <span>CSKH: <strong class="text-slate-600">${o.cskhName} ${o.cskhPhone || ''}</strong></span>
                    </div>
                    <div class="flex justify-between">
                         <span>Sale: <strong class="text-slate-600">${o.saleName}</strong></span>
                    </div>
                </div>
            </div>
        `).join('');
    } else {
        ordersWrap.innerHTML = '<div class="text-center py-10 text-slate-400 text-sm">Không tìm thấy đơn hàng nào ở trạng thái này</div>';
    }
}

// Helpers
function showLoading(show, title, subtitle) {
    const overlay = document.getElementById('loadingOverlay');
    if (!overlay) return;
    
    if (show) {
        if (title) document.getElementById('loadingTitle').textContent = title;
        if (subtitle) document.getElementById('loadingSubtitle').textContent = subtitle;
        overlay.style.display = 'flex';
    } else {
        overlay.style.display = 'none';
        // Reset defaults for next time
        document.getElementById('loadingTitle').textContent = "Đang tổng hợp dữ liệu 360...";
        document.getElementById('loadingSubtitle').textContent = "Kết nối Pancake POS & Lark Bitable";
    }
}

function formatPhone(p) {
    if (!p) return '';
    p = p.replace(/\D/g, '');
    if (p.length === 10) return `${p.slice(0,4)}.${p.slice(4,7)}.${p.slice(7)}`;
    return p;
}

function formatDate(d) {
    if (!d) return '-';
    // Parser for "2026-03-31T09:28:44.000Z" or similar
    try {
        const date = new Date(d);
        if (isNaN(date.getTime())) return d;
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const year = date.getFullYear();
        const hour = String(date.getHours()).padStart(2, '0');
        const min = String(date.getMinutes()).padStart(2, '0');
        return `${day}/${month}/${year} <span class="font-normal text-slate-400 ml-1">${hour}:${min}</span>`;
    } catch(e) { return d; }
}

function getStatusClass(s) {
    switch(Number(s)) {
        case 2: case 3: case 16:
            return 'bg-emerald-50 text-emerald-600 border-emerald-100'; // Gửi hàng / Nhận / Thu tiền
        case 4: case 5: case 15: case 6:
            return 'bg-rose-50 text-rose-600 border-rose-100'; // Hoàn / Hủy
        case 0: case 11: case 20: case 1: case 12: case 13: case 8: case 9:
            return 'bg-amber-50 text-amber-600 border-amber-100'; // Các trạng thái chờ & đóng hàng
        default: return 'bg-slate-50 text-slate-600 border-slate-100';
    }
}

function getStatusText(s) {
    const map = {
        0: 'Mới',
        1: 'Đã xác nhận',
        2: 'Đã gửi hàng',
        3: 'Đã nhận',
        4: 'Đang hoàn',
        5: 'Đã hoàn',
        6: 'Đã hủy',
        7: 'Đã xóa',
        8: 'Đang đóng hàng',
        9: 'Chờ chuyển hàng',
        11: 'Chờ hàng',
        12: 'Chờ in',
        13: 'Đã in',
        15: 'Hoàn một phần',
        16: 'Đã thu tiền',
        17: 'Chờ xác nhận',
        20: 'Đã đặt hàng'
    };
    return map[s] || `Trạng thái ${s}`;
}
