"use strict";

(() => {
    const PAGE_SIZE = 10;

    const API = {
        summary: "/api/outstanding/summary",
        customers: "/api/outstanding/customers",
        suppliers: "/api/outstanding/suppliers"
    };

    let summary = null;
    let customerRecords = [];
    let supplierRecords = [];

    let activeType = "CUSTOMERS";
    let currentPage = 0;
    let isLoading = false;

    const $ = id => document.getElementById(id);

    const el = {
        customerOutstanding: $("customer-outstanding"),
        supplierOutstanding: $("supplier-outstanding"),
        totalInvoices: $("total-invoices"),
        netOutstanding: $("net-outstanding"),

        customerCount: $("customer-count"),
        supplierCount: $("supplier-count"),

        message: $("page-message"),
        search: $("outstanding-search"),
        sort: $("sort-filter"),

        table: $("outstanding-table-body"),
        partyHeading: $("party-heading"),

        previous: $("previous-page"),
        next: $("next-page"),
        page: $("current-page"),
        pagination: $("pagination-info"),

        tabs: document.querySelectorAll(".outstanding-tab"),
        refresh: $("refresh-button")
    };

    document.addEventListener("DOMContentLoaded", initialize);

    async function apiRequest(url) {
        const response = await fetch(url, {
            method: "GET",
            headers: {
                Accept: "application/json"
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
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
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

    function formatDate(value) {
        if (!value) return "-";

        const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);

        let date;

        if (match) {
            date = new Date(
                Number(match[1]),
                Number(match[2]) - 1,
                Number(match[3])
            );
        } else {
            date = new Date(value);
        }

        if (Number.isNaN(date.getTime())) return "-";

        return date.toLocaleDateString("en-MY", {
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

    async function initialize() {
        bindEvents();
        await loadAll();
    }

    function bindEvents() {
        el.tabs.forEach(tab => {
            tab.addEventListener("click", () => {
                activeType = tab.dataset.type;
                currentPage = 0;

                el.tabs.forEach(item => {
                    item.classList.toggle("active", item === tab);
                });

                el.partyHeading.textContent =
                    activeType === "CUSTOMERS" ? "CUSTOMER" : "SUPPLIER";

                renderTable();
            });
        });

        el.search.addEventListener("input", () => {
            currentPage = 0;
            renderTable();
        });

        el.sort.addEventListener("change", () => {
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
            const totalPages = Math.ceil(
                getFilteredRecords().length / PAGE_SIZE
            );

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderTable();
            }
        });

        el.refresh.addEventListener("click", loadAll);
    }

    async function loadAll() {
        if (isLoading) return;

        isLoading = true;
        clearMessage();

        el.refresh.disabled = true;
        el.refresh.classList.add("loading");

        el.table.innerHTML = `
            <tr>
                <td colspan="5" class="table-empty">
                    Loading outstanding invoices...
                </td>
            </tr>
        `;

        try {
            const results = await Promise.all([
                apiRequest(API.summary),
                apiRequest(API.customers),
                apiRequest(API.suppliers)
            ]);

            summary = results[0];
            customerRecords = normalizeArray(results[1]);
            supplierRecords = normalizeArray(results[2]);

            renderSummary();
            renderCounts();
            renderTable();

        } catch (error) {
            showMessage(`Unable to load outstanding records: ${error.message}`);

            el.table.innerHTML = `
                <tr>
                    <td colspan="5" class="table-empty">
                        Unable to load outstanding records.
                    </td>
                </tr>
            `;
        } finally {
            isLoading = false;
            el.refresh.disabled = false;
            el.refresh.classList.remove("loading");
        }
    }

    function renderSummary() {
        if (!summary) return;

        const customerAmount =
            Number(summary.totalCustomerOutstanding || 0);

        const supplierAmount =
            Number(summary.totalSupplierOutstanding || 0);

        el.customerOutstanding.textContent = money(customerAmount);
        el.supplierOutstanding.textContent = money(supplierAmount);

        el.totalInvoices.textContent =
            Number(summary.totalOutstandingInvoices || 0);

        el.netOutstanding.textContent =
            money(customerAmount - supplierAmount);
    }

    function renderCounts() {
        el.customerCount.textContent = customerRecords.length;
        el.supplierCount.textContent = supplierRecords.length;
    }

    function getCurrentRecords() {
        return activeType === "CUSTOMERS"
            ? customerRecords
            : supplierRecords;
    }

    function getFilteredRecords() {
        const keyword = el.search.value.trim().toLowerCase();
        const sort = el.sort.value;

        const records = getCurrentRecords().filter(record => {
            const searchable = [
                record.invoiceNumber,
                record.partyCode,
                record.partyName
            ];

            return searchable.some(value =>
                String(value || "").toLowerCase().includes(keyword)
            );
        });

        records.sort((a, b) => {
            if (sort === "DATE_ASC") {
                return compareDate(a.invoiceDate, b.invoiceDate);
            }

            if (sort === "DATE_DESC") {
                return compareDate(b.invoiceDate, a.invoiceDate);
            }

            if (sort === "AMOUNT_ASC") {
                return Number(a.outstandingAmount || 0) -
                    Number(b.outstandingAmount || 0);
            }

            if (sort === "AMOUNT_DESC") {
                return Number(b.outstandingAmount || 0) -
                    Number(a.outstandingAmount || 0);
            }

            return 0;
        });

        return records;
    }

    function compareDate(a, b) {
        const first = a ? new Date(a).getTime() : 0;
        const second = b ? new Date(b).getTime() : 0;

        return first - second;
    }

    function renderTable() {
        const filtered = getFilteredRecords();

        const totalPages = Math.max(
            1,
            Math.ceil(filtered.length / PAGE_SIZE)
        );

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }

        const start = currentPage * PAGE_SIZE;
        const pageItems = filtered.slice(start, start + PAGE_SIZE);

        if (pageItems.length === 0) {
            el.table.innerHTML = `
                <tr>
                    <td colspan="5" class="table-empty">
                        No outstanding invoices found.
                    </td>
                </tr>
            `;
        } else {
            el.table.innerHTML = pageItems.map(record => `
                <tr>
                    <td>
                        <div class="invoice-number">
                            <strong>${safe(record.invoiceNumber)}</strong>
                            <small>Invoice ID: ${safe(record.invoiceId)}</small>
                        </div>
                    </td>

                    <td>
                        <div class="party-cell">
                            <strong>${safe(record.partyName)}</strong>
                            <small>${safe(record.partyCode)}</small>
                        </div>
                    </td>

                    <td>${safe(formatDate(record.invoiceDate))}</td>

                    <td class="amount-cell">
                        ${money(record.totalAmount)}
                    </td>

                    <td class="outstanding-cell">
                        ${money(record.outstandingAmount)}
                    </td>
                </tr>
            `).join("");
        }

        el.pagination.textContent = filtered.length === 0
            ? "Showing 0 invoices"
            : `Showing ${start + 1}-${Math.min(start + PAGE_SIZE, filtered.length)} of ${filtered.length} invoices`;

        el.page.textContent = currentPage + 1;
        el.previous.disabled = currentPage === 0;
        el.next.disabled = currentPage >= totalPages - 1;
    }

})();