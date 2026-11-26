"use strict";

/* ========================================
   CONFIGURATION
======================================== */

const SALES_ORDER_PAGE_SIZE = 10;
const PRODUCT_PAGE_SIZE = 100;


/* ========================================
   STATE
======================================== */

let salesOrders = [];
let customers = [];
let products = [];

let currentPage = 0;
let editingSalesOrderId = null;
let isSavingSalesOrder = false;


/* ========================================
   DOM ELEMENTS
======================================== */

const orderElements = {

    totalOrders: document.getElementById("total-orders"),
    draftOrders: document.getElementById("draft-orders"),
    completedOrders: document.getElementById("completed-orders"),
    cancelledOrders: document.getElementById("cancelled-orders"),

    pageMessage: document.getElementById("page-message"),

    search: document.getElementById("search-orders"),
    statusFilter: document.getElementById("status-filter"),

    tableBody: document.getElementById("sales-orders-table-body"),

    paginationInfo: document.getElementById("pagination-info"),
    currentPage: document.getElementById("current-page"),

    previousPage: document.getElementById("previous-page"),
    nextPage: document.getElementById("next-page"),

    addButton: document.getElementById("add-sales-order-button"),

    modal: document.getElementById("sales-order-modal"),
    modalTitle: document.getElementById("order-modal-title"),
    modalDescription: document.getElementById("order-modal-description"),

    form: document.getElementById("sales-order-form"),
    formMessage: document.getElementById("form-message"),

    customerSearch: document.getElementById("order-customer-search"),
    customerId: document.getElementById("order-customer"),
    customerOptions: document.getElementById("customer-options"),

    itemsContainer: document.getElementById("order-items-container"),

    addItemButton: document.getElementById("add-order-item"),

    orderTotal: document.getElementById("order-total"),

    saveButton: document.getElementById("save-sales-order-button"),

    closeModal: document.getElementById("close-order-modal"),
    cancelModal: document.getElementById("cancel-order-modal"),

    detailsModal: document.getElementById("order-details-modal"),
    detailsTitle: document.getElementById("details-title"),
    detailsSubtitle: document.getElementById("details-subtitle"),
    detailsContent: document.getElementById("order-details-content"),

    closeDetails: document.getElementById("close-order-details"),
    closeDetailsButton: document.getElementById("close-order-details-button")

};


/* ========================================
   INITIALIZATION
======================================== */

document.addEventListener("DOMContentLoaded", initializeSalesOrders);


async function initializeSalesOrders() {

    bindSalesOrderEvents();

    showOrdersLoading();

    const results = await Promise.allSettled([
        loadSalesOrders(),
        loadCustomers(),
        loadAllProducts()
    ]);

    const errors = [];

    if (results[0].status === "fulfilled") {
        salesOrders = results[0].value;
    } else {
        errors.push(
            `Sales orders: ${getErrorMessage(results[0].reason)}`
        );
    }

    if (results[1].status === "fulfilled") {
        customers = results[1].value;
    } else {
        errors.push(
            `Customers: ${getErrorMessage(results[1].reason)}`
        );
    }

    if (results[2].status === "fulfilled") {
        products = results[2].value;
    } else {
        errors.push(
            `Products: ${getErrorMessage(results[2].reason)}`
        );
    }

    updateOrderSummary();

    renderOrderRows();

    if (errors.length > 0) {
        showPageMessage(errors.join(" | "));
    }

}


/* ========================================
   API
======================================== */

async function loadSalesOrders() {

    const result = await apiGet("/api/sales");

    return normalizeArray(result);

}


async function loadCustomers() {

    const result = await apiGet("/api/customers");

    return normalizeArray(result);

}


async function loadAllProducts() {

    const allProducts = [];

    let page = 0;
    let totalPages = 1;

    do {

        const result = await apiGet(
            `/api/products?active=true&page=${page}&size=${PRODUCT_PAGE_SIZE}&sortBy=productName&direction=asc`
        );

        const pageProducts = normalizeArray(result);

        allProducts.push(...pageProducts);

        totalPages = Array.isArray(result)
            ? 1
            : Math.max(1, Number(result?.totalPages ?? 1));

        page++;

        if (page > 1000) {
            throw new Error("Product pagination exceeded the safety limit.");
        }

    } while (page < totalPages);

    return allProducts;

}


