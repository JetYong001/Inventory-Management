
"use strict";

const STOCK_PAGE_SIZE = 10;

let stockItems = [];
let lowStockItems = [];
let currentPage = 0;
let isSavingAdjustment = false;

const stockElements = {
    totalProducts: document.getElementById("total-products"),
    totalStockQuantity: document.getElementById("total-stock-quantity"),
    totalInventoryValue: document.getElementById("total-inventory-value"),
    lowStockCount: document.getElementById("low-stock-count"),
    outOfStockCount: document.getElementById("out-of-stock-count"),

    pageMessage: document.getElementById("page-message"),

    search: document.getElementById("stock-search"),
    statusFilter: document.getElementById("stock-status-filter"),
    tableBody: document.getElementById("stock-table-body"),

    paginationInfo: document.getElementById("pagination-info"),
    currentPage: document.getElementById("current-page"),
    previousPage: document.getElementById("previous-page"),
    nextPage: document.getElementById("next-page"),

    adjustButton: document.getElementById("adjust-stock-button"),
    adjustmentModal: document.getElementById("adjustment-modal"),
    adjustmentForm: document.getElementById("adjustment-form"),
    adjustmentMessage: document.getElementById("adjustment-message"),
    adjustmentProduct: document.getElementById("adjustment-product"),
    adjustmentCurrentStock: document.getElementById("adjustment-current-stock"),
    adjustmentType: document.getElementById("adjustment-type"),
    adjustmentQuantity: document.getElementById("adjustment-quantity"),
    adjustmentRemarks: document.getElementById("adjustment-remarks"),
    saveAdjustment: document.getElementById("save-adjustment"),
    closeAdjustment: document.getElementById("close-adjustment"),
    cancelAdjustment: document.getElementById("cancel-adjustment"),

    historyModal: document.getElementById("history-modal"),
    historyTitle: document.getElementById("history-title"),
    historyDescription: document.getElementById("history-description"),
    historyProductName: document.getElementById("history-product-name"),
    historyProductCode: document.getElementById("history-product-code"),
    historyTableBody: document.getElementById("history-table-body"),
    closeHistory: document.getElementById("close-history"),
    closeHistoryButton: document.getElementById("close-history-button")
};


/* ========================================
   INITIALIZATION
======================================== */

document.addEventListener("DOMContentLoaded", initializeStock);

async function initializeStock() {
    bindStockEvents();

    stockElements.tableBody.innerHTML = `
        <tr>
            <td colspan="7" class="table-loading">
                Loading stock...
            </td>
        </tr>
    `;

    const results = await Promise.allSettled([
        loadStockItems(),
        loadStockSummary(),
        loadLowStockItems()
    ]);

    if (results[0].status === "rejected") {
        showStockPageMessage(
            `Unable to load stock: ${results[0].reason.message}`
        );
    }

    if (results[1].status === "rejected") {
        showStockPageMessage(
            `Unable to load inventory summary: ${results[1].reason.message}`
        );
    }

    if (results[2].status === "rejected") {
        lowStockItems = [];
        showStockPageMessage(
            `Unable to load low stock records: ${results[2].reason.message}`
        );
    }

    populateAdjustmentProducts();
    renderStockTable();
}


/* ========================================
   API
======================================== */

async function loadStockItems() {
    const result = await apiGet("/api/inventory/stock");
    stockItems = normalizeArray(result);
}

async function loadStockSummary() {
    const summary = await apiGet("/api/inventory/summary");

    stockElements.totalProducts.textContent =
        formatNumber(summary.totalProducts);

    stockElements.totalStockQuantity.textContent =
        formatNumber(summary.totalStockQuantity);

    stockElements.totalInventoryValue.textContent =
        formatCurrency(summary.totalInventoryValue);

    stockElements.lowStockCount.textContent =
        formatNumber(summary.lowStockCount);

    stockElements.outOfStockCount.textContent =
        formatNumber(summary.outOfStockCount);
}

async function loadLowStockItems() {
    const result = await apiGet("/api/inventory/low-stock");
    lowStockItems = normalizeArray(result);
}


/* ========================================
   EVENTS
======================================== */

