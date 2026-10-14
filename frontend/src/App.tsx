import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import { RequireRole } from './components/RequireRole';
import { Spinner } from './components/ui';
import PublicLayout from './layouts/PublicLayout';
import Landing from './pages/Landing';
import JoinPage from './pages/JoinPage';
import JoinQueuePage from './pages/JoinQueuePage';
import TokenPage from './pages/TokenPage';
import CheckTokenPage from './pages/CheckTokenPage';
import ScanPage from './pages/ScanPage';
import { LoginPage, NotFoundPage, RegisterPage } from './pages/AuthPages';
import { PrivacyPage, TermsPage } from './pages/LegalPages';

// Staff and admin areas are code-split so customers on phones never download them
const StaffLayout = lazy(() => import('./layouts/StaffLayout'));
const StaffHome = lazy(() => import('./pages/staff/StaffHome'));
const StaffQueuePage = lazy(() => import('./pages/staff/StaffQueuePage'));
const AdminLayout = lazy(() => import('./layouts/AdminLayout'));
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const AnalyticsPage = lazy(() => import('./pages/admin/AnalyticsPage'));
const QueuesPage = lazy(() => import('./pages/admin/QueuesPage'));
const Catalog = lazy(() => import('./pages/admin/CatalogPages').then((m) => ({ default: m.OrganizationsPage })));
const Branches = lazy(() => import('./pages/admin/CatalogPages').then((m) => ({ default: m.BranchesPage })));
const Departments = lazy(() => import('./pages/admin/CatalogPages').then((m) => ({ default: m.DepartmentsPage })));
const Services = lazy(() => import('./pages/admin/CatalogPages').then((m) => ({ default: m.ServicesPage })));
const Counters = lazy(() => import('./pages/admin/CountersAndStaff').then((m) => ({ default: m.CountersPage })));
const Staff = lazy(() => import('./pages/admin/CountersAndStaff').then((m) => ({ default: m.StaffPage })));
const Customers = lazy(() => import('./pages/admin/AdminLists').then((m) => ({ default: m.CustomersPage })));
const Tokens = lazy(() => import('./pages/admin/AdminLists').then((m) => ({ default: m.TokensPage })));
const Audit = lazy(() => import('./pages/admin/AdminLists').then((m) => ({ default: m.AuditPage })));
const Settings = lazy(() => import('./pages/admin/AdminLists').then((m) => ({ default: m.SettingsPage })));

const Fallback = (
  <div className="flex min-h-[40vh] items-center justify-center text-ink-500">
    <Spinner />
  </div>
);

export default function App() {
  return (
    <Suspense fallback={Fallback}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<Landing />} />
          <Route path="join" element={<JoinPage />} />
          <Route path="join/:ref" element={<JoinQueuePage />} />
          <Route path="scan" element={<ScanPage />} />
          <Route path="token" element={<CheckTokenPage />} />
          <Route path="queue/token/:reference" element={<TokenPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="privacy" element={<PrivacyPage />} />
          <Route path="terms" element={<TermsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        <Route element={<RequireRole roles={['STAFF', 'ADMIN']} />}>
          <Route path="staff" element={<StaffLayout />}>
            <Route index element={<StaffHome />} />
            <Route path="queues/:queueId" element={<StaffQueuePage />} />
          </Route>
        </Route>

        <Route element={<RequireRole roles={['ADMIN']} />}>
          <Route path="admin" element={<AdminLayout />}>
            <Route index element={<AdminDashboard />} />
            <Route path="organizations" element={<Catalog />} />
            <Route path="branches" element={<Branches />} />
            <Route path="departments" element={<Departments />} />
            <Route path="services" element={<Services />} />
            <Route path="queues" element={<QueuesPage />} />
            <Route path="counters" element={<Counters />} />
            <Route path="staff" element={<Staff />} />
            <Route path="customers" element={<Customers />} />
            <Route path="tokens" element={<Tokens />} />
            <Route path="analytics" element={<AnalyticsPage />} />
            <Route path="audit" element={<Audit />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
