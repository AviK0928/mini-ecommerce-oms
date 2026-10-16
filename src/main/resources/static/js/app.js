'use strict';

/* ---------- State and small helpers ---------- */

const state = {
    products: []
};

const currencyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

function formatMoney(value) {
    return currencyFormat.format(Number(value));
}

function formatDate(isoString) {
    const date = new Date(isoString);
    return Number.isNaN(date.getTime()) ? isoString : date.toLocaleString();
}

function $(selector) {
    return document.querySelector(selector);
}

/** Creates an element. Text is always set with textContent, so data is never parsed as HTML. */
function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === 'className') {
            node.className = value;
        } else if (key === 'text') {
            node.textContent = value;
        } else {
            node.setAttribute(key, value);
        }
    }
    node.append(...children);
    return node;
}

function emptyRow(columnCount, text) {
    return el('tr', {}, el('td', { colspan: String(columnCount), className: 'empty', text }));
}

function findProduct(id) {
    return state.products.find((product) => String(product.id) === String(id));
}

function isPositiveWholeNumber(text) {
    return /^\d+$/.test(text) && Number(text) > 0;
}

/* ---------- API ---------- */

/** Calls the backend. Throws an Error carrying the server's message when the request fails. */
async function api(path, options = {}) {
    const init = { method: options.method || 'GET', headers: {} };
    if (options.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(options.body);
    }

    let response;
    try {
        response = await fetch(path, init);
    } catch (networkError) {
        throw new Error('Cannot reach the server. Is the backend running?');
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (parseError) {
            data = null;
        }
    }

    if (!response.ok) {
        throw new Error((data && data.error) || `Request failed with status ${response.status}`);
    }
    return data;
}

/* ---------- Feedback ---------- */

function showMessage(text, type) {
    const box = $('#message');
    box.textContent = text;
    box.className = `message ${type}`;
    box.hidden = false;
}

function clearMessage() {
    $('#message').hidden = true;
}

/** Disables the button while the action runs, preventing duplicate submissions. */
async function withBusy(button, busyText, action) {
    if (button.disabled) {
        return;
    }
    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = busyText;
    try {
        await action();
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
}

/* ---------- Tabs ---------- */

function showTab(name) {
    document.querySelectorAll('.tab').forEach((tab) => {
        tab.classList.toggle('active', tab.dataset.tab === name);
    });
    document.querySelectorAll('main > section.panel').forEach((section) => {
        section.hidden = section.id !== name;
    });
    clearMessage();
    if (name === 'orders') {
        loadOrders();
    } else {
        loadProducts();
    }
}

/* ---------- Products ---------- */

async function loadProducts() {
    try {
        state.products = await api('/api/products');
    } catch (error) {
        showMessage(error.message, 'error');
        return;
    }
    renderProducts();
    document.querySelectorAll('#order-items select').forEach(fillProductSelect);
    updateOrderPreview();
}

function renderProducts() {
    const body = $('#products-body');
    body.replaceChildren();

    if (state.products.length === 0) {
        body.append(emptyRow(6, 'No products yet. Add one above.'));
        return;
    }

    for (const product of state.products) {
        const stockInput = el('input', {
            type: 'number',
            min: '0',
            step: '1',
            value: String(product.stockQuantity),
            className: 'stock-input',
            'aria-label': `New stock for ${product.sku}`
        });
        const updateButton = el('button', { type: 'button', className: 'secondary', text: 'Update' });
        updateButton.addEventListener('click', () => updateStock(product, stockInput, updateButton));

        body.append(el('tr', {},
            el('td', { text: String(product.id) }),
            el('td', { text: product.name }),
            el('td', { text: product.sku }),
            el('td', { className: 'num', text: formatMoney(product.price) }),
            el('td', { className: 'num', text: String(product.stockQuantity) }),
            el('td', {}, stockInput, ' ', updateButton)
        ));
    }
}

async function updateStock(product, input, button) {
    const value = input.value.trim();
    if (!/^\d+$/.test(value)) {
        showMessage('Stock must be a whole number of 0 or more.', 'error');
        return;
    }
    await withBusy(button, 'Saving…', async () => {
        try {
            const updated = await api(`/api/products/${product.id}/stock`, {
                method: 'PATCH',
                body: { stockQuantity: Number(value) }
            });
            showMessage(`Stock for ${updated.sku} set to ${updated.stockQuantity}.`, 'success');
        } catch (error) {
            showMessage(error.message, 'error');
        }
        await loadProducts();
    });
}

async function handleProductSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const data = new FormData(form);
    const body = {
        name: String(data.get('name')).trim(),
        sku: String(data.get('sku')).trim(),
        price: Number(data.get('price')),
        stockQuantity: Number(data.get('stockQuantity'))
    };

    await withBusy(button, 'Adding…', async () => {
        try {
            const product = await api('/api/products', { method: 'POST', body });
            showMessage(`Product "${product.name}" (${product.sku}) added.`, 'success');
            form.reset();
            await loadProducts();
        } catch (error) {
            showMessage(error.message, 'error');
        }
    });
}

