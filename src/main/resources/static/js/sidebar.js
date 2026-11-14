
"use strict";

document.addEventListener("DOMContentLoaded", async () => {
    const sidebarContainer = document.querySelector("#sidebar-container");

    if (!sidebarContainer) return;

    const STORAGE_KEY = "smartstock-sidebar-collapsed";
    const MOBILE_BREAKPOINT = 760;

    try {
        const response = await fetch("/components/sidebar/sidebar.html");

        if (!response.ok) {
            throw new Error(`Sidebar loading failed: ${response.status}`);
        }

        const html = await response.text();
        sidebarContainer.outerHTML = html.trim();

        const sidebar = document.querySelector("#sidebar");
        const collapseButton = document.querySelector("#sidebar-collapse-button");
        const menuButton = document.querySelector("#menu-button");

        if (!sidebar || !collapseButton) return;

        const isMobile = () => window.innerWidth <= MOBILE_BREAKPOINT;

        function setCollapsed(collapsed, save = true) {
            document.body.classList.toggle("sidebar-collapsed", collapsed);

            collapseButton.setAttribute(
                "aria-expanded",
                String(!collapsed)
            );

            collapseButton.setAttribute(
                "aria-label",
                collapsed ? "Expand sidebar" : "Collapse sidebar"
            );

            collapseButton.setAttribute(
                "title",
                collapsed ? "Expand sidebar" : "Collapse sidebar"
            );

            if (save) {
                localStorage.setItem(STORAGE_KEY, String(collapsed));
            }
        }

        const savedState = localStorage.getItem(STORAGE_KEY) === "true";
        setCollapsed(savedState, false);

        collapseButton.addEventListener("click", () => {
            if (isMobile()) return;

            const currentlyCollapsed =
                document.body.classList.contains("sidebar-collapsed");

            setCollapsed(!currentlyCollapsed);
        });

        const currentPage =
            window.location.pathname.split("/").pop() || "dashboard.html";

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
                const isOpen = sidebar.classList.toggle("is-open");

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

        document.addEventListener("click", event => {
            if (!isMobile() || !sidebar.classList.contains("is-open")) return;

            if (
                sidebar.contains(event.target) ||
                (menuButton && menuButton.contains(event.target))
            ) {
                return;
            }

            sidebar.classList.remove("is-open");

            if (menuButton) {
                menuButton.setAttribute("aria-expanded", "false");
                menuButton.setAttribute("aria-label", "Open navigation");
            }
        });

        window.addEventListener("resize", () => {
            if (!isMobile()) {
                sidebar.classList.remove("is-open");

                if (menuButton) {
                    menuButton.setAttribute("aria-expanded", "false");
                    menuButton.setAttribute("aria-label", "Open navigation");
                }
            }
        });

    } catch (error) {
        console.error("Sidebar initialization failed:", error);
    }
});