function bindStockEvents() {
    stockElements.search.addEventListener("input", () => {
        currentPage = 0;
        renderStockTable();
    });

    stockElements.statusFilter.addEventListener("change", () => {
        currentPage = 0;
        renderStockTable();
    });

    stockElements.previousPage.addEventListener("click", () => {
        if (currentPage > 0) {
            currentPage--;
            renderStockTable();
        }
    });

    stockElements.nextPage.addEventListener("click", () => {
        const totalPages = Math.ceil(
            getFilteredStock().length / STOCK_PAGE_SIZE
        );

        if (currentPage < totalPages - 1) {
            currentPage++;
            renderStockTable();
        }
    });

    stockElements.adjustButton.addEventListener("click", () => {
        openAdjustmentModal();
    });

    stockElements.closeAdjustment.addEventListener("click", closeAdjustmentModal);
    stockElements.cancelAdjustment.addEventListener("click", closeAdjustmentModal);

    stockElements.adjustmentModal.addEventListener("click", event => {
        if (event.target.matches("[data-close-adjustment]")) {
            closeAdjustmentModal();
        }
    });

    stockElements.adjustmentForm.addEventListener(
        "submit",
        saveStockAdjustment
    );

    stockElements.adjustmentProduct.addEventListener("change", updateCurrentStock);

    stockElements.tableBody.addEventListener("click", handleStockTableAction);

    stockElements.closeHistory.addEventListener("click", closeHistoryModal);
    stockElements.closeHistoryButton.addEventListener("click", closeHistoryModal);

    stockElements.historyModal.addEventListener("click", event => {
        if (event.target.matches("[data-close-history]")) {
            closeHistoryModal();
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") {
            return;
        }

        if (!stockElements.adjustmentModal.hidden) {
            closeAdjustmentModal();
        }

        if (!stockElements.historyModal.hidden) {
            closeHistoryModal();
        }
    });
}


/* ========================================
   FILTERING
======================================== */

function getFilteredStock() {
    const keyword = stockElements.search.value.trim().toLowerCase();
    const status = stockElements.statusFilter.value;

    return stockItems.filter(item => {
        const matchesKeyword =
            !keyword ||
            String(item.productName || "").toLowerCase().includes(keyword) ||
            String(item.productCode || "").toLowerCase().includes(keyword);

        const matchesStatus =
            status === "all" ||
            item.stockStatus === status;

        return matchesKeyword && matchesStatus;
    });
}


/* ========================================
   TABLE
======================================== */

