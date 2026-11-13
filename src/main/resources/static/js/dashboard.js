
let salesPurchaseChart = null;
let stockStatusChart = null;

let dashboardSales = [];
let dashboardPurchases = [];

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("currentDate").textContent =
        new Date().toLocaleDateString("en-MY", {
            weekday: "short",
            day: "2-digit",
            month: "short",
            year: "numeric"
        });

    document.getElementById("refreshButton")
        .addEventListener("click", loadDashboard);

    document.getElementById("chartPeriod")
        .addEventListener("change", renderSalesPurchaseChart);

    document.getElementById("menuToggle")
        .addEventListener("click", () => {
            document.querySelector(".sidebar").classList.toggle("open");
        });

    loadDashboard();
});

async function loadDashboard() {
    const refreshButton = document.getElementById("refreshButton");
    const errorBox = document.getElementById("dashboardError");

    refreshButton.disabled = true;
    refreshButton.textContent = "Loading...";
    errorBox.classList.add("hidden");

    const results = await Promise.allSettled([
        apiGet("/api/inventory/summary"),
        apiGet("/api/inventory/stock"),
        apiGet("/api/inventory/low-stock"),
        apiGet("/api/sales"),
        apiGet("/api/purchases")
    ]);

    const [
        summaryResult,
        stockResult,
        lowStockResult,
        salesResult,
        purchasesResult
    ] = results;

    const errors = [];

    if (summaryResult.status === "fulfilled") {
        renderSummary(summaryResult.value);
    } else {
        errors.push("Inventory summary could not be loaded.");
    }

    let stockBalances = [];

    if (stockResult.status === "fulfilled") {
        stockBalances = normalizeArray(stockResult.value);
        renderStockStatus(stockBalances);
    } else {
        errors.push("Stock balances could not be loaded.");
    }

    if (lowStockResult.status === "fulfilled") {
        renderLowStock(normalizeArray(lowStockResult.value));
    } else if (stockResult.status === "fulfilled") {
        renderLowStock(stockBalances.filter(isLowOrOutOfStock));
    } else {
        errors.push("Low stock products could not be loaded.");
    }

    dashboardSales = salesResult.status === "fulfilled"
        ? normalizeArray(salesResult.value)
        : [];

    dashboardPurchases = purchasesResult.status === "fulfilled"
        ? normalizeArray(purchasesResult.value)
        : [];

    if (salesResult.status === "rejected") {
        errors.push("Sales records could not be loaded.");
    }

    if (purchasesResult.status === "rejected") {
        errors.push("Purchase records could not be loaded.");
    }

    renderSalesPurchaseChart();
    renderRecentTransactions(dashboardSales, dashboardPurchases);

    if (errors.length > 0) {
        errorBox.textContent = errors.join(" ");
        errorBox.classList.remove("hidden");
    }

    refreshButton.disabled = false;
    refreshButton.textContent = "↻ Refresh Data";
}

function renderSummary(summary) {
    document.getElementById("totalProducts").textContent =
        formatNumber(summary.totalProducts);

    document.getElementById("totalStock").textContent =
        formatNumber(summary.totalStockQuantity);

    document.getElementById("inventoryValue").textContent =
        formatCurrency(summary.totalInventoryValue);

    document.getElementById("lowStockCount").textContent =
        formatNumber(summary.lowStockCount);

    document.getElementById("outOfStockCount").textContent =
        formatNumber(summary.outOfStockCount);
}

function getStockType(product) {
    const quantity = Number(product.currentQuantity ?? 0);
    const minimum = Number(product.minimumStock ?? 0);

    if (quantity <= 0) {
        return "out";
    }

    if (quantity <= minimum) {
        return "low";
    }

    return "available";
}

function isLowOrOutOfStock(product) {
    return getStockType(product) !== "available";
}

