
const PRODUCT_PAGE_SIZE = 10;

let currentPage = 0;
let totalPages = 0;
let editingProductId = null;
let searchKeyword = "";
let activeFilter = "";

document.addEventListener("DOMContentLoaded", async () => {
    document.getElementById("currentDate").textContent =
        new Date().toLocaleDateString("en-MY", {
            weekday: "short",
            day: "2-digit",
            month: "short",
            year: "numeric"
        });

    document.getElementById("menuToggle").addEventListener("click", () => {
        document.querySelector(".sidebar").classList.toggle("open");
    });

    document.getElementById("addProductButton").addEventListener("click", openCreateModal);
    document.getElementById("closeModalButton").addEventListener("click", closeModal);
    document.getElementById("cancelModalButton").addEventListener("click", closeModal);

    document.getElementById("searchButton").addEventListener("click", () => {
        searchKeyword = document.getElementById("searchInput").value.trim();
        activeFilter = document.getElementById("statusFilter").value;
        currentPage = 0;
        loadProducts();
    });

    document.getElementById("searchInput").addEventListener("keydown", event => {
        if (event.key === "Enter") {
            document.getElementById("searchButton").click();
        }
    });

    document.getElementById("statusFilter").addEventListener("change", () => {
        activeFilter = document.getElementById("statusFilter").value;
        currentPage = 0;
        loadProducts();
    });

    document.getElementById("previousPage").addEventListener("click", () => {
        if (currentPage > 0) {
            currentPage--;
            loadProducts();
        }
    });

    document.getElementById("nextPage").addEventListener("click", () => {
        if (currentPage + 1 < totalPages) {
            currentPage++;
            loadProducts();
        }
    });

    document.getElementById("productForm").addEventListener("submit", saveProduct);

    await loadCategories();
    await loadProducts();
});

async function loadCategories(selectedId = null) {
    const select = document.getElementById("categoryId");

    try {
        const categories = normalizeArray(await apiGet("/api/categories"));

        select.innerHTML = `
            <option value="">Select a category</option>
            ${categories.map(category => `
                <option value="${category.id}">
                    ${escapeHTML(category.categoryName)}
                </option>
            `).join("")}
        `;

        if (selectedId !== null) {
            select.value = String(selectedId);
        }
    } catch (error) {
        select.innerHTML = `<option value="">Unable to load categories</option>`;
        showPageMessage(`Categories could not be loaded: ${error.message}`);
    }
}

async function loadProductStatistics() {
    try {
        const [all, active, inactive] = await Promise.all([
            apiGet("/api/products?page=0&size=1"),
            apiGet("/api/products?active=true&page=0&size=1"),
            apiGet("/api/products?active=false&page=0&size=1")
        ]);

        document.getElementById("totalProductCount").textContent =
            formatNumber(all.totalElements ?? 0);

        document.getElementById("activeProductCount").textContent =
            formatNumber(active.totalElements ?? 0);

        document.getElementById("inactiveProductCount").textContent =
            formatNumber(inactive.totalElements ?? 0);
    } catch {
        document.getElementById("totalProductCount").textContent = "—";
        document.getElementById("activeProductCount").textContent = "—";
        document.getElementById("inactiveProductCount").textContent = "—";
    }
}

