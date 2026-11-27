
"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const state = {
        customers: [],
        suppliers: [],
        refunds: [],
        activeTab: "ALL",
        currentPage: 1,
        pageSize: 10,
        selectedAccount: null,
        selectedBalance: 0,
        balanceRequestId: 0,
        submitting: false
    };

    const $ = id => document.getElementById(id);

    const el = {
        pageMessage: $("page-message"),
        tableBody: $("refund-table-body"),
        search: $("refund-search"),
        methodFilter: $("method-filter"),
        type: $("refund-type"),
        accountSearch: $("account-search"),
        accountResults: $("account-search-results"),
        accountLabel: $("account-label"),
        balanceCard: $("balance-card"),
        balance: $("available-balance"),
        amount: $("refund-amount"),
        date: $("refund-date"),
        method: $("refund-method"),
        reference: $("refund-reference"),
        remarks: $("refund-remarks"),
        formMessage: $("form-message"),
        submit: $("submit-refund-button"),
        modal: $("refund-modal"),
        detailsModal: $("details-modal")
    };

    const money = value => new Intl.NumberFormat("en-MY", {
        style: "currency",
        currency: "MYR",
        minimumFractionDigits: 2
    }).format(Number(value || 0));

    const escapeHTML = value => String(value ?? "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);

    const text = value => String(value ?? "").trim() || "-";

    function today() {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    }

    function formatDate(value) {
        if (!value) return "-";
        const d = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value);
        return Number.isNaN(d.getTime()) ? "-" : d.toLocaleDateString("en-MY", {
            day: "2-digit", month: "short", year: "numeric"
        });
    }

    function formatDateTime(value) {
        if (!value) return "-";
        const d = new Date(value);
        return Number.isNaN(d.getTime()) ? "-" : d.toLocaleString("en-MY");
    }

    function methodName(value) {
        return ({
            CASH: "Cash",
            BANK_TRANSFER: "Bank Transfer",
            CARD: "Card",
            E_WALLET: "E-Wallet"
        })[value] || text(value);
    }

    async function api(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                Accept: "application/json",
                ...(options.headers || {})
            }
        });

        const body = await response.text();
        let data = null;

        if (body) {
            try {
                data = JSON.parse(body);
            } catch {
                throw new Error("Invalid server response.");
            }
        }

        if (!response.ok) {
            throw new Error(data?.message || data?.error || `Request failed (${response.status})`);
        }

        return data;
    }

    function array(data) {
        if (Array.isArray(data)) return data;
        if (Array.isArray(data?.content)) return data.content;
        if (Array.isArray(data?.data)) return data.data;
        if (Array.isArray(data?.items)) return data.items;
        return [];
    }

    function showPageMessage(message, type = "error") {
        el.pageMessage.textContent = message;
        el.pageMessage.className = `page-message show ${type}`;
    }

    function clearPageMessage() {
        el.pageMessage.textContent = "";
        el.pageMessage.className = "page-message";
    }

    function showFormMessage(message) {
        el.formMessage.textContent = message;
        el.formMessage.classList.add("show");
    }

    function clearFormMessage() {
        el.formMessage.textContent = "";
        el.formMessage.classList.remove("show");
    }

    function accountName(refund) {
        return refund.type === "CUSTOMER" ? refund.customerName : refund.supplierName;
    }

    function renderSummary() {
        const customers = state.refunds.filter(x => x.type === "CUSTOMER");
        const suppliers = state.refunds.filter(x => x.type === "SUPPLIER");

        const customerTotal = customers.reduce((sum, x) => sum + Number(x.amount || 0), 0);
        const supplierTotal = suppliers.reduce((sum, x) => sum + Number(x.amount || 0), 0);

        $("summary-total-refunds").textContent = state.refunds.length;
        $("summary-customer-refunds").textContent = money(customerTotal);
        $("summary-supplier-refunds").textContent = money(supplierTotal);
        $("summary-total-amount").textContent = money(customerTotal + supplierTotal);

        $("all-count").textContent = state.refunds.length;
        $("customer-count").textContent = customers.length;
        $("supplier-count").textContent = suppliers.length;
    }

    function filteredRefunds() {
        const keyword = el.search.value.trim().toLowerCase();
        const method = el.methodFilter.value;

        return state.refunds.filter(item => {
            if (state.activeTab !== "ALL" && item.type !== state.activeTab) return false;
            if (method !== "ALL" && item.paymentMethod !== method) return false;

            if (!keyword) return true;

            const values = [
                item.refundNumber,
                accountName(item),
                item.referenceNumber,
                item.remarks,
                item.type
            ];

            return values.some(value => String(value || "").toLowerCase().includes(keyword));
        });
    }

    function renderTable() {
        const records = filteredRefunds();
        const pages = Math.max(1, Math.ceil(records.length / state.pageSize));

        state.currentPage = Math.min(state.currentPage, pages);

        const start = (state.currentPage - 1) * state.pageSize;
        const rows = records.slice(start, start + state.pageSize);

        if (!rows.length) {
            el.tableBody.innerHTML = `<tr><td colspan="8" class="table-empty">No refund records found.</td></tr>`;
        } else {
            el.tableBody.innerHTML = rows.map(item => {
                const customer = item.type === "CUSTOMER";
                return `
                    <tr>
                        <td>
                            <div class="refund-number">
                                <strong>${escapeHTML(text(item.refundNumber))}</strong>
                                <small>ID: ${escapeHTML(text(item.id))}</small>
                            </div>
                        </td>
                        <td><span class="type-badge ${customer ? "customer" : "supplier"}">${customer ? "Customer" : "Supplier"}</span></td>
                        <td><strong>${escapeHTML(text(accountName(item)))}</strong></td>
                        <td>${escapeHTML(formatDate(item.refundDate))}</td>
                        <td>${escapeHTML(methodName(item.paymentMethod))}</td>
                        <td>${escapeHTML(text(item.referenceNumber))}</td>
                        <td class="amount-cell">${money(item.amount)}</td>
                        <td>
                            <button class="table-action" type="button"
                                data-view-refund="${escapeHTML(item.type)}:${escapeHTML(item.id)}">View</button>
                        </td>
                    </tr>`;
            }).join("");
        }

        $("pagination-info").textContent =
            `Showing ${records.length ? start + 1 : 0}-${Math.min(start + state.pageSize, records.length)} of ${records.length} refunds`;

        $("current-page").textContent = `${state.currentPage} / ${pages}`;
        $("previous-page").disabled = state.currentPage <= 1;
        $("next-page").disabled = state.currentPage >= pages;
    }

    async function loadHistory(accounts, type) {
        const results = await Promise.allSettled(accounts.map(async account => {
            const endpoint = type === "CUSTOMER"
                ? `/api/customer-refunds/customer/${account.id}`
                : `/api/supplier-credits/refunds/${account.id}`;

            return array(await api(endpoint)).map(item => ({
                ...item,
                type
            }));
        }));

        const records = [];
        let failed = 0;

        results.forEach(result => {
            if (result.status === "fulfilled") records.push(...result.value);
            else {
                failed++;
                console.error(result.reason);
            }
        });

        return { records, failed };
    }

    async function loadAll() {
        clearPageMessage();
        el.tableBody.innerHTML = `<tr><td colspan="8" class="table-empty">Loading refunds...</td></tr>`;

        try {
            const [customers, suppliers] = await Promise.all([
                api("/api/customers"),
                api("/api/suppliers")
            ]);

            state.customers = array(customers);
            state.suppliers = array(suppliers);

            const [customerResult, supplierResult] = await Promise.all([
                loadHistory(state.customers, "CUSTOMER"),
                loadHistory(state.suppliers, "SUPPLIER")
            ]);

            state.refunds = [
                ...customerResult.records,
                ...supplierResult.records
            ].sort((a, b) => new Date(b.createdAt || b.refundDate || 0) - new Date(a.createdAt || a.refundDate || 0));

            renderSummary();
            renderTable();

            const failed = customerResult.failed + supplierResult.failed;
            if (failed) {
                showPageMessage(`${failed} account refund histories could not be loaded. Other records are displayed.`);
            }
        } catch (error) {
            console.error(error);
            el.tableBody.innerHTML = `<tr><td colspan="8" class="table-empty">Unable to load refund records.</td></tr>`;
            showPageMessage(error.message);
        }
    }

    function currentAccounts() {
        return el.type.value === "CUSTOMER" ? state.customers : state.suppliers;
    }

    function accountFields(account) {
        const customer = el.type.value === "CUSTOMER";
        return {
            id: account.id,
            code: customer ? account.customerCode : account.supplierCode,
            name: customer ? account.customerName : account.supplierName
        };
    }

    function resetSelectedAccount() {
        state.selectedAccount = null;
        state.selectedBalance = 0;
        state.balanceRequestId++;

        el.balanceCard.hidden = true;
        el.amount.value = "";
        el.amount.disabled = true;
        el.submit.disabled = true;
        el.accountResults.hidden = true;

        clearFormMessage();
    }

    function renderAccountSearch() {
        const keyword = el.accountSearch.value.trim().toLowerCase();
        const accounts = currentAccounts();

        resetSelectedAccount();

        if (!keyword) return;

        const matches = accounts
            .map(account => ({ account, fields: accountFields(account) }))
            .filter(item => {
                const code = String(item.fields.code || "").toLowerCase();
                const name = String(item.fields.name || "").toLowerCase();
                return code.includes(keyword) || name.includes(keyword);
            })
            .slice(0, 10);

        if (!matches.length) {
            el.accountResults.innerHTML = `<div class="account-no-results">No matching accounts found.</div>`;
            el.accountResults.hidden = false;
            return;
        }

        el.accountResults.innerHTML = matches.map(item => `
            <button type="button" class="account-option"
                data-account-id="${escapeHTML(item.fields.id)}">
                <strong>${escapeHTML(text(item.fields.name))}</strong>
                <span>${escapeHTML(text(item.fields.code))}</span>
            </button>
        `).join("");

        el.accountResults.hidden = false;
    }

    async function selectAccount(accountId) {
        const account = currentAccounts().find(item => String(item.id) === String(accountId));
        if (!account) return;

        const fields = accountFields(account);

        state.selectedAccount = account;
        state.selectedBalance = 0;
        const requestId = ++state.balanceRequestId;

        el.accountSearch.value = `${fields.code || ""} - ${fields.name || ""}`;
        el.accountResults.hidden = true;
        el.balanceCard.hidden = false;
        el.balance.textContent = "Loading...";
        el.amount.value = "";
        el.amount.disabled = true;
        el.submit.disabled = true;
        clearFormMessage();

        const endpoint = el.type.value === "CUSTOMER"
            ? `/api/customer-credits/${account.id}`
            : `/api/supplier-credits/${account.id}`;

        try {
            const result = await api(endpoint);
            if (requestId !== state.balanceRequestId) return;

            state.selectedBalance = Number(result?.availableCreditBalance || 0);
            el.balance.textContent = money(state.selectedBalance);
            el.amount.disabled = state.selectedBalance <= 0;
            el.submit.disabled = state.selectedBalance <= 0;

            if (state.selectedBalance <= 0) {
                showFormMessage("This account has no available credit to refund.");
            }
        } catch (error) {
            if (requestId !== state.balanceRequestId) return;
            el.balance.textContent = "Unavailable";
            showFormMessage(error.message);
        }
    }

    function openCreateModal() {
        clearFormMessage();
        el.type.value = "CUSTOMER";
        el.accountSearch.value = "";
        el.amount.value = "";
        el.date.value = today();
        el.date.max = today();
        el.method.value = "CASH";
        el.reference.value = "";
        el.remarks.value = "";

        resetSelectedAccount();
        updateAccountType();

        el.modal.hidden = false;
        document.body.classList.add("modal-open");
    }

    function closeCreateModal() {
        el.modal.hidden = true;
        document.body.classList.remove("modal-open");
    }

    function updateAccountType() {
        resetSelectedAccount();
        el.accountSearch.value = "";
        el.accountLabel.textContent = el.type.value === "CUSTOMER" ? "Customer *" : "Supplier *";
        el.accountSearch.placeholder = el.type.value === "CUSTOMER"
            ? "Search customer code or name..."
            : "Search supplier code or name...";
        el.accountResults.innerHTML = "";
        el.accountResults.hidden = true;
    }

    function validateForm() {
        if (!state.selectedAccount) {
            return "Please search and select a customer or supplier.";
        }

        const amount = Number(el.amount.value);

        if (!Number.isFinite(amount) || amount <= 0) {
            return "Refund amount must be greater than zero.";
        }

        if (Math.round(amount * 100) !== amount * 100) {
            return "Refund amount can only have up to 2 decimal places.";
        }

        if (amount > state.selectedBalance + 0.000001) {
            return `Refund amount cannot exceed ${money(state.selectedBalance)}.`;
        }

        if (!el.date.value || el.date.value > today()) {
            return "Please select a valid refund date.";
        }

        if (!el.method.value) return "Please select a payment method.";

        return null;
    }

    async function submitRefund(event) {
        event.preventDefault();
        if (state.submitting) return;

        clearFormMessage();

        const validation = validateForm();
        if (validation) {
            showFormMessage(validation);
            return;
        }

        const type = el.type.value;
        const accountId = Number(state.selectedAccount.id);

        const payload = {
            amount: Number(el.amount.value),
            paymentMethod: el.method.value,
            refundDate: el.date.value,
            referenceNumber: el.reference.value.trim(),
            remarks: el.remarks.value.trim()
        };

        if (type === "CUSTOMER") payload.customerId = accountId;
        else payload.supplierId = accountId;

        const endpoint = type === "CUSTOMER"
            ? "/api/customer-refunds"
            : "/api/supplier-credits/refund";

        state.submitting = true;
        el.submit.disabled = true;
        el.submit.textContent = "Saving...";

        try {
            await api(endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Idempotency-Key": window.crypto?.randomUUID
                        ? window.crypto.randomUUID()
                        : `REFUND-${Date.now()}`
                },
                body: JSON.stringify(payload)
            });

            closeCreateModal();
            await loadAll();
            showPageMessage("Refund recorded successfully.", "success");
        } catch (error) {
            showFormMessage(error.message);
        } finally {
            state.submitting = false;
            el.submit.textContent = "Save Refund";
            el.submit.disabled = !state.selectedAccount || state.selectedBalance <= 0;
        }
    }

    function openDetails(item) {
        $("details-refund-number").textContent = text(item.refundNumber);
        $("details-type").textContent = item.type === "CUSTOMER" ? "Customer Refund" : "Supplier Refund";
        $("details-account").textContent = text(accountName(item));
        $("details-date").textContent = formatDate(item.refundDate);
        $("details-method").textContent = methodName(item.paymentMethod);
        $("details-amount").textContent = money(item.amount);
        $("details-reference").textContent = text(item.referenceNumber);
        $("details-remarks").textContent = text(item.remarks);
        $("details-created-at").textContent = formatDateTime(item.createdAt);

        el.detailsModal.hidden = false;
        document.body.classList.add("modal-open");
    }

    function closeDetails() {
        el.detailsModal.hidden = true;
        document.body.classList.remove("modal-open");
    }

    $("add-refund-button").addEventListener("click", openCreateModal);
    $("close-refund-modal").addEventListener("click", closeCreateModal);
    $("cancel-refund-button").addEventListener("click", closeCreateModal);
    $("close-details-modal").addEventListener("click", closeDetails);
    $("close-details-button").addEventListener("click", closeDetails);

    document.querySelectorAll("[data-close-refund]").forEach(item =>
        item.addEventListener("click", closeCreateModal)
    );

    document.querySelectorAll("[data-close-details]").forEach(item =>
        item.addEventListener("click", closeDetails)
    );

    el.type.addEventListener("change", updateAccountType);
    el.accountSearch.addEventListener("input", renderAccountSearch);

    el.accountResults.addEventListener("click", event => {
        const button = event.target.closest("[data-account-id]");
        if (!button) return;
        selectAccount(button.dataset.accountId);
    });

    document.addEventListener("click", event => {
        if (!event.target.closest(".account-search-wrapper")) {
            el.accountResults.hidden = true;
        }
    });

    el.amount.addEventListener("input", () => {
        clearFormMessage();
        if (Number(el.amount.value) > state.selectedBalance) {
            showFormMessage(`Amount cannot exceed ${money(state.selectedBalance)}.`);
        }
    });

    $("refund-form").addEventListener("submit", submitRefund);

    el.search.addEventListener("input", () => {
        state.currentPage = 1;
        renderTable();
    });

    el.methodFilter.addEventListener("change", () => {
        state.currentPage = 1;
        renderTable();
    });

    document.querySelectorAll(".refund-tab").forEach(button => {
        button.addEventListener("click", () => {
            state.activeTab = button.dataset.type;
            state.currentPage = 1;

            document.querySelectorAll(".refund-tab").forEach(tab =>
                tab.classList.toggle("active", tab === button)
            );

            renderTable();
        });
    });

    $("previous-page").addEventListener("click", () => {
        if (state.currentPage > 1) {
            state.currentPage--;
            renderTable();
        }
    });

    $("next-page").addEventListener("click", () => {
        const pages = Math.max(1, Math.ceil(filteredRefunds().length / state.pageSize));
        if (state.currentPage < pages) {
            state.currentPage++;
            renderTable();
        }
    });

    el.tableBody.addEventListener("click", event => {
        const button = event.target.closest("[data-view-refund]");
        if (!button) return;

        const [type, id] = button.dataset.viewRefund.split(":");
        const item = state.refunds.find(refund =>
            refund.type === type && String(refund.id) === String(id)
        );

        if (item) openDetails(item);
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            if (!el.modal.hidden) closeCreateModal();
            if (!el.detailsModal.hidden) closeDetails();
            el.accountResults.hidden = true;
        }
    });

    loadAll();
});
