
"use strict";

(() => {

    const PAGE_SIZE = 10;

    const API = {
        purchases: "/api/purchases",
        invoices: "/api/purchase-invoices",
        returns: "/api/purchase-returns"
    };

    let purchaseRecords = [];
    let currentPage = 0;
    let selectedPurchase = null;
    let isSubmitting = false;

    const elements = {

        receivedCount: document.getElementById("received-purchase-count"),
        returnCount: document.getElementById("purchase-return-count"),
        returnQuantity: document.getElementById("purchase-return-quantity"),
        returnValue: document.getElementById("purchase-return-value"),

        pageMessage: document.getElementById("page-message"),

        search: document.getElementById("return-search"),
        filter: document.getElementById("return-filter"),

        tableBody: document.getElementById("purchase-returns-table-body"),

        paginationInfo: document.getElementById("pagination-info"),
        currentPage: document.getElementById("current-page"),
        previousPage: document.getElementById("previous-page"),
        nextPage: document.getElementById("next-page"),

        refreshButton: document.getElementById("refresh-button"),

        returnModal: document.getElementById("return-modal"),
        returnForm: document.getElementById("purchase-return-form"),
        formMessage: document.getElementById("return-form-message"),
        itemsBody: document.getElementById("return-items-body"),
        reason: document.getElementById("return-reason"),
        submitButton: document.getElementById("submit-return-button"),

        purchaseNumber: document.getElementById("return-purchase-number"),
        supplierName: document.getElementById("return-supplier-name"),
        invoiceNumber: document.getElementById("return-invoice-number"),
        originalAmount: document.getElementById("return-original-amount"),

        selectedQuantity: document.getElementById("selected-return-quantity"),
        selectedValue: document.getElementById("selected-return-value"),

        historyModal: document.getElementById("history-modal"),
        historyDescription: document.getElementById("history-description"),
        historyBody: document.getElementById("history-table-body")
    };


    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>"']/g, character => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#039;"
        })[character]);
    }


    function money(value) {
        return "RM " + Number(value ?? 0).toLocaleString("en-MY", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }


    function formatDate(value) {
        if (!value) return "—";

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) return "—";

        return date.toLocaleDateString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        });
    }


    function showPageMessage(message, success = false) {
        elements.pageMessage.textContent = message;
        elements.pageMessage.className = "page-message show";

        if (success) {
            elements.pageMessage.classList.add("success");
        }

        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });
    }


    function clearPageMessage() {
        elements.pageMessage.textContent = "";
        elements.pageMessage.className = "page-message";
    }


    function showFormMessage(message) {
        elements.formMessage.textContent = message;
        elements.formMessage.classList.add("show");
    }


    function clearFormMessage() {
        elements.formMessage.textContent = "";
        elements.formMessage.classList.remove("show");
    }


    async function apiRequest(url, options = {}) {

        const response = await fetch(url, {
            ...options,
            headers: {
                Accept: "application/json",
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
                data?.detail ||
                (typeof data === "string" ? data : null) ||
                response.statusText ||
                "Request failed";

            throw new Error(`${response.status}: ${message}`);
        }

        return data;
    }


    async function loadReturnsForPurchase(purchaseId) {

        const result = await apiRequest(
            `${API.returns}/purchase/${purchaseId}`
        );

        return Array.isArray(result) ? result : [];
    }


    async function loadData() {

        clearPageMessage();

        elements.refreshButton.disabled = true;

        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="8" class="table-loading">
                    Loading purchases...
                </td>
            </tr>
        `;

        try {

            const [purchases, invoices] = await Promise.all([
                apiRequest(API.purchases),
                apiRequest(API.invoices)
            ]);

            if (!Array.isArray(purchases) || !Array.isArray(invoices)) {
                throw new Error("Unexpected purchase or invoice response.");
            }

            const invoiceMap = new Map();

            invoices.forEach(invoice => {
                if (invoice.purchaseId != null) {
                    invoiceMap.set(Number(invoice.purchaseId), invoice);
                }
            });

            const eligiblePurchases = purchases.filter(purchase =>
                purchase.status === "RECEIVED"
                && invoiceMap.has(Number(purchase.id))
            );

            const results = await Promise.all(
                eligiblePurchases.map(async purchase => {

                    const returns = await loadReturnsForPurchase(purchase.id);

                    const returnedByItem = new Map();

                    returns.forEach(returnRecord => {
                        (returnRecord.items || []).forEach(item => {

                            const itemId = Number(item.purchaseItemId);

                            returnedByItem.set(
                                itemId,
                                (returnedByItem.get(itemId) || 0)
                                + Number(item.quantity || 0)
                            );
                        });
                    });

                    return {
                        ...purchase,
                        invoice: invoiceMap.get(Number(purchase.id)),
                        returns,
                        returnedByItem
                    };
                })
            );

            purchaseRecords = results.sort((a, b) =>
                new Date(b.purchaseDate || 0)
                - new Date(a.purchaseDate || 0)
            );

            currentPage = 0;

            updateSummary();
            renderTable();

        } catch (error) {

            console.error("Unable to load purchase returns:", error);

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        Unable to load purchase records.
                    </td>
                </tr>
            `;

            showPageMessage(error.message);

        } finally {
            elements.refreshButton.disabled = false;
        }
    }


    function getItemReceivedQuantity(item) {
        return Number(item.receivedQuantity ?? 0);
    }


    function getItemReturnedQuantity(purchase, item) {
        return Number(
            purchase.returnedByItem.get(Number(item.id)) || 0
        );
    }


    function getItemRemainingQuantity(purchase, item) {
        return Math.max(
            0,
            getItemReceivedQuantity(item)
            - getItemReturnedQuantity(purchase, item)
        );
    }


    function getReturnStatus(purchase) {

        const items = purchase.items || [];

        if (items.length === 0) {
            return "NOT_RETURNED";
        }

        let received = 0;
        let returned = 0;

        items.forEach(item => {
            received += getItemReceivedQuantity(item);
            returned += Math.min(
                getItemReturnedQuantity(purchase, item),
                getItemReceivedQuantity(item)
            );
        });

        if (returned <= 0) {
            return "NOT_RETURNED";
        }

        if (returned >= received) {
            return "RETURNED";
        }

        return "PARTIAL";
    }


    function getTotalReceived(purchase) {
        return (purchase.items || []).reduce(
            (sum, item) => sum + getItemReceivedQuantity(item),
            0
        );
    }


    function getTotalReturned(purchase) {
        return (purchase.items || []).reduce(
            (sum, item) => sum + getItemReturnedQuantity(purchase, item),
            0
        );
    }


    function updateSummary() {

        let totalReturns = 0;
        let totalQuantity = 0;
        let totalValue = 0;

        purchaseRecords.forEach(purchase => {

            totalReturns += purchase.returns.length;

            purchase.returns.forEach(record => {

                totalValue += Number(record.totalAmount || 0);

                (record.items || []).forEach(item => {
                    totalQuantity += Number(item.quantity || 0);
                });
            });
        });

        elements.receivedCount.textContent =
            purchaseRecords.length.toLocaleString();

        elements.returnCount.textContent =
            totalReturns.toLocaleString();

        elements.returnQuantity.textContent =
            totalQuantity.toLocaleString();

        elements.returnValue.textContent = money(totalValue);
    }


    function getFilteredPurchases() {

        const keyword = elements.search.value.trim().toLowerCase();
        const filter = elements.filter.value;

        return purchaseRecords.filter(purchase => {

            const status = getReturnStatus(purchase);

            if (filter !== "ALL" && status !== filter) {
                return false;
            }

            if (!keyword) {
                return true;
            }

            const searchable = [
                purchase.purchaseNumber,
                purchase.supplierName,
                purchase.invoice?.invoiceNumber
            ].join(" ").toLowerCase();

            return searchable.includes(keyword);
        });
    }


    function getStatusLabel(status) {

        if (status === "RETURNED") return "Fully Returned";
        if (status === "PARTIAL") return "Partially Returned";

        return "Not Returned";
    }


    function getStatusClass(status) {

        if (status === "RETURNED") return "returned";
        if (status === "PARTIAL") return "partial";

        return "not-returned";
    }


    function renderTable() {

        const filtered = getFilteredPurchases();

        const totalPages = Math.max(
            1,
            Math.ceil(filtered.length / PAGE_SIZE)
        );

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        const start = currentPage * PAGE_SIZE;

        const pageRecords = filtered.slice(
            start,
            start + PAGE_SIZE
        );

        if (pageRecords.length === 0) {

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        No received purchases found.
                    </td>
                </tr>
            `;

        } else {

            elements.tableBody.innerHTML = pageRecords.map(purchase => {

                const status = getReturnStatus(purchase);

                const returned = getTotalReturned(purchase);
                const received = getTotalReceived(purchase);

                return `
                    <tr>

                        <td>
                            <div class="return-purchase-number">
                                <strong>${escapeHtml(purchase.purchaseNumber)}</strong>
                                <small>${escapeHtml(purchase.items?.length || 0)} product lines</small>
                            </div>
                        </td>

                        <td>
                            <div class="supplier-cell">
                                <strong>${escapeHtml(purchase.supplierName)}</strong>
                            </div>
                        </td>

                        <td>${formatDate(purchase.purchaseDate)}</td>

                        <td>
                            <span class="return-invoice">
                                ${escapeHtml(purchase.invoice?.invoiceNumber || "—")}
                            </span>
                        </td>

                        <td>
                            <div class="receiving-progress">
                                <strong>${returned} / ${received}</strong>
                                <small>Returned / Received</small>
                            </div>
                        </td>

                        <td>
                            <span class="purchase-return-status ${getStatusClass(status)}">
                                ${getStatusLabel(status)}
                            </span>
                        </td>

                        <td class="amount-cell">
                            ${money(purchase.totalAmount)}
                        </td>

                        <td>
                            <div class="purchase-return-actions">

                                <button type="button"
                                        class="purchase-return-action create"
                                        data-action="create"
                                        data-purchase-id="${purchase.id}"
                                        ${status === "RETURNED" ? "disabled" : ""}>
                                    Return
                                </button>

                                <button type="button"
                                        class="purchase-return-action"
                                        data-action="history"
                                        data-purchase-id="${purchase.id}">
                                    History
                                </button>

                            </div>
                        </td>

                    </tr>
                `;

            }).join("");
        }

        const first = filtered.length === 0 ? 0 : start + 1;
        const last = Math.min(start + PAGE_SIZE, filtered.length);

        elements.paginationInfo.textContent =
            `Showing ${first}-${last} of ${filtered.length} purchases`;

        elements.currentPage.textContent = String(currentPage + 1);

        elements.previousPage.disabled = currentPage <= 0;
        elements.nextPage.disabled = currentPage >= totalPages - 1;
    }


    function openReturnModal(purchase) {

        selectedPurchase = purchase;

        clearFormMessage();

        elements.returnForm.reset();

        elements.purchaseNumber.textContent =
            purchase.purchaseNumber || "—";

        elements.supplierName.textContent =
            purchase.supplierName || "—";

        elements.invoiceNumber.textContent =
            purchase.invoice?.invoiceNumber || "—";

        elements.originalAmount.textContent =
            money(purchase.totalAmount);

        renderReturnItems();
        updateReturnTotal();

        elements.returnModal.hidden = false;
        document.body.style.overflow = "hidden";
    }


    function renderReturnItems() {

        if (!selectedPurchase) return;

        const items = selectedPurchase.items || [];

        if (items.length === 0) {

            elements.itemsBody.innerHTML = `
                <tr>
                    <td colspan="7" class="return-empty">
                        No purchase items found.
                    </td>
                </tr>
            `;

            return;
        }

        elements.itemsBody.innerHTML = items.map(item => {

            const ordered = Number(item.quantity || 0);
            const received = getItemReceivedQuantity(item);
            const returned = getItemReturnedQuantity(selectedPurchase, item);
            const remaining = getItemRemainingQuantity(selectedPurchase, item);
            const unitCost = Number(item.unitCost || 0);

            return `
                <tr>

                    <td>
                        <div class="return-product-cell">
                            <strong>${escapeHtml(item.productName)}</strong>
                            <small>${escapeHtml(item.productCode || "—")}</small>
                        </div>
                    </td>

                    <td>${ordered}</td>

                    <td>${received}</td>

                    <td>${returned}</td>

                    <td>
                        <strong class="return-remaining ${remaining === 0 ? "zero" : ""}">
                            ${remaining}
                        </strong>
                    </td>

                    <td class="items-price">
                        ${money(unitCost)}
                    </td>

                    <td>
                        <input type="number"
                               class="return-quantity"
                               data-purchase-item-id="${item.id}"
                               data-unit-cost="${unitCost}"
                               data-remaining="${remaining}"
                               min="0"
                               max="${remaining}"
                               step="1"
                               value="0"
                               ${remaining === 0 ? "disabled" : ""}
                               aria-label="Return quantity for ${escapeHtml(item.productName)}">
                    </td>

                </tr>
            `;

        }).join("");
    }


    function getSelectedItems() {

        const inputs = elements.itemsBody.querySelectorAll(
            ".return-quantity"
        );

        const result = [];

        inputs.forEach(input => {

            const quantity = Number(input.value || 0);

            if (quantity <= 0) return;

            result.push({
                purchaseItemId: Number(input.dataset.purchaseItemId),
                quantity,
                unitCost: Number(input.dataset.unitCost),
                remaining: Number(input.dataset.remaining)
            });
        });

        return result;
    }


    function updateReturnTotal() {

        const items = getSelectedItems();

        let quantity = 0;
        let value = 0;

        items.forEach(item => {
            quantity += item.quantity;
            value += item.quantity * item.unitCost;
        });

        elements.selectedQuantity.textContent =
            quantity.toLocaleString();

        elements.selectedValue.textContent = money(value);
    }


    function closeReturnModal() {

        elements.returnModal.hidden = true;
        document.body.style.overflow = "";

        selectedPurchase = null;
    }


    function openHistoryModal(purchase) {

        const returns = purchase.returns || [];

        elements.historyDescription.textContent =
            `${purchase.purchaseNumber} · ${purchase.supplierName}`;

        if (returns.length === 0) {

            elements.historyBody.innerHTML = `
                <tr>
                    <td colspan="6" class="return-empty">
                        No return history for this purchase.
                    </td>
                </tr>
            `;

        } else {

            elements.historyBody.innerHTML = returns.map(record => {

                const quantity = (record.items || []).reduce(
                    (sum, item) => sum + Number(item.quantity || 0),
                    0
                );

                return `
                    <tr>

                        <td>
                            <strong class="history-return-number">
                                ${escapeHtml(record.returnNumber)}
                            </strong>
                        </td>

                        <td>${formatDate(record.returnDate)}</td>

                        <td>${escapeHtml(record.invoiceNumber || "—")}</td>

                        <td>
                            <div class="history-reason">
                                ${escapeHtml(record.reason || "—")}
                            </div>
                        </td>

                        <td>${quantity}</td>

                        <td class="amount-cell">
                            ${money(record.totalAmount)}
                        </td>

                    </tr>
                `;

            }).join("");
        }

        elements.historyModal.hidden = false;
        document.body.style.overflow = "hidden";
    }


    function closeHistoryModal() {

        elements.historyModal.hidden = true;
        document.body.style.overflow = "";
    }


    function createIdempotencyKey() {

        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return `PR-${window.crypto.randomUUID()}`;
        }

        return `PR-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }


    async function submitReturn(event) {

        event.preventDefault();

        if (isSubmitting || !selectedPurchase) {
            return;
        }

        clearFormMessage();

        const reason = elements.reason.value.trim();
        const items = getSelectedItems();

        if (!reason) {
            showFormMessage("Please enter a return reason.");
            elements.reason.focus();
            return;
        }

        if (reason.length > 500) {
            showFormMessage("Return reason cannot exceed 500 characters.");
            return;
        }

        if (items.length === 0) {
            showFormMessage("Please select at least one item to return.");
            return;
        }

        for (const item of items) {

            if (!Number.isInteger(item.quantity) || item.quantity < 1) {
                showFormMessage("Return quantity must be a whole number greater than zero.");
                return;
            }

            if (item.quantity > item.remaining) {
                showFormMessage("Return quantity cannot exceed the remaining received quantity.");
                return;
            }
        }

        const requestBody = {
            purchaseId: Number(selectedPurchase.id),
            reason,
            items: items.map(item => ({
                purchaseItemId: item.purchaseItemId,
                quantity: item.quantity
            }))
        };

        isSubmitting = true;

        elements.submitButton.disabled = true;
        elements.submitButton.textContent = "Submitting...";

        try {

            await apiRequest(API.returns, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Idempotency-Key": createIdempotencyKey()
                },
                body: JSON.stringify(requestBody)
            });

            closeReturnModal();

            await loadData();

            showPageMessage(
                "Purchase return created successfully. Inventory and supplier credit have been updated.",
                true
            );

        } catch (error) {

            console.error("Unable to create purchase return:", error);

            showFormMessage(error.message);

        } finally {

            isSubmitting = false;

            elements.submitButton.disabled = false;
            elements.submitButton.textContent = "Submit Purchase Return";
        }
    }


    function bindEvents() {

        elements.refreshButton.addEventListener("click", loadData);

        elements.search.addEventListener("input", () => {
            currentPage = 0;
            renderTable();
        });

        elements.filter.addEventListener("change", () => {
            currentPage = 0;
            renderTable();
        });

        elements.previousPage.addEventListener("click", () => {

            if (currentPage > 0) {
                currentPage--;
                renderTable();
            }
        });

        elements.nextPage.addEventListener("click", () => {

            const totalPages = Math.max(
                1,
                Math.ceil(getFilteredPurchases().length / PAGE_SIZE)
            );

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderTable();
            }
        });


        elements.tableBody.addEventListener("click", event => {

            const button = event.target.closest("button[data-action]");

            if (!button || button.disabled) return;

            const purchaseId = Number(button.dataset.purchaseId);

            const purchase = purchaseRecords.find(
                record => Number(record.id) === purchaseId
            );

            if (!purchase) return;

            if (button.dataset.action === "create") {
                openReturnModal(purchase);
            }

            if (button.dataset.action === "history") {
                openHistoryModal(purchase);
            }
        });


        elements.itemsBody.addEventListener("input", event => {

            const input = event.target.closest(".return-quantity");

            if (!input) return;

            const maximum = Number(input.dataset.remaining || 0);

            let quantity = Number(input.value || 0);

            if (quantity < 0) quantity = 0;
            if (quantity > maximum) quantity = maximum;
            if (!Number.isInteger(quantity)) {
                quantity = Math.floor(quantity);
            }

            input.value = String(quantity);

            updateReturnTotal();
        });


        elements.returnForm.addEventListener("submit", submitReturn);


        document.getElementById("close-return-modal")
            .addEventListener("click", closeReturnModal);

        document.getElementById("cancel-return-button")
            .addEventListener("click", closeReturnModal);

        document.querySelectorAll("[data-close-return]")
            .forEach(element => {
                element.addEventListener("click", closeReturnModal);
            });


        document.getElementById("close-history-modal")
            .addEventListener("click", closeHistoryModal);

        document.getElementById("close-history-button")
            .addEventListener("click", closeHistoryModal);

        document.querySelectorAll("[data-close-history]")
            .forEach(element => {
                element.addEventListener("click", closeHistoryModal);
            });


        document.addEventListener("keydown", event => {

            if (event.key !== "Escape") return;

            if (!elements.returnModal.hidden) {
                closeReturnModal();
            }

            if (!elements.historyModal.hidden) {
                closeHistoryModal();
            }
        });
    }


    function initialize() {
        bindEvents();
        loadData();
    }


    initialize();

})();