async function loadProducts() {
    const tbody = document.getElementById("productsTable");
    const pageInfo = document.getElementById("pageInfo");

    tbody.innerHTML = `
        <tr><td colspan="9" class="table-message">Loading products...</td></tr>
    `;

    clearPageMessage();

    try {
        const params = new URLSearchParams({
            page: String(currentPage),
            size: String(PRODUCT_PAGE_SIZE),
            sortBy: "productName",
            direction: "asc"
        });

        if (activeFilter !== "") {
            params.set("active", activeFilter);
        }

        const endpoint = searchKeyword
            ? `/api/products/search?${params.toString()}&keyword=${encodeURIComponent(searchKeyword)}`
            : `/api/products?${params.toString()}`;

        const result = await apiGet(endpoint);
        const products = normalizeArray(result);

        totalPages = Number(result.totalPages ?? 0);

        renderProducts(products);

        const totalElements = Number(result.totalElements ?? products.length);
        pageInfo.textContent =
            `Page ${totalPages === 0 ? 0 : currentPage + 1} of ${totalPages} · ${totalElements} products`;

        document.getElementById("previousPage").disabled =
            currentPage <= 0;

        document.getElementById("nextPage").disabled =
            currentPage + 1 >= totalPages;

        await loadProductStatistics();

    } catch (error) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="table-message">
                    Could not load products.
                </td>
            </tr>
        `;

        showPageMessage(`Products could not be loaded: ${error.message}`);
    }
}

function renderProducts(products) {
    const tbody = document.getElementById("productsTable");

    if (!products.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="table-message">
                    No products found.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = products.map(product => {
        const active = product.active === true;

        const status = active
            ? `<span class="product-status active">Active</span>`
            : `<span class="product-status inactive">Inactive</span>`;

        const actions = active
            ? `
                <button class="table-action edit"
                    data-action="edit" data-id="${product.id}">Edit</button>
                <button class="table-action delete"
                    data-action="delete" data-id="${product.id}">Deactivate</button>
            `
            : `
                <button class="table-action activate"
                    data-action="reactivate" data-id="${product.id}">Reactivate</button>
            `;

        return `
            <tr>
                <td class="product-code">${escapeHTML(product.productCode)}</td>
                <td class="product-name">${escapeHTML(product.productName)}</td>
                <td>${escapeHTML(product.categoryName || "-")}</td>
                <td>${formatCurrency(product.costPrice)}</td>
                <td>${formatCurrency(product.sellingPrice)}</td>
                <td>${formatNumber(product.quantity)}</td>
                <td>${formatNumber(product.minimumStock)}</td>
                <td>${status}</td>
                <td><div class="action-group">${actions}</div></td>
            </tr>
        `;
    }).join("");

    tbody.querySelectorAll("[data-action]").forEach(button => {
        button.addEventListener("click", () => {
            handleProductAction(
                button.dataset.action,
                Number(button.dataset.id)
            );
        });
    });
}

async function handleProductAction(action, id) {
    try {
        if (action === "edit") {
            await editProduct(id);
            return;
        }

        if (action === "delete") {
            const confirmed = confirm(
                "Deactivate this product? It will not be permanently deleted."
            );

            if (!confirmed) return;

            await apiDelete(`/api/products/${id}`);
            showPageMessage("Product deactivated successfully.", "success");
        }

        if (action === "reactivate") {
            const confirmed = confirm("Reactivate this product?");

            if (!confirmed) return;

            await apiPatch(`/api/products/${id}/reactivate`);
            showPageMessage("Product reactivated successfully.", "success");
        }

        await loadProducts();

    } catch (error) {
        showPageMessage(error.message);
    }
}

function openCreateModal() {
    editingProductId = null;

    document.getElementById("productForm").reset();
    document.getElementById("productId").value = "";
    document.getElementById("modalTitle").textContent = "Add Product";
    document.getElementById("saveProductButton").textContent = "Create Product";
    document.getElementById("formError").classList.add("hidden");

    document.getElementById("productModal").classList.remove("hidden");
}

async function editProduct(id) {
    try {
        const product = await apiGet(`/api/products/${id}`);

        editingProductId = id;

        document.getElementById("productId").value = product.id;
        document.getElementById("productName").value = product.productName ?? "";
        document.getElementById("productDescription").value = product.description ?? "";
        document.getElementById("costPrice").value = product.costPrice ?? "";
        document.getElementById("sellingPrice").value = product.sellingPrice ?? "";
        document.getElementById("minimumStock").value = product.minimumStock ?? "";

        await loadCategories(product.categoryId);

        document.getElementById("modalTitle").textContent = "Edit Product";
        document.getElementById("saveProductButton").textContent = "Save Changes";
        document.getElementById("formError").classList.add("hidden");
        document.getElementById("productModal").classList.remove("hidden");

    } catch (error) {
        showPageMessage(`Unable to load product: ${error.message}`);
    }
}

async function saveProduct(event) {
    event.preventDefault();

    const saveButton = document.getElementById("saveProductButton");
    const errorBox = document.getElementById("formError");

    const request = {
        productName: document.getElementById("productName").value.trim(),
        description: document.getElementById("productDescription").value.trim(),
        costPrice: Number(document.getElementById("costPrice").value),
        sellingPrice: Number(document.getElementById("sellingPrice").value),
        minimumStock: Number(document.getElementById("minimumStock").value),
        categoryId: Number(document.getElementById("categoryId").value)
    };

    if (
        !request.productName ||
        !Number.isFinite(request.costPrice) ||
        !Number.isFinite(request.sellingPrice) ||
        !Number.isInteger(request.minimumStock) ||
        !Number.isInteger(request.categoryId) ||
        request.costPrice < 0 ||
        request.sellingPrice < 0 ||
        request.minimumStock < 0 ||
        request.categoryId <= 0
    ) {
        errorBox.textContent = "Please enter valid product information.";
        errorBox.classList.remove("hidden");
        return;
    }

    saveButton.disabled = true;
    saveButton.textContent = "Saving...";
    errorBox.classList.add("hidden");

    try {
        if (editingProductId !== null) {
            await apiPut(`/api/products/${editingProductId}`, request);
            showPageMessage("Product updated successfully.", "success");
        } else {
            await apiPost("/api/products", request);
            showPageMessage("Product created successfully.", "success");
        }

        closeModal();
        await loadProducts();

    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.classList.remove("hidden");
    } finally {
        saveButton.disabled = false;
        saveButton.textContent =
            editingProductId !== null ? "Save Changes" : "Create Product";
    }
}

function closeModal() {
    document.getElementById("productModal").classList.add("hidden");
}

function showPageMessage(message, type = "error") {
    const box = document.getElementById("pageMessage");

    box.textContent = message;
    box.classList.remove("hidden");
    box.classList.toggle("success-message", type === "success");
}

function clearPageMessage() {
    const box = document.getElementById("pageMessage");
    box.classList.add("hidden");
    box.classList.remove("success-message");
}


