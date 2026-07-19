/** Angular Imports */
import {
  Component,
  OnInit,
  Input,
  EventEmitter,
  Output,
  ViewChild,
  AfterViewInit,
  ElementRef,
  TemplateRef,
  AfterContentChecked,
  ChangeDetectorRef,
  inject
} from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Router, RouterLink } from '@angular/router';

/** rxjs Imports */
import { Observable, of } from 'rxjs';
import { catchError, finalize, map, take } from 'rxjs/operators';

/** Custom Services */
import { AuthenticationService } from '../../authentication/authentication.service';
import { PopoverService } from '../../../configuration-wizard/popover/popover.service';
import { ConfigurationWizardService } from '../../../configuration-wizard/configuration-wizard.service';

/** Custom Components */
import { NotificationsTrayComponent } from 'app/shared/notifications-tray/notifications-tray.component';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { SearchToolComponent } from '../../../shared/search-tool/search-tool.component';
import { LanguageSelectorComponent } from '../../../shared/language-selector/language-selector.component';
import { MatIcon } from '@angular/material/icon';
import { NotificationsTrayComponent as NotificationsTrayComponent_1 } from '../../../shared/notifications-tray/notifications-tray.component';
import { ThemeToggleComponent } from '../../../shared/theme-toggle/theme-toggle.component';
import { STANDALONE_SHARED_IMPORTS } from 'app/standalone-shared.module';

/**
 * Toolbar component.
 *
 * All business navigation (Institution, Accounting, Reports, Admin,
 * Configuration Wizard) lives in the sidebar. The toolbar only hosts the
 * sidenav toggles and utility controls: search, language, notifications,
 * theme and the user menu.
 */
@Component({
  selector: 'mifosx-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss'],
  imports: [
    ...STANDALONE_SHARED_IMPORTS,
    MatToolbar,
    MatIconButton,
    MatTooltip,
    FaIconComponent,
    MatMenuTrigger,
    SearchToolComponent,
    LanguageSelectorComponent,
    MatIcon,
    NotificationsTrayComponent_1,
    ThemeToggleComponent,
    MatMenu,
    MatMenuItem
  ]
})
export class ToolbarComponent implements OnInit, AfterViewInit, AfterContentChecked {
  private breakpointObserver = inject(BreakpointObserver);
  private router = inject(Router);
  private authenticationService = inject(AuthenticationService);
  private popoverService = inject(PopoverService);
  private configurationWizardService = inject(ConfigurationWizardService);
  private changeDetector = inject(ChangeDetectorRef);

  /* Reference of global search */
  @ViewChild('globalSearch') globalSearch: ElementRef<any>;
  /* Template for popover on global search */
  @ViewChild('templateGlobalSearch') templateGlobalSearch: TemplateRef<any>;
  /* Reference of appMenu */
  @ViewChild('appMenu') appMenu: ElementRef<any>;
  /* Template for popover on appMenu */
  @ViewChild('templateAppMenu') templateAppMenu: TemplateRef<any>;
  @ViewChild('notificationsTray') notificationsTray: NotificationsTrayComponent;

  /** Subscription to breakpoint observer for handset. */
  isHandset$: Observable<boolean> = this.breakpointObserver
    .observe(Breakpoints.Handset)
    .pipe(map((result) => result.matches));

  /** Sets the initial state of sidenav as collapsed. Not collapsed if false. */
  sidenavCollapsed = true;

  /** Instance of sidenav. */
  @Input() sidenav: MatSidenav;
  /** Sidenav collapse event. */
  @Output() collapse = new EventEmitter<boolean>();

  /**
   * Subscribes to breakpoint for handset.
   */
  ngOnInit() {
    this.isHandset$.subscribe((isHandset) => {
      if (isHandset && this.sidenavCollapsed) {
        this.toggleSidenavCollapse(false);
      }
    });
  }

  ngAfterContentChecked(): void {
    this.changeDetector.detectChanges();
  }

  /**
   * Toggles the current state of sidenav.
   */
  toggleSidenav() {
    this.sidenav.toggle();
  }

  /**
   * Toggles the current collapsed state of sidenav.
   */
  toggleSidenavCollapse(sidenavCollapsed?: boolean) {
    this.sidenavCollapsed = sidenavCollapsed || !this.sidenavCollapsed;
    this.collapse.emit(this.sidenavCollapsed);
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
   * Popover function
   * @param template TemplateRef<any>.
   * @param target HTMLElement | ElementRef<any>.
   * @param position String.
   * @param backdrop Boolean.
   */
  showPopover(template: TemplateRef<any>, target: ElementRef<any> | HTMLElement): void {
    if (!target) {
      return;
    }
    setTimeout(() => this.popoverService.open(template, target, 'bottom', true, {}), 200);
  }

  /**
   * Next Step (SideNavbar) Configuration Wizard.
   */
  nextStep() {
    this.configurationWizardService.showToolbar = false;
    this.configurationWizardService.showToolbarAdmin = false;
    this.configurationWizardService.showSideNav = true;
    this.router.routeReuseStrategy.shouldReuseRoute = () => false;
    this.router.onSameUrlNavigation = 'reload';
    this.router.navigate(['/home']);
  }

  /**
   * To show popovers
   */
  ngAfterViewInit() {
    if (this.configurationWizardService.showToolbar === true) {
      setTimeout(() => {
        this.showPopover(this.templateGlobalSearch, this.globalSearch.nativeElement);
      });
    }

    if (
      this.configurationWizardService.showSideNav === true ||
      this.configurationWizardService.showSideNavChartofAccounts === true
    ) {
      this.toggleSidenavCollapse();
    }

    if (this.configurationWizardService.showToolbarAdmin === true) {
      setTimeout(() => {
        this.showPopover(this.templateAppMenu, this.appMenu.nativeElement);
      });
    }
  }
}
