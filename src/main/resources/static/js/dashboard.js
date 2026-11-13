"use strict";

// ========================================
// CONFIGURATION
// ========================================

const chartColours = {
  sales: "#1466d8",
  purchases: "#8352e8",
  grid: "#dfe6ef",
  tick: "#62758a",
  inStock: "#169b7c",
  lowStock: "#f5a524",
  outOfStock: "#e65663"
};

// ========================================
// DASHBOARD DATA
// ========================================

let salesChart = null;
let stockChart = null;

let dashboardSummary = null;
let dashboardSales = [];
let dashboardPurchases = [];
let dashboardStock = [];

let apiAvailability = {
  summary: false,
  stock: false,
  sales: false,
  purchases: false
};

// ========================================
// INITIALIZATION
// ========================================

document.addEventListener("DOMContentLoaded", initializeDashboard);

function initializeDashboard() {
  const rangeSelect = document.getElementById("range-select");

  if (rangeSelect) {
    rangeSelect.addEventListener("change", () => {
      renderDashboardMetrics(dashboardSummary);
      renderSalesPurchaseChart();
    });
  }

  loadDashboard();
}

// ========================================
// API REQUEST
// ========================================

async function apiGet(endpoint) {
  const response = await fetch(endpoint, {
    method: "GET",
    headers: {
      Accept: "application/json"
    },
    credentials: "same-origin"
  });

  const responseText = await response.text();

  let data = null;

  if (responseText) {
    try {
      data = JSON.parse(responseText);
    } catch {
      throw new Error(`Invalid JSON response: ${endpoint}`);
    }
  }

  if (!response.ok) {
    throw new Error(
        data?.message ||
        data?.error ||
        `HTTP ${response.status}: ${endpoint}`
    );
  }

  return data;
}

function normalizeArray(data) {
  if (Array.isArray(data)) {
    return data;
  }

  if (Array.isArray(data?.content)) {
    return data.content;
  }

  if (Array.isArray(data?.data)) {
    return data.data;
  }

  return [];
}

// ========================================
// LOAD DASHBOARD
// ========================================

async function loadDashboard() {
  const metricCards = document.querySelectorAll(".metric-card");

  metricCards.forEach(card => {
    const value = card.querySelector(".metric-value");

    if (value) {
      value.textContent = "Loading...";
    }
  });

  const results = await Promise.allSettled([
    apiGet("/api/inventory/summary"),
    apiGet("/api/inventory/stock"),
    apiGet("/api/sales"),
    apiGet("/api/purchases")
  ]);

  const [
    summaryResult,
    stockResult,
    salesResult,
    purchasesResult
  ] = results;

  apiAvailability = {
    summary: summaryResult.status === "fulfilled",
    stock: stockResult.status === "fulfilled",
    sales: salesResult.status === "fulfilled",
    purchases: purchasesResult.status === "fulfilled"
  };

  dashboardSummary = apiAvailability.summary
      ? summaryResult.value
      : null;

  dashboardStock = apiAvailability.stock
      ? normalizeArray(stockResult.value)
      : [];

  dashboardSales = apiAvailability.sales
      ? normalizeArray(salesResult.value)
      : [];

  dashboardPurchases = apiAvailability.purchases
      ? normalizeArray(purchasesResult.value)
      : [];

  if (!apiAvailability.summary) {
    console.error("Inventory summary API failed:", summaryResult.reason);
  }

  if (!apiAvailability.stock) {
    console.error("Stock API failed:", stockResult.reason);
  }

  if (!apiAvailability.sales) {
    console.error("Sales API failed:", salesResult.reason);
  }

  if (!apiAvailability.purchases) {
    console.error("Purchases API failed:", purchasesResult.reason);
  }

  renderDashboardMetrics(dashboardSummary);
  renderStockStatus();
  renderSalesPurchaseChart();
}

// ========================================
// DATE RANGE
// ========================================

function getSelectedMonths() {
  const select = document.getElementById("range-select");

  const months = Number(select?.value || 6);

  return [1, 3, 6, 12].includes(months) ? months : 6;
}

function getPeriodLabel(months) {
  if (months === 1) {
    return "Current Month";
  }

  return `Last ${months} Months`;
}