async function refreshSalesOrders() {

    const result = await apiGet("/api/sales");

    salesOrders = normalizeArray(result);

    updateOrderSummary();

    renderOrderRows();

}


/* ========================================
   EVENT LISTENERS
======================================== */

function bindSalesOrderEvents() {

    orderElements.addButton.addEventListener("click", () => {
        openSalesOrderModal();
    });


    orderElements.addItemButton.addEventListener("click", () => {
        addOrderItemRow();
    });


    orderElements.form.addEventListener(
        "submit",
        saveSalesOrder
    );


    orderElements.closeModal.addEventListener(
        "click",
        closeSalesOrderModal
    );


    orderElements.cancelModal.addEventListener(
        "click",
        closeSalesOrderModal
    );


    orderElements.modal.addEventListener("click", event => {

        if (event.target.matches("[data-close-order-modal]")) {
            closeSalesOrderModal();
        }

    });


    orderElements.closeDetails.addEventListener(
        "click",
        closeOrderDetails
    );


    orderElements.closeDetailsButton.addEventListener(
        "click",
        closeOrderDetails
    );


    orderElements.detailsModal.addEventListener("click", event => {

        if (event.target.matches("[data-close-order-details]")) {
            closeOrderDetails();
        }

    });


    orderElements.search.addEventListener("input", () => {

        currentPage = 0;

        renderOrderRows();

    });


    orderElements.statusFilter.addEventListener("change", () => {

        currentPage = 0;

        renderOrderRows();

    });


    orderElements.previousPage.addEventListener("click", () => {

        if (currentPage > 0) {

            currentPage--;

            renderOrderRows();

        }

    });


    orderElements.nextPage.addEventListener("click", () => {

        const totalPages = Math.ceil(
            getFilteredSalesOrders().length / SALES_ORDER_PAGE_SIZE
        );

        if (currentPage < totalPages - 1) {

            currentPage++;

            renderOrderRows();

        }

    });


    orderElements.tableBody.addEventListener(
        "click",
        handleOrderTableAction
    );


    /* CUSTOMER SEARCH */

    orderElements.customerSearch.addEventListener("focus", () => {

        renderCustomerOptions(
            orderElements.customerSearch.value
        );

    });


    orderElements.customerSearch.addEventListener("input", () => {

        orderElements.customerId.value = "";

        renderCustomerOptions(
            orderElements.customerSearch.value
        );

    });


    orderElements.customerOptions.addEventListener("click", event => {

        const option = event.target.closest("[data-customer-id]");

        if (!option) return;

        const customer = customers.find(
            item => Number(item.id) === Number(option.dataset.customerId)
        );

        if (customer) {
            selectCustomer(customer);
        }

    });


    /* PRODUCT SEARCH */

    orderElements.itemsContainer.addEventListener("focusin", event => {

        const input = event.target.closest(".product-search-input");

        if (!input) return;

        const row = input.closest(".order-item-row");

        if (row) {
            renderProductOptions(row, input.value);
        }

    });


    orderElements.itemsContainer.addEventListener("input", event => {

        const input = event.target.closest(".product-search-input");

        if (!input) return;

        handleProductSearchInput(input);

    });


    orderElements.itemsContainer.addEventListener("click", event => {

        const option = event.target.closest("[data-product-id]");

        if (option) {

            const row = option.closest(".order-item-row");

            if (!row) return;

            const product = products.find(
                item => Number(item.id) === Number(option.dataset.productId)
            );

            if (product) {
                selectProduct(row, product);
            }

            return;

        }


        const removeButton = event.target.closest(".remove-item-button");

        if (removeButton) {

            const row = removeButton.closest(".order-item-row");

            if (row) {

                row.remove();

                recalculateOrderTotal();

            }

        }

    });


    orderElements.itemsContainer.addEventListener("input", event => {

        const quantityInput = event.target.closest(".order-quantity-input");

        if (!quantityInput) return;

        updateOrderItemRow(
            quantityInput.closest(".order-item-row")
        );

    });


    /* CLOSE DROPDOWNS */

    document.addEventListener("click", event => {

        if (!event.target.closest(".searchable-picker")) {
            closeAllPickers();
        }

    });


    document.addEventListener("keydown", event => {

        if (event.key === "Escape") {

            closeSalesOrderModal();

            closeOrderDetails();

            closeAllPickers();

        }

    });

}


