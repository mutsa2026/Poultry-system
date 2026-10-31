import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, money } from '../api'
import type { Customer } from '../types'
import { Empty, Field } from '../ui'

type P = { lat: number; lon: number }
const km = (a: P, b: P) => {
  const r = (x: number) => (x * Math.PI) / 180, R = 6371
  const q = Math.sin(r(b.lat - a.lat) / 2) ** 2 + Math.cos(r(a.lat)) * Math.cos(r(b.lat)) * Math.sin(r(b.lon - a.lon) / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(q)) * 1.3   // x1.3 turns straight-line into a rough road distance
}
const load = (): P => { try { const j = JSON.parse(localStorage.getItem('farm') ?? ''); if (typeof j.lat === 'number') return j } catch { /* none saved */ } return { lat: -17.93, lon: 30.96 } }

export default function DeliveryMap() {
  const q = useQuery({ queryKey: ['customers'], queryFn: () => api.get<Customer[]>('/customers') })
  const [farm, setFarm] = useState<P>(load)
  const [sel, setSel] = useState<number | null>(null)
  const set = (k: 'lat' | 'lon', v: string) => { const n = { ...farm, [k]: Number(v) }; setFarm(n); try { localStorage.setItem('farm', JSON.stringify(n)) } catch { /* private mode */ } }
  const placed = (q.data ?? []).filter(c => c.lat != null && c.lon != null) as (Customer & P)[]
  const missing = (q.data ?? []).length - placed.length
  const all: P[] = [farm, ...placed]
  const lats = all.map(p => p.lat), lons = all.map(p => p.lon)
  const pad = 0.02, minLat = Math.min(...lats) - pad, maxLat = Math.max(...lats) + pad, minLon = Math.min(...lons) - pad, maxLon = Math.max(...lons) + pad
  const W = 560, H = 320
  const px = (p: P) => [((p.lon - minLon) / (maxLon - minLon || 1)) * W, ((maxLat - p.lat) / (maxLat - minLat || 1)) * H]
  const f = px(farm)
  return (
    <>
      <h1>Delivery map</h1>
      <p className="sub">A simple map of your customers. Distances are rough road estimates. Tap Navigate to open Google Maps.</p>
      <div className="mapbox">
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Map of the farm and customers">
          <rect width={W} height={H} rx={14} style={{ fill: 'var(--soft)' }} />
          {placed.map(c => { const p = px(c); return <line key={c.id} x1={f[0]} y1={f[1]} x2={p[0]} y2={p[1]} strokeDasharray="6 5" strokeWidth={sel === c.id ? 3 : 1.5} style={{ stroke: sel === c.id ? 'var(--tan-d)' : 'var(--mute)' }} /> })}
          <rect x={f[0] - 13} y={f[1] - 13} width={26} height={26} rx={8} style={{ fill: 'var(--tan-d)' }} />
          <text x={f[0]} y={f[1] + 28} textAnchor="middle" className="ax">Farm</text>
          {placed.map(c => { const p = px(c); return (
            <g key={c.id} className="pin" tabIndex={0} role="button" aria-label={c.name} onClick={() => setSel(c.id)} onKeyDown={e => e.key === 'Enter' && setSel(c.id)}>
              <circle cx={p[0]} cy={p[1]} r={sel === c.id ? 12 : 9} style={{ fill: c.status === 'OVERDUE' ? 'var(--bad)' : 'var(--tan)', stroke: '#fff', strokeWidth: 2 }} />
              <text x={p[0]} y={p[1] - 15} textAnchor="middle" className="ax" style={{ fontWeight: 600, fill: 'var(--ink)' }}>{c.name}</text>
            </g>) })}
        </svg>
      </div>
      {q.isLoading && <Empty>Loading…</Empty>}
      {placed.length === 0 && !q.isLoading && <Empty>No customers have a location yet. Add latitude and longitude when you add or edit a customer.</Empty>}
      {missing > 0 && placed.length > 0 && <p className="note">{missing} customer{missing > 1 ? 's have' : ' has'} no location yet and {missing > 1 ? 'are' : 'is'} not shown.</p>}
      {placed.map(c => { const d = km(farm, c); return (
        <div key={c.id} className={'item' + (sel === c.id ? ' on' : '')} onClick={() => setSel(c.id)}>
          <div><b>{c.name}</b><br /><small>About {d.toFixed(0)} km · {Math.round((d / 35) * 60)} min · owes {money(c.balance)}</small></div>
          <a className="btn sm" target="_blank" rel="noopener noreferrer" href={`https://www.google.com/maps/dir/?api=1&destination=${c.lat},${c.lon}`} onClick={e => e.stopPropagation()}>Navigate</a>
        </div>) })}
      <h3 style={{ marginTop: 16 }}>Farm location</h3>
      <div className="form" style={{ maxWidth: 380 }}>
        <Field label="Latitude" type="number" step="any" value={farm.lat} onChange={e => set('lat', e.target.value)} />
        <Field label="Longitude" type="number" step="any" value={farm.lon} onChange={e => set('lon', e.target.value)} />
      </div>
    </>
  )
}
