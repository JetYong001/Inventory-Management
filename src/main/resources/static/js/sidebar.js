
"use strict";

const sidebarContainer = document.querySelector("#sidebar-container");

if (sidebarContainer) {

    fetch("/components/sidebar/sidebar.html")
        .then(response => {
            if (!response.ok) {
                throw new Error(`Sidebar loading failed: ${response.status}`);
            }

            return response.text();
        })
        .then(html => {

            sidebarContainer.outerHTML = html.trim();

            const sidebar = document.querySelector("#sidebar");
            const menuButton = document.querySelector("#menu-button");

            if (!sidebar) return;

            const currentPage =
                window.location.pathname.split("/").pop() ||
                "dashboard.html";

            sidebar.querySelectorAll(".side-nav__item").forEach(link => {

                link.classList.remove("is-active");
                link.removeAttribute("aria-current");

                const href = link.getAttribute("href");

                if (!href || href.startsWith("#")) return;

                const targetPage = href.split("/").pop();

                if (targetPage === currentPage) {
                    link.classList.add("is-active");
                    link.setAttribute("aria-current", "page");
                }
            });

            if (menuButton) {

                menuButton.addEventListener("click", () => {

                    const isOpen =
                        sidebar.classList.toggle("is-open");

                    menuButton.setAttribute(
                        "aria-expanded",
                        String(isOpen)
                    );

                    menuButton.setAttribute(
                        "aria-label",
                        isOpen ? "Close navigation" : "Open navigation"
                    );
                });
            }

        })
        .catch(error => {
            console.error("Sidebar error:", error);
        });
}