/* ---------- Create order ---------- */

function fillProductSelect(select) {
    const current = select.value;
    const placeholder = state.products.length ? 'Select a product' : 'No products – add some first';
    select.replaceChildren(el('option', { value: '', text: placeholder }));
    for (const product of state.products) {
        select.append(el('option', {
            value: String(product.id),
            text: `${product.name} (${product.sku}) – ${formatMoney(product.price)} – ${product.stockQuantity} in stock`
        }));
    }
    select.value = findProduct(current) ? current : '';
}

function addItemRow() {
    const select = el('select', { required: '', 'aria-label': 'Product' });
    const quantity = el('input', {
        type: 'number',
        min: '1',
        step: '1',
        value: '1',
        required: '',
        className: 'qty-input',
        'aria-label': 'Quantity'
    });
    const removeButton = el('button', { type: 'button', className: 'link', text: 'Remove' });

    const row = el('tr', { className: 'item-row' },
        el('td', {}, select),
        el('td', { className: 'num price-cell', text: '–' }),
        el('td', {}, quantity, el('span', { className: 'stock-hint' })),
        el('td', { className: 'num line-cell', text: '–' }),
        el('td', {}, removeButton)
    );

    removeButton.addEventListener('click', () => {
        row.remove();
        if (!document.querySelector('#order-items .item-row')) {
            addItemRow();
        }
        updateOrderPreview();
    });

    fillProductSelect(select);
    $('#order-items').append(row);
    updateOrderPreview();
}

/** Display-only preview. Computed in paise to avoid floating-point rounding. */
function updateOrderPreview() {
    let totalPaise = 0;

    for (const row of document.querySelectorAll('#order-items .item-row')) {
        const product = findProduct(row.querySelector('select').value);
        const quantityText = row.querySelector('.qty-input').value.trim();
        const priceCell = row.querySelector('.price-cell');
        const lineCell = row.querySelector('.line-cell');
        const hint = row.querySelector('.stock-hint');
        hint.textContent = '';

        if (!product) {
            priceCell.textContent = '–';
            lineCell.textContent = '–';
            continue;
        }

        priceCell.textContent = formatMoney(product.price);
        if (isPositiveWholeNumber(quantityText)) {
            const quantity = Number(quantityText);
            const linePaise = Math.round(product.price * 100) * quantity;
            totalPaise += linePaise;
            lineCell.textContent = formatMoney(linePaise / 100);
            if (quantity > product.stockQuantity) {
                hint.textContent = `Only ${product.stockQuantity} in stock`;
            }
        } else {
            lineCell.textContent = '–';
        }
    }

    $('#order-total').textContent = formatMoney(totalPaise / 100);
}

function resetOrderForm() {
    const form = $('#order-form');
    form.reset();
    $('#order-items').replaceChildren();
    addItemRow();
}

async function handleOrderSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const customerName = String(new FormData(form).get('customerName')).trim();

    const items = [];
    const seenProductIds = new Set();
    for (const row of document.querySelectorAll('#order-items .item-row')) {
        const productId = row.querySelector('select').value;
        const quantityText = row.querySelector('.qty-input').value.trim();

        if (!productId) {
            showMessage('Select a product in every item row, or remove the empty row.', 'error');
            return;
        }
        if (!isPositiveWholeNumber(quantityText)) {
            showMessage('Each quantity must be a whole number greater than 0.', 'error');
            return;
        }
        if (seenProductIds.has(productId)) {
            showMessage('Each product can appear only once. Combine the quantities into one row.', 'error');
            return;
        }
        seenProductIds.add(productId);
        items.push({ productId: Number(productId), quantity: Number(quantityText) });
    }

    await withBusy(button, 'Placing order…', async () => {
        try {
            const order = await api('/api/orders', { method: 'POST', body: { customerName, items } });
            showMessage(`Order #${order.id} placed for ${order.customerName}. Total: ${formatMoney(order.totalAmount)}.`, 'success');
            resetOrderForm();
        } catch (error) {
            showMessage(error.message, 'error');
        }
        await loadProducts(); // refresh stock either way
    });
}

