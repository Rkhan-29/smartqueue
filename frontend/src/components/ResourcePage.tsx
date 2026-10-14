import { useState, type ReactNode } from 'react';
import { Modal } from './ConfirmDialog';
import { Button, EmptyState, ErrorNotice, PageHeader, SelectField, Skeleton, TableWrap, TextAreaField, TextField, Toggle } from './ui';
import { errorMessage, useQuery } from '../hooks/useApi';
import { useToast } from '../hooks/useToast';
import { HttpError } from '../services/http';

export interface FieldDef {
  name: string;
  label: string;
  type?: 'text' | 'textarea' | 'select' | 'toggle';
  options?: { value: string; label: string }[];
  required?: boolean;
  hint?: string;
  /** Not editable after creation (e.g. the parent of a record). */
  createOnly?: boolean;
  placeholder?: string;
  maxLength?: number;
}

export interface Column<T> {
  header: string;
  render: (item: T) => ReactNode;
}

interface Props<T extends { id: string; active?: boolean }> {
  title: string;
  subtitle: string;
  noun: string;
  load: () => Promise<T[]>;
  columns: Column<T>[];
  fields: FieldDef[];
  save: (id: string | null, body: Record<string, unknown>) => Promise<unknown>;
  initialValues: (item: T | null) => Record<string, string | boolean>;
  emptyHint: string;
  /** When true the create button is disabled (e.g. no parent exists yet) and this message is shown. */
  blockedReason?: string;
  /** Re-load when this changes (e.g. parent options loaded). */
  deps?: unknown[];
}

/** Config-driven list + create/edit dialog used for organizations, branches, departments and services. */
export function ResourcePage<T extends { id: string; active?: boolean }>({
  title,
  subtitle,
  noun,
  load,
  columns,
  fields,
  save,
  initialValues,
  emptyHint,
  blockedReason,
  deps = [],
}: Props<T>) {
  const toast = useToast();
  const { data, loading, error, reload } = useQuery(load, deps);
  const [editing, setEditing] = useState<T | null | 'new'>(null);

  return (
    <>
      <PageHeader
        title={title}
        subtitle={subtitle}
        actions={
          <Button variant="primary" disabled={!!blockedReason} onClick={() => setEditing('new')}>
            Add {noun}
          </Button>
        }
      />
      {blockedReason && <p className="mb-4 rounded-md bg-signal-100 px-4 py-3">{blockedReason}</p>}

      {loading ? (
        <Skeleton className="h-48" />
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : !data || data.length === 0 ? (
        <EmptyState title={`No ${noun}s yet`} hint={emptyHint} />
      ) : (
        <TableWrap>
          <table className="w-full">
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c.header} className="table-th">
                    {c.header}
                  </th>
                ))}
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {data.map((item) => (
                <tr key={item.id} className="border-t border-paper-200">
                  {columns.map((c) => (
                    <td key={c.header} className="table-td">
                      {c.render(item)}
                    </td>
                  ))}
                  <td className="table-td">
                    <span className={`rounded-full px-2.5 py-0.5 text-sm font-semibold ${item.active === false ? 'bg-paper-200 text-ink-500' : 'bg-go-100 text-[#0d6551]'}`}>
                      {item.active === false ? 'Inactive' : 'Active'}
                    </span>
                  </td>
                  <td className="table-td text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(item)}>
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
        <EditDialog
          title={editing === 'new' ? `Add ${noun}` : `Edit ${noun}`}
          fields={fields}
          isNew={editing === 'new'}
          initial={initialValues(editing === 'new' ? null : editing)}
          onClose={() => setEditing(null)}
          onSave={async (values) => {
            await save(editing === 'new' ? null : editing.id, values);
            toast.success(editing === 'new' ? `${noun} added` : `${noun} saved`);
            setEditing(null);
            void reload();
          }}
        />
      )}
    </>
  );
}

function EditDialog({
  title,
  fields,
  isNew,
  initial,
  onClose,
  onSave,
}: {
  title: string;
  fields: FieldDef[];
  isNew: boolean;
  initial: Record<string, string | boolean>;
  onClose: () => void;
  onSave: (values: Record<string, unknown>) => Promise<void>;
}) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (name: string, v: string | boolean) => setValues((cur) => ({ ...cur, [name]: v }));

  return (
    <Modal open title={title} onClose={onClose}>
      <form
        className="grid gap-4"
        noValidate
        onSubmit={async (e) => {
          e.preventDefault();
          const missing: Record<string, string> = {};
          fields.forEach((f) => {
            if (f.required && f.type !== 'toggle' && !String(values[f.name] ?? '').trim()) missing[f.name] = `${f.label} is required`;
          });
          if (Object.keys(missing).length) {
            setErrors(missing);
            return;
          }
          setBusy(true);
          setErrors({});
          setFormError('');
          try {
            const body: Record<string, unknown> = {};
            fields.forEach((f) => {
              const v = values[f.name];
              body[f.name] = typeof v === 'string' ? v.trim() || undefined : v;
            });
            await onSave(body);
          } catch (err) {
            if (err instanceof HttpError && err.fieldErrors) setErrors(err.fieldErrors);
            else setFormError(errorMessage(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        {fields.map((f) => {
          const common = { label: f.label, hint: f.hint, error: errors[f.name] };
          const disabled = f.createOnly && !isNew;
          if (f.type === 'toggle') {
            return <Toggle key={f.name} label={f.label} hint={f.hint} checked={!!values[f.name]} onChange={(v) => set(f.name, v)} />;
          }
          if (f.type === 'select') {
            return (
              <SelectField key={f.name} {...common} disabled={disabled} value={String(values[f.name] ?? '')} onChange={(e) => set(f.name, e.target.value)}>
                <option value="">Select...</option>
                {f.options?.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </SelectField>
            );
          }
          if (f.type === 'textarea') {
            return <TextAreaField key={f.name} {...common} rows={3} maxLength={f.maxLength} value={String(values[f.name] ?? '')} onChange={(e) => set(f.name, e.target.value)} />;
          }
          return (
            <TextField key={f.name} {...common} placeholder={f.placeholder} maxLength={f.maxLength} value={String(values[f.name] ?? '')} onChange={(e) => set(f.name, e.target.value)} />
          );
        })}
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
