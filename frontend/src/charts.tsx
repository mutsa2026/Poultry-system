import { useRef, type ReactNode } from 'react'
import { Download } from 'lucide-react'
import { Empty } from './ui'

const FONT = 'Poppins, system-ui, sans-serif'
const PALETTE = ['var(--tan)', 'var(--ok)', 'var(--tan-d)', 'var(--bad)', 'var(--yolk)', 'var(--mute)']
const cash = (n: number) => '$' + Math.round(n).toLocaleString()

/** Saves a chart as a PNG. Theme colours are resolved first, because CSS variables don't survive inside an exported image. */
function savePng(svg: SVGSVGElement, name: string) {
  const cs = getComputedStyle(document.documentElement)
  const vb = svg.viewBox.baseVal
  let xml = new XMLSerializer().serializeToString(svg).replace(/var\((--[\w-]+)\)/g, (_, n) => cs.getPropertyValue(n).trim() || '#000')
  if (!xml.includes('xmlns=')) xml = xml.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')
  const img = new Image()
  img.onload = () => {
    const k = 2, c = document.createElement('canvas')
    c.width = vb.width * k; c.height = vb.height * k
    const ctx = c.getContext('2d')!
    ctx.fillStyle = cs.getPropertyValue('--card').trim() || '#fff'
    ctx.fillRect(0, 0, c.width, c.height)
    ctx.drawImage(img, 0, 0, c.width, c.height)
    c.toBlob(b => {
      if (!b) return
      const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = name + '.png'
      document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000)
    })
  }
  img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml)
}

export function ChartCard({ title, file, note, children }: { title: string; file: string; note?: string; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  return (
    <section className="card">
      <div className="row"><h3 style={{ margin: 0 }}>{title}</h3>
        <button className="btn ghost sm noprint" onClick={() => { const s = ref.current?.querySelector('svg'); if (s) savePng(s, file) }}><Download size={15} aria-hidden /> Save picture</button></div>
      {note && <p className="note" style={{ margin: '2px 0 8px' }}>{note}</p>}
      <div ref={ref} style={{ marginTop: 8 }}>{children}</div>
    </section>
  )
}

type Series = { name: string; color: string; values: number[] }
export function GroupedBars({ labels, series, label, money = true, every = 1 }: { labels: string[]; series: Series[]; label: string; money?: boolean; every?: number }) {
  const W = 560, H = 230, l = 46, r = 8, t = 28, b = 28
  const max = Math.max(1, ...series.flatMap(s => s.values))
  const gw = (W - l - r) / Math.max(1, labels.length), bw = (gw * 0.76) / series.length
  const fmt = (n: number) => (money ? cash(n) : String(Math.round(n)))
  if (labels.length === 0) return <Empty>No data yet.</Empty>
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} style={{ width: '100%', height: 'auto' }} fontFamily={FONT}>
      {[0, 0.5, 1].map(f => { const y = t + (H - t - b) * (1 - f); return <g key={f}><line x1={l} x2={W - r} y1={y} y2={y} style={{ stroke: 'var(--line)' }} /><text x={l - 6} y={y + 3} textAnchor="end" fontSize={10} style={{ fill: 'var(--mute)' }}>{fmt(max * f)}</text></g> })}
      {series.map((s, si) => <g key={s.name}><rect x={l + si * 110} y={6} width={10} height={10} rx={3} style={{ fill: s.color }} /><text x={l + si * 110 + 15} y={15} fontSize={11} style={{ fill: 'var(--ink)' }}>{s.name}</text></g>)}
      {labels.map((lb, i) => (
        <g key={i}>
          {series.map((s, si) => { const h = ((H - t - b) * s.values[i]) / max; return <rect key={si} x={l + i * gw + gw * 0.12 + si * bw} y={H - b - h} width={Math.max(1, bw - 2)} height={h} rx={3} style={{ fill: s.color }} /> })}
          {i % every === 0 && <text x={l + i * gw + gw / 2} y={H - 10} textAnchor="middle" fontSize={10} style={{ fill: 'var(--mute)' }}>{lb}</text>}
        </g>
      ))}
    </svg>
  )
}

export function HBars({ data, label, fmt = cash }: { data: { label: string; value: number; color?: string }[]; label: string; fmt?: (n: number) => string }) {
  if (data.length === 0) return <Empty>Nothing to show yet.</Empty>
  const W = 560, rowH = 32, lw = 150, H = data.length * rowH + 8, max = Math.max(1, ...data.map(d => d.value))
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} style={{ width: '100%', height: 'auto' }} fontFamily={FONT}>
      {data.map((d, i) => { const w = ((W - lw - 90) * d.value) / max, y = 4 + i * rowH; return (
        <g key={i}>
          <text x={0} y={y + 18} fontSize={12} style={{ fill: 'var(--ink)' }}>{d.label.length > 22 ? d.label.slice(0, 21) + '…' : d.label}</text>
          <rect x={lw} y={y + 4} width={Math.max(2, w)} height={18} rx={5} style={{ fill: d.color ?? 'var(--tan)' }} />
          <text x={lw + Math.max(2, w) + 8} y={y + 18} fontSize={12} fontWeight={600} style={{ fill: 'var(--ink)' }}>{fmt(d.value)}</text>
        </g>) })}
    </svg>
  )
}

export function Donut({ data, label }: { data: { label: string; value: number }[]; label: string }) {
  const total = data.reduce((s, d) => s + d.value, 0)
  if (total <= 0) return <Empty>Nothing to show yet.</Empty>
  const R = 62, C = 2 * Math.PI * R
  let acc = 0
  return (
    <svg viewBox="0 0 560 170" role="img" aria-label={label} style={{ width: '100%', height: 'auto' }} fontFamily={FONT}>
      <g transform="translate(90 85) rotate(-90)">
        {data.map((d, i) => { const len = (d.value / total) * C, el = <circle key={i} r={R} fill="none" strokeWidth={26} strokeDasharray={`${len} ${C - len}`} strokeDashoffset={-acc} style={{ stroke: PALETTE[i % PALETTE.length] }} />; acc += len; return el })}
      </g>
      <text x={90} y={82} textAnchor="middle" fontSize={11} style={{ fill: 'var(--mute)' }}>Total</text>
      <text x={90} y={100} textAnchor="middle" fontSize={15} fontWeight={700} style={{ fill: 'var(--ink)' }}>{cash(total)}</text>
      {data.slice(0, 6).map((d, i) => (
        <g key={i} transform={`translate(190 ${22 + i * 24})`}>
          <rect width={12} height={12} rx={3} style={{ fill: PALETTE[i % PALETTE.length] }} />
          <text x={20} y={11} fontSize={12} style={{ fill: 'var(--ink)' }}>{d.label} · {cash(d.value)} ({Math.round((d.value / total) * 100)}%)</text>
        </g>
      ))}
    </svg>
  )
}
