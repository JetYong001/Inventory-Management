
"use strict";

(() => {

    const PAGE_SIZE = 10;

    const API = {
        purchases: "/api/purchases",
        suppliers: "/api/suppliers",
        products: "/api/products",
        grn: "/api/grn"
    };

    let purchases = [];
    let suppliers = [];

    let currentPage = 0;

    let selectedSupplier = null;
    let selectedItems = [];

    let editingPurchaseId = null;

    let isSaving = false;
    let isReceiving = false;

    let productSearchToken = 0;
    let productSearchResults = [];

    let currentReceivingPurchase = null;
    let receiveRequestKey = null;

    const elements = {

        totalPurchases: document.getElementById("total-purchases"),
        draftPurchases: document.getElementById("draft-purchases"),
        partialPurchases: document.getElementById("partial-purchases"),
        receivedPurchases: document.getElementById("received-purchases"),

        pageMessage: document.getElementById("page-message"),

        search: document.getElementById("purchase-search"),
        statusFilter: document.getElementById("purchase-status-filter"),

        tableBody: document.getElementById("purchases-table-body"),

        paginationInfo: document.getElementById("pagination-info"),
        currentPage: document.getElementById("current-page"),
        previousPage: document.getElementById("previous-page"),
        nextPage: document.getElementById("next-page"),

        addButton: document.getElementById("add-purchase-button"),

        purchaseModal: document.getElementById("purchase-modal"),
        purchaseModalTitle: document.getElementById("purchase-modal-title"),
        purchaseModalDescription: document.getElementById("purchase-modal-description"),

        purchaseForm: document.getElementById("purchase-form"),
        formMessage: document.getElementById("form-message"),
        saveButton: document.getElementById("save-purchase-button"),

        closePurchaseModal: document.getElementById("close-purchase-modal"),
        cancelPurchaseModal: document.getElementById("cancel-purchase-modal"),

        supplierSearch: document.getElementById("supplier-search"),
        supplierResults: document.getElementById("supplier-results"),
        selectedSupplier: document.getElementById("selected-supplier"),

        productSearch: document.getElementById("product-search"),
        productResults: document.getElementById("product-results"),

        itemsBody: document.getElementById("purchase-items-body"),
        itemCount: document.getElementById("item-count"),
        totalQuantity: document.getElementById("total-quantity"),
        grandTotal: document.getElementById("grand-total"),

        receiveModal: document.getElementById("receive-modal"),
        receiveNumber: document.getElementById("receive-purchase-number"),
        receiveSupplier: document.getElementById("receive-supplier-name"),

        receiveForm: document.getElementById("receive-form"),
        receiveMessage: document.getElementById("receive-message"),
        receiveItemsBody: document.getElementById("receive-items-body"),
        receiveButton: document.getElementById("submit-receive-button"),

        closeReceiveModal: document.getElementById("close-receive-modal"),
        cancelReceiveModal: document.getElementById("cancel-receive-modal"),

        detailsModal: document.getElementById("details-modal"),

        detailsTitle: document.getElementById("details-title"),
        detailsDescription: document.getElementById("details-description"),

        detailsSupplier: document.getElementById("details-supplier"),
        detailsDate: document.getElementById("details-date"),
        detailsNumber: document.getElementById("details-number"),
        detailsStatus: document.getElementById("details-status"),

        detailsItemsBody: document.getElementById("details-items-body"),
        detailsTotal: document.getElementById("details-total"),

        receiptHistoryBody: document.getElementById("receipt-history-body"),

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


    async function loadPurchases() {

        const result = await apiRequest(API.purchases);

        return Array.isArray(result) ? result : [];
    }


    async function loadSuppliers() {

        const result = await apiRequest(API.suppliers);

        return Array.isArray(result) ? result : [];
    }


    async function loadPurchaseById(id) {

        return apiRequest(`${API.purchases}/${id}`);
    }


    async function loadProductById(id) {

        return apiRequest(`${API.products}/${id}`);
    }


    async function searchProducts(keyword) {

        const params = new URLSearchParams({
            keyword,
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


    /* ========================================
       INITIALIZATION
    ======================================== */

    document.addEventListener(
        "DOMContentLoaded",
        initializePurchases
    );


    async function initializePurchases() {

        bindEvents();

        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="8" class="table-loading">
                    Loading purchases...
                </td>
            </tr>
        `;

        try {

            const results = await Promise.allSettled([
                loadPurchases(),
                loadSuppliers()
            ]);

            if (results[0].status === "rejected") {
                throw results[0].reason;
            }

            purchases = results[0].value;

            if (results[1].status === "fulfilled") {
                suppliers = results[1].value;
            } else {
                suppliers = [];

                showPageMessage(
                    `Unable to load suppliers: ${results[1].reason.message}`
                );
            }

            updateSummary();
            renderPurchases();

        } catch (error) {

            showPageMessage(
                `Unable to load purchases: ${error.message}`
            );

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        Unable to load purchases.
                    </td>
                </tr>
            `;
        }
    }


    /* ========================================
       EVENTS
    ======================================== */

    function bindEvents() {

        elements.addButton.addEventListener("click", () => {
            openPurchaseModal();
        });


        elements.closePurchaseModal.addEventListener(
            "click",
            closePurchaseModal
        );

        elements.cancelPurchaseModal.addEventListener(
            "click",
            closePurchaseModal
        );


        elements.purchaseModal.addEventListener("click", event => {

            if (event.target.matches("[data-close-purchase]")) {
                closePurchaseModal();
            }
        });


        elements.purchaseForm.addEventListener(
            "submit",
            savePurchase
        );


        elements.search.addEventListener("input", () => {

            currentPage = 0;
            renderPurchases();
        });


        elements.statusFilter.addEventListener("change", () => {

            currentPage = 0;
            renderPurchases();
        });


        elements.previousPage.addEventListener("click", () => {

            if (currentPage > 0) {
                currentPage--;
                renderPurchases();
            }
        });


        elements.nextPage.addEventListener("click", () => {

            const totalPages = Math.ceil(
                getFilteredPurchases().length / PAGE_SIZE
            );

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderPurchases();
            }
        });


        elements.tableBody.addEventListener(
            "click",
            handleTableAction
        );


        /* SUPPLIER SEARCH */

        elements.supplierSearch.addEventListener("input", () => {

            renderSupplierResults(
                elements.supplierSearch.value
            );
        });


        elements.supplierSearch.addEventListener("focus", () => {

            renderSupplierResults(
                elements.supplierSearch.value
            );
        });


        elements.supplierResults.addEventListener("click", event => {

            const button = event.target.closest(
                "[data-supplier-id]"
            );

            if (!button) return;

            const supplier = suppliers.find(
                item => Number(item.id) ===
                    Number(button.dataset.supplierId)
            );

            if (supplier) {
                selectSupplier(supplier);
            }
        });


        elements.selectedSupplier.addEventListener("click", event => {

            if (event.target.closest("[data-clear-supplier]")) {
                clearSupplierSelection();
            }
        });


        /* PRODUCT SEARCH */

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

            const button = event.target.closest(
                "[data-product-id]"
            );

            if (!button) return;

            addProductFromSearch(
                Number(button.dataset.productId)
            );
        });


        /* PURCHASE ITEM QUANTITY / UNIT COST */

        elements.itemsBody.addEventListener("change", event => {

            const input = event.target.closest("[data-item-field]");

            if (!input) return;

            const index = Number(input.dataset.index);
            const field = input.dataset.itemField;

            const item = selectedItems[index];

            if (!item) return;

            if (field === "quantity") {

                item.quantity = Math.max(
                    1,
                    Math.floor(Number(input.value) || 1)
                );
            }

            if (field === "unitCost") {

                item.unitCost = Math.max(
                    0.01,
                    Number(input.value) || 0.01
                );
            }

            renderPurchaseItems();
        });


        elements.itemsBody.addEventListener("click", event => {

            const button = event.target.closest(
                "[data-remove-index]"
            );

            if (!button) return;

            selectedItems.splice(
                Number(button.dataset.removeIndex),
                1
            );

            renderPurchaseItems();
        });


        /* RECEIVE GOODS */

        elements.closeReceiveModal.addEventListener(
            "click",
            closeReceiveModal
        );

        elements.cancelReceiveModal.addEventListener(
            "click",
            closeReceiveModal
        );


        elements.receiveModal.addEventListener("click", event => {

            if (event.target.matches("[data-close-receive]")) {
                closeReceiveModal();
            }
        });


        elements.receiveForm.addEventListener(
            "submit",
            receiveGoods
        );


        /*
         * IMPORTANT:
         * Restrict receiving quantity immediately during input.
         * This also handles paste and spinner changes.
         */

        elements.receiveItemsBody.addEventListener(
            "input",
            handleReceivingQuantityInput
        );


        /*
         * Final correction when the user leaves an input.
         */

        elements.receiveItemsBody.addEventListener(
            "change",
            handleReceivingQuantityChange
        );


        /* DETAILS */

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


        /* CLOSE AUTOCOMPLETE */

        document.addEventListener("click", event => {

            if (
                !event.target.closest("#supplier-search") &&
                !event.target.closest("#supplier-results")
            ) {
                hideSupplierResults();
            }

            if (
                !event.target.closest("#product-search") &&
                !event.target.closest("#product-results")
            ) {
                hideProductResults();
            }
        });


        /* ESCAPE */

        document.addEventListener("keydown", event => {

            if (event.key !== "Escape") return;

            if (!elements.purchaseModal.hidden) {
                closePurchaseModal();
            }

            if (!elements.receiveModal.hidden) {
                closeReceiveModal();
            }

            if (!elements.detailsModal.hidden) {
                closeDetailsModal();
            }
        });
    }


    /* ========================================
       RECEIVING INPUT LIMIT
    ======================================== */

    function handleReceivingQuantityInput(event) {

        const input = event.target.closest(
            "[data-receive-item-id]"
        );

        if (!input) return;


        const maximum = Math.max(
            0,
            Math.floor(Number(input.max) || 0)
        );


        /*
         * Allow temporary empty input while editing.
         */

        if (input.value === "") {

            receiveRequestKey = null;

            return;
        }


        let quantity = Number(input.value);


        if (!Number.isFinite(quantity)) {

            input.value = "0";

            receiveRequestKey = null;

            return;
        }


        quantity = Math.floor(quantity);


        if (quantity < 0) {
            quantity = 0;
        }


        /*
         * THE ACTUAL LIMIT:
         * User cannot enter more than Remaining Quantity.
         */

        if (quantity > maximum) {

            quantity = maximum;

        }


        input.value = String(quantity);

        receiveRequestKey = null;

    }


    function handleReceivingQuantityChange(event) {

        const input = event.target.closest(
            "[data-receive-item-id]"
        );

        if (!input) return;


        const maximum = Math.max(
            0,
            Math.floor(Number(input.max) || 0)
        );


        if (input.value === "") {

            input.value = "0";

        }


        let quantity = Number(input.value);


        if (!Number.isFinite(quantity)) {
            quantity = 0;
        }


        quantity = Math.floor(quantity);

        quantity = Math.max(
            0,
            Math.min(quantity, maximum)
        );


        input.value = String(quantity);

        receiveRequestKey = null;

    }


    /* ========================================
       SUMMARY
    ======================================== */

    function updateSummary() {

        const draft = purchases.filter(
            item => item.status === "DRAFT"
        );

        const partial = purchases.filter(
            item => item.status === "PARTIALLY_RECEIVED"
        );

        const received = purchases.filter(
            item => item.status === "RECEIVED"
        );


        elements.totalPurchases.textContent =
            purchases.length.toLocaleString("en-MY");

        elements.draftPurchases.textContent =
            draft.length.toLocaleString("en-MY");

        elements.partialPurchases.textContent =
            partial.length.toLocaleString("en-MY");

        elements.receivedPurchases.textContent =
            received.length.toLocaleString("en-MY");

    }


    /* ========================================
       SEARCH AND FILTER
    ======================================== */

    function getFilteredPurchases() {

        const keyword = elements.search.value
            .trim()
            .toLowerCase();

        const status = elements.statusFilter.value;


        return [...purchases]

            .filter(purchase => {

                if (
                    status !== "ALL" &&
                    purchase.status !== status
                ) {
                    return false;
                }

                if (!keyword) return true;


                const number = String(
                    purchase.purchaseNumber || ""
                ).toLowerCase();

                const supplierName = String(
                    purchase.supplierName || ""
                ).toLowerCase();

                const supplierId = String(
                    purchase.supplierId || ""
                ).toLowerCase();


                const productMatch = (purchase.items || []).some(item => {

                    const code = String(
                        item.productCode || ""
                    ).toLowerCase();

                    const name = String(
                        item.productName || ""
                    ).toLowerCase();

                    return code.includes(keyword) ||
                        name.includes(keyword);
                });


                return number.includes(keyword) ||
                    supplierName.includes(keyword) ||
                    supplierId.includes(keyword) ||
                    productMatch;

            })

            .sort((a, b) => {

                return new Date(b.purchaseDate || 0) -
                    new Date(a.purchaseDate || 0);

            });
    }


    /* ========================================
       PURCHASE TABLE
    ======================================== */

    function renderPurchases() {

        const filtered = getFilteredPurchases();

        const totalPages = Math.ceil(
            filtered.length / PAGE_SIZE
        );


        if (currentPage >= totalPages) {

            currentPage = Math.max(
                0,
                totalPages - 1
            );
        }


        const start = currentPage * PAGE_SIZE;

        const pagePurchases = filtered.slice(
            start,
            start + PAGE_SIZE
        );


        if (pagePurchases.length === 0) {

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        No purchases found.
                    </td>
                </tr>
            `;

        } else {

            elements.tableBody.innerHTML = pagePurchases
                .map(renderPurchaseRow)
                .join("");

        }


        elements.paginationInfo.textContent =
            filtered.length === 0
                ? "Showing 0 purchases"
                : `Showing ${start + 1}-${Math.min(
                    start + PAGE_SIZE,
                    filtered.length
                )} of ${filtered.length} purchases`;


        elements.currentPage.textContent =
            totalPages === 0
                ? "0"
                : `${currentPage + 1} / ${totalPages}`;


        elements.previousPage.disabled =
            currentPage <= 0;

        elements.nextPage.disabled =
            currentPage >= totalPages - 1;

    }


    function renderPurchaseRow(purchase) {

        const items = purchase.items || [];


        const ordered = items.reduce(
            (total, item) => {

                return total + Number(item.quantity || 0);

            },
            0
        );


        const received = items.reduce(
            (total, item) => {

                return total + Number(item.receivedQuantity || 0);

            },
            0
        );


        const statusClass = getStatusClass(
            purchase.status
        );


        let actions = `

            <button class="table-action"
                    type="button"
                    data-action="view"
                    data-id="${purchase.id}">
                View
            </button>

        `;


        if (purchase.status === "DRAFT") {

            actions += `

                <button class="table-action"
                        type="button"
                        data-action="edit"
                        data-id="${purchase.id}">
                    Edit
                </button>

                <button class="table-action receive"
                        type="button"
                        data-action="receive"
                        data-id="${purchase.id}">
                    Receive
                </button>

                <button class="table-action cancel"
                        type="button"
                        data-action="cancel"
                        data-id="${purchase.id}">
                    Cancel
                </button>

            `;

        }


        if (purchase.status === "PARTIALLY_RECEIVED") {

            actions += `

                <button class="table-action receive"
                        type="button"
                        data-action="receive"
                        data-id="${purchase.id}">
                    Receive
                </button>

            `;

        }


        return `

            <tr>

                <td>

                    <div class="purchase-number">

                        <strong>
                            ${escapeHTML(purchase.purchaseNumber || "—")}
                        </strong>

                        <small>
                            ID: ${escapeHTML(purchase.id)}
                        </small>

                    </div>

                </td>


                <td>

                    <div class="supplier-cell">

                        <strong>
                            ${escapeHTML(purchase.supplierName || "—")}
                        </strong>

                        <small>
                            Supplier #${escapeHTML(purchase.supplierId || "—")}
                        </small>

                    </div>

                </td>


                <td>

                    <div class="purchase-item-cell">

                        <strong>
                            ${items.length} Products
                        </strong>

                        <small>
                            ${ordered} Units
                        </small>

                    </div>

                </td>


                <td>

                    <div class="receiving-progress">

                        <strong>
                            ${received} / ${ordered}
                        </strong>

                        <small>Units received</small>

                    </div>

                </td>


                <td>
                    ${formatDateTime(purchase.purchaseDate)}
                </td>


                <td class="amount-cell">
                    ${formatCurrency(purchase.totalAmount)}
                </td>


                <td>

                    <span class="status-badge ${statusClass}">
                        ${escapeHTML(formatStatus(purchase.status))}
                    </span>

                </td>


                <td>

                    <div class="purchase-actions">
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


        const purchase = purchases.find(
            item => Number(item.id) === id
        );

        if (!purchase) return;


        if (action === "view") {

            await openDetailsModal(purchase);

            return;
        }


        if (action === "edit") {

            if (purchase.status !== "DRAFT") return;

            await openPurchaseModal(purchase);

            return;
        }


        if (action === "receive") {

            await openReceiveModal(purchase);

            return;
        }


        if (action === "cancel") {

            await cancelPurchase(id);

        }
    }


    /* ========================================
       CREATE / EDIT PURCHASE
    ======================================== */

    async function openPurchaseModal(purchase = null) {

        if (isSaving) return;


        editingPurchaseId = purchase
            ? Number(purchase.id)
            : null;

        selectedSupplier = null;
        selectedItems = [];

        elements.purchaseForm.reset();

        clearFormMessage();

        hideSupplierResults();
        hideProductResults();

        productSearchResults = [];


        elements.supplierSearch.disabled = false;
        elements.productSearch.disabled = false;


        if (purchase) {

            elements.purchaseModalTitle.textContent =
                "Edit Purchase";

            elements.purchaseModalDescription.textContent =
                `Update draft ${purchase.purchaseNumber || ""}.`;

            elements.saveButton.textContent =
                "Update Purchase";


            try {

                const freshPurchase = await loadPurchaseById(
                    purchase.id
                );


                const supplier = suppliers.find(
                    item =>
                        Number(item.id) ===
                        Number(freshPurchase.supplierId)
                );


                selectedSupplier = supplier || {

                    id: freshPurchase.supplierId,
                    supplierName: freshPurchase.supplierName,
                    supplierCode: "",
                    active: false

                };


                selectedItems = await Promise.all(

                    (freshPurchase.items || []).map(async item => {

                        let product = null;

                        try {

                            product = await loadProductById(
                                item.productId
                            );

                        } catch {
                            product = null;
                        }


                        return {

                            productId: Number(item.productId),

                            productCode:
                                product?.productCode ||
                                item.productCode,

                            productName:
                                product?.productName ||
                                item.productName,

                            quantity: Number(item.quantity || 1),

                            unitCost: Number(item.unitCost || 0),

                            active: product?.active === true

                        };

                    })

                );


            } catch (error) {

                showFormMessage(
                    `Unable to load purchase details: ${error.message}`
                );

                return;
            }


        } else {

            elements.purchaseModalTitle.textContent =
                "Create Purchase";

            elements.purchaseModalDescription.textContent =
                "Select a supplier and add products to your purchase.";

            elements.saveButton.textContent =
                "Save Draft Purchase";

        }


        renderSelectedSupplier();

        renderPurchaseItems();

        elements.supplierSearch.value = "";
        elements.productSearch.value = "";

        elements.purchaseModal.hidden = false;

    }


    function closePurchaseModal() {

        if (isSaving) return;


        elements.purchaseModal.hidden = true;

        selectedSupplier = null;
        selectedItems = [];

        editingPurchaseId = null;

        productSearchResults = [];

        elements.purchaseForm.reset();

        clearFormMessage();

        hideSupplierResults();
        hideProductResults();

        elements.supplierSearch.disabled = false;
        elements.productSearch.disabled = false;

    }


    /* ========================================
       SUPPLIER SEARCH
    ======================================== */

    function renderSupplierResults(keyword) {

        if (selectedSupplier) {

            hideSupplierResults();

            return;
        }


        const search = String(keyword || "")
            .trim()
            .toLowerCase();


        const matches = suppliers

            .filter(supplier => supplier.active === true)

            .filter(supplier => {

                if (!search) return true;

                return [

                    supplier.supplierName,
                    supplier.supplierCode,
                    supplier.phone,
                    supplier.email,
                    supplier.address

                ].some(value => {

                    return String(value || "")
                        .toLowerCase()
                        .includes(search);

                });

            })

            .slice(0, 10);


        if (matches.length === 0) {

            elements.supplierResults.innerHTML = `
                <div class="autocomplete-empty">
                    No active suppliers found.
                </div>
            `;

        } else {

            elements.supplierResults.innerHTML = matches
                .map(supplier => `

                    <button type="button"
                            class="autocomplete-option"
                            data-supplier-id="${supplier.id}">

                        <span class="autocomplete-option-main">

                            <strong>
                                ${escapeHTML(supplier.supplierName)}
                            </strong>

                            <small>
                                ${escapeHTML(supplier.supplierCode || "No code")}
                                ·
                                ${escapeHTML(supplier.phone || "No phone")}
                            </small>

                        </span>

                        <span class="autocomplete-option-side">

                            <small>
                                ${escapeHTML(supplier.email || "")}
                            </small>

                        </span>

                    </button>

                `)
                .join("");

        }


        elements.supplierResults.classList.add("is-visible");

    }


    function selectSupplier(supplier) {

        if (supplier.active !== true) return;

        selectedSupplier = supplier;

        elements.supplierSearch.value = "";

        elements.supplierSearch.disabled = true;

        hideSupplierResults();

        renderSelectedSupplier();

    }


    function renderSelectedSupplier() {

        if (!selectedSupplier) {

            elements.selectedSupplier.hidden = true;

            elements.selectedSupplier.innerHTML = "";

            return;
        }


        elements.selectedSupplier.hidden = false;

        elements.selectedSupplier.innerHTML = `

            <div class="selected-record-main">

                <strong>
                    ${escapeHTML(selectedSupplier.supplierName)}
                </strong>

                <small>
                    ${escapeHTML(selectedSupplier.supplierCode || "Supplier")}
                    ·
                    ${escapeHTML(selectedSupplier.phone || "No phone")}
                </small>

            </div>

            <button type="button"
                    class="clear-selection"
                    data-clear-supplier>
                Change
            </button>

        `;

    }


    function clearSupplierSelection() {

        selectedSupplier = null;

        elements.supplierSearch.disabled = false;

        elements.supplierSearch.value = "";

        renderSelectedSupplier();

        elements.supplierSearch.focus();

    }


    function hideSupplierResults() {

        elements.supplierResults.classList.remove("is-visible");

    }


    /* ========================================
       PRODUCT SEARCH
    ======================================== */

    async function handleProductSearch(keyword) {

        const search = String(keyword || "").trim();

        const token = ++productSearchToken;


        if (!search) {

            productSearchResults = [];

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

            const result = await searchProducts(search);

            if (token !== productSearchToken) return;

            productSearchResults = result;


            if (result.length === 0) {

                elements.productResults.innerHTML = `
                    <div class="autocomplete-empty">
                        No active products found.
                    </div>
                `;

                return;
            }


            elements.productResults.innerHTML = result
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
                                ${formatCurrency(product.costPrice)}
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


    function addProductFromSearch(productId) {

        const product = productSearchResults.find(
            item => Number(item.id) === Number(productId)
        );


        if (!product || product.active !== true) {

            showFormMessage(
                "This product is no longer active."
            );

            return;
        }


        const existing = selectedItems.find(
            item => Number(item.productId) === Number(product.id)
        );


        if (existing) {

            existing.quantity += 1;

        } else {

            selectedItems.push({

                productId: Number(product.id),

                productCode: product.productCode,
                productName: product.productName,

                quantity: 1,

                unitCost: Number(product.costPrice || 0),

                active: true

            });

        }


        elements.productSearch.value = "";

        hideProductResults();

        renderPurchaseItems();

    }


    function hideProductResults() {

        elements.productResults.classList.remove("is-visible");

    }


    /* ========================================
       PURCHASE ITEMS
    ======================================== */

    function renderPurchaseItems() {

        if (selectedItems.length === 0) {

            elements.itemsBody.innerHTML = `
                <tr>
                    <td colspan="5" class="items-empty">
                        Search and select products to add.
                    </td>
                </tr>
            `;

        } else {

            elements.itemsBody.innerHTML = selectedItems
                .map((item, index) => {

                    const subtotal =
                        Number(item.quantity || 0) *
                        Number(item.unitCost || 0);


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

                                <input type="number"
                                       class="items-input"
                                       min="1"
                                       step="1"
                                       value="${Number(item.quantity || 1)}"
                                       data-index="${index}"
                                       data-item-field="quantity"
                                       aria-label="Purchase quantity">

                            </td>


                            <td>

                                <input type="number"
                                       class="items-input"
                                       min="0.01"
                                       step="0.01"
                                       value="${Number(item.unitCost || 0).toFixed(2)}"
                                       data-index="${index}"
                                       data-item-field="unitCost"
                                       aria-label="Unit cost">

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


        updatePurchaseTotal();

    }


    function updatePurchaseTotal() {

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
                    Number(item.unitCost || 0);

            },
            0
        );


        elements.itemCount.textContent =
            `${selectedItems.length} Items`;

        elements.totalQuantity.textContent =
            totalQuantity.toLocaleString("en-MY");

        elements.grandTotal.textContent =
            formatCurrency(totalAmount);

    }


    /* ========================================
       SAVE PURCHASE
    ======================================== */

    async function savePurchase(event) {

        event.preventDefault();

        if (isSaving) return;


        clearFormMessage();


        if (!selectedSupplier) {

            showFormMessage(
                "Please select an active supplier."
            );

            return;
        }


        if (selectedSupplier.active !== true) {

            showFormMessage(
                "The selected supplier is inactive."
            );

            return;
        }


        if (selectedItems.length === 0) {

            showFormMessage(
                "Please add at least one product."
            );

            return;
        }


        for (const item of selectedItems) {

            if (item.active !== true) {

                showFormMessage(
                    `${item.productName} is inactive. Remove it and select an active product.`
                );

                return;
            }


            if (
                !Number.isInteger(Number(item.quantity)) ||
                Number(item.quantity) < 1
            ) {

                showFormMessage(
                    "Purchase quantity must be a whole number greater than zero."
                );

                return;
            }


            if (
                !Number.isFinite(Number(item.unitCost)) ||
                Number(item.unitCost) <= 0
            ) {

                showFormMessage(
                    `Enter a valid unit cost for ${item.productName}.`
                );

                return;
            }

        }


        const isEditing = Boolean(editingPurchaseId);

        const payload = {

            supplierId: Number(selectedSupplier.id),

            items: selectedItems.map(item => ({

                productId: Number(item.productId),

                quantity: Number(item.quantity),

                unitCost: Number(
                    Number(item.unitCost).toFixed(2)
                )

            }))

        };


        isSaving = true;

        elements.saveButton.disabled = true;

        elements.saveButton.textContent = "Saving...";


        try {

            if (isEditing) {

                await apiRequest(
                    `${API.purchases}/${editingPurchaseId}`,
                    {
                        method: "PUT",
                        body: JSON.stringify(payload)
                    }
                );

            } else {

                await apiRequest(
                    API.purchases,
                    {
                        method: "POST",
                        body: JSON.stringify(payload)
                    }
                );

            }


            const message = isEditing
                ? "Purchase updated successfully."
                : "Purchase created successfully as DRAFT.";


            elements.purchaseModal.hidden = true;

            selectedSupplier = null;
            selectedItems = [];
            editingPurchaseId = null;

            elements.purchaseForm.reset();

            await reloadPurchases();

            showPageMessage(message, "success");


        } catch (error) {

            showFormMessage(error.message);

        } finally {

            isSaving = false;

            elements.saveButton.disabled = false;

            elements.saveButton.textContent =
                editingPurchaseId
                    ? "Update Purchase"
                    : "Save Draft Purchase";

        }

    }


    /* ========================================
       CANCEL PURCHASE
    ======================================== */

    async function cancelPurchase(id) {

        const confirmed = window.confirm(
            "Are you sure you want to cancel this draft purchase?"
        );

        if (!confirmed) return;


        try {

            await apiRequest(
                `${API.purchases}/${id}/cancel`,
                {
                    method: "PATCH"
                }
            );


            await reloadPurchases();

            showPageMessage(
                "Purchase cancelled successfully.",
                "success"
            );


        } catch (error) {

            showPageMessage(
                `Unable to cancel purchase: ${error.message}`
            );

        }

    }


    /* ========================================
       RECEIVE GOODS
    ======================================== */

    async function openReceiveModal(purchase) {

        clearReceiveMessage();

        currentReceivingPurchase = null;
        receiveRequestKey = null;

        elements.receiveButton.disabled = true;

        elements.receiveItemsBody.innerHTML = `
            <tr>
                <td colspan="5" class="table-loading">
                    Loading purchase details...
                </td>
            </tr>
        `;


        try {

            const freshPurchase = await loadPurchaseById(
                purchase.id
            );


            if (
                freshPurchase.status !== "DRAFT" &&
                freshPurchase.status !== "PARTIALLY_RECEIVED"
            ) {

                throw new Error(
                    "Only DRAFT or PARTIALLY_RECEIVED purchases can receive goods."
                );

            }


            currentReceivingPurchase = freshPurchase;


            elements.receiveNumber.textContent =
                freshPurchase.purchaseNumber || "—";

            elements.receiveSupplier.textContent =
                freshPurchase.supplierName || "—";


            renderReceiveItems(freshPurchase);

            elements.receiveModal.hidden = false;


        } catch (error) {

            currentReceivingPurchase = null;

            showPageMessage(
                `Unable to open receiving: ${error.message}`
            );

        }

    }


    function renderReceiveItems(purchase) {

        const pendingItems = (purchase.items || [])
            .filter(item => getRemainingQuantity(item) > 0);


        if (pendingItems.length === 0) {

            elements.receiveItemsBody.innerHTML = `
                <tr>
                    <td colspan="5" class="table-empty">
                        All purchase items have been received.
                    </td>
                </tr>
            `;

            elements.receiveButton.disabled = true;

            return;
        }


        elements.receiveButton.disabled = false;


        elements.receiveItemsBody.innerHTML = pendingItems
            .map(item => {

                const remaining = getRemainingQuantity(item);

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
                            ${Number(item.quantity || 0)}
                        </td>


                        <td>
                            ${Number(item.receivedQuantity || 0)}
                        </td>


                        <td>
                            <strong>${remaining}</strong>
                        </td>


                        <td>

                            <input type="number"
                                   class="items-input"
                                   min="0"
                                   max="${remaining}"
                                   step="1"
                                   value="0"
                                   data-receive-item-id="${item.id}"
                                   aria-label="Quantity received now">

                        </td>

                    </tr>

                `;

            })
            .join("");

    }


    async function receiveGoods(event) {

        event.preventDefault();

        if (isReceiving) return;


        clearReceiveMessage();


        if (!currentReceivingPurchase) {

            showReceiveMessage(
                "Purchase information is missing."
            );

            return;
        }


        const inputs = [
            ...elements.receiveItemsBody.querySelectorAll(
                "[data-receive-item-id]"
            )
        ];


        const items = [];


        for (const input of inputs) {

            const maximum = Math.floor(
                Number(input.max) || 0
            );

            let quantity = Number(input.value);


            if (!Number.isFinite(quantity)) {
                quantity = 0;
            }

            quantity = Math.floor(quantity);


            if (
                quantity < 0 ||
                quantity > maximum
            ) {

                showReceiveMessage(
                    `Receiving quantity cannot exceed the remaining quantity of ${maximum}.`
                );

                input.value = String(
                    Math.max(0, Math.min(quantity, maximum))
                );

                return;
            }


            if (quantity === 0) {
                continue;
            }


            items.push({

                purchaseItemId: Number(
                    input.dataset.receiveItemId
                ),

                quantity

            });

        }


        if (items.length === 0) {

            showReceiveMessage(
                "Enter at least one receiving quantity."
            );

            return;
        }


        /*
         * Keep the same request key for an unchanged retry.
         */

        if (!receiveRequestKey) {

            receiveRequestKey = createRequestKey();

        }


        const payload = {

            purchaseId: Number(currentReceivingPurchase.id),

            requestKey: receiveRequestKey,

            items

        };


        isReceiving = true;

        elements.receiveButton.disabled = true;

        elements.receiveButton.textContent = "Processing...";


        try {

            const result = await apiRequest(
                API.grn,
                {
                    method: "POST",
                    body: JSON.stringify(payload)
                }
            );


            elements.receiveModal.hidden = true;

            currentReceivingPurchase = null;

            receiveRequestKey = null;

            elements.receiveForm.reset();


            await reloadPurchases();


            showPageMessage(
                `Goods received successfully. GRN: ${result.grnNumber || "Created"}. Inventory stock has been updated.`,
                "success"
            );


        } catch (error) {

            /*
             * Keep the request key.
             * Retrying the same request will not create duplicate receiving.
             */

            showReceiveMessage(error.message);

        } finally {

            isReceiving = false;

            elements.receiveButton.disabled = false;

            elements.receiveButton.textContent =
                "Confirm Receiving";

        }

    }


    function closeReceiveModal() {

        if (isReceiving) return;


        elements.receiveModal.hidden = true;

        currentReceivingPurchase = null;

        receiveRequestKey = null;

        elements.receiveForm.reset();

        clearReceiveMessage();

    }


    function createRequestKey() {

        const randomPart =
            window.crypto?.randomUUID?.() ||
            `${Date.now()}-${Math.random().toString(36).slice(2)}`;

        return `GRN-${currentReceivingPurchase.id}-${randomPart}`
            .slice(0, 80);

    }


    function getRemainingQuantity(item) {

        if (
            item.remainingQuantity !== null &&
            item.remainingQuantity !== undefined
        ) {

            return Math.max(
                0,
                Number(item.remainingQuantity)
            );

        }


        return Math.max(
            0,
            Number(item.quantity || 0) -
            Number(item.receivedQuantity || 0)
        );

    }


    /* ========================================
       PURCHASE DETAILS
    ======================================== */

    async function openDetailsModal(purchase) {

        elements.detailsModal.hidden = false;

        elements.detailsItemsBody.innerHTML = `
            <tr>
                <td colspan="6" class="table-loading">
                    Loading purchase details...
                </td>
            </tr>
        `;

        elements.receiptHistoryBody.innerHTML = `
            <tr>
                <td colspan="3" class="table-loading">
                    Loading receiving history...
                </td>
            </tr>
        `;


        try {

            const results = await Promise.allSettled([

                loadPurchaseById(purchase.id),

                apiRequest(
                    `${API.grn}/purchase/${purchase.id}`
                )

            ]);


            if (results[0].status !== "fulfilled") {
                throw results[0].reason;
            }


            const freshPurchase = results[0].value;

            const grns =
                results[1].status === "fulfilled" &&
                Array.isArray(results[1].value)
                    ? results[1].value
                    : [];


            renderPurchaseDetails(freshPurchase);

            renderReceiptHistory(grns);


            if (results[1].status === "rejected") {

                elements.receiptHistoryBody.innerHTML = `
                    <tr>
                        <td colspan="3" class="table-empty">
                            Unable to load receiving history.
                        </td>
                    </tr>
                `;
            }


        } catch (error) {

            elements.detailsItemsBody.innerHTML = `
                <tr>
                    <td colspan="6" class="table-empty">
                        ${escapeHTML(error.message)}
                    </td>
                </tr>
            `;

        }

    }


    function renderPurchaseDetails(purchase) {

        elements.detailsTitle.textContent =
            purchase.purchaseNumber || "Purchase Order";

        elements.detailsDescription.textContent =
            "Purchase order information and goods receiving progress.";

        elements.detailsSupplier.textContent =
            purchase.supplierName || "—";

        elements.detailsDate.textContent =
            formatDateTime(purchase.purchaseDate);

        elements.detailsNumber.textContent =
            purchase.purchaseNumber || "—";


        elements.detailsStatus.innerHTML = `

            <span class="status-badge ${getStatusClass(purchase.status)}">
                ${escapeHTML(formatStatus(purchase.status))}
            </span>

        `;


        const items = purchase.items || [];


        if (items.length === 0) {

            elements.detailsItemsBody.innerHTML = `
                <tr>
                    <td colspan="6" class="table-empty">
                        No purchase items.
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
                            ${Number(item.quantity || 0)}
                        </td>


                        <td>
                            ${Number(item.receivedQuantity || 0)}
                        </td>


                        <td>
                            ${getRemainingQuantity(item)}
                        </td>


                        <td>
                            ${formatCurrency(item.unitCost)}
                        </td>


                        <td class="items-price">
                            ${formatCurrency(item.subtotal)}
                        </td>

                    </tr>

                `)
                .join("");

        }


        elements.detailsTotal.textContent =
            formatCurrency(purchase.totalAmount);

    }


    function renderReceiptHistory(grns) {

        if (!Array.isArray(grns) || grns.length === 0) {

            elements.receiptHistoryBody.innerHTML = `
                <tr>
                    <td colspan="3" class="table-empty">
                        No goods received yet.
                    </td>
                </tr>
            `;

            return;
        }


        elements.receiptHistoryBody.innerHTML = grns
            .map(grn => {

                const products = (grn.items || [])
                    .map(item => {

                        return `${escapeHTML(item.productName)} × ${Number(item.quantity || 0)}`;

                    })
                    .join(", ");


                return `

                    <tr>

                        <td>
                            <strong>
                                ${escapeHTML(grn.grnNumber || "—")}
                            </strong>
                        </td>

                        <td>
                            ${formatDateTime(grn.receivedAt)}
                        </td>

                        <td>
                            ${products || "—"}
                        </td>

                    </tr>

                `;

            })
            .join("");

    }


    function closeDetailsModal() {

        elements.detailsModal.hidden = true;

    }


    /* ========================================
       RELOAD
    ======================================== */

    async function reloadPurchases() {

        purchases = await loadPurchases();

        currentPage = 0;

        updateSummary();

        renderPurchases();

    }


    /* ========================================
       MESSAGE
    ======================================== */

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


    function showReceiveMessage(message) {

        elements.receiveMessage.textContent = message;

        elements.receiveMessage.classList.add("show");

    }


    function clearReceiveMessage() {

        elements.receiveMessage.textContent = "";

        elements.receiveMessage.classList.remove("show");

    }


    /* ========================================
       FORMATTERS
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


    function formatStatus(status) {

        return String(status || "DRAFT")
            .replaceAll("_", " ");

    }


    function getStatusClass(status) {

        return String(status || "DRAFT")
            .toLowerCase()
            .replaceAll("_", "-");

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


})();
