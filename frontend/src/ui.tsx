import { useState, type ReactNode, type InputHTMLAttributes, type SelectHTMLAttributes } from 'react'
import { Download } from 'lucide-react'
import { ApiError, download } from './api'

export const Card = ({ children, className = '' }: { children: ReactNode; className?: string }) => <section className={'card ' + className}>{children}</section>
export const Stat = ({ label, value, bad }: { label: string; value: ReactNode; bad?: boolean }) => (
  <div className={'stat' + (bad ? ' bad' : '')}><small>{label}</small><b>{value}</b></div>
)
export const Tag = ({ children, kind = '' }: { children: ReactNode; kind?: '' | 'go' | 'late' | 'warn' }) => <span className={'tag ' + kind}>{children}</span>
export const Empty = ({ children }: { children: ReactNode }) => <p className="note">{children}</p>

export function Field({ label, error, ...p }: { label: string; error?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return <label className="fld">{label}<input {...p} aria-invalid={!!error} />{error && <em>{error}</em>}</label>
}
export function Select({ label, children, ...p }: { label: string; children: ReactNode } & SelectHTMLAttributes<HTMLSelectElement>) {
  return <label className="fld">{label}<select {...p}>{children}</select></label>
}

export type Note = { kind: 'ok' | 'err'; text: string } | null
export const Msg = ({ note }: { note: Note }) => <div className={'msg ' + (note?.kind ?? '')} role="status">{note?.text ?? ''}</div>
export const errNote = (e: unknown): Note => ({ kind: 'err', text: e instanceof ApiError ? e.message : 'Something went wrong. Please try again.' })

/** Small helper for forms: state + note + a guarded submit that shows API errors. */
export function useForm<T extends Record<string, string>>(init: T) {
  const [v, setV] = useState<T>(init)
  const [note, setNote] = useState<Note>(null)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const bind = (k: keyof T) => ({ value: v[k], onChange: (e: { target: { value: string } }) => setV(s => ({ ...s, [k]: e.target.value })) })
  const submit = async (fn: () => Promise<string | void>, reset = true) => {
    setBusy(true); setNote(null); setFields({})
    try {
      const m = await fn()
      setNote({ kind: 'ok', text: m || 'Saved.' })
      if (reset) setV(init)
    } catch (e) {
      setNote(errNote(e)); if (e instanceof ApiError) setFields(e.fields)
    } finally { setBusy(false) }
  }
  return { v, setV, bind, note, setNote, fields, busy, submit }
}

export function Bars({ data, hot, label }: { data: { x: string; y: number }[]; hot?: (i: number) => boolean; label: string }) {
  const W = 560, H = 180, l = 26, b = 24, t = 12, max = Math.max(1, ...data.map(d => d.y)), bw = (W - l - 6) / Math.max(1, data.length)
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} className="chartsvg">
      {[0.5, 1].map(f => { const y = t + (H - t - b) * (1 - f); return <g key={f}><line className="gl" x1={l} x2={W - 6} y1={y} y2={y} /><text className="ax" x={2} y={y + 3}>{Math.round(max * f)}</text></g> })}
      {data.map((d, i) => {
        const h = (H - t - b) * d.y / max, x = l + i * bw + bw * 0.18, y = H - b - h, isHot = hot?.(i)
        return <g key={i}><rect className={'bar' + (isHot ? ' hot' : '')} x={x} y={y} width={bw * 0.64} height={h} rx={4} />
          {isHot && <text className="ax" x={x + bw * 0.32} y={y - 3} textAnchor="middle">{d.y}</text>}
          {i % 2 === (data.length - 1) % 2 && <text className="ax" x={x + bw * 0.32} y={H - 8} textAnchor="middle">{d.x}</text>}</g>
      })}
    </svg>
  )
}

export function DownloadButton({ path, file, label }: { path: string; file: string; label: string }) {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  return (
    <span>
      <button className="btn ghost sm" disabled={busy} onClick={async () => {
        setBusy(true); setErr('')
        try { await download(path, file) } catch (e) { setErr(e instanceof ApiError ? e.message : 'The download failed.') } finally { setBusy(false) }
      }}><Download size={15} aria-hidden /> {busy ? 'Preparing…' : label}</button>
      {err && <span className="msg err" style={{ marginLeft: 8 }}>{err}</span>}
    </span>
  )
}
