/** Angular Imports */
import { Component, OnInit, Input, TemplateRef, ElementRef, ViewChild, AfterViewInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

/** Custom Components */
import { KeyboardShortcutsDialogComponent } from 'app/shared/keyboard-shortcuts-dialog/keyboard-shortcuts-dialog.component';
import { ConfigurationWizardComponent } from '../../../configuration-wizard/configuration-wizard.component';

/** Custom Services */
import { AuthenticationService } from '../../authentication/authentication.service';
import { PopoverService } from '../../../configuration-wizard/popover/popover.service';
import { ConfigurationWizardService } from '../../../configuration-wizard/configuration-wizard.service';

/** Custom Imports */
import { frequentActivities } from './frequent-activities';
import { sidenavGroups, SidenavGroup, SidenavItem } from './sidenav-items';
import { SettingsService } from 'app/settings/settings.service';
import { NgClass } from '@angular/common';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MatDivider } from '@angular/material/divider';
import { MatNavList, MatListItem } from '@angular/material/list';
import { MatIcon } from '@angular/material/icon';
import { MatLine } from '@angular/material/grid-list';
import { STANDALONE_SHARED_IMPORTS } from 'app/standalone-shared.module';

import { catchError, finalize, of, take } from 'rxjs';

/** Local storage key remembering which sidebar groups the user collapsed. */
const COLLAPSED_GROUPS_STORAGE_KEY = 'somimasSidenavCollapsedGroups';

/**
 * Sidenav component.
 */
@Component({
  selector: 'mifosx-sidenav',
  templateUrl: './sidenav.component.html',
  styleUrls: ['./sidenav.component.scss'],
  imports: [
    ...STANDALONE_SHARED_IMPORTS,
    NgClass,
    MatIconButton,
    MatTooltip,
    FaIconComponent,
    MatDivider,
    MatNavList,
    MatListItem,
    RouterLinkActive,
    MatIcon,
    MatLine
  ]
})
export class SidenavComponent implements OnInit, AfterViewInit {
  private router = inject(Router);
  dialog = inject(MatDialog);
  private authenticationService = inject(AuthenticationService);
  private settingsService = inject(SettingsService);
  private configurationWizardService = inject(ConfigurationWizardService);
  private popoverService = inject(PopoverService);

  /** True if sidenav is in collapsed state. */
  @Input() sidenavCollapsed: boolean;
  /** Tooltip position */
  tooltipPosition = 'after';
  /** Username of authenticated user. */
  username: string;
  /** Array of all user activities */
  userActivity: string[];
  /** Mapped Activites */
  mappedActivities: any[] = [];
  /** Collection of possible frequent activities */
  frequentActivities: any[] = frequentActivities;
  /** Grouped navigation items, filtered by user permissions. */
  navGroups: SidenavGroup[] = [];
  /** Ids of groups the user collapsed. */
  private collapsedGroups = new Set<string>();

  /* Refernce of logo */
  @ViewChild('logo') logo: ElementRef<any>;
  /* Template for popover on logo */
  @ViewChild('templateLogo') templateLogo: TemplateRef<any>;
  /* Template for popover on chart of accounts */
  @ViewChild('templateChartOfAccounts') templateChartOfAccounts: TemplateRef<any>;

  constructor() {
    this.userActivity = JSON.parse(localStorage.getItem('mifosXLocation'));
    try {
      const stored = JSON.parse(localStorage.getItem(COLLAPSED_GROUPS_STORAGE_KEY) || '[]');
      if (Array.isArray(stored)) {
        this.collapsedGroups = new Set(stored);
      }
    } catch {
      this.collapsedGroups = new Set();
    }
  }

