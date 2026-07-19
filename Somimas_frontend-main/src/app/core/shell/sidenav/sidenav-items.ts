/**
 * Grouped sidebar navigation definition.
 *
 * All main navigation (previously split between the toolbar menus and the
 * sidebar "Main Items" list) is defined here so the sidebar renders it as
 * collapsible groups. Labels are translation keys under `labels.menus`.
 */
export interface SidenavItem {
  /** Unique id, also used as DOM anchor for the configuration-wizard tour. */
  id: string;
  /** Translation key under labels.menus. */
  label: string;
  /** Registered FontAwesome solid icon name. */
  icon: string;
  /** Router path. Mutually exclusive with `action`. */
  route?: string;
  /** Component action for non-route items. */
  action?: 'configurationWizard' | 'keyboardShortcuts' | 'help';
  /** Permission required to see this item. */
  permission?: string;
}

export interface SidenavGroup {
  id: string;
  /** Translation key under labels.menus. */
  label: string;
  /** Permission required to see the whole group. */
  permission?: string;
  items: SidenavItem[];
}

export const sidenavGroups: SidenavGroup[] = [
  {
    id: 'main',
    label: 'Main Items',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: 'tachometer-alt', route: '/dashboard' },
      { id: 'navigation', label: 'Navigation', icon: 'location-arrow', route: '/navigation' },
      {
        id: 'checker-inbox',
        label: 'Checker Inbox and Tasks',
        icon: 'check',
        route: '/checker-inbox-and-tasks/checker-inbox'
      },
      { id: 'notifications', label: 'Notifications', icon: 'bell', route: '/notifications' }
    ]
  },
  {
    id: 'institution',
    label: 'Institution',
    permission: 'READ_INSTITUTION',
    items: [
      { id: 'clients', label: 'Clients', icon: 'user', route: '/clients' },
      { id: 'groups', label: 'Groups', icon: 'users', route: '/groups' },
      { id: 'centers', label: 'Centers', icon: 'building', route: '/centers' },
      { id: 'collection-sheet', label: 'Collection Sheet', icon: 'tasks', route: '/collections/collection-sheet' },
      {
        id: 'individual-collection-sheet',
        label: 'Individual Collection Sheet',
        icon: 'tasks',
        route: '/collections/individual-collection-sheet'
      }
    ]
  },
  {
    id: 'accounting',
    label: 'Accounting',
    permission: 'READ_ACCOUNTING',
    items: [
      { id: 'accounting', label: 'Accounting', icon: 'money-bill-alt', route: '/accounting' },
      {
        id: 'frequent-postings',
        label: 'Frequent Postings',
        icon: 'sync',
        route: '/accounting/journal-entries/frequent-postings'
      },
      {
        id: 'create-journal-entry',
        label: 'Create Journal Entry',
        icon: 'plus',
        route: '/accounting/journal-entries/create'
      },
      { id: 'chart-of-accounts', label: 'Chart of Accounts', icon: 'sitemap', route: '/accounting/chart-of-accounts' }
    ]
  },
  {
    id: 'reports',
    label: 'Reports',
    permission: 'READ_REPORTS',
    items: [
      { id: 'reports-all', label: 'All', icon: 'chart-bar', route: '/reports' },
      { id: 'reports-clients', label: 'Clients', icon: 'user', route: '/reports/Client' },
      { id: 'reports-loans', label: 'Loans', icon: 'money-bill', route: '/reports/Loan' },
      { id: 'reports-savings', label: 'Savings', icon: 'piggy-bank', route: '/reports/Savings' },
      { id: 'reports-funds', label: 'Funds', icon: 'coins', route: '/reports/Fund' },
      { id: 'reports-accounting', label: 'Accounting', icon: 'table', route: '/reports/Accounting' }
    ]
  },
  {
    id: 'admin',
    label: 'Admin',
    permission: 'READ_ADMIN',
    items: [
      { id: 'users', label: 'Users', icon: 'user-shield', route: '/appusers' },
      { id: 'organization', label: 'Organization', icon: 'id-badge', route: '/organization' },
      { id: 'system', label: 'System', icon: 'cogs', route: '/system' },
      { id: 'products', label: 'Products', icon: 'book', route: '/products' },
      { id: 'templates', label: 'Templates', icon: 'address-card', route: '/templates' },
      {
        id: 'configuration-wizard',
        label: 'Configuration Wizard',
        icon: 'info',
        action: 'configurationWizard',
        permission: 'READ_CONFIG_WIZARD'
      }
    ]
  },
  {
    id: 'more',
    label: 'More',
    items: [
      { id: 'keyboard-shortcuts', label: 'Keyboard Shortcuts', icon: 'keyboard', action: 'keyboardShortcuts' },
      { id: 'help', label: 'Help', icon: 'question-circle', action: 'help' }
    ]
  }
];
