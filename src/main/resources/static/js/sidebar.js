const sidebarContainer = document.querySelector('#sidebar-container');

if (sidebarContainer) {
  fetch('/components/sidebar/sidebar.html')
      .then(response => {
        if (!response.ok) {
          throw new Error(`Failed to load sidebar: ${response.status}`);
        }

        return response.text();
      })
      .then(html => {
        sidebarContainer.outerHTML = html.trim();

        const sidebar = document.querySelector('#sidebar');
        const menuButton = document.querySelector('#menu-button');

        if (!sidebar || !menuButton) {
          return;
        }

        menuButton.addEventListener('click', () => {
          const isOpen = sidebar.classList.toggle('is-open');

          menuButton.setAttribute('aria-expanded', String(isOpen));
          menuButton.setAttribute(
              'aria-label',
              isOpen ? 'Close navigation' : 'Open navigation'
          );
        });
      })
      .catch(error => {
        console.error(error);
      });
}