/* ========================================
   CUSTOMER SEARCH
======================================== */

function getActiveCustomers() {

    return customers.filter(
        customer => customer.active === true
    );

}


function getCustomerLabel(customer) {

    const code = customer.customerCode
        ? `${customer.customerCode} · `
        : "";

    return `${code}${customer.customerName || "Unnamed customer"}`;

}


function renderCustomerOptions(searchText = "") {

    const keyword = searchText.trim().toLowerCase();

    const filteredCustomers = getActiveCustomers().filter(customer => {

        const code = String(
            customer.customerCode || ""
        ).toLowerCase();

        const name = String(
            customer.customerName || ""
        ).toLowerCase();

        return (
            !keyword ||
            code.includes(keyword) ||
            name.includes(keyword)
        );

    });


    if (filteredCustomers.length === 0) {

        orderElements.customerOptions.innerHTML = `
            <div class="picker-empty">
                No matching customers found.
            </div>
        `;

    } else {

        orderElements.customerOptions.innerHTML =
            filteredCustomers.map(customer => `

                <button
                    type="button"
                    class="picker-option"
                    data-customer-id="${escapeHTML(customer.id)}"
                >

                    <span class="picker-option-main">

                        <span class="picker-option-code">
                            ${escapeHTML(customer.customerCode || "Customer")}
                        </span>

                        <span class="picker-option-name">
                            ${escapeHTML(customer.customerName || "Unnamed customer")}
                        </span>

                    </span>

                </button>

            `).join("");

    }

    orderElements.customerOptions.hidden = false;

}


function selectCustomer(customer) {

    orderElements.customerId.value = String(customer.id);

    orderElements.customerSearch.value =
        getCustomerLabel(customer);

    orderElements.customerOptions.hidden = true;

}


/* ========================================
   PRODUCT SEARCH
======================================== */

function getActiveProducts() {

    return products.filter(
        product => product.active === true
    );

}


function getProductLabel(product) {

    const code = product.productCode
        ? `${product.productCode} · `
        : "";

    return `${code}${product.productName || "Unnamed product"}`;

}


function renderProductOptions(row, searchText = "") {

    const optionsContainer = row.querySelector(
        ".product-picker-options"
    );

    const keyword = searchText.trim().toLowerCase();

    const filteredProducts = getActiveProducts().filter(product => {

        const code = String(
            product.productCode || ""
        ).toLowerCase();

        const name = String(
            product.productName || ""
        ).toLowerCase();

        return (
            !keyword ||
            code.includes(keyword) ||
            name.includes(keyword)
        );

    });


    if (filteredProducts.length === 0) {

        optionsContainer.innerHTML = `
            <div class="picker-empty">
                No matching products found.
            </div>
        `;

    } else {

        optionsContainer.innerHTML =
            filteredProducts.map(product => `

                <button
                    type="button"
                    class="picker-option"
                    data-product-id="${escapeHTML(product.id)}"
                >

                    <span class="picker-option-main">

                        <span class="picker-option-code">
                            ${escapeHTML(product.productCode || "Product")}
                        </span>

                        <span class="picker-option-name">
                            ${escapeHTML(product.productName || "Unnamed product")}
                        </span>

                    </span>

                    <span class="picker-option-meta">

                        <span>
                            ${escapeHTML(formatCurrency(product.sellingPrice))}
                        </span>

                        <small>
                            Stock: ${escapeHTML(formatNumber(product.quantity))}
                        </small>

                    </span>

                </button>

            `).join("");

    }

    optionsContainer.hidden = false;

}


function handleProductSearchInput(input) {

    const row = input.closest(".order-item-row");

    if (!row) return;

    const oldLabel = row.dataset.selectedProductLabel || "";

    if (input.value !== oldLabel) {

        row.querySelector(".order-product-id").value = "";

        delete row.dataset.selectedProductLabel;

    }

    renderProductOptions(row, input.value);

    updateOrderItemRow(row);

}


