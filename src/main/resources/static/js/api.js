
const API_BASE_URL = "";

async function apiRequest(endpoint, options = {}) {
    const config = {
        method: options.method || "GET",
        headers: {
            "Accept": "application/json",
            ...(options.body !== undefined
                ? { "Content-Type": "application/json" }
                : {}),
            ...(options.headers || {})
        }
    };

    if (options.body !== undefined) {
        config.body = JSON.stringify(options.body);
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
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
            (typeof data === "string" ? data : null) ||
            `Request failed: ${response.status}`;

        throw new Error(message);
    }

    return data;
}

function apiGet(endpoint) {
    return apiRequest(endpoint);
}

function apiPost(endpoint, body) {
    return apiRequest(endpoint, {
        method: "POST",
        body
    });
}

function apiPut(endpoint, body) {
    return apiRequest(endpoint, {
        method: "PUT",
        body
    });
}

function apiPatch(endpoint, body) {
    return apiRequest(endpoint, {
        method: "PATCH",
        ...(body !== undefined ? { body } : {})
    });
}

function apiDelete(endpoint) {
    return apiRequest(endpoint, {
        method: "DELETE"
    });
}

function normalizeArray(data) {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.data)) return data.data;
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
    return new Intl.NumberFormat("en-MY").format(Number(value ?? 0));
}

function formatDate(value) {
    if (!value) return "-";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) return "-";

    return date.toLocaleDateString("en-MY", {
        day: "2-digit",
        month: "short",
        year: "numeric"
    });
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