function renderStockStatus(stockBalances) {
    let available = 0;
    let low = 0;
    let out = 0;

    stockBalances.forEach(product => {
        const type = getStockType(product);

        if (type === "available") available++;
        if (type === "low") low++;
        if (type === "out") out++;
    });

    document.getElementById("availableCount").textContent =
        formatNumber(available);

    document.getElementById("lowCount").textContent =
        formatNumber(low);

    document.getElementById("outCount").textContent =
        formatNumber(out);

    const canvas = document.getElementById("stockStatusChart");
    const emptyMessage = document.getElementById("stockChartEmpty");

    if (stockStatusChart) {
        stockStatusChart.destroy();
        stockStatusChart = null;
    }

    if (stockBalances.length === 0) {
        canvas.classList.add("hidden");
        emptyMessage.classList.remove("hidden");
        return;
    }

    canvas.classList.remove("hidden");
    emptyMessage.classList.add("hidden");

    if (typeof Chart === "undefined") {
        emptyMessage.textContent = "Chart library is unavailable.";
        emptyMessage.classList.remove("hidden");
        return;
    }

    stockStatusChart = new Chart(canvas, {
        type: "doughnut",
        data: {
            labels: ["Available", "Low Stock", "Out of Stock"],
            datasets: [{
                data: [available, low, out],
                backgroundColor: ["#3478ed", "#f1ad42", "#e65b65"],
                borderWidth: 0,
                hoverOffset: 5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: "72%",
            plugins: {
                legend: {
                    display: false
                }
            }
        }
    });
}

function renderLowStock(products) {
    const tbody = document.getElementById("lowStockTable");

    if (!products.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="table-message">
                    No low stock products.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = products.slice(0, 8).map(product => {
        const type = getStockType(product);

        const statusLabel = type === "out"
            ? "Out of Stock"
            : "Low Stock";

        return `
            <tr>
                <td>${escapeHTML(product.productName)}</td>
                <td>${escapeHTML(product.productCode)}</td>
                <td>${formatNumber(product.currentQuantity)}</td>
                <td>${formatNumber(product.minimumStock)}</td>
                <td>
                    <span class="stock-badge ${type}">
                        ${statusLabel}
                    </span>
                </td>
            </tr>
        `;
    }).join("");
}

function getDateValue(value) {
    if (!value) return null;

    const date = new Date(value);

    return Number.isNaN(date.getTime()) ? null : date;
}

function getMonthlyTotals(records, dateField, amountField, acceptedStatuses, months) {
    const now = new Date();
    const totals = new Map();
    const labels = [];

    for (let i = months - 1; i >= 0; i--) {
        const date = new Date(
            now.getFullYear(),
            now.getMonth() - i,
            1
        );

        const key = `${date.getFullYear()}-${date.getMonth()}`;
        const label = date.toLocaleDateString("en-MY", {
            month: "short",
            year: "2-digit"
        });

        totals.set(key, 0);
        labels.push({ key, label });
    }

    records.forEach(record => {
        const status = String(record.status ?? "").toUpperCase();

        if (!acceptedStatuses.includes(status)) {
            return;
        }

        const date = getDateValue(record[dateField]);

        if (!date) return;

        const key = `${date.getFullYear()}-${date.getMonth()}`;

        if (!totals.has(key)) return;

        totals.set(
            key,
            totals.get(key) + Number(record[amountField] ?? 0)
        );
    });

    return {
        labels: labels.map(item => item.label),
        values: labels.map(item => totals.get(item.key))
    };
}

function renderSalesPurchaseChart() {
    const months = Number(
        document.getElementById("chartPeriod").value
    );

    const sales = getMonthlyTotals(
        dashboardSales,
        "salesDate",
        "totalAmount",
        ["COMPLETED"],
        months
    );

    const purchases = getMonthlyTotals(
        dashboardPurchases,
        "purchaseDate",
        "totalAmount",
        ["RECEIVED"],
        months
    );

    const canvas = document.getElementById("salesPurchaseChart");
    const emptyMessage = document.getElementById("salesChartEmpty");

    if (salesPurchaseChart) {
        salesPurchaseChart.destroy();
        salesPurchaseChart = null;
    }

    const hasTransactions =
        sales.values.some(value => value > 0) ||
        purchases.values.some(value => value > 0);

    if (!hasTransactions) {
        canvas.classList.add("hidden");
        emptyMessage.classList.remove("hidden");
        return;
    }

    canvas.classList.remove("hidden");
    emptyMessage.classList.add("hidden");

    if (typeof Chart === "undefined") {
        emptyMessage.textContent = "Chart library is unavailable.";
        emptyMessage.classList.remove("hidden");
        return;
    }

    salesPurchaseChart = new Chart(canvas, {
        type: "line",
        data: {
            labels: sales.labels,
            datasets: [
                {
                    label: "Sales",
                    data: sales.values,
                    borderColor: "#3478ed",
                    backgroundColor: "rgba(52, 120, 237, 0.08)",
                    fill: true,
                    tension: 0.35,
                    borderWidth: 2,
                    pointRadius: 3
                },
                {
                    label: "Purchases",
                    data: purchases.values,
                    borderColor: "#20a36b",
                    backgroundColor: "rgba(32, 163, 107, 0.05)",
                    fill: true,
                    tension: 0.35,
                    borderWidth: 2,
                    pointRadius: 3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: "index"
            },
            plugins: {
                legend: {
                    position: "bottom",
                    labels: {
                        usePointStyle: true,
                        boxWidth: 7,
                        padding: 20,
                        font: {
                            size: 10
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: context =>
                            `${context.dataset.label}: ${formatCurrency(context.raw)}`
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        font: { size: 9 },
                        callback: value =>
                            `RM ${Number(value).toLocaleString("en-MY")}`
                    },
                    grid: {
                        color: "#f0f2f6"
                    }
                },
                x: {
                    ticks: {
                        font: { size: 9 }
                    },
                    grid: {
                        display: false
                    }
                }
            }
        }
    });
}

function renderRecentTransactions(sales, purchases) {
    const container = document.getElementById("recentTransactions");

    const transactions = [
        ...sales.map(item => ({
            type: "sale",
            number: item.salesNumber,
            name: item.customerName || "Customer",
            date: item.salesDate,
            amount: item.totalAmount,
            status: item.status
        })),
        ...purchases.map(item => ({
            type: "purchase",
            number: item.purchaseNumber,
            name: item.supplierName || "Supplier",
            date: item.purchaseDate,
            amount: item.totalAmount,
            status: item.status
        }))
    ];

    transactions.sort((a, b) => {
        const dateA = getDateValue(a.date)?.getTime() ?? 0;
        const dateB = getDateValue(b.date)?.getTime() ?? 0;

        return dateB - dateA;
    });

    if (!transactions.length) {
        container.innerHTML = `
            <div class="table-message">
                No transaction records available.
            </div>
        `;
        return;
    }

    container.innerHTML = transactions.slice(0, 6).map(item => {
        const isSale = item.type === "sale";
        const symbol = isSale ? "↑" : "↓";
        const label = isSale ? "Sales" : "Purchase";

        return `
            <div class="transaction-item">
                <div class="transaction-icon ${item.type}">
                    ${symbol}
                </div>

                <div class="transaction-info">
                    <strong>${escapeHTML(item.number)}</strong>
                    <span>
                        ${label} · ${escapeHTML(item.name)} · ${formatDate(item.date)}
                    </span>
                </div>

                <div class="transaction-amount ${item.type}">
                    ${formatCurrency(item.amount)}
                </div>
            </div>
        `;
    }).join("");
}

function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => {
        const entities = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        };

        return entities[character];
    });
}
