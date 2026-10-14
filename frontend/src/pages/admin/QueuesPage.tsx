import { useState } from 'react';
import { Modal } from '../../components/ConfirmDialog';
import { Button, EmptyState, ErrorNotice, PageHeader, QueueBadge, SelectField, Skeleton, TableWrap, TextField, Toggle } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { useToast } from '../../hooks/useToast';
import { HttpError } from '../../services/http';
import { adminApi, publicApi } from '../../services/api';
import type { AdminQueue, PriorityMode, QueueConfig, QueueStatus } from '../../types/api';

const DEFAULT_CONFIG: QueueConfig = {
  tokenPrefix: 'A',
  priorityPrefix: 'P',
  startingNumber: 1,
  numberPadding: 3,
  maxQueueSize: 200,
  priorityMode: 'DISABLED',
  priorityAfterN: 3,
  avgServiceMinutes: 5,
  openingTime: null,
  closingTime: null,
  breakStart: null,
  breakEnd: null,
  autoExpireMinutes: 240,
  noShowTimeoutMinutes: 5,
  allowDuplicateActive: false,
  directServing: false,
  nearTurnThreshold: 3,
};

export default function QueuesPage() {
  const { data, loading, error, reload } = useQuery(() => adminApi.queues(), []);
  const services = useQuery(() => adminApi.services(), []);
  const [editing, setEditing] = useState<AdminQueue | 'new' | null>(null);
  const [qr, setQr] = useState<AdminQueue | null>(null);

  const noServices = !services.loading && (services.data?.length ?? 0) === 0;

  return (
    <>
      <PageHeader
        title="Queues"
        subtitle="Each queue has its own token prefix, rules and QR code."
        actions={
          <Button disabled={noServices} onClick={() => setEditing('new')}>
            Add queue
          </Button>
        }
      />
      {noServices && <p className="mb-4 rounded-md bg-signal-100 px-4 py-3">Add a service first. Queues belong to a service.</p>}

      {loading ? (
        <Skeleton className="h-48" />
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : !data || data.length === 0 ? (
        <EmptyState title="No queues yet" hint="Create a queue to start issuing tokens." />
      ) : (
        <TableWrap>
          <table className="w-full">
            <thead>
              <tr>
                <th className="table-th">Queue</th>
                <th className="table-th">Service</th>
                <th className="table-th">Branch</th>
                <th className="table-th">Prefix</th>
                <th className="table-th">Waiting</th>
                <th className="table-th">Status</th>
                <th className="table-th" />
              </tr>
            </thead>
            <tbody>
              {data.map((q) => (
                <tr key={q.id} className="border-t border-paper-200">
                  <td className="table-td font-semibold">{q.name}</td>
                  <td className="table-td">{q.serviceName}</td>
                  <td className="table-td">{q.branchName}</td>
                  <td className="table-td font-display text-xl font-bold">{q.config.tokenPrefix}</td>
                  <td className="table-td">{q.waitingCount}</td>
                  <td className="table-td">
                    <div className="flex items-center gap-2">
                      <QueueBadge status={q.status} />
                      {!q.active && <span className="text-sm text-ink-500">Hidden</span>}
                    </div>
                  </td>
                  <td className="table-td">
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="secondary" onClick={() => setQr(q)}>
                        QR code
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setEditing(q)}>
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
        <QueueDialog
          queue={editing === 'new' ? null : editing}
          services={(services.data ?? []).map((s) => ({ value: s.id, label: `${s.name} (${s.departmentName}, ${s.branchName})` }))}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void reload();
          }}
        />
      )}
      {qr && <QrDialog queue={qr} onClose={() => setQr(null)} />}
    </>
  );
}

function QueueDialog({
  queue,
  services,
  onClose,
  onSaved,
}: {
  queue: AdminQueue | null;
  services: { value: string; label: string }[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(queue?.name ?? '');
  const [serviceId, setServiceId] = useState(services[0]?.value ?? '');
  const [status, setStatus] = useState<QueueStatus>(queue?.status ?? 'ACTIVE');
  const [active, setActive] = useState(queue?.active ?? true);
  const [cfg, setCfg] = useState<QueueConfig>(queue?.config ?? DEFAULT_CONFIG);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);

  const num = <K extends keyof QueueConfig>(key: K) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setCfg((c) => ({ ...c, [key]: Number(e.target.value) }));
  const text = <K extends keyof QueueConfig>(key: K, upper = false) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setCfg((c) => ({ ...c, [key]: upper ? e.target.value.toUpperCase() : e.target.value }));
  const time = <K extends keyof QueueConfig>(key: K) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setCfg((c) => ({ ...c, [key]: e.target.value || null }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setErrors({ name: 'Name is required' });
      return;
    }
    setBusy(true);
    setErrors({});
    setFormError('');
    try {
      if (queue) {
        await adminApi.updateQueue(queue.id, { name: name.trim(), status, active });
        await adminApi.updateQueueConfig(queue.id, cfg);
      } else {
        await adminApi.createQueue({ serviceId, name: name.trim(), config: cfg });
      }
      toast.success(queue ? 'Queue saved' : 'Queue created');
      onSaved();
    } catch (err) {
      if (err instanceof HttpError && err.fieldErrors) setErrors(err.fieldErrors);
      else setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open wide title={queue ? 'Edit queue' : 'Add queue'} onClose={onClose}>
      <form onSubmit={submit} className="grid gap-5" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Queue name" value={name} error={errors.name} maxLength={150} onChange={(e) => setName(e.target.value)} />
          {!queue ? (
            <SelectField label="Service" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
              {services.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </SelectField>
          ) : (
            <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as QueueStatus)}>
              <option value="ACTIVE">Open</option>
              <option value="PAUSED">Paused</option>
              <option value="CLOSED">Closed</option>
            </SelectField>
          )}
        </div>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 font-display text-2xl font-bold">Tokens</legend>
          <TextField label="Token prefix" value={cfg.tokenPrefix} error={errors.tokenPrefix} maxLength={6} onChange={text('tokenPrefix', true)} hint="Normal tokens look like A001" />
          <TextField label="Priority prefix" value={cfg.priorityPrefix} error={errors.priorityPrefix} maxLength={6} onChange={text('priorityPrefix', true)} hint="Must differ from the token prefix" />
          <TextField label="Starting number" type="number" min={0} value={cfg.startingNumber} error={errors.startingNumber} onChange={num('startingNumber')} hint="Numbers restart each day" />
          <TextField label="Digits in number" type="number" min={1} max={8} value={cfg.numberPadding} error={errors.numberPadding} onChange={num('numberPadding')} hint="3 gives A001" />
          <TextField label="Maximum queue size" type="number" min={1} value={cfg.maxQueueSize} error={errors.maxQueueSize} onChange={num('maxQueueSize')} />
          <TextField label="Average service time (minutes)" type="number" min={1} value={cfg.avgServiceMinutes} error={errors.avgServiceMinutes} onChange={num('avgServiceMinutes')} hint="Starting estimate. Real history takes over as visits complete." />
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 font-display text-2xl font-bold">Priority</legend>
          <SelectField label="Priority handling" value={cfg.priorityMode} onChange={(e) => setCfg((c) => ({ ...c, priorityMode: e.target.value as PriorityMode }))}>
            <option value="DISABLED">Disabled</option>
            <option value="ALWAYS_FIRST">Always served first</option>
            <option value="AFTER_N">After N normal customers</option>
          </SelectField>
          {cfg.priorityMode === 'AFTER_N' && (
            <TextField label="Normal customers between priority" type="number" min={1} value={cfg.priorityAfterN} error={errors.priorityAfterN} onChange={num('priorityAfterN')} />
          )}
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 font-display text-2xl font-bold">Hours (branch local time)</legend>
          <TextField label="Opens" type="time" value={cfg.openingTime ?? ''} onChange={time('openingTime')} hint="Leave both blank to accept tokens any time" />
          <TextField label="Closes" type="time" value={cfg.closingTime ?? ''} onChange={time('closingTime')} />
          <TextField label="Break starts" type="time" value={cfg.breakStart ?? ''} onChange={time('breakStart')} />
          <TextField label="Break ends" type="time" value={cfg.breakEnd ?? ''} onChange={time('breakEnd')} />
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 font-display text-2xl font-bold">Behavior</legend>
          <TextField label="No-show after (minutes)" type="number" min={1} value={cfg.noShowTimeoutMinutes} error={errors.noShowTimeoutMinutes} onChange={num('noShowTimeoutMinutes')} hint="Called tokens that do not arrive are marked no-show" />
          <TextField label="Expire waiting tokens after (minutes)" type="number" min={1} value={cfg.autoExpireMinutes} error={errors.autoExpireMinutes} onChange={num('autoExpireMinutes')} />
          <TextField label="Show 'turn is coming' at" type="number" min={0} value={cfg.nearTurnThreshold} error={errors.nearTurnThreshold} onChange={num('nearTurnThreshold')} hint="People ahead at or below this number" />
          <div className="grid content-start gap-4">
            <Toggle label="Allow several active tokens per person" checked={cfg.allowDuplicateActive} onChange={(v) => setCfg((c) => ({ ...c, allowDuplicateActive: v }))} />
            <Toggle label="Skip the 'called' step" hint="NEXT starts service straight away" checked={cfg.directServing} onChange={(v) => setCfg((c) => ({ ...c, directServing: v }))} />
          </div>
        </fieldset>

        {queue && <Toggle label="Visible to customers" hint="Hidden queues cannot be joined or found." checked={active} onChange={setActive} />}

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
            Save queue
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function QrDialog({ queue, onClose }: { queue: AdminQueue; onClose: () => void }) {
  const toast = useToast();
  const src = publicApi.qrUrl(queue.publicRef, { size: 512 });

  function print() {
    const w = window.open('', '_blank', 'width=720,height=900');
    if (!w) {
      toast.error('Allow pop-ups to print, or download the image instead.');
      return;
    }
    const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
    w.document.write(`<!doctype html><html><head><title>${esc(queue.name)} QR</title>
      <style>body{font-family:Barlow,Arial,sans-serif;text-align:center;margin:48px;color:#0B2545}
      h1{font-size:44px;margin:0 0 4px}p{font-size:22px;margin:4px 0}img{width:440px;height:440px;margin:28px 0}
      .small{font-size:15px;color:#4A6285;word-break:break-all}</style></head>
      <body><h1>${esc(queue.name)}</h1><p>${esc(queue.serviceName)} · ${esc(queue.branchName)}</p>
      <img src="${esc(src)}" alt="QR code to join ${esc(queue.name)}" />
      <p>Scan with your phone camera to get a token</p><p class="small">${esc(queue.joinUrl)}</p>
      <script>window.onload=function(){setTimeout(function(){window.print()},300)}<\/script></body></html>`);
    w.document.close();
  }

  return (
    <Modal open title={`${queue.name} QR code`} onClose={onClose}>
      <div className="flex flex-col items-center gap-3">
        <img src={src} alt={`QR code to join ${queue.name}`} width={256} height={256} className="rounded-md border border-paper-300" />
        <p className="max-w-full break-all text-center text-sm text-ink-500">{queue.joinUrl}</p>
        <p className="text-center text-sm text-ink-500">The code holds only this public link. It stays valid when you change queue settings.</p>
      </div>
      <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button
          variant="secondary"
          onClick={() => {
            void navigator.clipboard?.writeText(queue.joinUrl).then(() => toast.success('Link copied'));
          }}
        >
          Copy link
        </Button>
        <a
          href={publicApi.qrUrl(queue.publicRef, { size: 1024, download: true })}
          download={`smartqueue-${queue.publicRef}.png`}
          className="inline-flex min-h-[44px] items-center justify-center rounded-md border border-paper-300 bg-surface px-4 font-semibold hover:bg-paper-100"
        >
          Download PNG
        </a>
        <Button onClick={print}>Print</Button>
      </div>
    </Modal>
  );
}
