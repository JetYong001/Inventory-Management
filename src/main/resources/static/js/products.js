
"use strict";

const API_BASE_URL = "";

const PAGE_SIZE = 10;
const API_PAGE_SIZE = 100;

let products = [];
let categories = [];
let currentPage = 0;
let editingProductId = null;
let isSaving = false;

const elements = {
    totalProducts: document.getElementById("total-products"),
    activeProducts: document.getElementById("active-products"),
    lowStockProducts: document.getElementById("low-stock-products"),

    pageMessage: document.getElementById("page-message"),

    search: document.getElementById("search-products"),
    categoryFilter: document.getElementById("category-filter"),
    statusFilter: document.getElementById("status-filter"),

    tableBody: document.getElementById("products-table-body"),
    paginationInfo: document.getElementById("pagination-info"),
    currentPage: document.getElementById("current-page"),
    previousPage: document.getElementById("previous-page"),
    nextPage: document.getElementById("next-page"),

    addButton: document.getElementById("add-product-button"),

    modal: document.getElementById("product-modal"),
    modalTitle: document.getElementById("modal-title"),
    modalDescription: document.getElementById("modal-description"),
    form: document.getElementById("product-form"),
    formMessage: document.getElementById("form-message"),
    saveButton: document.getElementById("save-product-button"),

    closeModal: document.getElementById("close-modal"),
    cancelModal: document.getElementById("cancel-modal"),

    productName: document.getElementById("product-name"),
    productCategory: document.getElementById("product-category"),
    productDescription: document.getElementById("product-description"),
    costPrice: document.getElementById("cost-price"),
    sellingPrice: document.getElementById("selling-price"),
    minimumStock: document.getElementById("minimum-stock"),
    openingStock: document.getElementById("opening-stock"),
    openingStockRemarks: document.getElementById("opening-stock-remarks")
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

    let responseData = null;

    if (responseText) {
        try {
            responseData = JSON.parse(responseText);
        } catch {
            responseData = responseText;
        }
    }

    if (!response.ok) {
        const message =
            responseData?.message ||
            responseData?.error ||
            responseData ||
            response.statusText;

        throw new Error(
            `${response.status}: ${message}`
        );
    }

    return responseData;
}


async function getAllProducts() {
    const allProducts = [];

    let page = 0;
    let totalPages = 1;

    do {
        const result = await apiRequest(
            `/api/products?page=${page}&size=${API_PAGE_SIZE}&sortBy=productName&direction=asc`
        );

        const pageProducts = Array.isArray(result)
            ? result
            : result.content || [];

        allProducts.push(...pageProducts);

        totalPages = Array.isArray(result)
            ? 1
            : Number(result.totalPages || 1);

        page++;

        if (page > 1000) {
            throw new Error("Too many product pages to load.");
        }

    } while (page < totalPages);

    return allProducts;
}


async function getAllCategories() {
    const result = await apiRequest("/api/categories");

    return Array.isArray(result) ? result : [];
}


/* ========================================
   INITIALIZATION
======================================== */

document.addEventListener("DOMContentLoaded", initializeProducts);

async function initializeProducts() {
    bindEvents();

    elements.tableBody.innerHTML = `
        <tr>
            <td colspan="7" class="table-loading">
                Loading products...
            </td>
        </tr>
    `;

    const results = await Promise.allSettled([
        getAllProducts(),
        getAllCategories()
    ]);

    if (results[0].status === "fulfilled") {
        products = results[0].value;
    } else {
        showPageMessage(
            `Unable to load products: ${results[0].reason.message}`
        );
    }

    if (results[1].status === "fulfilled") {
        categories = results[1].value;
    } else {
        showPageMessage(
            `Unable to load categories: ${results[1].reason.message}`
        );
    }

    populateCategoryFilters();
    updateSummary();
    renderProducts();
}


/* ========================================
   EVENTS
======================================== */

