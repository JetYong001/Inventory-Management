
"use strict";

/* ========================================
   CONFIGURATION
======================================== */

const API_BASE_URL = "";
const PAGE_SIZE = 10;

let suppliers = [];
let customers = [];
let partners = [];

let activeTab = "all";
let currentPage = 0;

let editingPartner = null;
let selectedPartner = null;
let isSaving = false;

const elements = {
    totalPartners: document.getElementById("total-partners"),
    totalSuppliers: document.getElementById("total-suppliers"),
    totalCustomers: document.getElementById("total-customers"),
    activePartners: document.getElementById("active-partners"),

    allTabCount: document.getElementById("all-tab-count"),
    supplierTabCount: document.getElementById("supplier-tab-count"),
    customerTabCount: document.getElementById("customer-tab-count"),

    pageMessage: document.getElementById("page-message"),

    search: document.getElementById("partner-search"),
    statusFilter: document.getElementById("partner-status"),

    tableBody: document.getElementById("partners-table-body"),
    paginationInfo: document.getElementById("pagination-info"),
    currentPage: document.getElementById("current-page"),
    previousPage: document.getElementById("previous-page"),
    nextPage: document.getElementById("next-page"),

    addButton: document.getElementById("add-partner-button"),

    modal: document.getElementById("partner-modal"),
    modalTitle: document.getElementById("partner-modal-title"),
    modalDescription: document.getElementById("partner-modal-description"),
    form: document.getElementById("partner-form"),
    formMessage: document.getElementById("form-message"),
    saveButton: document.getElementById("save-partner-button"),

    closeModal: document.getElementById("close-partner-modal"),
    cancelModal: document.getElementById("cancel-partner-modal"),

    partnerType: document.getElementById("partner-type"),
    partnerName: document.getElementById("partner-name"),
    contactPerson: document.getElementById("contact-person"),
    contactPersonField: document.getElementById("contact-person-field"),
    phone: document.getElementById("partner-phone"),
    email: document.getElementById("partner-email"),
    address: document.getElementById("partner-address"),

    detailsModal: document.getElementById("details-modal"),
    closeDetails: document.getElementById("close-details-modal"),
    closeDetailsButton: document.getElementById("close-details-button"),
    editFromDetails: document.getElementById("edit-from-details")
};


/* ========================================
   API
======================================== */

async function apiRequest(endpoint, options = {}) {

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers: {
            Accept: "application/json",
            ...(options.body ? {
                "Content-Type": "application/json"
            } : {}),
            ...options.headers
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
            data ||
            response.statusText;

        throw new Error(`${response.status}: ${message}`);
    }

    return data;
}


async function loadSuppliers() {
    const result = await apiRequest("/api/suppliers");
    return Array.isArray(result) ? result : [];
}


async function loadCustomers() {
    const result = await apiRequest("/api/customers");
    return Array.isArray(result) ? result : [];
}


/* ========================================
   INITIALIZATION
======================================== */

document.addEventListener("DOMContentLoaded", initializePartners);


