
"use strict";

(() => {

    const PAGE_SIZE = 10;

    const API = {
        sales: "/api/sales",
        salesInvoices: "/api/sales-invoices",
        salesReturns: "/api/sales-returns"
    };

    let salesRecords = [];
    let currentPage = 0;

    let selectedSales = null;
    let selectedSalesReturns = [];

    let isSubmitting = false;

    const elements = {
        completedSalesCount: document.getElementById("completed-sales-count"),
        totalReturnsCount: document.getElementById("total-returns-count"),
        returnedQuantity: document.getElementById("returned-quantity"),
        returnAmount: document.getElementById("return-amount"),

        tableBody: document.getElementById("sales-returns-table-body"),
        search: document.getElementById("return-search"),
        filter: document.getElementById("return-filter"),

        paginationInfo: document.getElementById("pagination-info"),
        currentPage: document.getElementById("current-page"),
        previousPage: document.getElementById("previous-page"),
        nextPage: document.getElementById("next-page"),

        pageMessage: document.getElementById("page-message"),
        refreshButton: document.getElementById("refresh-button"),

        returnModal: document.getElementById("return-modal"),
        returnForm: document.getElementById("sales-return-form"),
        returnFormMessage: document.getElementById("return-form-message"),
        returnItemsBody: document.getElementById("return-items-body"),
        returnReason: document.getElementById("return-reason"),
        submitReturnButton: document.getElementById("submit-return-button"),

        returnSalesNumber: document.getElementById("return-sales-number"),
        returnCustomerName: document.getElementById("return-customer-name"),
        returnInvoiceNumber: document.getElementById("return-invoice-number"),
        returnSalesAmount: document.getElementById("return-sales-amount"),

        selectedReturnQuantity: document.getElementById("selected-return-quantity"),
        selectedReturnAmount: document.getElementById("selected-return-amount"),

        historyModal: document.getElementById("return-history-modal"),
        historyDescription: document.getElementById("history-modal-description"),
        historyBody: document.getElementById("return-history-body")
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
        const amount = Number(value ?? 0);

        return "RM " + amount.toLocaleString("en-MY", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }


    function formatDate(value) {
        if (!value) {
            return "—";
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return "—";
        }

        return date.toLocaleDateString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        });
    }


    function showMessage(message, type = "error") {
        elements.pageMessage.textContent = message;
        elements.pageMessage.className = "page-message show";

        if (type === "success") {
            elements.pageMessage.classList.add("success");
        }

        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });
    }


    function clearMessage() {
        elements.pageMessage.textContent = "";
        elements.pageMessage.className = "page-message";
    }


    function showFormMessage(message) {
        elements.returnFormMessage.textContent = message;
        elements.returnFormMessage.classList.add("show");
    }


    function clearFormMessage() {
        elements.returnFormMessage.textContent = "";
        elements.returnFormMessage.classList.remove("show");
    }


    async function apiRequest(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                ...(options.headers || {})
            }
        });

        const text = await response.text();

        let data = null;

        if (text) {
            try {
                data = JSON.parse(text);
            } catch {
                data = text;
            }
        }

        if (!response.ok) {
            let message = "Request failed.";

            if (typeof data === "string") {
                message = data;
            } else if (data) {
                message =
                    data.message ||
                    data.error ||
                    data.detail ||
                    data.title ||
                    message;
            }

            throw new Error(message);
        }

        return data;
    }


    async function loadSalesReturns(salesId) {
        try {
            const result = await apiRequest(
                `${API.salesReturns}/sales/${salesId}`
            );

            return Array.isArray(result) ? result : [];

        } catch (error) {
            console.error(
                `Unable to load sales returns for sales ID ${salesId}:`,
                error
            );

            throw error;
        }
    }


    async function loadData() {
        clearMessage();

        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="8" class="table-loading">
                    Loading sales records...
                </td>
            </tr>
        `;

        elements.refreshButton.disabled = true;

        try {

            const [sales, invoices] = await Promise.all([
                apiRequest(API.sales),
                apiRequest(API.salesInvoices)
            ]);

            if (!Array.isArray(sales) || !Array.isArray(invoices)) {
                throw new Error("Unexpected sales or invoice response.");
            }

            const invoiceMap = new Map();

            invoices.forEach(invoice => {
                if (invoice.salesId != null) {
                    invoiceMap.set(Number(invoice.salesId), invoice);
                }
            });

            const completedSales = sales.filter(sale =>
                sale.status === "COMPLETED"
                && invoiceMap.has(Number(sale.id))
            );

            const salesWithReturns = await Promise.all(
                completedSales.map(async sale => {

                    const returns = await loadSalesReturns(sale.id);

                    const returnedByItem = new Map();

                    returns.forEach(returnRecord => {
                        (returnRecord.items || []).forEach(item => {
                            const itemId = Number(item.salesItemId);

                            returnedByItem.set(
                                itemId,
                                (returnedByItem.get(itemId) || 0)
                                + Number(item.quantity || 0)
                            );
                        });
                    });

                    return {
                        ...sale,
                        invoice: invoiceMap.get(Number(sale.id)),
                        returns,
                        returnedByItem
                    };
                })
            );

            salesRecords = salesWithReturns.sort((a, b) => {
                return new Date(b.salesDate || 0)
                    - new Date(a.salesDate || 0);
            });

            currentPage = 0;

            updateSummary();
            renderTable();

        } catch (error) {

            console.error("Unable to load sales returns:", error);

            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        Unable to load sales records.
                    </td>
                </tr>
            `;

            showMessage(error.message);

        } finally {
            elements.refreshButton.disabled = false;
        }
    }


    function getSalesReturnStatus(sale) {
        const items = sale.items || [];

        if (items.length === 0) {
            return "NOT_RETURNED";
        }

        let totalOriginalQuantity = 0;
        let totalReturnedQuantity = 0;

        items.forEach(item => {
            const original = Number(item.quantity || 0);
            const returned = Number(
                sale.returnedByItem.get(Number(item.id)) || 0
            );

            totalOriginalQuantity += original;
            totalReturnedQuantity += Math.min(returned, original);
        });

        if (totalReturnedQuantity <= 0) {
            return "NOT_RETURNED";
        }

        if (totalReturnedQuantity >= totalOriginalQuantity) {
            return "RETURNED";
        }

        return "PARTIAL";
    }


    function updateSummary() {
        const totalReturns = salesRecords.reduce(
            (sum, sale) => sum + sale.returns.length,
            0
        );

        let quantity = 0;
        let amount = 0;

        salesRecords.forEach(sale => {
            sale.returns.forEach(returnRecord => {
                amount += Number(returnRecord.totalAmount || 0);

                (returnRecord.items || []).forEach(item => {
                    quantity += Number(item.quantity || 0);
                });
            });
        });

        elements.completedSalesCount.textContent =
            salesRecords.length.toLocaleString();

        elements.totalReturnsCount.textContent =
            totalReturns.toLocaleString();

        elements.returnedQuantity.textContent =
            quantity.toLocaleString();

        elements.returnAmount.textContent = money(amount);
    }


    function getFilteredSales() {
        const keyword = elements.search.value.trim().toLowerCase();
        const filter = elements.filter.value;

        return salesRecords.filter(sale => {

            const status = getSalesReturnStatus(sale);

            if (filter !== "ALL" && status !== filter) {
                return false;
            }

            if (!keyword) {
                return true;
            }

            const searchable = [
                sale.salesNumber,
                sale.customerName,
                sale.invoice?.invoiceNumber
            ].join(" ").toLowerCase();

            return searchable.includes(keyword);
        });
    }


    function getStatusLabel(status) {
        if (status === "RETURNED") {
            return "Fully Returned";
        }

        if (status === "PARTIAL") {
            return "Partially Returned";
        }

        return "Not Returned";
    }


    function getStatusClass(status) {
        if (status === "RETURNED") {
            return "returned";
        }

        if (status === "PARTIAL") {
            return "partial";
        }

        return "not-returned";
    }


    function getReturnedItemCount(sale) {
        let count = 0;

        (sale.items || []).forEach(item => {
            count += Number(
                sale.returnedByItem.get(Number(item.id)) || 0
            );
        });

        return count;
    }


    function getOriginalItemCount(sale) {
        return (sale.items || []).reduce(
            (sum, item) => sum + Number(item.quantity || 0),
            0
        );
    }


    function renderTable() {
        const filtered = getFilteredSales();

        const totalPages = Math.max(
            1,
            Math.ceil(filtered.length / PAGE_SIZE)
        );

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        const start = currentPage * PAGE_SIZE;
        const pageRecords = filtered.slice(start, start + PAGE_SIZE);

        if (pageRecords.length === 0) {
            elements.tableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        No sales records found.
                    </td>
                </tr>
            `;
        } else {

            elements.tableBody.innerHTML = pageRecords.map(sale => {

                const status = getSalesReturnStatus(sale);
                const returned = getReturnedItemCount(sale);
                const original = getOriginalItemCount(sale);

                const fullyReturned = status === "RETURNED";

                return `
                    <tr>

                        <td>
                            <div class="return-sales-number">
                                <strong>${escapeHtml(sale.salesNumber)}</strong>
                                <small>${escapeHtml(sale.items?.length || 0)} product lines</small>
                            </div>
                        </td>

                        <td>
                            <div class="customer-cell">
                                <strong>${escapeHtml(sale.customerName)}</strong>
                            </div>
                        </td>

                        <td>${formatDate(sale.salesDate)}</td>

                        <td>
                            <span class="return-invoice">
                                ${escapeHtml(sale.invoice?.invoiceNumber || "—")}
                            </span>
                        </td>

                        <td>
                            <div class="item-cell">
                                <strong>${returned} / ${original}</strong>
                                <small>Returned / Sold</small>
                            </div>
                        </td>

                        <td>
                            <span class="return-status ${getStatusClass(status)}">
                                ${getStatusLabel(status)}
                            </span>
                        </td>

                        <td class="amount-cell">
                            ${money(sale.totalAmount)}
                        </td>

                        <td>
                            <div class="return-actions">

                                <button type="button"
                                        class="return-action create"
                                        data-action="create"
                                        data-sales-id="${sale.id}"
                                        ${fullyReturned ? "disabled" : ""}>
                                    Return
                                </button>

                                <button type="button"
                                        class="return-action"
                                        data-action="history"
                                        data-sales-id="${sale.id}">
                                    History
                                </button>

                            </div>
                        </td>

                    </tr>
                `;

            }).join("");
        }

        const firstRecord = filtered.length === 0 ? 0 : start + 1;
        const lastRecord = Math.min(start + PAGE_SIZE, filtered.length);

        elements.paginationInfo.textContent =
            `Showing ${firstRecord}-${lastRecord} of ${filtered.length} sales records`;

        elements.currentPage.textContent = String(currentPage + 1);

        elements.previousPage.disabled = currentPage <= 0;
        elements.nextPage.disabled = currentPage >= totalPages - 1;
    }


    function openReturnModal(sale) {
        selectedSales = sale;

        clearFormMessage();

        elements.returnForm.reset();

        elements.returnSalesNumber.textContent =
            sale.salesNumber || "—";

        elements.returnCustomerName.textContent =
            sale.customerName || "—";

        elements.returnInvoiceNumber.textContent =
            sale.invoice?.invoiceNumber || "—";

        elements.returnSalesAmount.textContent =
            money(sale.totalAmount);

        renderReturnItems();

        updateReturnTotal();

        elements.returnModal.hidden = false;
        document.body.style.overflow = "hidden";
    }


    function renderReturnItems() {
        if (!selectedSales) {
            return;
        }

        const items = selectedSales.items || [];

        if (items.length === 0) {
            elements.returnItemsBody.innerHTML = `
                <tr>
                    <td colspan="6" class="return-empty">
                        This sales order has no items.
                    </td>
                </tr>
            `;
            return;
        }

        elements.returnItemsBody.innerHTML = items.map(item => {

            const soldQuantity = Number(item.quantity || 0);

            const returnedQuantity = Number(
                selectedSales.returnedByItem.get(Number(item.id)) || 0
            );

            const remainingQuantity = Math.max(
                0,
                soldQuantity - returnedQuantity
            );

            const unitPrice = Number(item.unitPrice || 0);

            return `
                <tr>

                    <td>
                        <div class="return-product-cell">
                            <strong>${escapeHtml(item.productName)}</strong>
                            <small>${escapeHtml(item.productCode || "—")}</small>
                        </div>
                    </td>

                    <td>${soldQuantity}</td>

                    <td>${returnedQuantity}</td>

                    <td>
                        <strong class="remaining-quantity ${remainingQuantity === 0 ? "zero" : ""}">
                            ${remainingQuantity}
                        </strong>
                    </td>

                    <td class="items-price">
                        ${money(unitPrice)}
                    </td>

                    <td>
                        <input type="number"
                               class="return-quantity"
                               data-sales-item-id="${item.id}"
                               data-unit-price="${unitPrice}"
                               data-remaining="${remainingQuantity}"
                               min="0"
                               max="${remainingQuantity}"
                               step="1"
                               value="0"
                               ${remainingQuantity === 0 ? "disabled" : ""}
                               aria-label="Return quantity for ${escapeHtml(item.productName)}">
                    </td>

                </tr>
            `;

        }).join("");
    }


    function getSelectedReturnItems() {
        const inputs = elements.returnItemsBody.querySelectorAll(
            ".return-quantity"
        );

        const selectedItems = [];

        inputs.forEach(input => {

            const quantity = Number(input.value || 0);

            if (quantity <= 0) {
                return;
            }

            selectedItems.push({
                salesItemId: Number(input.dataset.salesItemId),
                quantity,
                unitPrice: Number(input.dataset.unitPrice),
                remaining: Number(input.dataset.remaining)
            });
        });

        return selectedItems;
    }


    function updateReturnTotal() {
        const selectedItems = getSelectedReturnItems();

        let totalQuantity = 0;
        let totalAmount = 0;

        selectedItems.forEach(item => {
            totalQuantity += item.quantity;
            totalAmount += item.quantity * item.unitPrice;
        });

        elements.selectedReturnQuantity.textContent =
            totalQuantity.toLocaleString();

        elements.selectedReturnAmount.textContent =
            money(totalAmount);
    }


    function closeReturnModal() {
        elements.returnModal.hidden = true;
        document.body.style.overflow = "";

        selectedSales = null;
        selectedSalesReturns = [];
    }


    function openHistoryModal(sale) {
        const returns = sale.returns || [];

        elements.historyDescription.textContent =
            `${sale.salesNumber} · ${sale.customerName}`;

        if (returns.length === 0) {

            elements.historyBody.innerHTML = `
                <tr>
                    <td colspan="6" class="return-empty">
                        No return history for this sales order.
                    </td>
                </tr>
            `;

        } else {

            elements.historyBody.innerHTML = returns.map(record => {

                const itemCount = (record.items || []).reduce(
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

                        <td>${itemCount}</td>

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
            return `SR-${window.crypto.randomUUID()}`;
        }

        return `SR-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }


    async function submitSalesReturn(event) {
        event.preventDefault();

        if (isSubmitting || !selectedSales) {
            return;
        }

        clearFormMessage();

        const reason = elements.returnReason.value.trim();
        const selectedItems = getSelectedReturnItems();

        if (!reason) {
            showFormMessage("Please enter a return reason.");
            elements.returnReason.focus();
            return;
        }

        if (reason.length > 500) {
            showFormMessage("Return reason cannot exceed 500 characters.");
            return;
        }

        if (selectedItems.length === 0) {
            showFormMessage("Please select at least one item to return.");
            return;
        }

        for (const item of selectedItems) {

            if (!Number.isInteger(item.quantity) || item.quantity < 1) {
                showFormMessage("Return quantity must be a whole number greater than zero.");
                return;
            }

            if (item.quantity > item.remaining) {
                showFormMessage("Return quantity cannot exceed the remaining quantity.");
                return;
            }
        }

        const requestBody = {
            salesId: Number(selectedSales.id),
            reason,
            items: selectedItems.map(item => ({
                salesItemId: item.salesItemId,
                quantity: item.quantity
            }))
        };

        isSubmitting = true;
        elements.submitReturnButton.disabled = true;
        elements.submitReturnButton.textContent = "Submitting...";

        try {

            await apiRequest(API.salesReturns, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Idempotency-Key": createIdempotencyKey()
                },
                body: JSON.stringify(requestBody)
            });

            closeReturnModal();

            await loadData();

            showMessage(
                "Sales return created successfully. Inventory and customer credit have been updated.",
                "success"
            );

        } catch (error) {

            console.error("Unable to create sales return:", error);

            showFormMessage(error.message);

        } finally {

            isSubmitting = false;
            elements.submitReturnButton.disabled = false;
            elements.submitReturnButton.textContent = "Submit Sales Return";
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
                Math.ceil(getFilteredSales().length / PAGE_SIZE)
            );

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderTable();
            }
        });


        elements.tableBody.addEventListener("click", event => {

            const button = event.target.closest("button[data-action]");

            if (!button || button.disabled) {
                return;
            }

            const salesId = Number(button.dataset.salesId);

            const sale = salesRecords.find(
                record => Number(record.id) === salesId
            );

            if (!sale) {
                return;
            }

            if (button.dataset.action === "create") {
                openReturnModal(sale);
            }

            if (button.dataset.action === "history") {
                openHistoryModal(sale);
            }
        });


        elements.returnItemsBody.addEventListener("input", event => {

            const input = event.target.closest(".return-quantity");

            if (!input) {
                return;
            }

            const maximum = Number(input.dataset.remaining || 0);
            let value = Number(input.value || 0);

            if (value < 0) {
                value = 0;
            }

            if (value > maximum) {
                value = maximum;
            }

            if (!Number.isInteger(value)) {
                value = Math.floor(value);
            }

            input.value = String(value);

            updateReturnTotal();
        });


        elements.returnForm.addEventListener(
            "submit",
            submitSalesReturn
        );


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

            if (event.key !== "Escape") {
                return;
            }

            if (!elements.returnModal.hidden) {
                closeReturnModal();
            }

            if (!elements.historyModal.hidden) {
                closeHistoryModal();
            }
        });
    }


    function init() {
        bindEvents();
        loadData();
    }


    init();

})();
