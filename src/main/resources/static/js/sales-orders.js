
"use strict";

(() => {

    const PAGE_SIZE = 10;

    const API = {
        sales: "/api/sales",
        customers: "/api/customers",
        products: "/api/products"
    };

    let salesOrders = [];
    let customers = [];

    let currentPage = 0;

    let selectedCustomer = null;
    let selectedItems = [];

    let editingOrderId = null;
    let isSaving = false;

    let productSearchToken = 0;

    const elements = {

        totalOrders: document.getElementById("total-orders"),
        draftOrders: document.getElementById("draft-orders"),
        completedOrders: document.getElementById("completed-orders"),
        salesRevenue: document.getElementById("sales-revenue"),

        pageMessage: document.getElementById("page-message"),

        search: document.getElementById("order-search"),
        statusFilter: document.getElementById("status-filter"),

        tableBody: document.getElementById("orders-table-body"),

        paginationInfo: document.getElementById("pagination-info"),
        currentPage: document.getElementById("current-page"),
        previousPage: document.getElementById("previous-page"),
        nextPage: document.getElementById("next-page"),

        addButton: document.getElementById("add-order-button"),

        orderModal: document.getElementById("order-modal"),
        orderModalTitle: document.getElementById("order-modal-title"),
        orderModalDescription: document.getElementById("order-modal-description"),

        orderForm: document.getElementById("sales-order-form"),
        formMessage: document.getElementById("form-message"),
        saveButton: document.getElementById("save-order-button"),

        closeOrderModal: document.getElementById("close-order-modal"),
        cancelOrderModal: document.getElementById("cancel-order-modal"),

        customerSearch: document.getElementById("customer-search"),
        customerResults: document.getElementById("customer-results"),
        selectedCustomer: document.getElementById("selected-customer"),

        productSearch: document.getElementById("product-search"),
        productResults: document.getElementById("product-results"),

        itemsBody: document.getElementById("order-items-body"),
        itemCount: document.getElementById("item-count"),
        totalQuantity: document.getElementById("total-quantity"),
        grandTotal: document.getElementById("grand-total"),

        stockWarning: document.getElementById("stock-warning"),

        detailsModal: document.getElementById("details-modal"),
        detailsTitle: document.getElementById("details-title"),
        detailsDescription: document.getElementById("details-description"),

        detailsCustomer: document.getElementById("details-customer"),
        detailsDate: document.getElementById("details-date"),
        detailsStatus: document.getElementById("details-status"),
        detailsNumber: document.getElementById("details-number"),

        detailsItemsBody: document.getElementById("details-items-body"),
        detailsTotal: document.getElementById("details-total"),

        closeDetails: document.getElementById("close-details-modal"),
        closeDetailsButton: document.getElementById("close-details-button")
    };


    /* ========================================
       API
    ======================================== */

    async function apiRequest(endpoint, options = {}) {

        const response = await fetch(endpoint, {

            ...options,

            headers: {
                Accept: "application/json",

                ...(options.body ? {
                    "Content-Type": "application/json"
                } : {}),

                ...(options.headers || {})
            }

        });

        const responseText = await response.text();

        let data = null;

        if (responseText) {

            try {
                data = JSON.parse(responseText);
            } catch {
                data = responseText;
            }

        }

        if (!response.ok) {

            const message =
                data?.message ||
                data?.error ||
                (typeof data === "string" ? data : null) ||
                response.statusText ||
                "Request failed";

            throw new Error(`${response.status}: ${message}`);

        }

        return data;

    }


    async function loadSalesOrders() {

        const result = await apiRequest(API.sales);

        return Array.isArray(result) ? result : [];

    }


    async function loadCustomers() {

        const result = await apiRequest(API.customers);

        return Array.isArray(result) ? result : [];

    }


    async function searchProducts(keyword) {

        const params = new URLSearchParams({

            keyword: keyword,
            active: "true",
            page: "0",
            size: "10",
            sortBy: "productName",
            direction: "asc"

        });

        const result = await apiRequest(
            `${API.products}/search?${params.toString()}`
        );

        if (Array.isArray(result)) {
            return result;
        }

        return Array.isArray(result?.content)
            ? result.content
            : [];

    }


    async function loadProductById(productId) {

        return apiRequest(`${API.products}/${productId}`);

    }


    /* ========================================
       INITIALIZATION
    ======================================== */

    document.addEventListener(
        "DOMContentLoaded",
        initializeOrders
    );


    async function initializeOrders() {

        bindEvents();

        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-loading">
                    Loading sales orders...
                </td>
            </tr>
        `;

        const results = await Promise.allSettled([

            loadSalesOrders(),
            loadCustomers()

        ]);

        if (results[0].status === "fulfilled") {

            salesOrders = results[0].value;

        } else {

            showPageMessage(
                `Unable to load sales orders: ${results[0].reason.message}`
            );

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="7" class="table-empty">
                        Unable to load sales orders.
                    </td>
                </tr>
            `;

            return;

        }


        if (results[1].status === "fulfilled") {

            customers = results[1].value;

        } else {

            customers = [];

            showPageMessage(
                `Unable to load customers: ${results[1].reason.message}`
            );

        }

        updateSummary();
        renderOrders();

    }


    /* ========================================
       EVENTS
    ======================================== */

    function bindEvents() {

        elements.addButton.addEventListener("click", () => {
            openOrderModal();
        });


        elements.closeOrderModal.addEventListener(
            "click",
            closeOrderModal
        );

        elements.cancelOrderModal.addEventListener(
            "click",
            closeOrderModal
        );


        elements.orderModal.addEventListener("click", event => {

            if (event.target.matches("[data-close-order-modal]")) {
                closeOrderModal();
            }

        });


        elements.orderForm.addEventListener(
            "submit",
            saveOrder
        );


        elements.search.addEventListener("input", () => {

            currentPage = 0;
            renderOrders();

        });


        elements.statusFilter.addEventListener("change", () => {

            currentPage = 0;
            renderOrders();

        });


        elements.previousPage.addEventListener("click", () => {

            if (currentPage > 0) {

                currentPage--;
                renderOrders();

            }

        });


        elements.nextPage.addEventListener("click", () => {

            const totalPages = Math.ceil(
                getFilteredOrders().length / PAGE_SIZE
            );

            if (currentPage < totalPages - 1) {

                currentPage++;
                renderOrders();

            }

        });


        elements.tableBody.addEventListener(
            "click",
            handleTableAction
        );


        elements.customerSearch.addEventListener("input", () => {

            renderCustomerResults(
                elements.customerSearch.value
            );

        });


        elements.customerSearch.addEventListener("focus", () => {

            renderCustomerResults(
                elements.customerSearch.value
            );

        });


        elements.customerResults.addEventListener("click", event => {

            const button = event.target.closest("[data-customer-id]");

            if (!button) return;

            const customerId = Number(button.dataset.customerId);

            const customer = customers.find(
                item => Number(item.id) === customerId
            );

            if (customer) {
                selectCustomer(customer);
            }

        });


        elements.selectedCustomer.addEventListener("click", event => {

            if (event.target.closest("[data-clear-customer]")) {
                clearCustomerSelection();
            }

        });


        elements.productSearch.addEventListener("input", () => {

            handleProductSearch(
                elements.productSearch.value
            );

        });


        elements.productSearch.addEventListener("focus", () => {

            if (elements.productSearch.value.trim()) {
                handleProductSearch(
                    elements.productSearch.value
                );
            }

        });


        elements.productResults.addEventListener("click", event => {

            const button = event.target.closest("[data-product-id]");

            if (!button) return;

            const productId = Number(button.dataset.productId);

            addProductFromSearch(productId);

        });


        elements.itemsBody.addEventListener("change", event => {

            const quantityInput = event.target.closest(
                "[data-quantity-index]"
            );

            if (!quantityInput) return;

            const index = Number(
                quantityInput.dataset.quantityIndex
            );

            const quantity = Math.max(
                1,
                Number.parseInt(quantityInput.value, 10) || 1
            );

            if (!selectedItems[index]) return;

            selectedItems[index].quantity = quantity;

            renderOrderItems();

        });


        elements.itemsBody.addEventListener("click", event => {

            const button = event.target.closest("[data-remove-index]");

            if (!button) return;

            const index = Number(button.dataset.removeIndex);

            selectedItems.splice(index, 1);

            renderOrderItems();

        });


        elements.closeDetails.addEventListener(
            "click",
            closeDetailsModal
        );

        elements.closeDetailsButton.addEventListener(
            "click",
            closeDetailsModal
        );


        elements.detailsModal.addEventListener("click", event => {

            if (event.target.matches("[data-close-details]")) {
                closeDetailsModal();
            }

        });


        document.addEventListener("click", event => {

            if (!event.target.closest("#customer-search") &&
                !event.target.closest("#customer-results")) {

                hideCustomerResults();

            }

            if (!event.target.closest("#product-search") &&
                !event.target.closest("#product-results")) {

                hideProductResults();

            }

        });


        document.addEventListener("keydown", event => {

            if (event.key !== "Escape") return;

            if (!elements.orderModal.hidden) {
                closeOrderModal();
            }

            if (!elements.detailsModal.hidden) {
                closeDetailsModal();
            }

        });

    }


    /* ========================================
       SUMMARY
    ======================================== */

    function updateSummary() {

        const draftOrders = salesOrders.filter(
            order => order.status === "DRAFT"
        );

        const completedOrders = salesOrders.filter(
            order => order.status === "COMPLETED"
        );

        const revenue = completedOrders.reduce(
            (total, order) => {
                return total + Number(order.totalAmount || 0);
            },
            0
        );

        elements.totalOrders.textContent =
            salesOrders.length.toLocaleString("en-MY");

        elements.draftOrders.textContent =
            draftOrders.length.toLocaleString("en-MY");

        elements.completedOrders.textContent =
            completedOrders.length.toLocaleString("en-MY");

        elements.salesRevenue.textContent =
            formatCurrency(revenue);

    }


    /* ========================================
       ORDER SEARCH AND FILTER
    ======================================== */

    function getFilteredOrders() {

        const keyword = elements.search.value
            .trim()
            .toLowerCase();

        const status = elements.statusFilter.value;

        return [...salesOrders]

            .filter(order => {

                if (status !== "ALL" && order.status !== status) {
                    return false;
                }

                if (!keyword) return true;

                const orderNumber = String(
                    order.salesNumber || ""
                ).toLowerCase();

                const customerName = String(
                    order.customerName || ""
                ).toLowerCase();

                const customerId = String(
                    order.customerId || ""
                ).toLowerCase();

                const productMatch = (order.items || []).some(item => {

                    const productCode = String(
                        item.productCode || ""
                    ).toLowerCase();

                    const productName = String(
                        item.productName || ""
                    ).toLowerCase();

                    return productCode.includes(keyword) ||
                        productName.includes(keyword);

                });

                return orderNumber.includes(keyword) ||
                    customerName.includes(keyword) ||
                    customerId.includes(keyword) ||
                    productMatch;

            })

            .sort((a, b) => {

                return new Date(b.salesDate || 0) -
                    new Date(a.salesDate || 0);

            });

    }


    /* ========================================
       RENDER ORDERS
    ======================================== */

    function renderOrders() {

        const filtered = getFilteredOrders();

        const totalPages = Math.ceil(
            filtered.length / PAGE_SIZE
        );

        if (currentPage >= totalPages) {
            currentPage = Math.max(0, totalPages - 1);
        }

        const start = currentPage * PAGE_SIZE;

        const pageOrders = filtered.slice(
            start,
            start + PAGE_SIZE
        );


        if (pageOrders.length === 0) {

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="7" class="table-empty">
                        No sales orders found.
                    </td>
                </tr>
            `;

        } else {

            elements.tableBody.innerHTML = pageOrders
                .map(renderOrderRow)
                .join("");

        }


        elements.paginationInfo.textContent =
            filtered.length === 0
                ? "Showing 0 orders"
                : `Showing ${start + 1}-${Math.min(
                    start + PAGE_SIZE,
                    filtered.length
                )} of ${filtered.length} orders`;


        elements.currentPage.textContent =
            totalPages === 0
                ? "0"
                : `${currentPage + 1} / ${totalPages}`;


        elements.previousPage.disabled =
            currentPage <= 0;

        elements.nextPage.disabled =
            currentPage >= totalPages - 1;

    }


    function renderOrderRow(order) {

        const items = order.items || [];

        const totalQuantity = items.reduce(
            (total, item) => {
                return total + Number(item.quantity || 0);
            },
            0
        );

        const status = String(
            order.status || "DRAFT"
        ).toLowerCase();


        let actions = `
            <button class="table-action"
                    type="button"
                    data-action="view"
                    data-id="${order.id}">
                View
            </button>
        `;


        if (order.status === "DRAFT") {

            actions += `
                <button class="table-action"
                        type="button"
                        data-action="edit"
                        data-id="${order.id}">
                    Edit
                </button>

                <button class="table-action complete"
                        type="button"
                        data-action="complete"
                        data-id="${order.id}">
                    Complete
                </button>

                <button class="table-action cancel"
                        type="button"
                        data-action="cancel"
                        data-id="${order.id}">
                    Cancel
                </button>
            `;

        }


        return `

            <tr>

                <td>

                    <div class="order-number">

                        <strong>
                            ${escapeHTML(order.salesNumber || "—")}
                        </strong>

                        <small>
                            ID: ${escapeHTML(order.id)}
                        </small>

                    </div>

                </td>


                <td>

                    <div class="customer-cell">

                        <strong>
                            ${escapeHTML(order.customerName || "—")}
                        </strong>

                        <small>
                            Customer #${escapeHTML(order.customerId || "—")}
                        </small>

                    </div>

                </td>


                <td>

                    <div class="item-cell">

                        <strong>
                            ${items.length} Products
                        </strong>

                        <small>
                            ${totalQuantity} Units
                        </small>

                    </div>

                </td>


                <td>
                    ${formatDateTime(order.salesDate)}
                </td>


                <td class="amount-cell">
                    ${formatCurrency(order.totalAmount)}
                </td>


                <td>

                    <span class="status-badge ${status}">
                        ${escapeHTML(order.status || "DRAFT")}
                    </span>

                </td>


                <td>
                    <div class="order-actions">
                        ${actions}
                    </div>
                </td>

            </tr>

        `;

    }


    /* ========================================
       TABLE ACTIONS
    ======================================== */

    async function handleTableAction(event) {

        const button = event.target.closest("[data-action]");

        if (!button) return;

        const id = Number(button.dataset.id);
        const action = button.dataset.action;

        const order = salesOrders.find(
            item => Number(item.id) === id
        );

        if (!order) return;


        if (action === "view") {

            openDetailsModal(order);

            return;

        }


        if (action === "edit") {

            if (order.status !== "DRAFT") return;

            await openOrderModal(order);

            return;

        }


        if (action === "complete") {

            await completeOrder(id);

            return;

        }


        if (action === "cancel") {

            await cancelOrder(id);

        }

    }


    async function completeOrder(id) {

        const confirmed = window.confirm(
            "Complete this sales order?\n\n" +
            "The backend will deduct stock and generate the sales invoice."
        );

        if (!confirmed) return;


        try {

            await apiRequest(
                `${API.sales}/${id}/complete`,
                {
                    method: "PATCH"
                }
            );

            await reloadOrders();

            showPageMessage(
                "Sales order completed successfully. Stock has been updated and the sales invoice has been generated.",
                "success"
            );

        } catch (error) {

            showPageMessage(
                `Unable to complete sales order: ${error.message}`
            );

        }

    }


    async function cancelOrder(id) {

        const confirmed = window.confirm(
            "Are you sure you want to cancel this draft sales order?"
        );

        if (!confirmed) return;


        try {

            await apiRequest(
                `${API.sales}/${id}/cancel`,
                {
                    method: "PATCH"
                }
            );

            await reloadOrders();

            showPageMessage(
                "Sales order cancelled successfully.",
                "success"
            );

        } catch (error) {

            showPageMessage(
                `Unable to cancel sales order: ${error.message}`
            );

        }

    }


    /* ========================================
       CREATE / EDIT MODAL
    ======================================== */

    async function openOrderModal(order = null) {

        editingOrderId = order ? Number(order.id) : null;

        selectedCustomer = null;
        selectedItems = [];

        elements.orderForm.reset();

        clearFormMessage();

        hideCustomerResults();
        hideProductResults();

        elements.customerSearch.disabled = false;

        elements.productSearch.disabled = false;

        elements.productSearch.placeholder =
            "Search product name or product code...";


        if (order) {

            elements.orderModalTitle.textContent =
                "Edit Sales Order";

            elements.orderModalDescription.textContent =
                `Update draft ${order.salesNumber || ""}.`;

            elements.saveButton.textContent =
                "Update Sales Order";


            const existingCustomer = customers.find(
                customer =>
                    Number(customer.id) === Number(order.customerId)
            );


            selectedCustomer = existingCustomer || {

                id: order.customerId,
                customerName: order.customerName,
                customerCode: "",
                active: false

            };


            selectedItems = await Promise.all(

                (order.items || []).map(async item => {

                    try {

                        const product = await loadProductById(
                            item.productId
                        );

                        return {

                            productId: item.productId,

                            productCode: product.productCode,
                            productName: product.productName,

                            quantity: Number(item.quantity || 1),

                            unitPrice: Number(
                                product.sellingPrice || item.unitPrice || 0
                            ),

                            availableStock: Number(
                                product.quantity || 0
                            )

                        };

                    } catch {

                        return {

                            productId: item.productId,

                            productCode: item.productCode,
                            productName: item.productName,

                            quantity: Number(item.quantity || 1),

                            unitPrice: Number(item.unitPrice || 0),

                            availableStock: null

                        };

                    }

                })

            );

        } else {

            elements.orderModalTitle.textContent =
                "Create Sales Order";

            elements.orderModalDescription.textContent =
                "Select a customer and add products to your order.";

            elements.saveButton.textContent =
                "Save Draft Order";

        }


        renderSelectedCustomer();
        renderOrderItems();

        elements.productSearch.value = "";
        elements.customerSearch.value = "";

        elements.orderModal.hidden = false;

    }


    function closeOrderModal() {

        elements.orderModal.hidden = true;

        selectedCustomer = null;
        selectedItems = [];

        editingOrderId = null;

        elements.orderForm.reset();

        clearFormMessage();

        hideCustomerResults();
        hideProductResults();

        elements.customerSearch.disabled = false;

    }


    /* ========================================
       CUSTOMER SEARCH
    ======================================== */

    function renderCustomerResults(keyword) {

        if (selectedCustomer) {
            hideCustomerResults();
            return;
        }


        const search = String(keyword || "")
            .trim()
            .toLowerCase();


        const matches = customers

            .filter(customer => customer.active === true)

            .filter(customer => {

                if (!search) return true;

                return [

                    customer.customerName,
                    customer.customerCode,
                    customer.phone,
                    customer.email

                ].some(value => {

                    return String(value || "")
                        .toLowerCase()
                        .includes(search);

                });

            })

            .slice(0, 10);


        if (matches.length === 0) {

            elements.customerResults.innerHTML = `
                <div class="autocomplete-empty">
                    No active customers found.
                </div>
            `;

        } else {

            elements.customerResults.innerHTML = matches
                .map(customer => `

                    <button type="button"
                            class="autocomplete-option"
                            data-customer-id="${customer.id}">

                        <span class="autocomplete-option-main">

                            <strong>
                                ${escapeHTML(customer.customerName)}
                            </strong>

                            <small>
                                ${escapeHTML(customer.customerCode || "No code")}
                                ·
                                ${escapeHTML(customer.phone || "No phone")}
                            </small>

                        </span>

                        <span class="autocomplete-option-side">

                            <small>
                                ${escapeHTML(customer.email || "")}
                            </small>

                        </span>

                    </button>

                `)
                .join("");

        }


        elements.customerResults.classList.add("is-visible");

    }


    function selectCustomer(customer) {

        if (customer.active !== true) {
            return;
        }

        selectedCustomer = customer;

        elements.customerSearch.value = "";

        elements.customerSearch.disabled = true;

        hideCustomerResults();

        renderSelectedCustomer();

    }


    function renderSelectedCustomer() {

        if (!selectedCustomer) {

            elements.selectedCustomer.hidden = true;

            elements.selectedCustomer.innerHTML = "";

            return;

        }


        elements.selectedCustomer.hidden = false;

        elements.selectedCustomer.innerHTML = `

            <div class="selected-record-main">

                <strong>
                    ${escapeHTML(selectedCustomer.customerName)}
                </strong>

                <small>
                    ${escapeHTML(selectedCustomer.customerCode || "Customer")}
                    ·
                    ${escapeHTML(selectedCustomer.phone || "No phone")}
                </small>

            </div>

            <button type="button"
                    class="clear-selection"
                    data-clear-customer>
                Change
            </button>

        `;

    }


    function clearCustomerSelection() {

        selectedCustomer = null;

        elements.customerSearch.disabled = false;

        elements.customerSearch.value = "";

        renderSelectedCustomer();

        elements.customerSearch.focus();

    }


    function hideCustomerResults() {

        elements.customerResults.classList.remove("is-visible");

    }


    /* ========================================
       PRODUCT SEARCH
    ======================================== */

    async function handleProductSearch(keyword) {

        const search = String(keyword || "").trim();

        const token = ++productSearchToken;


        if (!search) {

            hideProductResults();

            return;

        }


        elements.productResults.innerHTML = `
            <div class="autocomplete-empty">
                Searching products...
            </div>
        `;

        elements.productResults.classList.add("is-visible");


        try {

            const products = await searchProducts(search);

            if (token !== productSearchToken) return;


            if (products.length === 0) {

                elements.productResults.innerHTML = `
                    <div class="autocomplete-empty">
                        No active products found.
                    </div>
                `;

                return;

            }


            elements.productResults.innerHTML = products
                .map(product => `

                    <button type="button"
                            class="autocomplete-option"
                            data-product-id="${product.id}">

                        <span class="autocomplete-option-main">

                            <strong>
                                ${escapeHTML(product.productName)}
                            </strong>

                            <small>
                                ${escapeHTML(product.productCode || "No code")}
                            </small>

                        </span>

                        <span class="autocomplete-option-side">

                            <strong>
                                ${formatCurrency(product.sellingPrice)}
                            </strong>

                            <small>
                                Stock: ${Number(product.quantity || 0)}
                            </small>

                        </span>

                    </button>

                `)
                .join("");


        } catch (error) {

            if (token !== productSearchToken) return;

            elements.productResults.innerHTML = `
                <div class="autocomplete-empty">
                    ${escapeHTML(error.message)}
                </div>
            `;

        }

    }


    async function addProductFromSearch(productId) {

        const search = elements.productSearch.value.trim();

        if (!search) return;


        try {

            const products = await searchProducts(search);

            const product = products.find(
                item => Number(item.id) === Number(productId)
            );

            if (!product) {
                throw new Error("Product is no longer available.");
            }


            const existing = selectedItems.find(
                item => Number(item.productId) === Number(product.id)
            );


            if (existing) {

                existing.quantity += 1;

                existing.unitPrice = Number(
                    product.sellingPrice || 0
                );

                existing.availableStock = Number(
                    product.quantity || 0
                );

            } else {

                selectedItems.push({

                    productId: Number(product.id),

                    productCode: product.productCode,
                    productName: product.productName,

                    quantity: 1,

                    unitPrice: Number(
                        product.sellingPrice || 0
                    ),

                    availableStock: Number(
                        product.quantity || 0
                    )

                });

            }


            elements.productSearch.value = "";

            hideProductResults();

            renderOrderItems();

        } catch (error) {

            showFormMessage(error.message);

        }

    }


    function hideProductResults() {

        elements.productResults.classList.remove("is-visible");

    }


    /* ========================================
       ORDER ITEMS
    ======================================== */

    function renderOrderItems() {

        if (selectedItems.length === 0) {

            elements.itemsBody.innerHTML = `
                <tr>
                    <td colspan="6" class="items-empty">
                        Search and select products to add.
                    </td>
                </tr>
            `;

        } else {

            elements.itemsBody.innerHTML = selectedItems
                .map((item, index) => {

                    const quantity = Number(item.quantity || 0);

                    const subtotal =
                        quantity * Number(item.unitPrice || 0);

                    const available =
                        item.availableStock === null
                            ? "—"
                            : Number(item.availableStock || 0);


                    return `

                        <tr>

                            <td>

                                <div class="items-product">

                                    <strong>
                                        ${escapeHTML(item.productName)}
                                    </strong>

                                    <small>
                                        ${escapeHTML(item.productCode || "—")}
                                    </small>

                                </div>

                            </td>


                            <td>
                                ${available}
                            </td>


                            <td class="items-price">
                                ${formatCurrency(item.unitPrice)}
                            </td>


                            <td>

                                <input type="number"
                                       class="items-quantity"
                                       min="1"
                                       step="1"
                                       value="${quantity}"
                                       data-quantity-index="${index}"
                                       aria-label="Product quantity">

                            </td>


                            <td class="items-price">
                                ${formatCurrency(subtotal)}
                            </td>


                            <td>

                                <button type="button"
                                        class="remove-item"
                                        data-remove-index="${index}"
                                        aria-label="Remove product">
                                    ×
                                </button>

                            </td>

                        </tr>

                    `;

                })
                .join("");

        }


        updateOrderTotal();

    }


    function updateOrderTotal() {

        const totalQuantity = selectedItems.reduce(
            (total, item) => {

                return total + Number(item.quantity || 0);

            },
            0
        );


        const totalAmount = selectedItems.reduce(
            (total, item) => {

                return total +
                    Number(item.quantity || 0) *
                    Number(item.unitPrice || 0);

            },
            0
        );


        elements.itemCount.textContent =
            `${selectedItems.length} Items`;

        elements.totalQuantity.textContent =
            totalQuantity.toLocaleString("en-MY");

        elements.grandTotal.textContent =
            formatCurrency(totalAmount);


        const shortages = selectedItems.filter(item => {

            if (item.availableStock === null) {
                return false;
            }

            return Number(item.quantity) >
                Number(item.availableStock);

        });


        if (shortages.length === 0) {

            elements.stockWarning.hidden = true;

            elements.stockWarning.textContent = "";

        } else {

            elements.stockWarning.hidden = false;

            elements.stockWarning.textContent =
                shortages.map(item => {

                    return `${item.productName}: requested ${item.quantity}, available ${item.availableStock}.`;

                }).join(" ");

        }

    }


    /* ========================================
       SAVE ORDER
    ======================================== */

    async function saveOrder(event) {

        event.preventDefault();

        if (isSaving) return;


        clearFormMessage();


        if (!selectedCustomer) {

            showFormMessage(
                "Please select an active customer."
            );

            return;

        }


        if (selectedItems.length === 0) {

            showFormMessage(
                "Please add at least one product."
            );

            return;

        }


        const payload = {

            customerId: Number(selectedCustomer.id),

            items: selectedItems.map(item => ({

                productId: Number(item.productId),

                quantity: Number(item.quantity)

            }))

        };


        isSaving = true;

        elements.saveButton.disabled = true;

        elements.saveButton.textContent = "Saving...";


        try {

            if (editingOrderId) {

                await apiRequest(
                    `${API.sales}/${editingOrderId}`,
                    {
                        method: "PUT",
                        body: JSON.stringify(payload)
                    }
                );

            } else {

                await apiRequest(
                    API.sales,
                    {
                        method: "POST",
                        body: JSON.stringify(payload)
                    }
                );

            }


            const message = editingOrderId
                ? "Sales order updated successfully."
                : "Sales order created successfully as DRAFT.";


            closeOrderModal();

            await reloadOrders();

            showPageMessage(message, "success");


        } catch (error) {

            showFormMessage(error.message);

        } finally {

            isSaving = false;

            elements.saveButton.disabled = false;

            elements.saveButton.textContent =
                editingOrderId
                    ? "Update Sales Order"
                    : "Save Draft Order";

        }

    }


    /* ========================================
       VIEW ORDER DETAILS
    ======================================== */

    function openDetailsModal(order) {

        const items = order.items || [];

        elements.detailsTitle.textContent =
            order.salesNumber || "Sales Order";

        elements.detailsDescription.textContent =
            "Sales order information and product details.";

        elements.detailsCustomer.textContent =
            order.customerName || "—";

        elements.detailsDate.textContent =
            formatDateTime(order.salesDate);

        elements.detailsNumber.textContent =
            order.salesNumber || "—";

        elements.detailsStatus.innerHTML = `

            <span class="status-badge ${String(
                order.status || "DRAFT"
            ).toLowerCase()}">

                ${escapeHTML(order.status || "DRAFT")}

            </span>

        `;


        if (items.length === 0) {

            elements.detailsItemsBody.innerHTML = `
                <tr>
                    <td colspan="4" class="table-empty">
                        No order items.
                    </td>
                </tr>
            `;

        } else {

            elements.detailsItemsBody.innerHTML = items
                .map(item => `

                    <tr>

                        <td>

                            <div class="items-product">

                                <strong>
                                    ${escapeHTML(item.productName)}
                                </strong>

                                <small>
                                    ${escapeHTML(item.productCode || "—")}
                                </small>

                            </div>

                        </td>

                        <td>
                            ${formatCurrency(item.unitPrice)}
                        </td>

                        <td>
                            ${Number(item.quantity || 0)}
                        </td>

                        <td>
                            ${formatCurrency(item.subtotal)}
                        </td>

                    </tr>

                `)
                .join("");

        }


        elements.detailsTotal.textContent =
            formatCurrency(order.totalAmount);


        elements.detailsModal.hidden = false;

    }


    function closeDetailsModal() {

        elements.detailsModal.hidden = true;

    }


    /* ========================================
       RELOAD
    ======================================== */

    async function reloadOrders() {

        salesOrders = await loadSalesOrders();

        currentPage = 0;

        updateSummary();

        renderOrders();

    }


    /* ========================================
       HELPERS
    ======================================== */

    function formatCurrency(value) {

        const amount = Number(value || 0);

        return `RM ${amount.toLocaleString(
            "en-MY",
            {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }
        )}`;

    }


    function formatDateTime(value) {

        if (!value) return "—";

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return "—";
        }

        return date.toLocaleString(
            "en-MY",
            {
                day: "2-digit",
                month: "short",
                year: "numeric",
                hour: "2-digit",
                minute: "2-digit"
            }
        );

    }


    function escapeHTML(value) {

        return String(value ?? "").replace(
            /[&<>"']/g,

            character => ({

                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;"

            })[character]

        );

    }


    function showPageMessage(message, type = "error") {

        elements.pageMessage.textContent = message;

        elements.pageMessage.classList.add("show");

        elements.pageMessage.classList.toggle(
            "success",
            type === "success"
        );

    }


    function showFormMessage(message) {

        elements.formMessage.textContent = message;

        elements.formMessage.classList.add("show");

    }


    function clearFormMessage() {

        elements.formMessage.textContent = "";

        elements.formMessage.classList.remove("show");

    }


})();