function bindEvents() {

    elements.addButton.addEventListener("click", () => {
        openProductModal();
    });

    elements.closeModal.addEventListener("click", closeProductModal);
    elements.cancelModal.addEventListener("click", closeProductModal);

    elements.modal.addEventListener("click", event => {
        if (
            event.target.matches("[data-close-modal]")
        ) {
            closeProductModal();
        }
    });

    elements.form.addEventListener("submit", saveProduct);

    elements.search.addEventListener("input", () => {
        currentPage = 0;
        renderProducts();
    });

    elements.categoryFilter.addEventListener("change", () => {
        currentPage = 0;
        renderProducts();
    });

    elements.statusFilter.addEventListener("change", () => {
        currentPage = 0;
        renderProducts();
    });

    elements.previousPage.addEventListener("click", () => {
        if (currentPage > 0) {
            currentPage--;
            renderProducts();
        }
    });

    elements.nextPage.addEventListener("click", () => {
        const totalPages = Math.ceil(
            getFilteredProducts().length / PAGE_SIZE
        );

        if (currentPage < totalPages - 1) {
            currentPage++;
            renderProducts();
        }
    });

    elements.tableBody.addEventListener("click", handleTableAction);

    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && !elements.modal.hidden) {
            closeProductModal();
        }
    });
}


/* ========================================
   CATEGORY FILTERS
======================================== */

function populateCategoryFilters() {

    const categoryOptions = categories
        .map(category => `
            <option value="${category.id}">
                ${escapeHtml(category.categoryName)}
            </option>
        `)
        .join("");

    elements.categoryFilter.innerHTML = `
        <option value="">All Categories</option>
        ${categoryOptions}
    `;

    elements.productCategory.innerHTML = `
        <option value="">Select category</option>
        ${categoryOptions}
    `;
}


/* ========================================
   SUMMARY
======================================== */

function updateSummary() {

    const activeProducts = products.filter(
        product => product.active === true
    );

    const lowStockProducts = activeProducts.filter(product => {
        const quantity = Number(product.quantity || 0);
        const minimumStock = Number(product.minimumStock || 0);

        return quantity <= minimumStock;
    });

    elements.totalProducts.textContent = products.length.toLocaleString();

    elements.activeProducts.textContent =
        activeProducts.length.toLocaleString();

    elements.lowStockProducts.textContent =
        lowStockProducts.length.toLocaleString();
}


/* ========================================
   FILTERING
======================================== */

function getFilteredProducts() {

    const keyword = elements.search.value
        .trim()
        .toLowerCase();

    const categoryId = elements.categoryFilter.value;
    const status = elements.statusFilter.value;

    return products.filter(product => {

        const matchesKeyword =
            !keyword ||
            String(product.productName || "")
                .toLowerCase()
                .includes(keyword) ||
            String(product.productCode || "")
                .toLowerCase()
                .includes(keyword);

        const matchesCategory =
            !categoryId ||
            String(product.categoryId) === categoryId;

        const matchesStatus =
            status === "all" ||
            (status === "active" && product.active === true) ||
            (status === "inactive" && product.active === false);

        return (
            matchesKeyword &&
            matchesCategory &&
            matchesStatus
        );
    });
}


/* ========================================
   RENDER PRODUCTS
======================================== */