function getDateRange() {
  const months = getSelectedMonths();

  const now = new Date();

  const start = new Date(
      now.getFullYear(),
      now.getMonth() - months + 1,
      1
  );

  const end = new Date(now);
  end.setHours(23, 59, 59, 999);

  return {
    months,
    label: getPeriodLabel(months),
    start,
    end
  };
}

function parseDate(value) {
  if (!value) {
    return null;
  }

  let date;

  if (
      typeof value === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(value)
  ) {
    const [year, month, day] = value.split("-").map(Number);

    date = new Date(year, month - 1, day);
  } else {
    date = new Date(value);
  }

  return Number.isNaN(date.getTime()) ? null : date;
}

// ========================================
// TRANSACTION DATA
// ========================================

function getSalesDate(record) {
  return parseDate(record.salesDate);
}

function getPurchaseDate(record) {
  return parseDate(record.purchaseDate);
}

function getTransactionAmount(record) {
  const amount = Number(record.totalAmount ?? 0);

  return Number.isFinite(amount) ? amount : 0;
}

function getTransactionStatus(record) {
  return String(record.status ?? "").toUpperCase();
}

function isAcceptedSales(record) {
  return getTransactionStatus(record) === "COMPLETED";
}

function isAcceptedPurchase(record) {
  const status = getTransactionStatus(record);

  return [
    "RECEIVED",
    "PARTIALLY_RECEIVED"
  ].includes(status);
}

function isWithinSelectedRange(date) {
  if (!date) {
    return false;
  }

  const range = getDateRange();

  return date >= range.start && date <= range.end;
}

// ========================================
// CALCULATE SALES
// ========================================

function calculateSalesTotal() {
  return dashboardSales.reduce((total, record) => {
    if (!isAcceptedSales(record)) {
      return total;
    }

    const date = getSalesDate(record);

    if (!isWithinSelectedRange(date)) {
      return total;
    }

    return total + getTransactionAmount(record);
  }, 0);
}

// ========================================
// CALCULATE PURCHASES
// ========================================

function calculatePurchaseTotal() {
  return dashboardPurchases.reduce((total, record) => {
    if (!isAcceptedPurchase(record)) {
      return total;
    }

    const date = getPurchaseDate(record);

    if (!isWithinSelectedRange(date)) {
      return total;
    }

    return total + getTransactionAmount(record);
  }, 0);
}

// ========================================
// FORMAT
// ========================================

function formatCurrency(value) {
  return new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value) || 0);
}

function formatNumber(value) {
  return new Intl.NumberFormat("en-MY").format(
      Number(value) || 0
  );
}

// ========================================
// DASHBOARD METRICS
// ========================================

function renderDashboardMetrics(summary) {
  const cards = document.querySelectorAll(".metric-card");

  if (cards.length < 4) {
    console.error("Dashboard metric cards not found.");
    return;
  }

  const [
    stockCard,
    salesCard,
    purchaseCard,
    inventoryValueCard
  ] = cards;

  const totalStock = summary?.totalStockQuantity ??
      dashboardStock.reduce((total, product) => {
        return total + Number(product.currentQuantity ?? 0);
      }, 0);

  setMetric(
      stockCard,
      apiAvailability.summary || apiAvailability.stock
          ? formatNumber(totalStock)
          : "—",
      apiAvailability.summary || apiAvailability.stock
          ? "Current inventory quantity"
          : "Inventory data unavailable"
  );

  setMetric(
      salesCard,
      apiAvailability.sales
          ? formatCurrency(calculateSalesTotal())
          : "—",
      apiAvailability.sales
          ? getPeriodLabel(getSelectedMonths())
          : "Sales data unavailable"
  );

  setMetric(
      purchaseCard,
      apiAvailability.purchases
          ? formatCurrency(calculatePurchaseTotal())
          : "—",
      apiAvailability.purchases
          ? getPeriodLabel(getSelectedMonths())
          : "Purchase data unavailable"
  );

  const inventoryValue = summary?.totalInventoryValue;

  setMetric(
      inventoryValueCard,
      apiAvailability.summary && inventoryValue != null
          ? formatCurrency(inventoryValue)
          : "—",
      apiAvailability.summary
          ? "Current stock value at cost"
          : "Inventory value unavailable"
  );
}

