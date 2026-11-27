
"use strict";

(() => {
    const PAGE_SIZE = 10;

    const API = {
        salesInvoices: "/api/sales-invoices",
        purchaseInvoices: "/api/purchase-invoices",
        sales: "/api/sales",
        purchases: "/api/purchases"
    };

    let activeType = "SALES";
    let salesInvoices = [];
    let purchaseInvoices = [];
    let sales = [];
    let purchases = [];

    let currentPage = 0;
    let selectedSourceId = null;
    let isCreating = false;
    let selectedInvoice = null;

    const $ = id => document.getElementById(id);

    const el = {
        total: $("total-invoices"),
        unpaid: $("unpaid-invoices"),
        paid: $("paid-invoices"),
        outstanding: $("total-outstanding"),

        salesCount: $("sales-count"),
        purchaseCount: $("purchase-count"),

        message: $("page-message"),
        search: $("invoice-search"),
        status: $("invoice-status-filter"),
        table: $("invoice-table-body"),

        previous: $("previous-page"),
        next: $("next-page"),
        page: $("current-page"),
        pagination: $("pagination-info"),

        tabs: document.querySelectorAll(".invoice-tab"),

        createButton: $("create-invoice-button"),
        createModal: $("create-modal"),
        createTitle: $("create-modal-title"),
        sourceSearch: $("source-search"),
        sourceList: $("source-list"),
        createMessage: $("create-message"),
        confirmCreate: $("confirm-create-invoice"),

        detailsModal: $("details-modal")
    };

    document.addEventListener("DOMContentLoaded", initialize);

    async function apiRequest(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                Accept: "application/json",
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
            throw new Error(
                data?.message ||
                data?.error ||
                (typeof data === "string" ? data : null) ||
                `Request failed: ${response.status}`
            );
        }

        return data;
    }

    function normalizeArray(data) {
        if (Array.isArray(data)) return data;
        if (Array.isArray(data?.content)) return data.content;
        if (Array.isArray(data?.data)) return data.data;
        return [];
    }

    function money(value) {
        const amount = Number(value || 0);

        return new Intl.NumberFormat("en-MY", {
            style: "currency",
            currency: "MYR",
            minimumFractionDigits: 2
        }).format(Number.isFinite(amount) ? amount : 0);
    }

    function safe(value) {
        return String(value ?? "").replace(/[&<>"']/g, char => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        })[char]);
    }

    function date(value) {
        if (!value) return "-";

        const parsed = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);

        let result;

        if (parsed) {
            result = new Date(
                Number(parsed[1]),
                Number(parsed[2]) - 1,
                Number(parsed[3])
            );
        } else {
            result = new Date(value);
        }

        if (Number.isNaN(result.getTime())) return "-";

        return result.toLocaleDateString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        });
    }

    function showMessage(message, success = false) {
        el.message.textContent = message;
        el.message.className = success
            ? "page-message show success"
            : "page-message show";
    }

    function clearMessage() {
        el.message.textContent = "";
        el.message.className = "page-message";
    }

    function showCreateMessage(message) {
        el.createMessage.textContent = message;
        el.createMessage.classList.add("show");
    }

    async function initialize() {
        bindEvents();

        try {
            await loadAll();
            render();
        } catch (error) {
            showMessage(`Unable to load invoices: ${error.message}`);
            el.table.innerHTML = `
                <tr><td colspan="9" class="table-empty">
                    Unable to load invoice records.
                </td></tr>`;
        }
    }

    async function loadAll() {
        const results = await Promise.all([
            apiRequest(API.salesInvoices),
            apiRequest(API.purchaseInvoices),
            apiRequest(API.sales),
            apiRequest(API.purchases)
        ]);

        salesInvoices = normalizeArray(results[0]);
        purchaseInvoices = normalizeArray(results[1]);
        sales = normalizeArray(results[2]);
        purchases = normalizeArray(results[3]);
    }

    function bindEvents() {
        el.tabs.forEach(tab => {
            tab.addEventListener("click", () => {
                activeType = tab.dataset.type;
                currentPage = 0;

                el.tabs.forEach(item => {
                    item.classList.toggle("active", item === tab);
                });

                $("party-heading").textContent =
                    activeType === "SALES" ? "CUSTOMER" : "SUPPLIER";

                clearMessage();
                render();
            });
        });

        el.search.addEventListener("input", () => {
            currentPage = 0;
            renderTable();
        });

        el.status.addEventListener("change", () => {
            currentPage = 0;
            renderTable();
        });

        el.previous.addEventListener("click", () => {
            if (currentPage > 0) {
                currentPage--;
                renderTable();
            }
        });

        el.next.addEventListener("click", () => {
            const totalPages = Math.ceil(getFilteredInvoices().length / PAGE_SIZE);

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderTable();
            }
        });

        el.table.addEventListener("click", handleTableAction);

        el.createButton.addEventListener("click", openCreateModal);

        $("close-create-modal").addEventListener("click", closeCreateModal);
        $("cancel-create-modal").addEventListener("click", closeCreateModal);

        el.createModal.addEventListener("click", event => {
            if (event.target.matches("[data-close-create]")) {
                closeCreateModal();
            }
        });

        el.sourceSearch.addEventListener("input", renderSourceList);

        el.sourceList.addEventListener("click", event => {
            const option = event.target.closest("[data-source-id]");
            if (!option) return;

            selectedSourceId = Number(option.dataset.sourceId);
            renderSourceList();
            el.confirmCreate.disabled = false;
        });

        el.confirmCreate.addEventListener("click", createInvoice);

        $("close-details-modal").addEventListener("click", closeDetails);
        $("close-details-button").addEventListener("click", closeDetails);

        el.detailsModal.addEventListener("click", event => {
            if (event.target.matches("[data-close-details]")) {
                closeDetails();
            }
        });

        $("print-invoice-button").addEventListener("click", () => {
            window.print();
        });
    }

    function getInvoices() {
        return activeType === "SALES"
            ? salesInvoices
            : purchaseInvoices;
    }

    function render() {
        renderSummary();
        renderTable();
        el.salesCount.textContent = salesInvoices.length;
        el.purchaseCount.textContent = purchaseInvoices.length;
    }

    function renderSummary() {
        const all = [...salesInvoices, ...purchaseInvoices];

        const unpaid = all.filter(invoice =>
            invoice.status === "UNPAID"
        );

        const paid = all.filter(invoice =>
            ["PAID", "SETTLED"].includes(invoice.status)
        );

        const outstanding = all.reduce(
            (sum, invoice) => sum + Number(invoice.outstandingAmount || 0),
            0
        );

        el.total.textContent = all.length;
        el.unpaid.textContent = unpaid.length;
        el.paid.textContent = paid.length;
        el.outstanding.textContent = money(outstanding);
    }

    function getFilteredInvoices() {
        const keyword = el.search.value.trim().toLowerCase();
        const status = el.status.value;

        return getInvoices().filter(invoice => {
            const partyName = activeType === "SALES"
                ? invoice.customerName
                : invoice.supplierName;

            const reference = activeType === "SALES"
                ? invoice.salesNumber
                : invoice.purchaseNumber;

            const matchesSearch = [
                invoice.invoiceNumber,
                partyName,
                reference
            ].some(value => String(value || "").toLowerCase().includes(keyword));

            const matchesStatus =
                status === "ALL" || invoice.status === status;

            return matchesSearch && matchesStatus;
        });
    }

    function statusClass(status) {
        return String(status || "UNPAID")
            .toLowerCase()
            .replaceAll("_", "-");
    }

    function statusLabel(status) {
        return String(status || "UNPAID")
            .replaceAll("_", " ");
    }

    function renderTable() {
        const filtered = getFilteredInvoices();
        const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
        const start = currentPage * PAGE_SIZE;
        const pageItems = filtered.slice(start, start + PAGE_SIZE);

        if (currentPage >= totalPages && currentPage > 0) {
            currentPage = Math.max(0, totalPages - 1);
            return renderTable();
        }

        if (!pageItems.length) {
            el.table.innerHTML = `
                <tr>
                    <td colspan="9" class="table-empty">
                        No ${activeType.toLowerCase()} invoices found.
                    </td>
                </tr>`;
        } else {
            el.table.innerHTML = pageItems.map(invoice => {
                const partyName = activeType === "SALES"
                    ? invoice.customerName
                    : invoice.supplierName;

                const partyCode = activeType === "SALES"
                    ? invoice.customerCode
                    : invoice.supplierCode;

                const reference = activeType === "SALES"
                    ? invoice.salesNumber
                    : invoice.purchaseNumber;

                return `
                    <tr>
                        <td>
                            <div class="invoice-number">
                                <strong>${safe(invoice.invoiceNumber)}</strong>
                                <small>${safe(activeType === "SALES" ? "Sales Invoice" : "Purchase Invoice")}</small>
                            </div>
                        </td>

                        <td>
                            <div class="party-cell">
                                <strong>${safe(partyName)}</strong>
                                <small>${safe(partyCode)}</small>
                            </div>
                        </td>

                        <td>${safe(reference)}</td>
                        <td>${date(invoice.invoiceDate)}</td>
                        <td class="amount-cell">${money(invoice.totalAmount)}</td>
                        <td>${money(invoice.totalPaid)}</td>
                        <td class="outstanding-cell">${money(invoice.outstandingAmount)}</td>

                        <td>
                            <span class="status-badge ${statusClass(invoice.status)}">
                                ${safe(statusLabel(invoice.status))}
                            </span>
                        </td>

                        <td>
                            <div class="row-actions">
                                <button class="table-action"
                                        data-action="view"
                                        data-id="${invoice.id}">
                                    View
                                </button>
                                <button class="table-action"
                                        data-action="print"
                                        data-id="${invoice.id}">
                                    Print
                                </button>
                            </div>
                        </td>
                    </tr>`;
            }).join("");
        }

        const from = filtered.length ? start + 1 : 0;
        const to = Math.min(start + PAGE_SIZE, filtered.length);

        el.pagination.textContent =
            `Showing ${from}-${to} of ${filtered.length} invoices`;

        el.page.textContent = totalPages ? currentPage + 1 : 0;
        el.previous.disabled = currentPage <= 0;
        el.next.disabled = currentPage >= totalPages - 1;
    }

    async function handleTableAction(event) {
        const button = event.target.closest("[data-action]");
        if (!button) return;

        const id = Number(button.dataset.id);
        const invoice = getInvoices().find(item => item.id === id);

        if (!invoice) return;

        if (button.dataset.action === "view") {
            openDetails(invoice);
        }

        if (button.dataset.action === "print") {
            openDetails(invoice);
            setTimeout(() => window.print(), 250);
        }
    }

    function getAvailableSources() {
        if (activeType === "SALES") {
            const invoicedIds = new Set(
                salesInvoices.map(invoice => Number(invoice.salesId))
            );

            return sales.filter(item =>
                item.status === "COMPLETED" &&
                !invoicedIds.has(Number(item.id))
            );
        }

        const invoicedIds = new Set(
            purchaseInvoices.map(invoice => Number(invoice.purchaseId))
        );

        return purchases.filter(item =>
            item.status === "RECEIVED" &&
            !invoicedIds.has(Number(item.id))
        );
    }

    function openCreateModal() {
        selectedSourceId = null;
        isCreating = false;

        el.createTitle.textContent = activeType === "SALES"
            ? "Create Sales Invoice"
            : "Create Purchase Invoice";

        el.sourceSearch.value = "";
        el.createMessage.textContent = "";
        el.createMessage.classList.remove("show");
        el.confirmCreate.disabled = true;
        el.confirmCreate.textContent = "Generate Invoice";

        el.createModal.hidden = false;
        renderSourceList();
    }

    function closeCreateModal() {
        if (isCreating) return;
        el.createModal.hidden = true;
        selectedSourceId = null;
    }

    function renderSourceList() {
        const keyword = el.sourceSearch.value.trim().toLowerCase();

        const available = getAvailableSources().filter(item => {
            const number = activeType === "SALES"
                ? item.salesNumber
                : item.purchaseNumber;

            const name = activeType === "SALES"
                ? item.customerName
                : item.supplierName;

            return `${number || ""} ${name || ""}`
                .toLowerCase()
                .includes(keyword);
        });

        if (!available.length) {
            el.sourceList.innerHTML = `
                <div class="table-empty">
                    No eligible transactions found.
                </div>`;
            return;
        }

        el.sourceList.innerHTML = available.map(item => {
            const number = activeType === "SALES"
                ? item.salesNumber
                : item.purchaseNumber;

            const name = activeType === "SALES"
                ? item.customerName
                : item.supplierName;

            const selected = Number(item.id) === selectedSourceId;

            return `
                <div class="source-option ${selected ? "selected" : ""}"
                     data-source-id="${item.id}">
                    <div>
                        <strong>${safe(number)}</strong>
                        <small>${safe(name)} · ${date(activeType === "SALES" ? item.salesDate : item.purchaseDate)}</small>
                    </div>
                    <b>${money(item.totalAmount)}</b>
                </div>`;
        }).join("");
    }

    async function createInvoice() {
        if (!selectedSourceId || isCreating) return;

        isCreating = true;
        el.confirmCreate.disabled = true;
        el.confirmCreate.textContent = "Generating...";

        const endpoint = activeType === "SALES"
            ? `${API.salesInvoices}/from-sales/${selectedSourceId}`
            : `${API.purchaseInvoices}/from-purchase/${selectedSourceId}`;

        try {
            await apiRequest(endpoint, { method: "POST" });

            el.createModal.hidden = true;

            await loadAll();

            currentPage = 0;
            render();

            showMessage("Invoice generated successfully.", true);

        } catch (error) {
            showCreateMessage(error.message);
        } finally {
            isCreating = false;
            el.confirmCreate.disabled = false;
            el.confirmCreate.textContent = "Generate Invoice";
        }
    }

    function openDetails(invoice) {
        selectedInvoice = invoice;

        const isSales = activeType === "SALES";

        const partyName = isSales
            ? invoice.customerName
            : invoice.supplierName;

        const partyCode = isSales
            ? invoice.customerCode
            : invoice.supplierCode;

        const partyPhone = isSales
            ? invoice.customerPhone
            : invoice.supplierPhone;

        const partyEmail = isSales
            ? invoice.customerEmail
            : invoice.supplierEmail;

        const partyAddress = isSales
            ? invoice.customerAddress
            : invoice.supplierAddress;

        const reference = isSales
            ? invoice.salesNumber
            : invoice.purchaseNumber;

        $("details-title").textContent = invoice.invoiceNumber;
        $("details-description").textContent =
            `${isSales ? "Sales" : "Purchase"} invoice details`;

        $("document-type").textContent =
            isSales ? "SALES INVOICE" : "PURCHASE INVOICE";

        $("document-number").textContent = invoice.invoiceNumber;
        $("party-label").textContent = isSales ? "Bill To" : "Supplier";
        $("document-party").textContent = partyName || "-";
        $("document-code").textContent = partyCode || "";
        $("document-phone").textContent = partyPhone || "";
        $("document-email").textContent = partyEmail || "";
        $("document-address").textContent = partyAddress || "";

        $("document-date").textContent = date(invoice.invoiceDate);
        $("document-reference").textContent = reference || "-";
        $("document-status").textContent = statusLabel(invoice.status);

        $("document-total").textContent = money(invoice.totalAmount);
        $("document-paid").textContent = money(invoice.totalPaid);
        $("document-credit").textContent = money(invoice.totalCreditApplied);
        $("document-outstanding").textContent = money(invoice.outstandingAmount);

        const items = invoice.items || [];

        $("document-items").innerHTML = items.length
            ? items.map((item, index) => {
                const unitPrice = isSales ? item.unitPrice : item.unitCost;

                return `
                    <tr>
                        <td>${index + 1}</td>
                        <td>
                            <strong>${safe(item.productName)}</strong>
                            <br>
                            <small>${safe(item.productCode)}</small>
                        </td>
                        <td>${Number(item.quantity || 0)}</td>
                        <td>${money(unitPrice)}</td>
                        <td>${money(item.subtotal)}</td>
                    </tr>`;
            }).join("")
            : `<tr><td colspan="5" class="table-empty">No invoice items.</td></tr>`;

        el.detailsModal.hidden = false;
    }

    function closeDetails() {
        el.detailsModal.hidden = true;
        selectedInvoice = null;
    }

})();