function renderStockTable() {
    const filteredItems = getFilteredStock();

    const totalItems = filteredItems.length;
    const totalPages = Math.ceil(totalItems / STOCK_PAGE_SIZE);

    if (totalPages > 0 && currentPage >= totalPages) {
        currentPage = totalPages - 1;
    }

    const startIndex = currentPage * STOCK_PAGE_SIZE;

    const visibleItems = filteredItems.slice(
        startIndex,
        startIndex + STOCK_PAGE_SIZE
    );

    if (visibleItems.length === 0) {
        stockElements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-empty">
                    No stock records found.
                </td>
            </tr>
        `;
    } else {
        stockElements.tableBody.innerHTML = visibleItems
            .map(renderStockRow)
            .join("");
    }

    const firstItem = totalItems === 0 ? 0 : startIndex + 1;
    const lastItem = Math.min(
        startIndex + STOCK_PAGE_SIZE,
        totalItems
    );

    stockElements.paginationInfo.textContent =
        `Showing ${firstItem}-${lastItem} of ${totalItems} products`;

    stockElements.currentPage.textContent =
        totalPages === 0
            ? "0"
            : `${currentPage + 1} / ${totalPages}`;

    stockElements.previousPage.disabled = currentPage === 0;

    stockElements.nextPage.disabled =
        totalPages === 0 || currentPage >= totalPages - 1;
}


function renderStockRow(item) {
    const quantity = Number(item.currentQuantity || 0);
    const minimumStock = Number(item.minimumStock || 0);

    const stockStatus = getStockStatus(item.stockStatus);

    return `
        <tr>
            <td>
                <div class="stock-product-cell">
                    <strong>${escapeHTML(item.productName)}</strong>
                    <span>${escapeHTML(item.productCode || "-")}</span>
                </div>
            </td>

            <td class="quantity-cell">
                ${formatNumber(quantity)}
            </td>

            <td class="minimum-cell">
                ${formatNumber(minimumStock)}
            </td>

            <td class="price-cell">
                ${formatCurrency(item.costPrice)}
            </td>

            <td class="price-cell">
                ${formatCurrency(item.stockValue)}
            </td>

            <td>
                <span class="stock-status ${stockStatus.className}">
                    ${stockStatus.label}
                </span>
            </td>

            <td>
                <div class="action-buttons">
                    <button type="button"
                            class="table-action"
                            data-action="history"
                            data-id="${Number(item.productId)}">
                        History
                    </button>

                    <button type="button"
                            class="table-action adjust"
                            data-action="adjust"
                            data-id="${Number(item.productId)}">
                        Adjust
                    </button>
                </div>
            </td>
        </tr>
    `;
}


function getStockStatus(status) {
    if (status === "OUT_OF_STOCK") {
        return {
            label: "Out of Stock",
            className: "out"
        };
    }

    if (status === "LOW_STOCK") {
        return {
            label: "Low Stock",
            className: "low"
        };
    }

    return {
        label: "In Stock",
        className: "normal"
    };
}


/* ========================================
   ADJUSTMENT PRODUCT SELECT
======================================== */

function populateAdjustmentProducts() {
    const options = stockItems
        .map(item => `
            <option value="${Number(item.productId)}">
                ${escapeHTML(item.productCode)} - ${escapeHTML(item.productName)}
            </option>
        `)
        .join("");

    stockElements.adjustmentProduct.innerHTML = `
        <option value="">Select product</option>
        ${options}
    `;
}


function updateCurrentStock() {
    const productId = Number(stockElements.adjustmentProduct.value);

    const item = stockItems.find(
        stock => Number(stock.productId) === productId
    );

    stockElements.adjustmentCurrentStock.value = item
        ? formatNumber(item.currentQuantity)
        : "—";
}


/* ========================================
   OPEN / CLOSE ADJUSTMENT
======================================== */

function openAdjustmentModal(productId = null) {
    stockElements.adjustmentForm.reset();

    hideAdjustmentMessage();

    stockElements.adjustmentCurrentStock.value = "—";
    stockElements.adjustmentType.value = "IN";
    stockElements.adjustmentModal.hidden = false;

    if (productId !== null) {
        stockElements.adjustmentProduct.value = String(productId);
        updateCurrentStock();
    }

    stockElements.adjustmentProduct.focus();
}


function closeAdjustmentModal() {
    if (isSavingAdjustment) {
        return;
    }

    stockElements.adjustmentModal.hidden = true;
    stockElements.adjustmentForm.reset();
    hideAdjustmentMessage();
}


/* ========================================
   SAVE ADJUSTMENT
======================================== */

async function saveStockAdjustment(event) {
    event.preventDefault();

    if (isSavingAdjustment) {
        return;
    }

    const payload = {
        productId: Number(stockElements.adjustmentProduct.value),
        quantity: Number(stockElements.adjustmentQuantity.value),
        adjustmentType: stockElements.adjustmentType.value,
        remarks: stockElements.adjustmentRemarks.value.trim()
    };

    if (
        !payload.productId ||
        !Number.isInteger(payload.quantity) ||
        payload.quantity < 1
    ) {
        showAdjustmentMessage(
            "Please select a product and enter a valid quantity."
        );
        return;
    }

    const selectedProduct = stockItems.find(
        item => Number(item.productId) === payload.productId
    );

    if (!selectedProduct) {
        showAdjustmentMessage("Selected product could not be found.");
        return;
    }

    const currentQuantity = Number(selectedProduct.currentQuantity || 0);

    if (
        payload.adjustmentType === "OUT" &&
        payload.quantity > currentQuantity
    ) {
        showAdjustmentMessage(
            "Adjustment quantity cannot exceed the current stock."
        );
        return;
    }

    isSavingAdjustment = true;
    stockElements.saveAdjustment.disabled = true;
    stockElements.saveAdjustment.textContent = "Saving...";

    try {
        await apiPost("/api/inventory/adjust", payload);

        closeAdjustmentAfterSave();

        await Promise.all([
            loadStockItems(),
            loadStockSummary(),
            loadLowStockItems()
        ]);

        populateAdjustmentProducts();
        renderStockTable();

        showStockPageMessage("Stock adjustment recorded successfully.");

    } catch (error) {
        showAdjustmentMessage(error.message);

    } finally {
        isSavingAdjustment = false;
        stockElements.saveAdjustment.disabled = false;
        stockElements.saveAdjustment.textContent = "Save Adjustment";
    }
}


function closeAdjustmentAfterSave() {
    stockElements.adjustmentModal.hidden = true;
    stockElements.adjustmentForm.reset();
    hideAdjustmentMessage();
}


/* ========================================
   TABLE ACTIONS
======================================== */

async function handleStockTableAction(event) {
    const button = event.target.closest("[data-action]");

    if (!button) {
        return;
    }

    const productId = Number(button.dataset.id);
    const action = button.dataset.action;

    if (action === "adjust") {
        openAdjustmentModal(productId);
        return;
    }

    if (action === "history") {
        await openHistoryModal(productId);
    }
}


/* ========================================
   STOCK HISTORY
======================================== */

async function openHistoryModal(productId) {
    const item = stockItems.find(
        stock => Number(stock.productId) === productId
    );

    if (!item) {
        showStockPageMessage("Product could not be found.");
        return;
    }

    stockElements.historyTitle.textContent = "Stock History";

    stockElements.historyDescription.textContent =
        "View all recorded stock movements for this product.";

    stockElements.historyProductName.textContent =
        item.productName || "—";

    stockElements.historyProductCode.textContent =
        item.productCode || "—";

    stockElements.historyTableBody.innerHTML = `
        <tr>
            <td colspan="5" class="table-loading">
                Loading history...
            </td>
        </tr>
    `;

    stockElements.historyModal.hidden = false;

    try {
        const result = await apiGet(
            `/api/inventory/history/${productId}`
        );

        const movements = normalizeArray(result);

        renderHistoryTable(movements);

    } catch (error) {
        stockElements.historyTableBody.innerHTML = `
            <tr>
                <td colspan="5" class="table-empty">
                    Unable to load history: ${escapeHTML(error.message)}
                </td>
            </tr>
        `;
    }
}


function renderHistoryTable(movements) {
    if (movements.length === 0) {
        stockElements.historyTableBody.innerHTML = `
            <tr>
                <td colspan="5" class="table-empty">
                    No stock movement records found.
                </td>
            </tr>
        `;
        return;
    }

    stockElements.historyTableBody.innerHTML = movements
        .map(movement => {
            const quantityChange = Number(movement.quantityChange || 0);
            const isIncoming = quantityChange > 0;

            const movementClass = isIncoming ? "in" : "out";

            const movementLabel = String(movement.movementType || "")
                .replaceAll("_", " ");

            const quantityText = isIncoming
                ? `+${formatNumber(quantityChange)}`
                : formatNumber(quantityChange);

            return `
                <tr>
                    <td>${formatDate(movement.createdAt)}</td>

                    <td>
                        <span class="movement-type ${movementClass}">
                            ${escapeHTML(movementLabel)}
                        </span>
                    </td>

                    <td>
                        ${escapeHTML(movement.referenceNumber || "-")}
                    </td>

                    <td class="movement-quantity ${movementClass}">
                        ${quantityText}
                    </td>

                    <td>
                        ${formatNumber(movement.quantityAfter)}
                    </td>
                </tr>
            `;
        })
        .join("");
}


function closeHistoryModal() {
    stockElements.historyModal.hidden = true;
}


/* ========================================
   MESSAGES
======================================== */

function showStockPageMessage(message) {
    stockElements.pageMessage.textContent = message;
    stockElements.pageMessage.classList.add("show");
}

function showAdjustmentMessage(message) {
    stockElements.adjustmentMessage.textContent = message;
    stockElements.adjustmentMessage.classList.add("show");
}

function hideAdjustmentMessage() {
    stockElements.adjustmentMessage.textContent = "";
    stockElements.adjustmentMessage.classList.remove("show");
}
