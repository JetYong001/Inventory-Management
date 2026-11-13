
const API_BASE_URL = "";

async function apiRequest(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
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
      throw new Error(`Invalid JSON response from ${endpoint}`);
    }
  }

  if (!response.ok) {
    const message =
        data?.message ||
        data?.error ||
        `Request failed: ${response.status}`;

    throw new Error(message);
  }

  return data;
}

async function apiGet(endpoint) {
  return apiRequest(endpoint, {
    method: "GET"
  });
}

async function apiPost(endpoint, body) {
  return apiRequest(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
}

async function apiPut(endpoint, body) {
  return apiRequest(endpoint, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
}

async function apiDelete(endpoint) {
  return apiRequest(endpoint, {
    method: "DELETE"
  });
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

  if (Array.isArray(data?.items)) {
    return data.items;
  }

  return [];
}

function formatCurrency(value) {
  const amount = Number(value ?? 0);

  return new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number.isFinite(amount) ? amount : 0);
}

function formatNumber(value) {
  const number = Number(value ?? 0);

  return new Intl.NumberFormat("en-MY").format(
      Number.isFinite(number) ? number : 0
  );
}

function formatDate(value) {
  const date = parseDate(value);

  if (!date) {
    return "-";
  }

  return date.toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function parseDate(value) {
  if (!value) {
    return null;
  }

  let date;

  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    date = new Date(year, month - 1, day);
  } else {
    date = new Date(value);
  }

  return Number.isNaN(date.getTime()) ? null : date;
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
