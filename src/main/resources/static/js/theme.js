/* ==============================================
   MERA GROUP THEME SCRIPT
   Light/Dark Mode Toggle
   ============================================== */

(function() {
    'use strict';

    // Theme Configuration
    const THEME_KEY = 'mera-theme';
    const DARK_THEME = 'dark';
    const LIGHT_THEME = 'light';

    // Initialize Theme
    function initTheme() {
        const savedTheme = localStorage.getItem(THEME_KEY);
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        
        // Priority: saved preference > system preference
        const theme = savedTheme || (prefersDark ? DARK_THEME : LIGHT_THEME);
        setTheme(theme);
        
        // Listen for system preference changes
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
            if (!localStorage.getItem(THEME_KEY)) {
                setTheme(e.matches ? DARK_THEME : LIGHT_THEME);
            }
        });
    }

    // Set Theme
    function setTheme(theme) {
        if (theme === DARK_THEME) {
            document.documentElement.setAttribute('data-theme', DARK_THEME);
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
        
        // Update toggle button
        updateToggleButton(theme);
        
        // Save preference
        localStorage.setItem(THEME_KEY, theme);
        
        // Dispatch event for other components
        window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
    }

    // Toggle Theme
    function toggleTheme() {
        const currentTheme = document.documentElement.hasAttribute('data-theme') ? DARK_THEME : LIGHT_THEME;
        const newTheme = currentTheme === DARK_THEME ? LIGHT_THEME : DARK_THEME;
        setTheme(newTheme);
    }

    // Update Toggle Button
    function updateToggleButton(theme) {
        const toggleBtn = document.getElementById('theme-toggle');
        if (toggleBtn) {
            toggleBtn.innerHTML = theme === DARK_THEME 
                ? '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>' // Sun icon for dark mode
                : '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>'; // Moon icon for light mode
            
            toggleBtn.setAttribute('aria-label', theme === DARK_THEME ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối');
        }
    }

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        initTheme();
    }

    // Expose globally
    window.meraTheme = {
        toggle: toggleTheme,
        set: setTheme,
        get: function() {
            return document.documentElement.hasAttribute('data-theme') ? DARK_THEME : LIGHT_THEME;
        }
    };

    // Attach to toggle button if exists
    document.addEventListener('click', function(e) {
        const toggleBtn = e.target.closest('#theme-toggle');
        if (toggleBtn) {
            e.preventDefault();
            toggleTheme();
        }
    });

})();
