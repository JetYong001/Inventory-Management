
(() => {
    "use strict";

    const API = {
        sales: "/api/sales-credit-notes",
        purchase: "/api/purchase-credit-notes"
    };

    const $ = id => document.getElementById(id);

    const el = {
        table: $("credit-table-body"),
        search: $("credit-search"),
        status: $("status-filter"),
        message: $("page-message"),
        modal: $("details-modal"),
        refresh: $("refresh-button"),
        previous: $("previous-page"),
        next: $("next-page")
    };

    let salesNotes = [];
    let purchaseNotes = [];
    let activeType = "SALES";
    let currentPage = 1;
    const pageSize = 10;

    function money(value) {
        return new Intl.NumberFormat("en-MY", {
            style: "currency",
            currency: "MYR",
            minimumFractionDigits: 2
        }).format(Number(value || 0));
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

        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) return "-";

        return parsed.toLocaleDateString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        });
    }

    function formatDateTime(value) {
        if (!value) return "-";

        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) return "-";

        return parsed.toLocaleString("en-MY", {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    async function request(url) {
        const response = await fetch(url, {
            headers: { "Accept": "application/json" }
        });

        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || `Request failed (${response.status})`);
        }

        return response.json();
    }

    function showMessage(message, success = false) {
        el.message.textContent = message;
        el.message.className = `page-message show${success ? " success" : ""}`;
    }

    function hideMessage() {
        el.message.textContent = "";
        el.message.className = "page-message";
    }

    function getCurrentNotes() {
        return activeType === "SALES" ? salesNotes : purchaseNotes;
    }

    function updateSummary() {
        const all = [...salesNotes, ...purchaseNotes];

        $("total-count").textContent = all.length;
        $("sales-count").textContent = salesNotes.length;
        $("purchase-count").textContent = purchaseNotes.length;

        $("sales-tab-count").textContent = salesNotes.length;
        $("purchase-tab-count").textContent = purchaseNotes.length;

        const total = all.reduce(
            (sum, note) => sum + Number(note.totalAmount || 0), 0
        );

        $("total-amount").textContent = money(total);
    }

    async function loadData() {
        el.refresh.disabled = true;
        el.table.innerHTML =
            `<tr><td colspan="7" class="table-empty">Loading credit notes...</td></tr>`;

        hideMessage();

        try {
            const [sales, purchases] = await Promise.all([
                request(API.sales),
                request(API.purchase)
            ]);

            if (!Array.isArray(sales) || !Array.isArray(purchases)) {
                throw new Error("Unexpected credit note response from server.");
            }

            salesNotes = sales;
            purchaseNotes = purchases;

            updateSummary();
            render();
        } catch (error) {
            el.table.innerHTML =
                `<tr><td colspan="7" class="table-empty">Unable to load credit notes.</td></tr>`;
            showMessage(error.message || "Unable to load credit notes.");
        } finally {
            el.refresh.disabled = false;
        }
    }

    function getFilteredNotes() {
        const keyword = el.search.value.trim().toLowerCase();
        const status = el.status.value;

        return getCurrentNotes().filter(note => {
            const matchesStatus =
                status === "ALL" || note.status === status;

            const searchable = [
                note.creditNoteNumber,
                note.returnNumber,
                note.invoiceNumber,
                note.status
            ].join(" ").toLowerCase();

            return matchesStatus && searchable.includes(keyword);
        }).sort((a, b) => {
            return new Date(b.creditNoteDate || b.createdAt || 0)
                - new Date(a.creditNoteDate || a.createdAt || 0);
        });
    }

    function statusClass(status) {
        return String(status || "").toLowerCase();
    }

    function render() {
        const records = getFilteredNotes();
        const totalPages = Math.max(1, Math.ceil(records.length / pageSize));

        if (currentPage > totalPages) currentPage = totalPages;

        const start = (currentPage - 1) * pageSize;
        const pageRecords = records.slice(start, start + pageSize);

        if (!pageRecords.length) {
            el.table.innerHTML =
                `<tr><td colspan="7" class="table-empty">No credit notes found.</td></tr>`;
        } else {
            el.table.innerHTML = pageRecords.map(note => `
                <tr>
                    <td>
                        <div class="credit-number">
                            <strong>${safe(note.creditNoteNumber)}</strong>
                            <small>ID: ${safe(note.id)}</small>
                        </div>
                    </td>
                    <td>${safe(note.returnNumber || "-")}</td>
                    <td>${safe(note.invoiceNumber || "-")}</td>
                    <td>${formatDate(note.creditNoteDate)}</td>
                    <td class="amount-cell">${money(note.totalAmount)}</td>
                    <td>
                        <span class="status-badge ${statusClass(note.status)}">
                            ${safe(note.status || "-")}
                        </span>
                    </td>
                    <td>
                        <button class="table-action" type="button"
                                data-view-id="${safe(note.id)}">
                            View
                        </button>
                    </td>
                </tr>
            `).join("");
        }

        const shownStart = records.length ? start + 1 : 0;
        const shownEnd = Math.min(start + pageSize, records.length);

        $("pagination-info").textContent =
            `Showing ${shownStart}-${shownEnd} of ${records.length} records`;

        $("page-number").textContent = `${currentPage} / ${totalPages}`;
        el.previous.disabled = currentPage <= 1;
        el.next.disabled = currentPage >= totalPages;
    }

    function switchTab(type) {
        activeType = type;
        currentPage = 1;

        document.querySelectorAll(".credit-tab").forEach(button => {
            button.classList.toggle("active", button.dataset.type === type);
        });

        render();
    }

    function openDetails(id) {
        const note = getCurrentNotes().find(item => Number(item.id) === Number(id));
        if (!note) return;

        const isSales = activeType === "SALES";

        $("details-title").textContent = note.creditNoteNumber || "Credit Note";
        $("details-subtitle").textContent =
            `${isSales ? "Sales" : "Purchase"} credit note details`;

        $("document-type").textContent =
            isSales ? "SALES CREDIT NOTE" : "PURCHASE CREDIT NOTE";

        $("document-number").textContent = note.creditNoteNumber || "-";
        $("document-status").textContent = note.status || "-";
        $("document-status").className =
            `status-badge ${statusClass(note.status)}`;

        $("document-return").textContent = note.returnNumber || "-";
        $("document-invoice").textContent = note.invoiceNumber || "-";
        $("document-date").textContent = formatDateTime(note.creditNoteDate);
        $("document-created").textContent = formatDateTime(note.createdAt);
        $("document-amount").textContent = money(note.totalAmount);

        el.modal.hidden = false;
    }

    function closeDetails() {
        el.modal.hidden = true;
    }

    document.querySelectorAll(".credit-tab").forEach(button => {
        button.addEventListener("click", () => switchTab(button.dataset.type));
    });

    el.search.addEventListener("input", () => {
        currentPage = 1;
        render();
    });

    el.status.addEventListener("change", () => {
        currentPage = 1;
        render();
    });

    el.table.addEventListener("click", event => {
        const button = event.target.closest("[data-view-id]");
        if (button) openDetails(button.dataset.viewId);
    });

    el.refresh.addEventListener("click", loadData);

    el.previous.addEventListener("click", () => {
        if (currentPage > 1) {
            currentPage--;
            render();
        }
    });

    el.next.addEventListener("click", () => {
        const totalPages = Math.max(
            1, Math.ceil(getFilteredNotes().length / pageSize)
        );

        if (currentPage < totalPages) {
            currentPage++;
            render();
        }
    });

    $("close-details").addEventListener("click", closeDetails);
    $("close-details-bottom").addEventListener("click", closeDetails);

    el.modal.addEventListener("click", event => {
        if (event.target.dataset.close === "true") closeDetails();
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") closeDetails();
    });

    loadData();
})();