function selectProduct(row, product) {

    const searchInput = row.querySelector(
        ".product-search-input"
    );

    const hiddenInput = row.querySelector(
        ".order-product-id"
    );

    const options = row.querySelector(
        ".product-picker-options"
    );

    hiddenInput.value = String(product.id);

    searchInput.value = getProductLabel(product);

    row.dataset.selectedProductLabel = getProductLabel(product);

    options.hidden = true;

    updateOrderItemRow(row);

}


/* ========================================
   SUMMARY
======================================== */

function updateOrderSummary() {

    orderElements.totalOrders.textContent =
        formatNumber(salesOrders.length);


    orderElements.draftOrders.textContent =
        formatNumber(
            salesOrders.filter(
                order => order.status === "DRAFT"
            ).length
        );


    orderElements.completedOrders.textContent =
        formatNumber(
            salesOrders.filter(
                order => order.status === "COMPLETED"
            ).length
        );


    orderElements.cancelledOrders.textContent =
        formatNumber(
            salesOrders.filter(
                order => order.status === "CANCELLED"
            ).length
        );

}


/* ========================================
   FILTER ORDERS
======================================== */

function getFilteredSalesOrders() {

    const keyword = orderElements.search.value
        .trim()
        .toLowerCase();

    const selectedStatus = orderElements.statusFilter.value;


    return [...salesOrders]

        .filter(order => {

            const orderNumber = String(
                order.salesNumber || ""
            ).toLowerCase();

            const customerName = String(
                order.customerName || ""
            ).toLowerCase();

            const matchesKeyword =
                !keyword ||
                orderNumber.includes(keyword) ||
                customerName.includes(keyword);

            const matchesStatus =
                selectedStatus === "all" ||
                order.status === selectedStatus;

            return matchesKeyword && matchesStatus;

        })

        .sort((a, b) => Number(b.id || 0) - Number(a.id || 0));

}


/* ========================================
   RENDER ORDERS
======================================== */