async function initializePartners() {

    bindEvents();

    elements.tableBody.innerHTML = `
        <tr>
            <td colspan="7" class="table-loading">
                Loading business partners...
            </td>
        </tr>
    `;

    const results = await Promise.allSettled([
        loadSuppliers(),
        loadCustomers()
    ]);

    let hasError = false;

    if (results[0].status === "fulfilled") {
        suppliers = results[0].value;
    } else {
        hasError = true;
        showPageMessage(
            `Unable to load suppliers: ${results[0].reason.message}`
        );
    }

    if (results[1].status === "fulfilled") {
        customers = results[1].value;
    } else {
        hasError = true;
        showPageMessage(
            `Unable to load customers: ${results[1].reason.message}`
        );
    }

    if (hasError && results.every(result => result.status === "rejected")) {
        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-empty">
                    Unable to load business partners.
                </td>
            </tr>
        `;
        return;
    }

    rebuildPartners();
    updateSummary();
    renderPartners();
}


/* ========================================
   EVENTS
======================================== */

function bindEvents() {

    elements.addButton.addEventListener("click", () => {
        openPartnerModal();
    });

    elements.closeModal.addEventListener("click", closePartnerModal);
    elements.cancelModal.addEventListener("click", closePartnerModal);

    elements.modal.addEventListener("click", event => {
        if (event.target.matches("[data-close-modal]")) {
            closePartnerModal();
        }
    });

    elements.form.addEventListener("submit", savePartner);

    elements.partnerType.addEventListener("change", updatePartnerTypeFields);

    elements.search.addEventListener("input", () => {
        currentPage = 0;
        renderPartners();
    });

    elements.statusFilter.addEventListener("change", () => {
        currentPage = 0;
        renderPartners();
    });

    document.querySelectorAll(".partner-tab").forEach(tab => {
        tab.addEventListener("click", () => {
            activeTab = tab.dataset.tab;
            currentPage = 0;

            document.querySelectorAll(".partner-tab").forEach(item => {
                item.classList.toggle(
                    "is-active",
                    item.dataset.tab === activeTab
                );
            });

            renderPartners();
        });
    });

    elements.previousPage.addEventListener("click", () => {
        if (currentPage > 0) {
            currentPage--;
            renderPartners();
        }
    });

    elements.nextPage.addEventListener("click", () => {
        const totalPages = Math.ceil(
            getFilteredPartners().length / PAGE_SIZE
        );

        if (currentPage < totalPages - 1) {
            currentPage++;
            renderPartners();
        }
    });

    elements.tableBody.addEventListener("click", handleTableAction);

    elements.closeDetails.addEventListener("click", closeDetailsModal);
    elements.closeDetailsButton.addEventListener("click", closeDetailsModal);

    elements.detailsModal.addEventListener("click", event => {
        if (event.target.matches("[data-close-details]")) {
            closeDetailsModal();
        }
    });

    elements.editFromDetails.addEventListener("click", () => {
        if (!selectedPartner) return;

        const partner = selectedPartner;

        closeDetailsModal();
        openPartnerModal(partner);
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            if (!elements.modal.hidden) {
                closePartnerModal();
            }

            if (!elements.detailsModal.hidden) {
                closeDetailsModal();
            }
        }
    });
}


/* ========================================
   DATA
======================================== */

function rebuildPartners() {

    partners = [
        ...suppliers.map(item => ({
            ...item,
            partnerType: "supplier",
            partnerCode: item.supplierCode,
            partnerName: item.supplierName,
            contact: item.contactPerson || ""
        })),

        ...customers.map(item => ({
            ...item,
            partnerType: "customer",
            partnerCode: item.customerCode,
            partnerName: item.customerName,
            contact: ""
        }))
    ];
}


function updateSummary() {

    const activeCount = partners.filter(
        partner => partner.active === true
    ).length;

    elements.totalPartners.textContent = partners.length.toLocaleString();
    elements.totalSuppliers.textContent = suppliers.length.toLocaleString();
    elements.totalCustomers.textContent = customers.length.toLocaleString();
    elements.activePartners.textContent = activeCount.toLocaleString();

    elements.allTabCount.textContent = partners.length;
    elements.supplierTabCount.textContent = suppliers.length;
    elements.customerTabCount.textContent = customers.length;
}


/* ========================================
   FILTERING
======================================== */

function getFilteredPartners() {

    const keyword = elements.search.value.trim().toLowerCase();
    const status = elements.statusFilter.value;

    return partners.filter(partner => {

        const matchesTab =
            activeTab === "all" ||
            partner.partnerType === activeTab;

        const searchableValues = [
            partner.partnerCode,
            partner.partnerName,
            partner.contact,
            partner.phone,
            partner.email
        ];

        const matchesSearch =
            !keyword ||
            searchableValues.some(value =>
                String(value || "").toLowerCase().includes(keyword)
            );

        const matchesStatus =
            status === "all" ||
            (status === "active" && partner.active === true) ||
            (status === "inactive" && partner.active === false);

        return matchesTab && matchesSearch && matchesStatus;
    });
}


/* ========================================
   RENDER
======================================== */

function renderPartners() {

    const filteredPartners = getFilteredPartners();

    const total = filteredPartners.length;
    const totalPages = Math.ceil(total / PAGE_SIZE);

    if (totalPages > 0 && currentPage >= totalPages) {
        currentPage = totalPages - 1;
    }

    const startIndex = currentPage * PAGE_SIZE;

    const visiblePartners = filteredPartners.slice(
        startIndex,
        startIndex + PAGE_SIZE
    );

    if (visiblePartners.length === 0) {
        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-empty">
                    No business partners found.
                </td>
            </tr>
        `;
    } else {
        elements.tableBody.innerHTML = visiblePartners
            .map(renderPartnerRow)
            .join("");
    }

    const start = total === 0 ? 0 : startIndex + 1;
    const end = Math.min(startIndex + PAGE_SIZE, total);

    elements.paginationInfo.textContent =
        `Showing ${start}-${end} of ${total} partners`;

    elements.currentPage.textContent =
        totalPages === 0 ? "0" : `${currentPage + 1} / ${totalPages}`;

    elements.previousPage.disabled = currentPage === 0;
    elements.nextPage.disabled =
        totalPages === 0 || currentPage >= totalPages - 1;
}


