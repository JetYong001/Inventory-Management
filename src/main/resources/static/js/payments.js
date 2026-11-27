"use strict";

(() => {
    const PAGE_SIZE = 10;

    const API = {
        payments: "/api/payments",
        salesInvoices: "/api/sales-invoices",
        purchaseInvoices: "/api/purchase-invoices"
    };

    let payments = [];
    let salesInvoices = [];
    let purchaseInvoices = [];

    let activeType = "ALL";
    let currentPage = 0;
    let selectedInvoice = null;
    let isSubmitting = false;

    const $ = id => document.getElementById(id);

    const el = {
        total: $("total-payments"),
        customerReceipts: $("customer-receipts"),
        supplierPayments: $("supplier-payments"),
        netCashFlow: $("net-cash-flow"),

        allCount: $("all-count"),
        customerCount: $("customer-count"),
        supplierCount: $("supplier-count"),

        message: $("page-message"),
        search: $("payment-search"),
        methodFilter: $("method-filter"),
        table: $("payment-table-body"),

        previous: $("previous-page"),
        next: $("next-page"),
        page: $("current-page"),
        pagination: $("pagination-info"),

        tabs: document.querySelectorAll(".payment-tab"),

        paymentModal: $("payment-modal"),
        detailsModal: $("details-modal"),
        paymentForm: $("payment-form"),

        paymentType: $("payment-type"),
        invoiceSelect: $("invoice-select"),
        paymentAmount: $("payment-amount"),
        paymentDate: $("payment-date"),
        paymentMethod: $("payment-method"),
        referenceNumber: $("reference-number"),
        remarks: $("payment-remarks"),

        invoiceBalance: $("invoice-balance"),
        invoiceTotal: $("selected-invoice-total"),
        invoiceOutstanding: $("selected-invoice-outstanding"),

        formMessage: $("form-message"),
        submitButton: $("submit-payment-button")
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

    function formatDateTime(value) {
        if (!value) return "-";

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) return value;

        return date.toLocaleString("en-MY");
    }

    function today() {
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, "0");
        const day = String(now.getDate()).padStart(2, "0");

        return `${year}-${month}-${day}`;
    }

    function paymentTypeLabel(type) {
        return type === "CUSTOMER_RECEIPT"
            ? "Customer Receipt"
            : "Supplier Payment";
    }

    function paymentMethodLabel(method) {
        const labels = {
            CASH: "Cash",
            BANK_TRANSFER: "Bank Transfer",
            CARD: "Card",
            E_WALLET: "E-Wallet"
        };

        return labels[method] || method || "-";
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

    function showFormMessage(message) {
        el.formMessage.textContent = message;
        el.formMessage.classList.add("show");
    }

    function clearFormMessage() {
        el.formMessage.textContent = "";
        el.formMessage.classList.remove("show");
    }

    async function initialize() {
        bindEvents();

        el.paymentDate.value = today();

        try {
            await loadAll();
            render();
        } catch (error) {
            showMessage(`Unable to load payment records: ${error.message}`);

            el.table.innerHTML = `
                <tr>
                    <td colspan="8" class="table-empty">
                        Unable to load payment records.
                    </td>
                </tr>
            `;
        }
    }

    async function loadAll() {
        const results = await Promise.all([
            apiRequest(API.payments),
            apiRequest(API.salesInvoices),
            apiRequest(API.purchaseInvoices)
        ]);

        payments = normalizeArray(results[0]);
        salesInvoices = normalizeArray(results[1]);
        purchaseInvoices = normalizeArray(results[2]);
    }

    function bindEvents() {
        el.tabs.forEach(tab => {
            tab.addEventListener("click", () => {
                activeType = tab.dataset.type;
                currentPage = 0;

                el.tabs.forEach(item => {
                    item.classList.toggle("active", item === tab);
                });

                renderTable();
            });
        });

        el.search.addEventListener("input", () => {
            currentPage = 0;
            renderTable();
        });

        el.methodFilter.addEventListener("change", () => {
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
                getFilteredPayments().length / PAGE_SIZE
            );

            if (currentPage < totalPages - 1) {
                currentPage++;
                renderTable();
            }
        });

        el.table.addEventListener("click", handleTableAction);

        $("add-payment-button").addEventListener("click", openPaymentModal);

        $("close-payment-modal").addEventListener("click", closePaymentModal);
        $("cancel-payment-button").addEventListener("click", closePaymentModal);

        el.paymentModal.addEventListener("click", event => {
            if (event.target.matches("[data-close-payment]")) {
                closePaymentModal();
            }
        });

        el.paymentType.addEventListener("change", () => {
            selectedInvoice = null;
            el.paymentAmount.value = "";
            loadInvoiceOptions();
        });

        el.invoiceSelect.addEventListener("change", handleInvoiceSelection);

        el.paymentAmount.addEventListener("input", validatePaymentAmount);

        el.paymentForm.addEventListener("submit", submitPayment);

        $("close-details-modal").addEventListener("click", closeDetails);
        $("close-details-button").addEventListener("click", closeDetails);

        el.detailsModal.addEventListener("click", event => {
            if (event.target.matches("[data-close-details]")) {
                closeDetails();
            }
        });
    }

    function render() {
        renderSummary();
        renderCounts();
        renderTable();
    }

    function renderSummary() {
        const customerTotal = payments
            .filter(payment => payment.paymentType === "CUSTOMER_RECEIPT")
            .reduce((sum, payment) => sum + Number(payment.amount || 0), 0);

        const supplierTotal = payments
            .filter(payment => payment.paymentType === "SUPPLIER_PAYMENT")
            .reduce((sum, payment) => sum + Number(payment.amount || 0), 0);

        el.total.textContent = payments.length;
        el.customerReceipts.textContent = money(customerTotal);
        el.supplierPayments.textContent = money(supplierTotal);
        el.netCashFlow.textContent = money(customerTotal - supplierTotal);
    }

    function renderCounts() {
        el.allCount.textContent = payments.length;

        el.customerCount.textContent = payments.filter(
            payment => payment.paymentType === "CUSTOMER_RECEIPT"
        ).length;

        el.supplierCount.textContent = payments.filter(
            payment => payment.paymentType === "SUPPLIER_PAYMENT"
        ).length;
    }

    function getFilteredPayments() {
        const keyword = el.search.value.trim().toLowerCase();
        const method = el.methodFilter.value;

        return payments.filter(payment => {
            const invoiceNumber =
                payment.salesInvoiceNumber ||
                payment.purchaseInvoiceNumber ||
                "";

            const matchesType =
                activeType === "ALL" ||
                payment.paymentType === activeType;

            const matchesMethod =
                method === "ALL" ||
                payment.paymentMethod === method;

            const searchable = [
                payment.paymentNumber,
                payment.paymentType,
                payment.paymentMethod,
                payment.referenceNumber,
                payment.remarks,
                invoiceNumber
            ];

            const matchesSearch = searchable.some(value =>
                String(value || "").toLowerCase().includes(keyword)
            );

            return matchesType && matchesMethod && matchesSearch;
        });
    }

    function renderTable() {
        const filtered = getFilteredPayments();

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
                    <td colspan="8" class="table-empty">
                        No payment records found.
                    </td>
                </tr>
            `;
        } else {
            el.table.innerHTML = pageItems.map(payment => {
                const invoiceNumber =
                    payment.salesInvoiceNumber ||
                    payment.purchaseInvoiceNumber ||
                    "-";

                const typeClass =
                    payment.paymentType === "CUSTOMER_RECEIPT"
                        ? "customer"
                        : "supplier";

                return `
                    <tr>
                        <td>
                            <div class="payment-number">
                                <strong>${safe(payment.paymentNumber)}</strong>
                                <small>ID: ${safe(payment.id)}</small>
                            </div>
                        </td>

                        <td>
                            <span class="type-badge ${typeClass}">
                                ${safe(paymentTypeLabel(payment.paymentType))}
                            </span>
                        </td>

                        <td>
                            <div class="invoice-cell">
                                <strong>${safe(invoiceNumber)}</strong>
                            </div>
                        </td>

                        <td>${safe(formatDate(payment.paymentDate))}</td>

                        <td>${safe(paymentMethodLabel(payment.paymentMethod))}</td>

                        <td>${safe(payment.referenceNumber || "-")}</td>

                        <td class="amount-cell">
                            ${money(payment.amount)}
                        </td>

                        <td>
                            <div class="row-actions">
                                <button class="table-action"
                                        data-action="view"
                                        data-id="${safe(payment.id)}">
                                    View
                                </button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join("");
        }

        el.pagination.textContent = filtered.length === 0
            ? "Showing 0 payments"
            : `Showing ${start + 1}-${Math.min(start + PAGE_SIZE, filtered.length)} of ${filtered.length} payments`;

        el.page.textContent = currentPage + 1;
        el.previous.disabled = currentPage === 0;
        el.next.disabled = currentPage >= totalPages - 1;
    }

    async function handleTableAction(event) {
        const button = event.target.closest("[data-action='view']");

        if (!button) return;

        const paymentId = button.dataset.id;

        try {
            const payment = await apiRequest(
                `${API.payments}/${encodeURIComponent(paymentId)}`
            );

            renderDetails(payment);
            el.detailsModal.hidden = false;
        } catch (error) {
            showMessage(`Unable to load payment details: ${error.message}`);
        }
    }

    function renderDetails(payment) {
        const invoiceNumber =
            payment.salesInvoiceNumber ||
            payment.purchaseInvoiceNumber ||
            "-";

        $("details-payment-number").textContent =
            payment.paymentNumber || "Payment";

        $("details-type").textContent =
            paymentTypeLabel(payment.paymentType);

        $("details-method").textContent =
            paymentMethodLabel(payment.paymentMethod);

        $("details-date").textContent =
            formatDate(payment.paymentDate);

        $("details-amount").textContent =
            money(payment.amount);

        $("details-invoice").textContent = invoiceNumber;

        $("details-reference").textContent =
            payment.referenceNumber || "-";

        $("details-remarks").textContent =
            payment.remarks || "-";

        $("details-created-at").textContent =
            formatDateTime(payment.createdAt);
    }

    function openPaymentModal() {
        clearFormMessage();
        clearMessage();

        el.paymentForm.reset();
        el.paymentDate.value = today();

        selectedInvoice = null;

        el.invoiceBalance.hidden = true;
        el.paymentAmount.removeAttribute("max");

        loadInvoiceOptions();

        el.paymentModal.hidden = false;
    }

    function closePaymentModal() {
        if (isSubmitting) return;

        el.paymentModal.hidden = true;
        selectedInvoice = null;
    }

    function closeDetails() {
        el.detailsModal.hidden = true;
    }

    function getAvailableInvoices() {
        const type = el.paymentType.value;

        const source = type === "CUSTOMER_RECEIPT"
            ? salesInvoices
            : purchaseInvoices;

        return source.filter(invoice => {
            const outstanding = Number(invoice.outstandingAmount || 0);

            return outstanding > 0.009;
        });
    }

    function loadInvoiceOptions() {
        const invoices = getAvailableInvoices();

        const type = el.paymentType.value;

        const partyLabel = type === "CUSTOMER_RECEIPT"
            ? "Customer"
            : "Supplier";

        el.invoiceSelect.innerHTML = `
            <option value="">Select an invoice</option>
            ${invoices.map(invoice => {
            const partyName = type === "CUSTOMER_RECEIPT"
                ? invoice.customerName
                : invoice.supplierName;

            const invoiceNumber = invoice.invoiceNumber || "-";

            return `
                    <option value="${safe(invoice.id)}">
                        ${safe(invoiceNumber)} - ${safe(partyName || "Unknown")} - Outstanding ${money(invoice.outstandingAmount)}
                    </option>
                `;
        }).join("")}
        `;

        $("invoice-hint").textContent = invoices.length > 0
            ? `${invoices.length} invoice(s) with outstanding balance.`
            : `No invoices with outstanding balance for this ${partyLabel.toLowerCase()}.`;

        el.invoiceBalance.hidden = true;
        el.paymentAmount.value = "";
        el.paymentAmount.removeAttribute("max");
    }

    function handleInvoiceSelection() {
        const invoiceId = Number(el.invoiceSelect.value);

        if (!invoiceId) {
            selectedInvoice = null;
            el.invoiceBalance.hidden = true;
            el.paymentAmount.value = "";
            el.paymentAmount.removeAttribute("max");
            return;
        }

        const invoices = getAvailableInvoices();

        selectedInvoice = invoices.find(
            invoice => Number(invoice.id) === invoiceId
        );

        if (!selectedInvoice) {
            el.invoiceBalance.hidden = true;
            return;
        }

        const total = Number(selectedInvoice.totalAmount || 0);
        const outstanding = Number(selectedInvoice.outstandingAmount || 0);

        el.invoiceBalance.hidden = false;
        el.invoiceTotal.textContent = money(total);
        el.invoiceOutstanding.textContent = money(outstanding);

        el.paymentAmount.max = outstanding.toFixed(2);
        el.paymentAmount.value = outstanding.toFixed(2);

        validatePaymentAmount();
    }

    function validatePaymentAmount() {
        const amount = Number(el.paymentAmount.value);
        const outstanding = Number(
            selectedInvoice?.outstandingAmount || 0
        );

        if (!selectedInvoice) {
            el.paymentAmount.setCustomValidity("");
            return false;
        }

        if (!Number.isFinite(amount) || amount <= 0) {
            el.paymentAmount.setCustomValidity(
                "Payment amount must be greater than zero."
            );
            return false;
        }

        if (amount > outstanding + 0.000001) {
            el.paymentAmount.setCustomValidity(
                `Payment cannot exceed ${money(outstanding)}.`
            );
            return false;
        }

        if (Math.round(amount * 100) !== amount * 100) {
            el.paymentAmount.setCustomValidity(
                "Payment amount can only have two decimal places."
            );
            return false;
        }

        el.paymentAmount.setCustomValidity("");
        return true;
    }

    async function submitPayment(event) {
        event.preventDefault();

        if (isSubmitting) return;

        clearFormMessage();

        if (!el.paymentForm.reportValidity()) return;

        if (!selectedInvoice) {
            showFormMessage("Please select an invoice.");
            return;
        }

        if (!validatePaymentAmount()) {
            el.paymentAmount.reportValidity();
            return;
        }

        const amount = Number(el.paymentAmount.value);
        const outstanding = Number(selectedInvoice.outstandingAmount || 0);

        if (amount > outstanding + 0.000001) {
            showFormMessage(
                `Payment amount cannot exceed ${money(outstanding)}.`
            );
            return;
        }

        const request = {
            amount: amount.toFixed(2),
            paymentMethod: el.paymentMethod.value,
            paymentDate: el.paymentDate.value,
            referenceNumber: el.referenceNumber.value.trim() || null,
            remarks: el.remarks.value.trim() || null
        };

        const type = el.paymentType.value;

        const endpoint = type === "CUSTOMER_RECEIPT"
            ? `${API.payments}/sales-invoice/${selectedInvoice.id}`
            : `${API.payments}/purchase-invoice/${selectedInvoice.id}`;

        isSubmitting = true;
        el.submitButton.disabled = true;
        el.submitButton.textContent = "Saving...";

        try {
            await apiRequest(endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Idempotency-Key": createIdempotencyKey()
                },
                body: JSON.stringify(request)
            });

            el.paymentModal.hidden = true;

            await loadAll();

            activeType = "ALL";
            currentPage = 0;

            el.tabs.forEach(tab => {
                tab.classList.toggle(
                    "active",
                    tab.dataset.type === "ALL"
                );
            });

            render();

            showMessage("Payment recorded successfully.", true);

        } catch (error) {
            showFormMessage(error.message);
        } finally {
            isSubmitting = false;
            el.submitButton.disabled = false;
            el.submitButton.textContent = "Save Payment";
        }
    }

    function createIdempotencyKey() {
        if (window.crypto?.randomUUID) {
            return `PAY-${window.crypto.randomUUID()}`;
        }

        return `PAY-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
    }

})();