function renderProducts() {

    const filteredProducts = getFilteredProducts();

    const totalProducts = filteredProducts.length;
    const totalPages = Math.ceil(totalProducts / PAGE_SIZE);

    if (totalPages > 0 && currentPage >= totalPages) {
        currentPage = totalPages - 1;
    }

    const startIndex = currentPage * PAGE_SIZE;

    const visibleProducts = filteredProducts.slice(
        startIndex,
        startIndex + PAGE_SIZE
    );

    if (visibleProducts.length === 0) {

        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="table-empty">
                    No products found.
                </td>
            </tr>
        `;

    } else {

        elements.tableBody.innerHTML = visibleProducts
            .map(renderProductRow)
            .join("");
    }

    const firstItem = totalProducts === 0
        ? 0
        : startIndex + 1;

    const lastItem = Math.min(
        startIndex + PAGE_SIZE,
        totalProducts
    );

    elements.paginationInfo.textContent =
        `Showing ${firstItem}-${lastItem} of ${totalProducts} products`;

    elements.currentPage.textContent =
        totalPages === 0
            ? "0"
            : `${currentPage + 1} / ${totalPages}`;

    elements.previousPage.disabled = currentPage === 0;
    elements.nextPage.disabled =
        totalPages === 0 || currentPage >= totalPages - 1;
}


function renderProductRow(product) {

    const quantity = Number(product.quantity || 0);
    const minimumStock = Number(product.minimumStock || 0);

    const isActive = product.active === true;

    let stockStatus = "In Stock";
    let stockClass = "stock-normal";

    if (quantity <= 0) {
        stockStatus = "Out of Stock";
        stockClass = "stock-out";
    } else if (quantity <= minimumStock) {
        stockStatus = "Low Stock";
        stockClass = "stock-low";
    }

    const statusClass = isActive
        ? "status-active"
        : "status-inactive";

    const statusText = isActive
        ? "Active"
        : "Inactive";

    const categoryName = product.categoryName || "Uncategorised";

    const actions = isActive
        ? `
            <button type="button"
                    class="action-button"
                    data-action="edit"
                    data-id="${product.id}">
                Edit
            </button>

            <button type="button"
                    class="action-button action-button--danger"
                    data-action="deactivate"
                    data-id="${product.id}">
                Deactivate
            </button>
        `
        : `
            <button type="button"
                    class="action-button"
                    data-action="reactivate"
                    data-id="${product.id}">
                Reactivate
            </button>
        `;

    return `
        <tr>

            <td>
                <div class="product-name-cell">
                    <strong>${escapeHtml(product.productName)}</strong>
                    <span>${escapeHtml(product.productCode || "-")}</span>
                </div>
            </td>

            <td>${escapeHtml(categoryName)}</td>

            <td class="price-cell">
                ${formatCurrency(product.costPrice)}
            </td>

            <td class="price-cell">
                ${formatCurrency(product.sellingPrice)}
            </td>

            <td>
                <div class="product-name-cell">
                    <strong class="stock-value">
                        ${quantity.toLocaleString()}
                    </strong>
                    <span>Min: ${minimumStock}</span>
                    <span class="stock-pill ${stockClass}">
                        ${stockStatus}
                    </span>
                </div>
            </td>

            <td>
                <span class="status-pill ${statusClass}">
                    ${statusText}
                </span>
            </td>

            <td>
                <div class="action-buttons">
                    ${actions}
                </div>
            </td>

        </tr>
    `;
}


/* ========================================
   TABLE ACTIONS
======================================== */

async function handleTableAction(event) {

    const button = event.target.closest("[data-action]");

    if (!button) return;

    const productId = Number(button.dataset.id);
    const action = button.dataset.action;

    const product = products.find(
        item => Number(item.id) === productId
    );

    if (!product) return;

    if (action === "edit") {
        openProductModal(product);
        return;
    }

    if (action === "deactivate") {

        const confirmed = confirm(
            `Deactivate "${product.productName}"?`
        );

        if (!confirmed) return;

        try {
            await apiRequest(`/api/products/${productId}`, {
                method: "DELETE"
            });

            await reloadProducts();

        } catch (error) {
            showPageMessage(
                `Unable to deactivate product: ${error.message}`
            );
        }

        return;
    }

    if (action === "reactivate") {

        const confirmed = confirm(
            `Reactivate "${product.productName}"?`
        );

        if (!confirmed) return;

        try {
            await apiRequest(
                `/api/products/${productId}/reactivate`,
                {
                    method: "PATCH"
                }
            );

            await reloadProducts();

        } catch (error) {
            showPageMessage(
                `Unable to reactivate product: ${error.message}`
            );
        }
    }
}


/* ========================================
   PRODUCT MODAL
======================================== */

function openProductModal(product = null) {

    editingProductId = product ? product.id : null;

    elements.form.reset();
    elements.formMessage.classList.remove("show");
    elements.formMessage.textContent = "";

    elements.minimumStock.value = 5;
    elements.openingStock.value = 0;

    elements.modalTitle.textContent = product
        ? "Edit Product"
        : "Add Product";

    elements.modalDescription.textContent = product
        ? "Update your product information."
        : "Enter the information for your new product.";

    elements.saveButton.textContent = product
        ? "Update Product"
        : "Save Product";

    document.querySelectorAll(".opening-stock-field")
        .forEach(field => {
            field.hidden = Boolean(product);
        });

    if (product) {

        elements.productName.value =
            product.productName || "";

        elements.productCategory.value =
            String(product.categoryId || "");

        elements.productDescription.value =
            product.description || "";

        elements.costPrice.value =
            product.costPrice ?? "";

        elements.sellingPrice.value =
            product.sellingPrice ?? "";

        elements.minimumStock.value =
            product.minimumStock ?? 5;
    }

    elements.modal.hidden = false;
    elements.productName.focus();
}


function closeProductModal() {

    if (isSaving) return;

    elements.modal.hidden = true;
    elements.form.reset();

    editingProductId = null;
}


/* ========================================
   SAVE PRODUCT
======================================== */

async function saveProduct(event) {

    event.preventDefault();

    if (isSaving) return;

    const payload = {
        productName: elements.productName.value.trim(),
        description: elements.productDescription.value.trim(),
        costPrice: Number(elements.costPrice.value),
        sellingPrice: Number(elements.sellingPrice.value),
        minimumStock: Number(elements.minimumStock.value),
        categoryId: Number(elements.productCategory.value)
    };

    if (
        !payload.productName ||
        !payload.categoryId ||
        !Number.isFinite(payload.costPrice) ||
        !Number.isFinite(payload.sellingPrice) ||
        !Number.isInteger(payload.minimumStock) ||
        payload.minimumStock < 0
    ) {
        showFormMessage("Please check all required fields.");
        return;
    }

    if (payload.costPrice < 0 || payload.sellingPrice < 0) {
        showFormMessage("Prices cannot be negative.");
        return;
    }

    isSaving = true;
    elements.saveButton.disabled = true;
    elements.saveButton.textContent = "Saving...";

    try {

        if (editingProductId) {

            await apiRequest(
                `/api/products/${editingProductId}`,
                {
                    method: "PUT",
                    body: JSON.stringify(payload)
                }
            );

            closeModalAfterSave();
            await reloadProducts();

            showPageMessage("Product updated successfully.");

        } else {

            const openingStock = Number(
                elements.openingStock.value || 0
            );

            if (
                !Number.isInteger(openingStock) ||
                openingStock < 0
            ) {
                showFormMessage(
                    "Opening stock must be a non-negative whole number."
                );
                return;
            }

            const createdProduct = await apiRequest(
                "/api/products",
                {
                    method: "POST",
                    body: JSON.stringify(payload)
                }
            );

            let openingStockError = null;

            if (openingStock > 0) {

                try {

                    await apiRequest(
                        "/api/inventory/opening-stock",
                        {
                            method: "POST",
                            body: JSON.stringify({
                                productId: createdProduct.id,
                                quantity: openingStock,
                                remarks:
                                    elements.openingStockRemarks.value.trim() ||
                                    "Opening stock"
                            })
                        }
                    );

                } catch (error) {
                    openingStockError = error.message;
                }
            }

            closeModalAfterSave();
            await reloadProducts();

            if (openingStockError) {
                showPageMessage(
                    `Product created, but opening stock could not be recorded: ${openingStockError}`
                );
            } else {
                showPageMessage("Product created successfully.");
            }
        }

    } catch (error) {

        showFormMessage(error.message);

    } finally {

        isSaving = false;
        elements.saveButton.disabled = false;

        elements.saveButton.textContent = editingProductId
            ? "Update Product"
            : "Save Product";
    }
}


function closeModalAfterSave() {

    elements.modal.hidden = true;
    elements.form.reset();

    editingProductId = null;
}


/* ========================================
   RELOAD
======================================== */

async function reloadProducts() {

    products = await getAllProducts();

    currentPage = 0;

    updateSummary();
    renderProducts();
}


/* ========================================
   HELPERS
======================================== */

function formatCurrency(value) {

    return `RM ${Number(value || 0).toLocaleString(
        "en-MY",
        {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }
    )}`;
}


function escapeHtml(value) {

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