function renderPartnerRow(partner) {

    const typeLabel =
        partner.partnerType === "supplier" ? "Supplier" : "Customer";

    const statusLabel =
        partner.active === true ? "Active" : "Inactive";

    const contactPerson =
        partner.contact || "—";

    return `
        <tr>
            <td>
                <div class="partner-name-cell">
                    <div class="partner-avatar">
                        ${escapeHTML(getInitial(partner.partnerName))}
                    </div>

                    <div class="partner-name-info">
                        <strong>${escapeHTML(partner.partnerName)}</strong>
                        <span>${escapeHTML(partner.partnerCode || "—")}</span>
                    </div>
                </div>
            </td>

            <td>
                <span class="type-badge ${partner.partnerType}">
                    ${typeLabel}
                </span>
            </td>

            <td>${escapeHTML(contactPerson)}</td>

            <td>${escapeHTML(partner.phone || "—")}</td>

            <td>${escapeHTML(partner.email || "—")}</td>

            <td>
                <span class="status-badge ${partner.active ? "active" : "inactive"}">
                    ${statusLabel}
                </span>
            </td>

            <td>
                <div class="action-buttons">
                    <button type="button"
                            class="table-action"
                            data-action="view"
                            data-type="${partner.partnerType}"
                            data-id="${partner.id}">
                        View
                    </button>

                    <button type="button"
                            class="table-action edit"
                            data-action="edit"
                            data-type="${partner.partnerType}"
                            data-id="${partner.id}">
                        Edit
                    </button>
                </div>
            </td>
        </tr>
    `;
}


/* ========================================
   TABLE ACTIONS
======================================== */

function handleTableAction(event) {

    const button = event.target.closest("[data-action]");

    if (!button) return;

    const type = button.dataset.type;
    const id = Number(button.dataset.id);
    const action = button.dataset.action;

    const partner = partners.find(item =>
        item.partnerType === type &&
        Number(item.id) === id
    );

    if (!partner) {
        showPageMessage("Partner record could not be found.");
        return;
    }

    if (action === "view") {
        openDetailsModal(partner);
    }

    if (action === "edit") {
        openPartnerModal(partner);
    }
}


/* ========================================
   ADD / EDIT MODAL
======================================== */

function openPartnerModal(partner = null) {

    editingPartner = partner;

    elements.form.reset();
    elements.formMessage.textContent = "";
    elements.formMessage.classList.remove("show");

    elements.modal.hidden = false;

    if (partner) {

        elements.modalTitle.textContent = "Edit Partner";
        elements.modalDescription.textContent =
            "Update the business partner information.";

        elements.partnerType.value = partner.partnerType;
        elements.partnerName.value = partner.partnerName || "";
        elements.contactPerson.value = partner.contact || "";
        elements.phone.value = partner.phone || "";
        elements.email.value = partner.email || "";
        elements.address.value = partner.address || "";

        elements.partnerType.disabled = true;

    } else {

        elements.modalTitle.textContent = "Add Partner";
        elements.modalDescription.textContent =
            "Enter the information for your new business partner.";

        elements.partnerType.disabled = false;
        elements.partnerType.value = activeTab === "all"
            ? "supplier"
            : activeTab;
    }

    updatePartnerTypeFields();

    elements.saveButton.textContent = partner
        ? "Update Partner"
        : "Save Partner";

    elements.partnerName.focus();
}


function closePartnerModal() {

    if (isSaving) return;

    elements.modal.hidden = true;
    elements.form.reset();

    elements.partnerType.disabled = false;
    editingPartner = null;
}


function updatePartnerTypeFields() {

    const isSupplier =
        elements.partnerType.value === "supplier";

    elements.contactPersonField.hidden = !isSupplier;
}


/* ========================================
   SAVE PARTNER
======================================== */

