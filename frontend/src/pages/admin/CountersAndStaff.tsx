import { useEffect, useState } from 'react';
import { Modal } from '../../components/ConfirmDialog';
import { Button, EmptyState, ErrorNotice, PageHeader, SelectField, Skeleton, TableWrap, TextField, Toggle } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { HttpError } from '../../services/http';
import { adminApi } from '../../services/api';
import type { AdminQueue, AdminUser, Counter, Role, UserStatus } from '../../types/api';
import { formatDateTime } from '../../utils/format';

// ------------------------------------------------------------------ Counters

export function CountersPage() {
  const toast = useToast();
  const queues = useQuery(() => adminApi.queues(), []);
  const [queueId, setQueueId] = useState('');
  const [editing, setEditing] = useState<Counter | 'new' | null>(null);
  const [name, setName] = useState('');
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (!queueId && queues.data?.length) setQueueId(queues.data[0].id);
  }, [queues.data, queueId]);

  const counters = useQuery(() => (queueId ? adminApi.counters(queueId) : Promise.resolve([] as Counter[])), [queueId]);

  function open(c: Counter | 'new') {
    setEditing(c);
    setName(c === 'new' ? '' : c.name);
    setActive(c === 'new' ? true : c.active);
    setErr('');
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setErr('Name is required');
      return;
    }
    setBusy(true);
    try {
      await adminApi.saveCounter(queueId, editing === 'new' ? null : (editing as Counter).id, { name: name.trim(), active });
      toast.success('Counter saved');
      setEditing(null);
      void counters.reload();
    } catch (e2) {
      setErr(e2 instanceof HttpError && e2.fieldErrors?.name ? e2.fieldErrors.name : errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Counters"
        subtitle="Desks or windows where tokens are served. Staff pick a counter when calling the next token."
        actions={
          <Button disabled={!queueId} onClick={() => open('new')}>
            Add counter
          </Button>
        }
      />
      <div className="mb-5 max-w-sm">
        <SelectField label="Queue" value={queueId} onChange={(e) => setQueueId(e.target.value)}>
          {queues.data?.length ? (
            queues.data.map((q: AdminQueue) => (
              <option key={q.id} value={q.id}>
                {q.name} ({q.branchName})
              </option>
            ))
          ) : (
            <option value="">No queues yet</option>
          )}
        </SelectField>
      </div>

      {queues.loading || counters.loading ? (
        <Skeleton className="h-40" />
      ) : counters.error ? (
        <ErrorNotice message={errorMessage(counters.error)} onRetry={() => void counters.reload()} />
      ) : !counters.data || counters.data.length === 0 ? (
        <EmptyState title="No counters for this queue" hint="Without counters, customers are told to go to the service area." />
      ) : (
        <TableWrap>
          <table className="w-full">
            <thead>
              <tr>
                <th className="table-th">Counter</th>
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {counters.data.map((c) => (
                <tr key={c.id} className="border-t border-paper-200">
                  <td className="table-td font-semibold">{c.name}</td>
                  <td className="table-td">{c.active ? 'Active' : 'Inactive'}</td>
                  <td className="table-td text-right">
                    <Button size="sm" variant="ghost" onClick={() => open(c)}>
                      Edit
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}

      {editing && (
        <Modal open title={editing === 'new' ? 'Add counter' : 'Edit counter'} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="grid gap-4" noValidate>
            <TextField label="Name" value={name} maxLength={80} error={err} onChange={(e) => setName(e.target.value)} placeholder="Counter 1" />
            <Toggle label="Active" hint="Inactive counters cannot be chosen when calling tokens." checked={active} onChange={setActive} />
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="secondary" onClick={() => setEditing(null)}>
                Cancel
              </Button>
              <Button type="submit" loading={busy}>
                Save
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}

// ------------------------------------------------------------------ Staff

export function StaffPage() {
  const { user: me } = useAuth();
  const staff = useQuery(() => adminApi.staff(), []);
  const queues = useQuery(() => adminApi.queues(), []);
  const [editing, setEditing] = useState<AdminUser | 'new' | null>(null);
  const [resetFor, setResetFor] = useState<AdminUser | null>(null);

  return (
    <>
      <PageHeader
        title="Staff"
        subtitle="People who sign in to call tokens or administer SmartQueue."
        actions={<Button onClick={() => setEditing('new')}>Add staff member</Button>}
      />
      {staff.loading ? (
        <Skeleton className="h-48" />
      ) : staff.error ? (
        <ErrorNotice message={errorMessage(staff.error)} onRetry={() => void staff.reload()} />
      ) : !staff.data || staff.data.length === 0 ? (
        <EmptyState title="No staff yet" hint="Add a staff member and assign them to queues." />
      ) : (
        <TableWrap>
          <table className="w-full">
            <thead>
              <tr>
                <th className="table-th">Name</th>
                <th className="table-th">Username</th>
                <th className="table-th">Role</th>
                <th className="table-th">Queues</th>
                <th className="table-th">Last sign-in</th>
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {staff.data.map((u) => (
                <tr key={u.id} className="border-t border-paper-200">
                  <td className="table-td">
                    <div className="font-semibold">{u.fullName}</div>
                    <div className="text-sm text-ink-500">{u.email}</div>
                  </td>
                  <td className="table-td">{u.username}</td>
                  <td className="table-td">{u.role === 'ADMIN' ? 'Admin' : 'Staff'}</td>
                  <td className="table-td">{u.role === 'ADMIN' ? 'All' : u.queueIds.length}</td>
                  <td className="table-td">{formatDateTime(u.lastLoginAt)}</td>
                  <td className="table-td">{u.status === 'ACTIVE' ? 'Active' : 'Disabled'}</td>
                  <td className="table-td">
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => setResetFor(u)}>
                        Reset password
                      </Button>
                      <Button size="sm" variant="secondary" onClick={() => setEditing(u)}>
                        Edit
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}

      {editing && (
        <StaffDialog
          user={editing === 'new' ? null : editing}
          isSelf={editing !== 'new' && editing.id === me?.id}
          queues={queues.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void staff.reload();
          }}
        />
      )}
      {resetFor && <ResetDialog user={resetFor} onClose={() => setResetFor(null)} />}
    </>
  );
}

function QueueChecklist({ queues, value, onChange }: { queues: AdminQueue[]; value: Set<string>; onChange: (s: Set<string>) => void }) {
  if (queues.length === 0) return <p className="text-sm text-ink-500">No queues exist yet.</p>;
  return (
    <fieldset>
      <legend className="mb-1 text-[15px] font-semibold">Assigned queues</legend>
      <div className="grid max-h-44 gap-1 overflow-y-auto rounded-md border border-paper-300 p-3">
        {queues.map((q) => (
          <label key={q.id} className="flex items-center gap-3">
            <input
              type="checkbox"
              className="h-5 w-5 accent-ink"
              checked={value.has(q.id)}
              onChange={(e) => {
                const next = new Set(value);
                if (e.target.checked) next.add(q.id);
                else next.delete(q.id);
                onChange(next);
              }}
            />
            <span>
              {q.name} <span className="text-ink-500">({q.branchName})</span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function StaffDialog({
  user,
  isSelf,
  queues,
  onClose,
  onSaved,
}: {
  user: AdminUser | null;
  isSelf: boolean;
  queues: AdminQueue[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(user?.role ?? 'STAFF');
  const [status, setStatus] = useState<UserStatus>(user?.status ?? 'ACTIVE');
  const [assigned, setAssigned] = useState<Set<string>>(new Set(user?.queueIds ?? []));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    setFormError('');
    try {
      if (user) {
        await adminApi.updateUser(user.id, { fullName, role, status, queueIds: [...assigned] });
      } else {
        await adminApi.createStaff({ fullName, email, username, password, role, queueIds: [...assigned] });
      }
      toast.success(user ? 'Staff member saved' : 'Staff member added');
      onSaved();
    } catch (err) {
      if (err instanceof HttpError && err.fieldErrors) setErrors(err.fieldErrors);
      else setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title={user ? 'Edit staff member' : 'Add staff member'} onClose={onClose}>
      <form onSubmit={submit} className="grid gap-4" noValidate>
        <TextField label="Full name" value={fullName} error={errors.fullName} onChange={(e) => setFullName(e.target.value)} />
        {!user && (
          <>
            <TextField label="Email" type="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
            <TextField label="Username" value={username} error={errors.username} onChange={(e) => setUsername(e.target.value)} />
            <TextField
              label="Temporary password"
              type="password"
              autoComplete="new-password"
              value={password}
              error={errors.password}
              hint="At least 10 characters with upper and lower case letters, a number and a symbol."
              onChange={(e) => setPassword(e.target.value)}
            />
          </>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Role" value={role} disabled={isSelf} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="STAFF">Staff</option>
            <option value="ADMIN">Admin</option>
          </SelectField>
          {user && (
            <SelectField label="Account" value={status} disabled={isSelf} onChange={(e) => setStatus(e.target.value as UserStatus)} hint={isSelf ? 'You cannot change your own role or status.' : undefined}>
              <option value="ACTIVE">Active</option>
              <option value="DISABLED">Disabled</option>
            </SelectField>
          )}
        </div>
        {role === 'STAFF' && <QueueChecklist queues={queues} value={assigned} onChange={setAssigned} />}
        {formError && (
          <p role="alert" className="rounded-md bg-stop-100 px-3 py-2 text-[15px]">
            {formError}
          </p>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={busy}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function ResetDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  return (
    <Modal open title={`Reset password for ${user.fullName}`} onClose={onClose}>
      <form
        className="grid gap-4"
        noValidate
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError('');
          try {
            await adminApi.resetPassword(user.id, password);
            toast.success('Password reset. They are signed out everywhere.');
            onClose();
          } catch (err) {
            setError(err instanceof HttpError && err.fieldErrors?.newPassword ? err.fieldErrors.newPassword : errorMessage(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        <TextField label="New password" type="password" autoComplete="new-password" value={password} error={error} onChange={(e) => setPassword(e.target.value)} />
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={busy} disabled={!password}>
            Reset password
          </Button>
        </div>
      </form>
    </Modal>
  );
}
