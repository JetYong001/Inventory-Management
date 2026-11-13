(function () {
  "use strict";

  const REQUEST_TIMEOUT_MS = 12000;

  async function getJson(path) {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(path, {
        method: "GET",
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        signal: controller.signal
      });

      const responseText = await response.text();
      if (!response.ok) {
        const detail = responseText ? responseText.slice(0, 240) : response.statusText;
        throw new Error(`${response.status} ${response.statusText}${detail ? ` — ${detail}` : ""}`);
      }

      if (!responseText) return null;

      try {
        return JSON.parse(responseText);
      } catch {
        throw new Error(`Invalid JSON returned by ${path}`);
      }
    } catch (error) {
      if (error && error.name === "AbortError") {
        throw new Error(`Request timed out after ${REQUEST_TIMEOUT_MS / 1000}s: ${path}`);
      }
      throw error;
    } finally {
      window.clearTimeout(timeoutId);
    }
  }

  window.SmartStockApi = Object.freeze({ getJson });
})();
