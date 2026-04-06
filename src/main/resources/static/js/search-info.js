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
                    <p class="font-medium">${n.message}</p>
                    <p class="text-[10px] text-slate-400 mt-1">${n.createdAt}</p>
                 </div>
            `).join('');
        } else {
            posNotesWrap.innerHTML = '<p class="text-[11px] text-slate-400 italic">Không có ghi chú từ POS</p>';
        }
    }

    const exchangeHistoryWrap = document.getElementById('exchangeHistoryWrap');
    if (exchangeHistoryWrap) {
        if (exchanges && exchanges.length > 0) {
            exchangeHistoryWrap.innerHTML = exchanges.map((ex, idx) => `
                <div class="relative pl-8 mb-6 last:mb-0">
                    <div class="absolute left-0 top-1 w-6 h-6 bg-white border-2 ${idx === 0 ? 'border-brand-500' : 'border-slate-300'} rounded-full flex items-center justify-center z-10 shadow-sm">
                        <i class="fa-solid ${idx === 0 ? 'fa-check text-brand-500' : 'fa-clock text-slate-300'} text-[10px]"></i>
                    </div>
                    <div>
                        <span class="text-[10px] text-slate-400 font-bold uppercase tracking-wider">${ex.date || 'N/A'}</span>
                        <div class="bg-white p-2.5 rounded-lg border border-slate-200 shadow-sm mt-1 group hover:border-brand-200 transition">
                            <p class="text-slate-800 text-sm font-medium">${ex.content}</p>
                            <div class="flex justify-between items-center mt-1">
                                <p class="text-[10px] text-slate-400">Người: <span class="font-bold">${ex.person || 'N/A'}</span></p>
                                <p class="text-[10px] text-brand-600 font-bold bg-brand-50 px-1.5 py-0.5 rounded border border-brand-100">${ex.source || 'Lark'}</p>
                            </div>
                        </div>
                    </div>
                </div>
            `).join('');
        } else {
            exchangeHistoryWrap.innerHTML = '<div class="text-center py-4 text-slate-400 text-xs italic">Chưa có nhật ký từ Lark</div>';
        }
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