function setMetric(card, value, caption) {
  if (!card) {
    return;
  }

  const valueElement = card.querySelector(".metric-value");
  const trendElement = card.querySelector(".trend");

  if (valueElement) {
    valueElement.textContent = value;
  }

  if (trendElement) {
    trendElement.textContent = caption;
    trendElement.classList.remove("trend--up");
  }
}

// ========================================
// STOCK STATUS
// ========================================

function getStockType(product) {
  const status = String(product.stockStatus ?? "").toUpperCase();

  if (status === "OUT_OF_STOCK") {
    return "out";
  }

  if (status === "LOW_STOCK") {
    return "low";
  }

  if (status === "NORMAL") {
    return "available";
  }

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

function renderStockStatus() {
  const canvas = document.getElementById("stock-status-chart");

  if (!canvas) {
    return;
  }

  if (stockChart) {
    stockChart.destroy();
    stockChart = null;
  }

  const available = dashboardStock.filter(
      product => getStockType(product) === "available"
  ).length;

  const low = dashboardStock.filter(
      product => getStockType(product) === "low"
  ).length;

  const out = dashboardStock.filter(
      product => getStockType(product) === "out"
  ).length;

  const total = dashboardStock.length;

  const percentage = total > 0
      ? Math.round((available / total) * 100)
      : 0;

  const centreValue = document.querySelector(
      ".doughnut-centre strong"
  );

  const legendItems = document.querySelectorAll(
      ".stock-legend li strong"
  );

  const heading = document.querySelector(
      ".chart-card--stock .chart-heading p"
  );

  if (centreValue) {
    centreValue.textContent = `${percentage}%`;
  }

  if (legendItems.length >= 3) {
    legendItems[0].textContent = formatNumber(available);
    legendItems[1].textContent = formatNumber(low);
    legendItems[2].textContent = formatNumber(out);
  }

  if (heading) {
    heading.textContent = `${formatNumber(total)} products monitored`;
  }

  if (typeof Chart === "undefined") {
    console.error("Chart.js is unavailable.");
    return;
  }

  const reduceMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)"
  ).matches;

  stockChart = new Chart(canvas, {
    type: "doughnut",

    data: {
      labels: [
        "In stock",
        "Low stock",
        "Out of stock"
      ],

      datasets: [{
        data: [
          available,
          low,
          out
        ],

        backgroundColor: [
          chartColours.inStock,
          chartColours.lowStock,
          chartColours.outOfStock
        ],

        borderWidth: 0,
        hoverOffset: 5
      }]
    },

    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: "72%",

      animation: reduceMotion
          ? false
          : { duration: 450 },

      plugins: {
        legend: {
          display: false
        },

        tooltip: {
          callbacks: {
            label(context) {
              return `${context.label}: ${context.parsed} products`;
            }
          }
        }
      }
    }
  });
}

// ========================================
// DAILY SALES AND PURCHASES
// ========================================

function getDailyTotals(records, type) {
  const now = new Date();

  const year = now.getFullYear();
  const month = now.getMonth();

  const daysInMonth = new Date(
      year,
      month + 1,
      0
  ).getDate();

  const totals = new Map();
  const labels = [];

  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(year, month, day);

    const key = [
      year,
      String(month + 1).padStart(2, "0"),
      String(day).padStart(2, "0")
    ].join("-");

    totals.set(key, 0);

    labels.push({
      key,
      label: date.toLocaleDateString("en-MY", {
        day: "numeric",
        month: "short"
      })
    });
  }

  const range = getDateRange();

  records.forEach(record => {
    let date;
    let accepted;

    if (type === "sales") {
      date = getSalesDate(record);
      accepted = isAcceptedSales(record);
    } else {
      date = getPurchaseDate(record);
      accepted = isAcceptedPurchase(record);
    }

    if (!accepted || !date) {
      return;
    }

    if (date < range.start || date > range.end) {
      return;
    }

    const key = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0")
    ].join("-");

    if (!totals.has(key)) {
      return;
    }

    totals.set(
        key,
        totals.get(key) + getTransactionAmount(record)
    );
  });

  return {
    labels: labels.map(item => item.label),
    values: labels.map(item => totals.get(item.key))
  };
}

