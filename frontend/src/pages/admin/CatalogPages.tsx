import { ResourcePage } from '../../components/ResourcePage';
import { useQuery } from '../../hooks/useApi';
import { adminApi } from '../../services/api';
import type { Branch, Department, Organization, ServiceItem } from '../../types/api';

export function OrganizationsPage() {
  return (
    <ResourcePage<Organization>
      title="Organizations"
      subtitle="The hospitals, banks or offices that run queues."
      noun="organization"
      load={adminApi.organizations}
      columns={[
        { header: 'Name', render: (o) => <span className="font-semibold">{o.name}</span> },
        { header: 'Slug', render: (o) => o.slug },
        { header: 'Description', render: (o) => o.description ?? '-' },
      ]}
      fields={[
        { name: 'name', label: 'Name', required: true, maxLength: 150 },
        { name: 'slug', label: 'Slug', required: true, hint: 'Lowercase letters, numbers and dashes, for example smartcare-hospital', maxLength: 80 },
        { name: 'description', label: 'Description', type: 'textarea', maxLength: 500 },
        { name: 'active', label: 'Active', type: 'toggle', hint: 'Inactive organizations are hidden from customers.' },
      ]}
      initialValues={(o) => ({ name: o?.name ?? '', slug: o?.slug ?? '', description: o?.description ?? '', active: o?.active ?? true })}
      save={(id, body) => adminApi.saveOrganization(id, body)}
      emptyHint="Add your first organization to get started."
    />
  );
}

export function BranchesPage() {
  const orgs = useQuery(() => adminApi.organizations(), []);
  const options = (orgs.data ?? []).map((o) => ({ value: o.id, label: o.name }));
  return (
    <ResourcePage<Branch>
      title="Branches"
      subtitle="Locations of an organization. Each branch has its own time zone for working hours and daily token numbers."
      noun="branch"
      load={adminApi.branches}
      deps={[orgs.data]}
      blockedReason={!orgs.loading && options.length === 0 ? 'Add an organization first.' : undefined}
      columns={[
        { header: 'Branch', render: (b) => <span className="font-semibold">{b.name}</span> },
        { header: 'Organization', render: (b) => b.organizationName },
        { header: 'City', render: (b) => b.city ?? '-' },
        { header: 'Time zone', render: (b) => b.timeZone },
      ]}
      fields={[
        { name: 'organizationId', label: 'Organization', type: 'select', options, required: true, createOnly: true },
        { name: 'name', label: 'Name', required: true, maxLength: 150 },
        { name: 'address', label: 'Address', maxLength: 300 },
        { name: 'city', label: 'City', maxLength: 100 },
        { name: 'timeZone', label: 'Time zone', placeholder: 'Asia/Kolkata', hint: 'An IANA time zone such as Asia/Kolkata or Europe/London.', maxLength: 60 },
        { name: 'active', label: 'Active', type: 'toggle' },
      ]}
      initialValues={(b) => ({
        organizationId: b?.organizationId ?? options[0]?.value ?? '',
        name: b?.name ?? '',
        address: b?.address ?? '',
        city: b?.city ?? '',
        timeZone: b?.timeZone ?? 'UTC',
        active: b?.active ?? true,
      })}
      save={(id, body) => adminApi.saveBranch(id, body)}
      emptyHint="Add a branch for an organization."
    />
  );
}

export function DepartmentsPage() {
  const branches = useQuery(() => adminApi.branches(), []);
  const options = (branches.data ?? []).map((b) => ({ value: b.id, label: `${b.name} (${b.organizationName})` }));
  return (
    <ResourcePage<Department>
      title="Departments"
      subtitle="Groups of services inside a branch, such as Outpatient or Accounts."
      noun="department"
      load={adminApi.departments}
      deps={[branches.data]}
      blockedReason={!branches.loading && options.length === 0 ? 'Add a branch first.' : undefined}
      columns={[
        { header: 'Department', render: (d) => <span className="font-semibold">{d.name}</span> },
        { header: 'Branch', render: (d) => d.branchName },
      ]}
      fields={[
        { name: 'branchId', label: 'Branch', type: 'select', options, required: true, createOnly: true },
        { name: 'name', label: 'Name', required: true, maxLength: 150 },
        { name: 'active', label: 'Active', type: 'toggle' },
      ]}
      initialValues={(d) => ({ branchId: d?.branchId ?? options[0]?.value ?? '', name: d?.name ?? '', active: d?.active ?? true })}
      save={(id, body) => adminApi.saveDepartment(id, body)}
      emptyHint="Add a department to a branch."
    />
  );
}

export function ServicesPage() {
  const depts = useQuery(() => adminApi.departments(), []);
  const options = (depts.data ?? []).map((d) => ({ value: d.id, label: `${d.name} (${d.branchName})` }));
  return (
    <ResourcePage<ServiceItem>
      title="Services"
      subtitle="What customers come for, such as General Consultation or Billing. Each service can have one or more queues."
      noun="service"
      load={adminApi.services}
      deps={[depts.data]}
      blockedReason={!depts.loading && options.length === 0 ? 'Add a department first.' : undefined}
      columns={[
        { header: 'Service', render: (s) => <span className="font-semibold">{s.name}</span> },
        { header: 'Department', render: (s) => s.departmentName },
        { header: 'Branch', render: (s) => s.branchName },
        { header: 'Description', render: (s) => s.description ?? '-' },
      ]}
      fields={[
        { name: 'departmentId', label: 'Department', type: 'select', options, required: true, createOnly: true },
        { name: 'name', label: 'Name', required: true, maxLength: 150 },
        { name: 'description', label: 'Description', type: 'textarea', maxLength: 500 },
        { name: 'active', label: 'Active', type: 'toggle' },
      ]}
      initialValues={(s) => ({
        departmentId: s?.departmentId ?? options[0]?.value ?? '',
        name: s?.name ?? '',
        description: s?.description ?? '',
        active: s?.active ?? true,
      })}
      save={(id, body) => adminApi.saveService(id, body)}
      emptyHint="Add a service to a department."
    />
  );
}
