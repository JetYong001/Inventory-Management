
(() => {
    "use strict";

    const API = {
        customers: "/api/customers",
        suppliers: "/api/suppliers",
        customerCredits: "/api/customer-credits",
        supplierCredits: "/api/supplier-credits",
        salesInvoices: "/api/sales-invoices",
        purchaseInvoices: "/api/purchase-invoices"
    };

    const $ = id => document.getElementById(id);

    let activeType = "CUSTOMER";
    let customers = [];
    let suppliers = [];
    let selectedAccount = null;
    let selectedBalance = null;
    let salesInvoices = [];
    let purchaseInvoices = [];
    let currentInvoices = [];

    const accountBody = $("account-table-body");
    const details = $("account-details");
    const message = $("page-message");
    const applyModal = $("apply-modal");
    const reverseModal = $("reverse-modal");

    function money(value) {
        return new Intl.NumberFormat("en-MY", {
            style: "currency",
            currency: "MYR",
            minimumFractionDigits: 2
        }).format(Number(value || 0));
    }

    function number(value) {
        const result = Number(value);
        return Number.isFinite(result) ? result : 0;
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

    function dateTime(value) {
        if (!value) return "-";

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "-";

        return date.toLocaleString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    async function request(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                "Accept": "application/json",
                ...(options.body ? { "Content-Type": "application/json" } : {}),
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
            const errorMessage =
                typeof data === "string"
                    ? data
                    : data?.message || data?.detail || data?.error ||
                    `Request failed (${response.status})`;

            throw new Error(errorMessage);
        }

        return data;
    }

    function showMessage(text, success = false) {
        message.textContent = text;
        message.className = `page-message show${success ? " success" : ""}`;
    }

    function hideMessage() {
        message.textContent = "";
        message.className = "page-message";
    }

    function getAccounts() {
        return activeType === "CUSTOMER" ? customers : suppliers;
    }

    function accountId(account) {
        return account?.id;
    }

    function accountCode(account) {
        return activeType === "CUSTOMER"
            ? account.customerCode
            : account.supplierCode;
    }

    function accountName(account) {
        return activeType === "CUSTOMER"
            ? account.customerName
            : account.supplierName;
    }

    function creditEndpoint(id) {
        return activeType === "CUSTOMER"
            ? `${API.customerCredits}/${id}`
            : `${API.supplierCredits}/${id}`;
    }

    function getInvoiceOutstanding(invoice) {
        return number(invoice.outstandingAmount);
    }

    function getInvoiceOwner(invoice) {
        return activeType === "CUSTOMER"
            ? {
                code: invoice.customerCode,
                name: invoice.customerName
            }
            : {
                code: invoice.supplierCode,
                name: invoice.supplierName
            };
    }

    function updateSummary() {
        const all = [
            ...customers.map(account => account._credit).filter(Boolean),
            ...suppliers.map(account => account._credit).filter(Boolean)
        ];

        const issued = all.reduce(
            (sum, item) => sum + number(item.totalCreditIssued), 0
        );

        const applied = all.reduce(
            (sum, item) => sum + number(item.totalCreditApplied), 0
        );

        const refunded = all.reduce(
            (sum, item) => sum + number(item.totalRefunded), 0
        );

        const available = all.reduce(
            (sum, item) => sum + number(item.availableCreditBalance), 0
        );

        $("summary-issued").textContent = money(issued);
        $("summary-applied").textContent = money(applied);
        $("summary-refunded").textContent = money(refunded);
        $("summary-available").textContent = money(available);
    }

    async function loadAll() {
        $("refresh-button").disabled = true;
        hideMessage();

        accountBody.innerHTML =
            `<tr><td colspan="3" class="table-empty">Loading accounts...</td></tr>`;

        try {
            const [customerData, supplierData, salesData, purchaseData] =
                await Promise.all([
                    request(API.customers),
                    request(API.suppliers),
                    request(API.salesInvoices),
                    request(API.purchaseInvoices)
                ]);

            if (!Array.isArray(customerData) ||
                !Array.isArray(supplierData) ||
                !Array.isArray(salesData) ||
                !Array.isArray(purchaseData)) {
                throw new Error("Unexpected data format returned by the server.");
            }

            customers = customerData.filter(item => item.active !== false);
            suppliers = supplierData.filter(item => item.active !== false);
            salesInvoices = salesData;
            purchaseInvoices = purchaseData;

            await loadAllBalances();
            renderAccounts();

            if (selectedAccount) {
                const refreshed = getAccounts().find(
                    item => Number(item.id) === Number(selectedAccount.id)
                );

                if (refreshed) {
                    selectedAccount = refreshed;
                    await loadSelectedAccount();
                } else {
                    selectedAccount = null;
                    selectedBalance = null;
                    renderEmptySelection();
                }
            } else {
                renderEmptySelection();
            }
        } catch (error) {
            accountBody.innerHTML =
                `<tr><td colspan="3" class="table-empty">Unable to load accounts.</td></tr>`;
            showMessage(error.message || "Unable to load credit information.");
        } finally {
            $("refresh-button").disabled = false;
        }
    }

    async function loadAllBalances() {
        const tasks = [
            ...customers.map(async account => {
                try {
                    account._credit = await request(
                        `${API.customerCredits}/${account.id}`
                    );
                } catch {
                    account._credit = null;
                }
            }),
            ...suppliers.map(async account => {
                try {
                    account._credit = await request(
                        `${API.supplierCredits}/${account.id}`
                    );
                } catch {
                    account._credit = null;
                }
            })
        ];

        await Promise.all(tasks);
        updateSummary();
    }

    function renderAccounts() {
        const keyword = $("account-search").value.trim().toLowerCase();

        const filtered = getAccounts().filter(account => {
            const code = accountCode(account) || "";
            const name = accountName(account) || "";

            return `${code} ${name}`.toLowerCase().includes(keyword);
        });

        $("account-list-title").textContent =
            activeType === "CUSTOMER" ? "Customers" : "Suppliers";

        $("account-count").textContent = `${filtered.length} records`;

        if (!filtered.length) {
            accountBody.innerHTML =
                `<tr><td colspan="3" class="table-empty">No accounts found.</td></tr>`;
            return;
        }

        accountBody.innerHTML = filtered.map(account => {
            const balance = account._credit
                ? money(account._credit.availableCreditBalance)
                : "Not loaded";

            const selected = selectedAccount &&
                Number(selectedAccount.id) === Number(account.id);

            return `
                <tr class="${selected ? "selected-row" : ""}">
                    <td>
                        <div class="account-name-cell">
                            <strong>${safe(accountName(account))}</strong>
                            <small>${safe(accountCode(account))}</small>
                        </div>
                    </td>
                    <td>
                        <span class="account-balance">${safe(balance)}</span>
                    </td>
                    <td>
                        <button class="view-account-button"
                                type="button" data-account-id="${safe(account.id)}">
                            View
                        </button>
                    </td>
                </tr>
            `;
        }).join("");
    }

    function renderEmptySelection() {
        details.innerHTML = `
            <div class="empty-selection">
                <div class="empty-icon">↗</div>
                <h3>Select an Account</h3>
                <p>Select a customer or supplier from the list to view credit information.</p>
            </div>
        `;
    }

    async function loadSelectedAccount() {
        if (!selectedAccount) {
            renderEmptySelection();
            return;
        }

        details.innerHTML =
            `<div class="details-loading">Loading credit account...</div>`;

        try {
            selectedBalance = await request(
                creditEndpoint(selectedAccount.id)
            );

            renderDetails();
        } catch (error) {
            details.innerHTML =
                `<div class="details-error">${safe(error.message)}</div>`;
        }
    }

    function renderDetails() {
        if (!selectedBalance) {
            renderEmptySelection();
            return;
        }

        const account = selectedAccount;
        const balance = selectedBalance;
        const transactions = Array.isArray(balance.transactions)
            ? balance.transactions
            : [];

        const available = number(balance.availableCreditBalance);

        details.innerHTML = `
            <div class="details-heading">
                <div>
                    <span class="section-eyebrow">CREDIT ACCOUNT</span>
                    <h3>${safe(balance[activeType === "CUSTOMER" ? "customerName" : "supplierName"] || accountName(account))}</h3>
                    <p>${safe(balance[activeType === "CUSTOMER" ? "customerCode" : "supplierCode"] || accountCode(account))}</p>
                </div>
                <button class="primary-button" id="open-apply-button"
                        type="button" ${available <= 0 ? "disabled" : ""}>
                    + Apply Credit
                </button>
            </div>

            <div class="balance-highlight">
                <span>Available Credit Balance</span>
                <strong>${money(available)}</strong>
            </div>

            <div class="credit-metrics">
                <div>
                    <span>Total Issued</span>
                    <strong>${money(balance.totalCreditIssued)}</strong>
                </div>
                <div>
                    <span>Total Applied</span>
                    <strong>${money(balance.totalCreditApplied)}</strong>
                </div>
                <div>
                    <span>Total Refunded</span>
                    <strong>${money(balance.totalRefunded)}</strong>
                </div>
            </div>

            <div class="transaction-heading">
                <div>
                    <h4>Credit Transactions</h4>
                    <span>${transactions.length} transactions</span>
                </div>
            </div>

            <div class="transaction-table-wrap">
                <table class="transaction-table">
                    <thead>
                    <tr>
                        <th>DATE</th>
                        <th>TYPE</th>
                        <th>REFERENCE</th>
                        <th>AMOUNT</th>
                        <th>ACTION</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${transactions.length ? transactions.map(transaction => {
            const type = transaction.transactionType || "";
            const amount = number(transaction.amount);
            const canReverse = type === "CREDIT_APPLIED";

            return `
                            <tr>
                                <td>${dateTime(transaction.createdAt)}</td>
                                <td>
                                    <span class="transaction-type ${safe(type.toLowerCase())}">
                                        ${safe(type.replaceAll("_", " "))}
                                    </span>
                                </td>
                                <td>
                                    <div class="reference-cell">
                                        <strong>${safe(transaction.referenceNumber || transaction.creditNoteNumber || "-")}</strong>
                                        <small>${safe(transaction.invoiceNumber || transaction.creditNoteNumber || transaction.remarks || "")}</small>
                                    </div>
                                </td>
                                <td class="${amount < 0 ? "negative-amount" : "positive-amount"}">
                                    ${amount < 0 ? "-" : "+"}${money(Math.abs(amount))}
                                </td>
                                <td>
                                    ${canReverse ? `
                                        <button class="reverse-button"
                                                type="button"
                                                data-reverse-id="${safe(transaction.id)}">
                                            Reverse
                                        </button>
                                    ` : `<span class="no-action">—</span>`}
                                </td>
                            </tr>
                        `;
        }).join("") : `
                        <tr><td colspan="5" class="table-empty">No credit transactions found.</td></tr>
                    `}
                    </tbody>
                </table>
            </div>
        `;

        $("open-apply-button").addEventListener("click", openApplyModal);
    }

    function switchType(type) {
        activeType = type;
        selectedAccount = null;
        selectedBalance = null;

        document.querySelectorAll(".credit-tab").forEach(button => {
            button.classList.toggle("active", button.dataset.type === type);
        });

        $("account-search").value = "";
        renderAccounts();
        renderEmptySelection();
    }

    function invoiceMatchesAccount(invoice) {
        const owner = getInvoiceOwner(invoice);

        const selectedCode = accountCode(selectedAccount);
        const selectedName = accountName(selectedAccount);

        return (
            (owner.code && selectedCode &&
                owner.code === selectedCode) ||
            (owner.name && selectedName &&
                owner.name === selectedName)
        );
    }

    function getAvailableInvoices() {
        const invoices = activeType === "CUSTOMER"
            ? salesInvoices
            : purchaseInvoices;

        return invoices.filter(invoice =>
            invoiceMatchesAccount(invoice) &&
            getInvoiceOutstanding(invoice) > 0
        );
    }

    function openApplyModal() {
        if (!selectedAccount || !selectedBalance) return;

        currentInvoices = getAvailableInvoices();

        const select = $("invoice-select");

        select.innerHTML = `
            <option value="">Select an outstanding invoice</option>
            ${currentInvoices.map(invoice => `
                <option value="${safe(invoice.id)}">
                    ${safe(invoice.invoiceNumber)} — Outstanding: ${money(invoice.outstandingAmount)}
                </option>
            `).join("")}
        `;

        $("apply-title").textContent =
            activeType === "CUSTOMER"
                ? "Apply Customer Credit"
                : "Apply Supplier Credit";

        $("apply-subtitle").textContent =
            `Available credit: ${money(selectedBalance.availableCreditBalance)}`;

        $("available-credit-hint").textContent =
            `Available credit: ${money(selectedBalance.availableCreditBalance)}`;

        $("apply-amount").value = "";
        $("apply-remarks").value = "";
        $("invoice-balance-hint").textContent =
            currentInvoices.length
                ? "Select an invoice to view its outstanding balance."
                : "No outstanding invoices found for this account.";

        $("apply-error").textContent = "";
        $("apply-submit").disabled = currentInvoices.length === 0;

        applyModal.hidden = false;
    }

    function selectedInvoice() {
        const id = Number($("invoice-select").value);

        return currentInvoices.find(invoice =>
            Number(invoice.id) === id
        );
    }

    function updateApplyAmountLimit() {
        const invoice = selectedInvoice();
        const amountInput = $("apply-amount");

        if (!invoice) {
            amountInput.max = "";
            $("invoice-balance-hint").textContent =
                "Select an invoice to view its outstanding balance.";
            return;
        }

        const outstanding = getInvoiceOutstanding(invoice);
        const available = number(selectedBalance?.availableCreditBalance);
        const maximum = Math.min(outstanding, available);

        amountInput.max = maximum.toFixed(2);

        $("invoice-balance-hint").textContent =
            `Invoice outstanding: ${money(outstanding)}`;

        amountInput.placeholder = `Maximum ${maximum.toFixed(2)}`;
    }

    async function submitApply(event) {
        event.preventDefault();

        const invoice = selectedInvoice();
        const amount = Number($("apply-amount").value);
        const available = number(selectedBalance?.availableCreditBalance);

        $("apply-error").textContent = "";

        if (!invoice) {
            $("apply-error").textContent = "Please select an invoice.";
            return;
        }

        if (!Number.isFinite(amount) || amount <= 0) {
            $("apply-error").textContent =
                "Enter an amount greater than zero.";
            return;
        }

        if (Math.round(amount * 100) > Math.round(available * 100)) {
            $("apply-error").textContent =
                "Amount exceeds the available credit balance.";
            return;
        }

        if (Math.round(amount * 100) >
            Math.round(getInvoiceOutstanding(invoice) * 100)) {
            $("apply-error").textContent =
                "Amount exceeds the invoice outstanding balance.";
            return;
        }

        const payload = activeType === "CUSTOMER"
            ? {
                customerId: selectedAccount.id,
                salesInvoiceId: invoice.id,
                amount: amount,
                remarks: $("apply-remarks").value.trim()
            }
            : {
                supplierId: selectedAccount.id,
                purchaseInvoiceId: invoice.id,
                amount: amount,
                remarks: $("apply-remarks").value.trim()
            };

        const endpoint = activeType === "CUSTOMER"
            ? `${API.customerCredits}/apply`
            : `${API.supplierCredits}/apply`;

        const button = $("apply-submit");
        button.disabled = true;
        button.textContent = "Applying...";

        try {
            await request(endpoint, {
                method: "POST",
                headers: {
                    "Idempotency-Key": createIdempotencyKey()
                },
                body: JSON.stringify(payload)
            });

            applyModal.hidden = true;
            showMessage("Credit has been applied successfully.", true);

            await refreshSelectedAndSummary();
        } catch (error) {
            $("apply-error").textContent =
                error.message || "Unable to apply credit.";
        } finally {
            button.disabled = false;
            button.textContent = "Apply Credit";
        }
    }

    function createIdempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return `CREDIT-${window.crypto.randomUUID()}`;
        }

        return `CREDIT-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
    }

    function openReverseModal(transactionId) {
        $("reverse-transaction-id").value = transactionId;
        $("reverse-remarks").value = "";
        $("reverse-error").textContent = "";
        reverseModal.hidden = false;
    }

    async function submitReverse(event) {
        event.preventDefault();

        const transactionId = $("reverse-transaction-id").value;
        const remarks = $("reverse-remarks").value.trim();

        if (!remarks) {
            $("reverse-error").textContent =
                "Please enter the reason for reversal.";
            return;
        }

        const endpoint = activeType === "CUSTOMER"
            ? `${API.customerCredits}/reverse/${transactionId}`
            : `${API.supplierCredits}/reverse/${transactionId}`;

        const button = $("reverse-submit");
        button.disabled = true;
        button.textContent = "Reversing...";

        try {
            await request(endpoint, {
                method: "POST",
                body: JSON.stringify({ remarks })
            });

            reverseModal.hidden = true;
            showMessage("Credit application reversed successfully.", true);

            await refreshSelectedAndSummary();
        } catch (error) {
            $("reverse-error").textContent =
                error.message || "Unable to reverse credit application.";
        } finally {
            button.disabled = false;
            button.textContent = "Confirm Reversal";
        }
    }

    async function refreshSelectedAndSummary() {
        await loadAllBalances();

        if (selectedAccount) {
            const refreshed = getAccounts().find(
                account => Number(account.id) === Number(selectedAccount.id)
            );

            if (refreshed) {
                selectedAccount = refreshed;
                await loadSelectedAccount();
            }
        }

        renderAccounts();
    }

    function closeModal(modal) {
        modal.hidden = true;
    }

    document.querySelectorAll(".credit-tab").forEach(button => {
        button.addEventListener("click", () => switchType(button.dataset.type));
    });

    accountBody.addEventListener("click", async event => {
        const button = event.target.closest("[data-account-id]");
        if (!button) return;

        const id = Number(button.dataset.accountId);
        selectedAccount = getAccounts().find(
            account => Number(account.id) === id
        );

        selectedBalance = null;
        renderAccounts();
        await loadSelectedAccount();
    });

    details.addEventListener("click", event => {
        const button = event.target.closest("[data-reverse-id]");
        if (button) openReverseModal(button.dataset.reverseId);
    });

    $("account-search").addEventListener("input", renderAccounts);
    $("refresh-button").addEventListener("click", loadAll);

    $("invoice-select").addEventListener("change", updateApplyAmountLimit);
    $("apply-form").addEventListener("submit", submitApply);
    $("reverse-form").addEventListener("submit", submitReverse);

    document.querySelectorAll("[data-close='true']").forEach(button => {
        button.addEventListener("click", () => {
            closeModal(button.closest(".credits-modal"));
        });
    });

    [applyModal, reverseModal].forEach(modal => {
        modal.addEventListener("click", event => {
            if (event.target.classList.contains("modal-backdrop")) {
                closeModal(modal);
            }
        });
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            closeModal(applyModal);
            closeModal(reverseModal);
        }
    });

    loadAll();
})();