// ========================================
// MONTHLY SALES AND PURCHASES
// ========================================

function getMonthlyTotals(records, type, months) {
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

    totals.set(key, 0);

    labels.push({
      key,
      label: date.toLocaleDateString("en-MY", {
        month: "long",
        year: "numeric"
      })
    });
  }

  const range = getDateRange();

  records.forEach(record => {
    let date;
    let accepted;

    if (type === "sales") {
      date = getSalesDate(record);
      accepted = isAcceptedSales(record);
    } else {
      date = getPurchaseDate(record);
      accepted = isAcceptedPurchase(record);
    }

    if (!accepted || !date) {
      return;
    }

    if (date < range.start || date > range.end) {
      return;
    }

    const key = `${date.getFullYear()}-${date.getMonth()}`;

    if (!totals.has(key)) {
      return;
    }

    totals.set(
        key,
        totals.get(key) + getTransactionAmount(record)
    );
  });

  return {
    labels: labels.map(item => item.label),
    values: labels.map(item => totals.get(item.key))
  };
}

// ========================================
// SALES VS PURCHASES CHART
// ========================================

function renderSalesPurchaseChart() {
  const canvas = document.getElementById(
      "sales-purchases-chart"
  );

  if (!canvas || typeof Chart === "undefined") {
    return;
  }

  if (salesChart) {
    salesChart.destroy();
    salesChart = null;
  }

  const months = getSelectedMonths();

  let sales;
  let purchases;

  if (months === 1) {
    sales = getDailyTotals(
        dashboardSales,
        "sales"
    );

    purchases = getDailyTotals(
        dashboardPurchases,
        "purchases"
    );
  } else {
    sales = getMonthlyTotals(
        dashboardSales,
        "sales",
        months
    );

    purchases = getMonthlyTotals(
        dashboardPurchases,
        "purchases",
        months
    );
  }

  const reduceMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)"
  ).matches;

  salesChart = new Chart(canvas, {
    type: "line",

    data: {
      labels: sales.labels,

      datasets: [
        {
          label: "Sales",

          data: sales.values,

          borderColor: chartColours.sales,
          backgroundColor: "rgba(20, 102, 216, 0.08)",

          borderWidth: 2,
          pointRadius: months === 1 ? 2 : 3,
          pointHoverRadius: 5,
          pointBackgroundColor: chartColours.sales,

          fill: true,
          tension: 0.32
        },

        {
          label: "Purchases",

          data: purchases.values,

          borderColor: chartColours.purchases,
          backgroundColor: "transparent",

          borderWidth: 2,
          pointRadius: months === 1 ? 2 : 3,
          pointHoverRadius: 5,
          pointBackgroundColor: chartColours.purchases,

          fill: false,
          tension: 0.32
        }
      ]
    },

    options: {
      responsive: true,
      maintainAspectRatio: false,

      animation: reduceMotion
          ? false
          : { duration: 450 },

      interaction: {
        mode: "index",
        intersect: false
      },

      plugins: {
        legend: {
          display: false
        },

        tooltip: {
          callbacks: {
            label(context) {
              return `${context.dataset.label}: ${formatCurrency(context.parsed.y)}`;
            }
          }
        }
      },

      scales: {
        x: {
          grid: {
            display: false
          },

          border: {
            display: false
          },

          ticks: {
            color: chartColours.tick,

            font: {
              size: months === 1 ? 9 : 10,
              weight: "600"
            },

            autoSkip: false,
            maxRotation: months === 1 ? 60 : 45,
            minRotation: 0
          }
        },

        y: {
          beginAtZero: true,

          ticks: {
            color: chartColours.tick,

            font: {
              size: 10,
              weight: "600"
            },

            callback(value) {
              if (value === 0) {
                return "0";
              }

              if (Math.abs(value) >= 1000) {
                return `${value / 1000}k`;
              }

              return value;
            }
          },

          grid: {
            color: chartColours.grid
          },

          border: {
            display: false
          }
        }
      }
    }
  });
}