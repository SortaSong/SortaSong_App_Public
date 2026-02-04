// SortaSong Shared Top Bar functionality

// Check if admin is logged in (has Supabase session)
function checkAdminLogin() {
    try {
        const session = localStorage.getItem('sb-hjzhojjnjnawwnwhzgwq-auth-token');
        if (session) {
            const parsed = JSON.parse(session);
            if (parsed && parsed.access_token) {
                document.body.classList.add('admin-logged-in');
                return true;
            }
        }
    } catch (e) {
        // Ignore errors
    }
    document.body.classList.remove('admin-logged-in');
    return false;
}

// Initialize top bar
function initTopBar() {
    // Check admin login
    checkAdminLogin();
    
    // Mark current page as active
    const currentPath = window.location.pathname;
    document.querySelectorAll('.top-bar-link').forEach(link => {
        const href = link.getAttribute('href');
        if (href && currentPath.includes(href.replace('../', '').replace('./', ''))) {
            link.classList.add('active');
        }
    });
}

// Run on DOM ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTopBar);
} else {
    initTopBar();
}