  /**
   * Sets the username of the authenticated user and builds the
   * permission-filtered navigation groups.
   */
  ngOnInit() {
    const credentials = this.authenticationService.getCredentials();
    this.username = credentials.username;
    this.setMappedAcitivites();
    this.navGroups = sidenavGroups
      .filter((group) => this.hasPermission(group.permission))
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => this.hasPermission(item.permission))
      }))
      .filter((group) => group.items.length > 0);
  }

  /**
   * Checks a permission code against the user's permissions,
   * mirroring the HasPermissionDirective rules.
   */
  private hasPermission(permission?: string): boolean {
    if (!permission) {
      return true;
    }
    const userPermissions: string[] = this.authenticationService.getCredentials().permissions || [];
    if (userPermissions.includes('ALL_FUNCTIONS')) {
      return true;
    }
    if (permission.startsWith('READ_') && userPermissions.includes('ALL_FUNCTIONS_READ')) {
      return true;
    }
    return userPermissions.includes(permission);
  }

  /** True unless the user explicitly collapsed the group. */
  isExpanded(groupId: string): boolean {
    return !this.collapsedGroups.has(groupId);
  }

  /** Toggles a group's expanded state and persists it. */
  toggleGroup(groupId: string) {
    if (this.collapsedGroups.has(groupId)) {
      this.collapsedGroups.delete(groupId);
    } else {
      this.collapsedGroups.add(groupId);
    }
    localStorage.setItem(COLLAPSED_GROUPS_STORAGE_KEY, JSON.stringify(Array.from(this.collapsedGroups)));
  }

  /** Runs a non-route navigation item action. */
  runAction(action: SidenavItem['action']) {
    switch (action) {
      case 'configurationWizard':
        this.openConfigurationWizard();
        break;
      case 'keyboardShortcuts':
        this.showKeyboardShortcuts();
        break;
      case 'help':
        this.help();
        break;
    }
  }

  /**
   * Opens the Configuration Wizard dialog (previously launched from the toolbar).
   */
  openConfigurationWizard() {
    const configWizardRef = this.dialog.open(ConfigurationWizardComponent, {});

    configWizardRef.afterClosed().subscribe((response: { show: number } | undefined) => {
      if (!response) {
        return;
      }

      switch (response.show) {
        case 1:
          this.configurationWizardService.showToolbar = true;
          this.router.routeReuseStrategy.shouldReuseRoute = () => false;
          this.router.onSameUrlNavigation = 'reload';
          this.router.navigate(['/home']);
          break;
        case 2:
          this.configurationWizardService.showCreateOffice = true;
          this.router.navigate(['/organization']);
          break;
        case 3:
          this.configurationWizardService.showDatatables = true;
          this.router.navigate(['/system']);
          break;
        case 4:
          this.configurationWizardService.showChartofAccounts = true;
          this.router.navigate(['/accounting']);
          break;
        case 5:
          this.configurationWizardService.showCharges = true;
          this.router.navigate(['/products']);
          break;
        case 6:
          this.configurationWizardService.showManageFunds = true;
          this.router.navigate(['/organization']);
          break;
        default:
          break;
      }
    });
  }

  /**
   * Logs out the authenticated user and redirects to login page.
   * Uses unified AuthenticationService which handles both OAuth2 and OIDC logout.
   */
  logout() {
    this.authenticationService
      .logout()
      .pipe(
        take(1),
        catchError(() => of(void 0)),
        finalize(() => this.router.navigate(['/login'], { replaceUrl: true }))
      )
      .subscribe();
  }

  /**
   * Opens the Softecki support page.
   */
  help() {
    window.open('https://softecki.co.tz', '_blank');
  }

  /**
   * Opens Keyboard shortcuts dialog.
   */
  showKeyboardShortcuts() {
    const dialogRef = this.dialog.open(KeyboardShortcutsDialogComponent);
    dialogRef.afterClosed().subscribe((response: any) => {});
  }

  /**
   * Returns top three frequent activities.
   */
  getFrequentActivities() {
    const frequencyCounts: any = {};
    let index = this.userActivity?.length;
    while (index) {
      const activity = this.userActivity[--index];
      frequencyCounts[activity] = (frequencyCounts[activity] || 0) + 1;
    }
    const frequencyCountsArray = Object.entries(frequencyCounts);
    const topThreeFrequentActivities = frequencyCountsArray
      .sort((a: any, b: any) => b[1] - a[1])
      .map((entry: any[]) => entry[0])
      .filter(
        (activity: string) => ![
            '/',
            '/login',
            '/home',
            '/dashboard'
          ].includes(activity)
      )
      .slice(0, 3);
    return topThreeFrequentActivities;
  }

  /**
   * Maps frequently accessed urls to button objects.
   */
  setMappedAcitivites() {
    const activities: string[] = this.getFrequentActivities();
    activities.forEach((activity: string) => {
      if (activity.includes('/clients')) {
        this.pushActivity('/clients');
      } else if (activity.includes('/groups')) {
        this.pushActivity('/groups');
      } else if (activity.includes('/centers')) {
        this.pushActivity('/centers');
      } else if (activity.includes('/accounting')) {
        this.pushActivity('/accounting');
      } else if (activity.includes('/reports')) {
        this.pushActivity('/reports');
      } else if (activity.includes('/appusers')) {
        this.pushActivity('/appusers');
      } else if (activity.includes('/organization')) {
        this.pushActivity('/organization');
      } else if (activity.includes('/system')) {
        this.pushActivity('/system');
      } else if (activity.includes('/products')) {
        this.pushActivity('/products');
      } else if (activity.includes('/templates')) {
        this.pushActivity('/templates');
      }
    });
    this.mappedActivities.reverse();
  }

  /**
   * Pushes activity to mapped activities
   * @param {string} path Activity Path
   */
  pushActivity(path: string) {
    const activity = this.frequentActivities.find((entry: any) => entry.path === path);
    if (!this.mappedActivities.includes(activity)) {
      this.mappedActivities.push(activity);
    }
  }

  /**
   * Popover function
   * @param template TemplateRef<any>.
   * @param target HTMLElement | ElementRef<any>.
   * @param position String.
   * @param backdrop Boolean.
   */
  showPopover(
    template: TemplateRef<any>,
    target: HTMLElement | ElementRef<any>,
    position: string,
    backdrop: boolean
  ): void {
    setTimeout(() => this.popoverService.open(template, target, position, backdrop, {}), 200);
  }

  /**
   * Shows a popover anchored to a navigation item rendered from the grouped
   * config (items carry `id="sidenav-item-<id>"` in the DOM).
   */
  showPopoverById(template: TemplateRef<any>, itemId: string, position: string): void {
    const target = document.getElementById('sidenav-item-' + itemId);
    if (target) {
      this.showPopover(template, target, position, true);
    }
  }

  /**
   * To show popovers
   */
  ngAfterViewInit() {
    if (this.configurationWizardService.showSideNav === true) {
      setTimeout(() => {
        this.showPopover(this.templateLogo, this.logo.nativeElement, 'bottom', true);
      });
    }
    if (this.configurationWizardService.showSideNavChartofAccounts === true) {
      setTimeout(() => {
        this.showPopoverById(this.templateChartOfAccounts, 'chart-of-accounts', 'top');
      });
    }
  }

  /**
   * Next Step (Breadcrumbs) Configuration Wizard.
   */
  nextStep() {
    this.configurationWizardService.showSideNav = false;
    this.configurationWizardService.showSideNavChartofAccounts = false;
    this.configurationWizardService.showBreadcrumbs = true;
    this.router.routeReuseStrategy.shouldReuseRoute = () => false;
    this.router.onSameUrlNavigation = 'reload';
    this.router.navigate(['/home']);
  }

  /**
   * Previous Step (Toolbar) Configuration Wizard.
   */
  previousStep() {
    this.configurationWizardService.showSideNav = false;
    this.configurationWizardService.showSideNavChartofAccounts = false;
    this.configurationWizardService.showToolbarAdmin = true;
    this.router.routeReuseStrategy.shouldReuseRoute = () => false;
    this.router.onSameUrlNavigation = 'reload';
    this.router.navigate(['/home']);
  }

  get tenantIdentifier(): string {
    if (!this.settingsService.tenantIdentifier || this.settingsService.tenantIdentifier === '') {
      return 'default';
    }
    return this.settingsService.tenantIdentifier;
  }
}