async function savePartner(event) {

    event.preventDefault();

    if (isSaving) return;

    const type = editingPartner
        ? editingPartner.partnerType
        : elements.partnerType.value;

    const name = elements.partnerName.value.trim();
    const phone = elements.phone.value.trim();
    const email = elements.email.value.trim();
    const address = elements.address.value.trim();
    const contactPerson = elements.contactPerson.value.trim();

    if (!name) {
        showFormMessage("Partner name is required.");
        return;
    }

    if (type === "supplier") {

        if (name.length > 150) {
            showFormMessage("Supplier name cannot exceed 150 characters.");
            return;
        }

        if (contactPerson.length > 100) {
            showFormMessage("Contact person cannot exceed 100 characters.");
            return;
        }

        if (phone.length > 30 || email.length > 150 || address.length > 500) {
            showFormMessage("Please check the maximum length of your contact information.");
            return;
        }

    } else {

        if (name.length > 150) {
            showFormMessage("Customer name cannot exceed 150 characters.");
            return;
        }

        if (phone.length > 30 || email.length > 150 || address.length > 500) {
            showFormMessage("Please check the maximum length of your contact information.");
            return;
        }
    }

    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showFormMessage("Please enter a valid email address.");
        return;
    }

    const payload = type === "supplier"
        ? {
            supplierName: name,
            contactPerson,
            phone,
            email,
            address
        }
        : {
            customerName: name,
            phone,
            email,
            address
        };

    const endpoint = type === "supplier"
        ? "/api/suppliers"
        : "/api/customers";

    const requestUrl = editingPartner
        ? `${endpoint}/${editingPartner.id}`
        : endpoint;

    isSaving = true;
    elements.saveButton.disabled = true;
    elements.saveButton.textContent = "Saving...";

    try {

        await apiRequest(requestUrl, {
            method: editingPartner ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });

        const wasEditing = Boolean(editingPartner);

        elements.modal.hidden = true;
        elements.form.reset();
        editingPartner = null;

        await reloadPartners();

        showPageMessage(
            wasEditing
                ? `${capitalize(type)} updated successfully.`
                : `${capitalize(type)} created successfully.`
        );

    } catch (error) {

        showFormMessage(error.message);

    } finally {

        isSaving = false;
        elements.saveButton.disabled = false;

        elements.saveButton.textContent = editingPartner
            ? "Update Partner"
            : "Save Partner";
    }
}


/* ========================================
   DETAILS MODAL
======================================== */

function openDetailsModal(partner) {

    selectedPartner = partner;

    const isSupplier = partner.partnerType === "supplier";

    const typeLabel = isSupplier ? "Supplier" : "Customer";
    const statusLabel = partner.active ? "Active" : "Inactive";

    document.getElementById("details-name").textContent =
        partner.partnerName || "Partner Details";

    document.getElementById("details-code").textContent =
        partner.partnerCode || "—";

    document.getElementById("details-avatar").textContent =
        getInitial(partner.partnerName);

    document.getElementById("details-company-name").textContent =
        partner.partnerName || "—";

    const typeElement = document.getElementById("details-type");
    typeElement.textContent = typeLabel;
    typeElement.className = `type-badge ${partner.partnerType}`;

    const statusElement = document.getElementById("details-status");
    statusElement.textContent = statusLabel;
    statusElement.className =
        `status-badge ${partner.active ? "active" : "inactive"}`;

    document.getElementById("details-partner-code").textContent =
        partner.partnerCode || "—";

    document.getElementById("details-partner-type").textContent =
        typeLabel;

    document.getElementById("details-partner-name").textContent =
        partner.partnerName || "—";

    document.getElementById("details-contact-person").textContent =
        partner.contact || "—";

    document.getElementById("details-contact-wrapper").hidden =
        !isSupplier;

    document.getElementById("details-phone").textContent =
        partner.phone || "—";

    document.getElementById("details-email").textContent =
        partner.email || "—";

    document.getElementById("details-address").textContent =
        partner.address || "—";

    document.getElementById("details-created-date").textContent =
        formatDateTime(partner.createdAt);

    elements.detailsModal.hidden = false;
}


function closeDetailsModal() {
    elements.detailsModal.hidden = true;
    selectedPartner = null;
}


/* ========================================
   RELOAD
======================================== */

async function reloadPartners() {

    const results = await Promise.all([
        loadSuppliers(),
        loadCustomers()
    ]);

    suppliers = results[0];
    customers = results[1];

    rebuildPartners();
    updateSummary();

    currentPage = 0;
    renderPartners();
}


/* ========================================
   HELPERS
======================================== */

function getInitial(value) {

    const name = String(value || "").trim();

    return name ? name.charAt(0).toUpperCase() : "P";
}


function capitalize(value) {
    return value.charAt(0).toUpperCase() + value.slice(1);
}


function formatDateTime(value) {

    if (!value) return "—";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "—";
    }

    return date.toLocaleString("en-MY", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
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


function showPageMessage(message) {

    elements.pageMessage.textContent = message;
    elements.pageMessage.classList.add("show");
}


function showFormMessage(message) {

    elements.formMessage.textContent = message;
    elements.formMessage.classList.add("show");
}
