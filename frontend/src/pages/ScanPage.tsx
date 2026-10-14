import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import jsQR from 'jsqr';
import { Button, Card, TextField } from '../components/ui';
import { extractQueueRef } from '../utils/format';

/** Method 2: scan a queue QR code with the device camera (or paste the link as a fallback). */
export default function ScanPage() {
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [status, setStatus] = useState<'idle' | 'starting' | 'scanning' | 'denied' | 'unsupported'>('idle');
  const [manual, setManual] = useState('');
  const [manualError, setManualError] = useState('');
  const streamRef = useRef<MediaStream | null>(null);

  function stop() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }

  async function start() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setStatus('unsupported');
      return;
    }
    setStatus('starting');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) return;
      video.srcObject = stream;
      await video.play();
      setStatus('scanning');
    } catch {
      setStatus('denied');
    }
  }

  useEffect(() => {
    if (status !== 'scanning') return;
    let raf = 0;
    let last = 0;
    const tick = (time: number) => {
      raf = requestAnimationFrame(tick);
      if (time - last < 120) return; // ~8 scans per second is plenty
      last = time;
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (!video || !canvas || video.videoWidth === 0) return;
      const w = Math.min(video.videoWidth, 640);
      const h = Math.round((video.videoHeight / video.videoWidth) * w);
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      if (!ctx) return;
      ctx.drawImage(video, 0, 0, w, h);
      const code = jsQR(ctx.getImageData(0, 0, w, h).data, w, h, { inversionAttempts: 'dontInvert' });
      const ref = code ? extractQueueRef(code.data) : null;
      if (ref) {
        stop();
        navigate(`/join/${ref}`);
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [status, navigate]);

  useEffect(() => stop, []);

  return (
    <div className="mx-auto max-w-lg px-4 py-10">
      <h1 className="text-5xl">Scan a queue code</h1>
      <p className="mt-2 text-ink-500">Point your camera at the QR code on the counter or entrance sign.</p>

      <div className="relative mt-6 aspect-square overflow-hidden rounded-lg bg-ink">
        <video ref={videoRef} className="h-full w-full object-cover" playsInline muted aria-label="Camera preview" />
        <canvas ref={canvasRef} className="hidden" />
        {status === 'scanning' && (
          <div className="pointer-events-none absolute inset-8 rounded-lg border-4 border-signal" aria-hidden />
        )}
        {status !== 'scanning' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-white">
            {status === 'denied' && <p>Camera access was blocked. Allow it in your browser settings, or paste the link below.</p>}
            {status === 'unsupported' && <p>This browser cannot open the camera here. Paste the link below instead.</p>}
            {(status === 'idle' || status === 'starting') && <p>The camera is only used on this page to read the code. Nothing is recorded.</p>}
            <Button variant="call" size="lg" loading={status === 'starting'} onClick={() => void start()}>
              {status === 'denied' ? 'Try the camera again' : 'Start camera'}
            </Button>
          </div>
        )}
      </div>

      <Card className="mt-6">
        <form
          className="grid gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            const ref = extractQueueRef(manual);
            if (!ref) {
              setManualError('That does not look like a SmartQueue link.');
              return;
            }
            navigate(`/join/${ref}`);
          }}
        >
          <TextField
            label="Or paste the queue link"
            value={manual}
            error={manualError}
            onChange={(e) => {
              setManual(e.target.value);
              setManualError('');
            }}
            placeholder="https://.../join/..."
          />
          <Button type="submit" variant="secondary" disabled={!manual.trim()}>
            Open queue
          </Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-ink-500">
        No QR code nearby?{' '}
        <Link to="/join" className="font-semibold text-ink underline underline-offset-4">
          Pick a queue from the list
        </Link>
      </p>
    </div>
  );
}