/* ---------- Orders ---------- */

function statusBadge(status) {
    return el('span', { className: `badge ${status.toLowerCase()}`, text: status });
}

async function loadOrders() {
    let orders;
    try {
        orders = await api('/api/orders');
    } catch (error) {
        showMessage(error.message, 'error');
        return;
    }

    const body = $('#orders-body');
    body.replaceChildren();
    if (orders.length === 0) {
        body.append(emptyRow(6, 'No orders yet.'));
        return;
    }

    for (const order of orders) {
        const viewButton = el('button', { type: 'button', className: 'secondary', text: 'View' });
        viewButton.addEventListener('click', () => showOrderDetail(order.id));

        body.append(el('tr', {},
            el('td', { text: String(order.id) }),
            el('td', { text: order.customerName }),
            el('td', { className: 'num', text: formatMoney(order.totalAmount) }),
            el('td', {}, statusBadge(order.status)),
            el('td', { text: formatDate(order.createdAt) }),
            el('td', {}, viewButton)
        ));
    }
}

async function showOrderDetail(orderId) {
    try {
        renderOrderDetail(await api(`/api/orders/${orderId}`));
    } catch (error) {
        showMessage(error.message, 'error');
    }
}

function renderOrderDetail(order) {
    const panel = $('#order-detail');
    panel.replaceChildren();
    panel.hidden = false;

    const itemRows = order.items.map((item) => el('tr', {},
        el('td', { text: item.productName }),
        el('td', { className: 'num', text: String(item.quantity) }),
        el('td', { className: 'num', text: formatMoney(item.unitPrice) }),
        el('td', { className: 'num', text: formatMoney(item.lineTotal) })
    ));

    panel.append(
        el('h3', { text: `Order #${order.id}` }),
        el('p', {},
            `Customer: ${order.customerName} · Created: ${formatDate(order.createdAt)} · Status: `,
            statusBadge(order.status)),
        el('table', {},
            el('thead', {}, el('tr', {},
                el('th', { text: 'Product' }),
                el('th', { className: 'num', text: 'Quantity' }),
                el('th', { className: 'num', text: 'Unit price' }),
                el('th', { className: 'num', text: 'Line total' }))),
            el('tbody', {}, ...itemRows),
            el('tfoot', {}, el('tr', {},
                el('td', { colspan: '3', className: 'num' }, el('strong', { text: 'Total' })),
                el('td', { className: 'num' }, el('strong', { text: formatMoney(order.totalAmount) })))))
    );

    const actions = el('div', { className: 'actions' });
    if (order.status === 'PLACED') {
        const cancelButton = el('button', { type: 'button', className: 'danger', text: 'Cancel order' });
        cancelButton.addEventListener('click', () => cancelOrder(order, cancelButton));
        actions.append(cancelButton);
    }
    const closeButton = el('button', { type: 'button', className: 'secondary', text: 'Close' });
    closeButton.addEventListener('click', () => { panel.hidden = true; });
    actions.append(closeButton);
    panel.append(actions);
}

async function cancelOrder(order, button) {
    if (!window.confirm(`Cancel order #${order.id}? Its items will be returned to stock.`)) {
        return;
    }
    await withBusy(button, 'Cancelling…', async () => {
        try {
            const updated = await api(`/api/orders/${order.id}/cancel`, { method: 'POST' });
            const restored = updated.items.map((item) => `${item.productName} +${item.quantity}`).join(', ');
            showMessage(`Order #${updated.id} cancelled. Stock restored: ${restored}.`, 'success');
            renderOrderDetail(updated);
        } catch (error) {
            showMessage(error.message, 'error');
            try {
                renderOrderDetail(await api(`/api/orders/${order.id}`)); // show the real current status
            } catch (ignored) {
                // the error message above is already shown
            }
        }
        await Promise.all([loadOrders(), loadProducts()]);
    });
}

/* ---------- Start-up ---------- */

document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => showTab(tab.dataset.tab));
});
$('#product-form').addEventListener('submit', handleProductSubmit);
$('#order-form').addEventListener('submit', handleOrderSubmit);
$('#add-item').addEventListener('click', addItemRow);
$('#order-items').addEventListener('input', updateOrderPreview);
$('#order-items').addEventListener('change', updateOrderPreview);

addItemRow();
loadProducts();