function renderOrderRows() {

    const filteredOrders = getFilteredSalesOrders();

    const totalPages = Math.max(
        1,
        Math.ceil(filteredOrders.length / SALES_ORDER_PAGE_SIZE)
    );

    currentPage = Math.min(
        currentPage,
        totalPages - 1
    );

    const startIndex =
        currentPage * SALES_ORDER_PAGE_SIZE;

    const visibleOrders = filteredOrders.slice(
        startIndex,
        startIndex + SALES_ORDER_PAGE_SIZE
    );


    if (visibleOrders.length === 0) {

        const message = salesOrders.length === 0
            ? "No sales orders found. Create your first sales order."
            : "No sales orders match your search or filter.";

        orderElements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-empty">
                    ${escapeHTML(message)}
                </td>
            </tr>
        `;

    } else {

        orderElements.tableBody.innerHTML =
            visibleOrders.map(renderSalesOrderRow).join("");

    }


    const showingStart =
        filteredOrders.length === 0 ? 0 : startIndex + 1;

    const showingEnd = Math.min(
        startIndex + SALES_ORDER_PAGE_SIZE,
        filteredOrders.length
    );


    orderElements.paginationInfo.textContent =
        `Showing ${showingStart}–${showingEnd} of ${filteredOrders.length} orders`;

    orderElements.currentPage.textContent =
        String(currentPage + 1);

    orderElements.previousPage.disabled =
        currentPage <= 0;

    orderElements.nextPage.disabled =
        currentPage >= totalPages - 1 ||
        filteredOrders.length === 0;

}


function renderSalesOrderRow(order) {

    const status = String(
        order.status || ""
    ).toUpperCase();

    const items = Array.isArray(order.items)
        ? order.items
        : [];


    const actions = [

        `<button
            class="action-button"
            type="button"
            data-action="view"
            data-id="${escapeHTML(order.id)}"
        >
            View
        </button>`

    ];


    if (status === "DRAFT") {

        actions.push(

            `<button
                class="action-button"
                type="button"
                data-action="edit"
                data-id="${escapeHTML(order.id)}"
            >
                Edit
            </button>`,

            `<button
                class="action-button action-button--complete"
                type="button"
                data-action="complete"
                data-id="${escapeHTML(order.id)}"
            >
                Complete
            </button>`,

            `<button
                class="action-button action-button--cancel"
                type="button"
                data-action="cancel"
                data-id="${escapeHTML(order.id)}"
            >
                Cancel
            </button>`

        );

    }


    return `

        <tr>

            <td>

                <span class="order-number">
                    ${escapeHTML(order.salesNumber || `SO-${order.id}`)}
                </span>

                <span class="order-subtext">
                    ID: ${escapeHTML(order.id)}
                </span>

            </td>


            <td>
                <span class="order-customer">
                    ${escapeHTML(order.customerName || "Unknown customer")}
                </span>
            </td>


            <td>
                ${escapeHTML(formatDate(order.salesDate))}
            </td>


            <td>
                ${formatNumber(items.length)}
                ${items.length === 1 ? "item" : "items"}
            </td>


            <td>
                <span class="order-amount">
                    ${escapeHTML(formatCurrency(order.totalAmount))}
                </span>
            </td>


            <td>
                ${renderStatusBadge(status)}
            </td>


            <td>
                <div class="order-actions">
                    ${actions.join("")}
                </div>
            </td>

        </tr>

    `;

}


function renderStatusBadge(status) {

    const labels = {
        DRAFT: "Draft",
        COMPLETED: "Completed",
        CANCELLED: "Cancelled"
    };

    const statusClass = {
        DRAFT: "status-draft",
        COMPLETED: "status-completed",
        CANCELLED: "status-cancelled"
    }[status] || "status-draft";


    return `

        <span class="status-badge ${statusClass}">
            ${escapeHTML(labels[status] || status || "Unknown")}
        </span>

    `;

}


function showOrdersLoading() {

    orderElements.tableBody.innerHTML = `

        <tr>

            <td colspan="7" class="table-loading">

                <span class="loading-spinner"></span>

                Loading sales orders...

            </td>

        </tr>

    `;

}


/* ========================================
   ORDER ACTIONS
======================================== */

async function handleOrderTableAction(event) {

    const button = event.target.closest(
        "button[data-action]"
    );

    if (!button) return;

    const orderId = Number(button.dataset.id);

    const order = salesOrders.find(
        item => Number(item.id) === orderId
    );

    if (!order) return;


    const action = button.dataset.action;


    if (action === "view") {

        openOrderDetails(order);

    } else if (action === "edit") {

        if (order.status === "DRAFT") {
            openSalesOrderModal(order);
        }

    } else if (action === "complete") {

        await completeSalesOrder(order);

    } else if (action === "cancel") {

        await cancelSalesOrder(order);

    }

}


async function completeSalesOrder(order) {

    const orderNumber =
        order.salesNumber || `SO-${order.id}`;


    const confirmed = window.confirm(
        `Complete ${orderNumber}? This will deduct stock and create a sales invoice.`
    );

    if (!confirmed) return;


    try {

        await apiRequest(
            `/api/sales/${order.id}/complete`,
            {
                method: "PATCH"
            }
        );

        showPageMessage(
            `${orderNumber} completed successfully.`,
            "success"
        );

        await refreshSalesOrders();

    } catch (error) {

        showPageMessage(
            `Unable to complete ${orderNumber}: ${getErrorMessage(error)}`
        );

    }

}


async function cancelSalesOrder(order) {

    const orderNumber =
        order.salesNumber || `SO-${order.id}`;


    const confirmed = window.confirm(
        `Cancel ${orderNumber}?`
    );

    if (!confirmed) return;


    try {

        await apiRequest(
            `/api/sales/${order.id}/cancel`,
            {
                method: "PATCH"
            }
        );

        showPageMessage(
            `${orderNumber} cancelled successfully.`,
            "success"
        );

        await refreshSalesOrders();

    } catch (error) {

        showPageMessage(
            `Unable to cancel ${orderNumber}: ${getErrorMessage(error)}`
        );

    }

}


/* ========================================
   OPEN CREATE / EDIT MODAL
======================================== */

function openSalesOrderModal(order = null) {

    editingSalesOrderId = order
        ? Number(order.id)
        : null;

    orderElements.form.reset();

    orderElements.itemsContainer.innerHTML = "";

    orderElements.customerId.value = "";
    orderElements.customerSearch.value = "";

    orderElements.customerOptions.hidden = true;

    hideFormMessage();


    if (order) {

        orderElements.modalTitle.textContent =
            `Edit ${order.salesNumber || "Sales Order"}`;

        orderElements.modalDescription.textContent =
            "Update the customer and order items.";

        orderElements.saveButton.textContent =
            "Update Draft Order";


        const customer = customers.find(
            item => Number(item.id) === Number(order.customerId)
        );

        if (customer && customer.active === true) {

            selectCustomer(customer);

        } else {

            orderElements.customerSearch.value =
                order.customerName || "Select an active customer";

        }


        const orderItems = Array.isArray(order.items)
            ? order.items
            : [];

        orderItems.forEach(item => {
            addOrderItemRow(item);
        });


    } else {

        orderElements.modalTitle.textContent =
            "New Sales Order";

        orderElements.modalDescription.textContent =
            "Search for a customer and add products to this order.";

        orderElements.saveButton.textContent =
            "Save Draft Order";

        addOrderItemRow();

    }


    recalculateOrderTotal();

    orderElements.modal.hidden = false;

    document.body.classList.add("modal-open");

}


function closeSalesOrderModal() {

    if (orderElements.modal.hidden) return;

    orderElements.modal.hidden = true;

    editingSalesOrderId = null;

    closeAllPickers();

    document.body.classList.remove("modal-open");

}


/* ========================================
   ADD PRODUCT ROW
======================================== */

function addOrderItemRow(existingItem = null) {

    const row = document.createElement("div");

    row.className = "order-item-row";


    row.innerHTML = `

        <div class="product-line-field">

            <label class="mobile-field-label">
                Product
            </label>

            <div class="searchable-picker product-picker">

                <input
                    type="text"
                    class="picker-search-input product-search-input"
                    placeholder="Search product code or name..."
                    autocomplete="off"
                >

                <input
                    type="hidden"
                    class="order-product-id"
                >

                <div
                    class="picker-options product-picker-options"
                    hidden
                ></div>

            </div>

            <small class="order-stock-note"></small>

        </div>


        <div class="quantity-line-field">

            <label class="mobile-field-label">
                Quantity
            </label>

            <input
                class="order-quantity-input"
                type="number"
                min="1"
                step="1"
                value="1"
                aria-label="Quantity"
                required
            >

        </div>


        <div class="price-line-field">

            <label class="mobile-field-label">
                Unit Price
            </label>

            <span class="order-price-preview">—</span>

        </div>


        <div class="subtotal-line-field">

            <label class="mobile-field-label">
                Subtotal
            </label>

            <span class="order-line-subtotal">
                RM 0.00
            </span>

        </div>


        <div class="remove-line-field">

            <button
                class="remove-item-button"
                type="button"
                aria-label="Remove item"
            >
                ×
            </button>

        </div>

    `;


    orderElements.itemsContainer.appendChild(row);


    const searchInput = row.querySelector(
        ".product-search-input"
    );

    const quantityInput = row.querySelector(
        ".order-quantity-input"
    );


    if (existingItem) {

        const product = products.find(
            item => Number(item.id) === Number(existingItem.productId)
        );

        if (product && product.active === true) {

            selectProduct(row, product);

        } else {

            searchInput.value =
                existingItem.productCode
                    ? `${existingItem.productCode} · ${existingItem.productName || ""}`
                    : existingItem.productName || "";

            row.querySelector(".order-price-preview").textContent =
                formatCurrency(existingItem.unitPrice);

            row.dataset.savedUnitPrice =
                String(existingItem.unitPrice || 0);

        }


        quantityInput.value =
            String(existingItem.quantity || 1);

    }


    updateOrderItemRow(row);

}


/* ========================================
   UPDATE PRODUCT ROW
======================================== */

function updateOrderItemRow(row) {

    if (!row) return;

    const productId = Number(
        row.querySelector(".order-product-id").value
    );

    const quantity = Number(
        row.querySelector(".order-quantity-input").value
    );


    const product = products.find(
        item => Number(item.id) === productId
    );


    const priceElement = row.querySelector(
        ".order-price-preview"
    );

    const subtotalElement = row.querySelector(
        ".order-line-subtotal"
    );

    const stockNote = row.querySelector(
        ".order-stock-note"
    );


    let unitPrice = product
        ? Number(product.sellingPrice || 0)
        : Number(row.dataset.savedUnitPrice || 0);


    if (!Number.isFinite(unitPrice)) {
        unitPrice = 0;
    }


    priceElement.textContent =
        product || row.dataset.savedUnitPrice
            ? formatCurrency(unitPrice)
            : "—";


    const validQuantity =
        Number.isInteger(quantity) &&
        quantity > 0;


    subtotalElement.textContent =
        validQuantity
            ? formatCurrency(unitPrice * quantity)
            : formatCurrency(0);


    stockNote.textContent = "";

    stockNote.className = "order-stock-note";


    if (product) {

        const available = Number(
            product.quantity || 0
        );

        stockNote.textContent =
            `Available stock: ${formatNumber(available)}`;


        if (validQuantity && quantity > available) {

            stockNote.textContent +=
                " · Insufficient stock to complete now";

            stockNote.classList.add("warning");

        }

    }


    recalculateOrderTotal();

}


/* ========================================
   CALCULATE TOTAL
======================================== */

function recalculateOrderTotal() {

    let total = 0;


    orderElements.itemsContainer
        .querySelectorAll(".order-item-row")
        .forEach(row => {

            const productId = Number(
                row.querySelector(".order-product-id").value
            );

            const quantity = Number(
                row.querySelector(".order-quantity-input").value
            );


            const product = products.find(
                item => Number(item.id) === productId
            );


            const unitPrice = product
                ? Number(product.sellingPrice || 0)
                : Number(row.dataset.savedUnitPrice || 0);


            if (
                Number.isFinite(unitPrice) &&
                Number.isInteger(quantity) &&
                quantity > 0
            ) {

                total += unitPrice * quantity;

            }

        });


    orderElements.orderTotal.textContent =
        formatCurrency(total);

}


/* ========================================
   SAVE SALES ORDER
======================================== */

async function saveSalesOrder(event) {

    event.preventDefault();

    if (isSavingSalesOrder) return;


    hideFormMessage();


    const customerId = Number(
        orderElements.customerId.value
    );


    const customer = customers.find(
        item => Number(item.id) === customerId
    );


    if (!customer || customer.active !== true) {

        showFormMessage(
            "Please search and select an active customer."
        );

        return;

    }


    const rows = [
        ...orderElements.itemsContainer.querySelectorAll(".order-item-row")
    ];


    if (rows.length === 0) {

        showFormMessage(
            "Add at least one product."
        );

        return;

    }


    const items = [];

    const selectedProductIds = new Set();


    for (let index = 0; index < rows.length; index++) {

        const row = rows[index];


        const productId = Number(
            row.querySelector(".order-product-id").value
        );


        const quantity = Number(
            row.querySelector(".order-quantity-input").value
        );


        const product = products.find(
            item => Number(item.id) === productId
        );


        if (!product || product.active !== true) {

            showFormMessage(
                `Item ${index + 1}: search and select an active product.`
            );

            return;

        }


        if (
            !Number.isInteger(quantity) ||
            quantity < 1
        ) {

            showFormMessage(
                `Item ${index + 1}: quantity must be at least 1.`
            );

            return;

        }


        if (selectedProductIds.has(productId)) {

            showFormMessage(
                "The same product is selected more than once. Update the quantity in the existing row."
            );

            return;

        }


        selectedProductIds.add(productId);


        items.push({

            productId: productId,

            quantity: quantity

        });

    }


    const requestBody = {

        customerId: customerId,

        items: items

    };


    const isEditing = Boolean(editingSalesOrderId);

    const editingId = editingSalesOrderId;


    isSavingSalesOrder = true;

    orderElements.saveButton.disabled = true;

    orderElements.saveButton.textContent = "Saving...";


    try {

        if (isEditing) {

            await apiPut(
                `/api/sales/${editingId}`,
                requestBody
            );

        } else {

            await apiPost(
                "/api/sales",
                requestBody
            );

        }


        closeSalesOrderModal();

        currentPage = 0;


        showPageMessage(

            isEditing
                ? "Sales order updated successfully."
                : "Sales order saved as draft successfully.",

            "success"

        );


        try {

            await refreshSalesOrders();

        } catch (refreshError) {

            showPageMessage(
                `Order saved, but the list could not refresh: ${getErrorMessage(refreshError)}`
            );

        }


    } catch (error) {

        showFormMessage(
            getErrorMessage(error)
        );

    } finally {

        isSavingSalesOrder = false;

        orderElements.saveButton.disabled = false;

        orderElements.saveButton.textContent =
            isEditing
                ? "Update Draft Order"
                : "Save Draft Order";

    }

}


/* ========================================
   ORDER DETAILS
======================================== */

function openOrderDetails(order) {

    const items = Array.isArray(order.items)
        ? order.items
        : [];

    const status = String(
        order.status || ""
    ).toUpperCase();


    orderElements.detailsTitle.textContent =
        order.salesNumber || `SO-${order.id}`;


    orderElements.detailsSubtitle.textContent =
        "Sales order information and item breakdown.";


    const itemRows = items.map(item => `

        <tr>

            <td>

                <strong>
                    ${escapeHTML(item.productCode || "")}
                </strong>

                <span class="order-subtext">
                    ${escapeHTML(item.productName || "Product")}
                </span>

            </td>

            <td>
                ${formatNumber(item.quantity)}
            </td>

            <td>
                ${escapeHTML(formatCurrency(item.unitPrice))}
            </td>

            <td>
                ${escapeHTML(formatCurrency(item.subtotal))}
            </td>

        </tr>

    `).join("");


    orderElements.detailsContent.innerHTML = `

        <div class="order-detail-meta">

            <div>
                <span>Customer</span>
                <strong>
                    ${escapeHTML(order.customerName || "Unknown customer")}
                </strong>
            </div>

            <div>
                <span>Order Date</span>
                <strong>
                    ${escapeHTML(formatDate(order.salesDate))}
                </strong>
            </div>

            <div>
                <span>Status</span>
                <strong>
                    ${renderStatusBadge(status)}
                </strong>
            </div>

        </div>


        <h3 class="details-items-title">
            Order Items
        </h3>


        ${
        items.length > 0

            ? `

                    <div class="table-container">

                        <table class="details-items-table">

                            <thead>

                                <tr>
                                    <th>PRODUCT</th>
                                    <th>QTY</th>
                                    <th>UNIT PRICE</th>
                                    <th>SUBTOTAL</th>
                                </tr>

                            </thead>

                            <tbody>
                                ${itemRows}
                            </tbody>

                        </table>

                    </div>


                    <div class="details-total">

                        <span>Order Total</span>

                        <strong>
                            ${escapeHTML(formatCurrency(order.totalAmount))}
                        </strong>

                    </div>

                `

            : `

                    <div class="details-empty">
                        This order has no items.
                    </div>

                `

    }

    `;


    orderElements.detailsModal.hidden = false;

    document.body.classList.add("modal-open");

}


function closeOrderDetails() {

    if (orderElements.detailsModal.hidden) return;

    orderElements.detailsModal.hidden = true;

    document.body.classList.remove("modal-open");

}


/* ========================================
   CLOSE PICKERS
======================================== */

function closeAllPickers() {

    orderElements.customerOptions.hidden = true;


    orderElements.itemsContainer
        .querySelectorAll(".product-picker-options")
        .forEach(options => {

            options.hidden = true;

        });

}


/* ========================================
   MESSAGE HELPERS
======================================== */

function showPageMessage(message, type = "error") {

    orderElements.pageMessage.textContent = message;

    orderElements.pageMessage.classList.add("show");

    orderElements.pageMessage.classList.toggle(
        "message-success",
        type === "success"
    );

}


function showFormMessage(message) {

    orderElements.formMessage.textContent = message;

    orderElements.formMessage.classList.add("show");

}


function hideFormMessage() {

    orderElements.formMessage.textContent = "";

    orderElements.formMessage.classList.remove("show");

}


function getErrorMessage(error) {

    return error?.message ||
        "An unexpected error occurred.";

}