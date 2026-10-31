import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, daysOld, money, num, today } from '../api'
import { useAuth } from '../auth'
import { Bars, Card, DownloadButton, Empty, Field, Msg, Select, Stat, Tag, useForm } from '../ui'
import type { Batch, Climate, DayCount, Profit, Vaccine } from '../types'

const stage = (d: number) => (d <= 14 ? 'Starter' : d <= 28 ? 'Grower' : 'Finisher')
const thumb = (d: number) => (d <= 14 ? '/images/house-a.jpg' : d <= 28 ? '/images/house-b.jpg' : '/images/golden-t.jpg')

function NewBatch() {
  const qc = useQueryClient()
  const f = useForm({ batchCode: '', arrivalDate: today(), breed: '', initialQuantity: '', purchaseCost: '' })
  return (
    <Card>
      <h3>Register a new batch</h3>
      <form onSubmit={e => { e.preventDefault(); f.submit(async () => {
        await api.post('/batches', { batchCode: f.v.batchCode.trim(), arrivalDate: f.v.arrivalDate, breed: f.v.breed || null, initialQuantity: num(f.v.initialQuantity), purchaseCost: num(f.v.purchaseCost) })
        qc.invalidateQueries({ queryKey: ['batches'] }); return 'Batch registered with its opening stock.'
      }) }}>
        <div className="form">
          <Field label="Batch code" required error={f.fields.batchCode} {...f.bind('batchCode')} />
          <Field label="Arrival date" type="date" required {...f.bind('arrivalDate')} />
          <Field label="Breed" {...f.bind('breed')} />
          <Field label="Chicks received" type="number" min={1} required error={f.fields.initialQuantity} {...f.bind('initialQuantity')} />
          <Field label="Purchase cost ($)" type="number" min={0} step="0.01" required {...f.bind('purchaseCost')} />
          <button className="btn" disabled={f.busy}>Register</button>
        </div>
        <Msg note={f.note} />
      </form>
    </Card>
  )
}

function Detail({ b }: { b: Batch }) {
  const qc = useQueryClient()
  const { can } = useAuth()
  const age = daysOld(b.arrivalDate) + 1
  const daily = useQuery({ queryKey: ['daily', b.id], queryFn: () => api.get<DayCount[]>(`/batches/${b.id}/mortality-daily?days=14`) })
  const vax = useQuery({ queryKey: ['vax', b.id], queryFn: () => api.get<Vaccine[]>(`/batches/${b.id}/vaccinations`) })
  const profit = useQuery({ queryKey: ['profit', b.id], enabled: can('PERM_PROFIT'), queryFn: () => api.get<Profit>(`/reports/profit?batchId=${b.id}`) })
  const [clim, setClim] = useState<{ t: string; h: string } | null>(null)
  const climate = useQuery({ queryKey: ['climate', b.id, clim], enabled: !!clim, queryFn: () => api.get<Climate>(`/batches/${b.id}/climate?temp=${clim!.t}&humidity=${clim!.h}`) })
  const death = useForm({ quantity: '', cause: '' })
  const ck = useForm({ t: '31', h: '55' })

  const d = daily.data ?? []
  const base = d.length > 2 ? d.slice(0, -2).reduce((s, x) => s + x.deaths, 0) / (d.length - 2) : 0
  const limit = Math.max(5, 2.5 * base)
  const lost = b.initialQuantity - b.currentStock
  const cap = Math.min(b.initialQuantity, 300), gone = Math.round((lost / b.initialQuantity) * cap)

  return (
    <>
      <Card>
        <div className="row"><h3>{b.batchCode}</h3><Tag>{stage(age)} · day {age}</Tag></div>
        <div className="stats" style={{ marginTop: 8 }}>
          <Stat label="Alive" value={b.currentStock.toLocaleString()} />
          <Stat label="Lost or sold" value={lost} />
          <Stat label="Started with" value={b.initialQuantity.toLocaleString()} />
        </div>
        <div className="flock" role="img" aria-label="One dot per bird. Pale dots are birds no longer in the batch.">
          {Array.from({ length: cap }, (_, i) => <i key={i} className={i >= cap - gone ? 'lost' : ''} />)}
        </div>
        {profit.data && (
          <p className="sub">Estimated profit {money(profit.data.profit)} · break-even {money(profit.data.breakEvenPerBird)} per bird alive. Costs are only what has been recorded for this batch.</p>
        )}
      </Card>

      <Card>
        <h3>Record deaths</h3>
        <form onSubmit={e => { e.preventDefault(); recordDeaths() }}>
          <div className="form">
            <Field label="Birds that died" type="number" min={1} required error={death.fields.quantity} {...death.bind('quantity')} />
            <Field label="Cause, e.g. heat stress" {...death.bind('cause')} />
            <button className="btn" disabled={death.busy}>Record</button>
          </div>
          <Msg note={death.note} />
        </form>
        <h3 style={{ marginTop: 14 }}>Deaths per day</h3>
        {daily.isLoading ? <Empty>Loading…</Empty> : <Bars label={`Deaths per day for ${b.batchCode}`} data={d.map(x => ({ x: x.date.slice(5), y: x.deaths }))} hot={i => i >= d.length - 2 && d[i].deaths >= limit} />}
      </Card>

      <Card>
        <h3>House climate</h3>
        <form onSubmit={e => { e.preventDefault(); setClim({ t: ck.v.t, h: ck.v.h }) }}>
          <div className="form">
            <Field label="House temperature (°C)" type="number" step="0.5" required {...ck.bind('t')} />
            <Field label="Humidity (%)" type="number" min={0} max={100} required {...ck.bind('h')} />
            <button className="btn">Get advice</button>
          </div>
        </form>
        {climate.data && <>
          <p className="sub" style={{ marginTop: 10 }}>Target for day {climate.data.dayOfLife}: {climate.data.targetTempC} °C</p>
          {climate.data.advice.map(a => <div key={a.area} className={'adv ' + a.level}><b>{a.area}: {a.title}</b><p>{a.text}</p></div>)}
        </>}
      </Card>

      <Card>
        <h3>Vaccinations</h3>
        <p className="note">Example schedule. Your vet should confirm it.</p>
        {vax.data?.map(v => (
          <div className="item" key={v.day}>
            <div><b>Day {v.day}</b> · {v.vaccine}<br /><small>{v.dueDate}</small></div>
            <div>
              <Tag kind={v.status === 'DONE' ? 'go' : v.status === 'OVERDUE' ? 'late' : v.status === 'DUE' ? 'warn' : ''}>{v.status === 'DONE' ? 'Done' : v.status === 'OVERDUE' ? 'Overdue' : v.status === 'DUE' ? 'Due today' : 'Coming up'}</Tag>{' '}
              {(v.status === 'DUE' || v.status === 'OVERDUE') && <button className="btn sm" onClick={async () => { await api.post(`/batches/${b.id}/vaccinations/${v.day}/done`); qc.invalidateQueries({ queryKey: ['vax', b.id] }); qc.invalidateQueries({ queryKey: ['dashboard'] }) }}>Mark done</button>}
            </div>
          </div>
        ))}
      </Card>
    </>
  )

  function recordDeaths() {
    death.submit(async () => {
      const q = num(death.v.quantity)
      await api.post(`/batches/${b.id}/mortality`, { eventDate: today(), quantity: q, cause: death.v.cause || null })
      for (const k of [['batches'], ['daily', b.id], ['dashboard'], ['profit', b.id]]) qc.invalidateQueries({ queryKey: k })
      return `Recorded ${q} deaths.`
    })
  }
}

export default function Batches() {
  const { owner } = useAuth()
  const q = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const [sel, setSel] = useState<number | null>(null)
  const [filter, setFilter] = useState('All')
  const list = (q.data ?? []).filter(b => filter === 'All' || stage(daysOld(b.arrivalDate) + 1) === filter)
  const chosen = q.data?.find(b => b.id === (sel ?? list[0]?.id))
  return (
    <>
      <div className="row"><div><h1>Batches</h1><p className="sub">Tap a batch to record deaths, check climate and vaccines.</p></div><DownloadButton path="/reports/export/mortality" file="poultry-deaths.csv" label="Download deaths" /></div>
      <div className="chips">{['All', 'Starter', 'Grower', 'Finisher'].map(s => <button key={s} className={'chip' + (s === filter ? ' on' : '')} onClick={() => setFilter(s)}>{s}</button>)}</div>
      {q.isLoading && <Empty>Loading…</Empty>}
      {q.isError && <p className="msg err">Couldn’t load batches.</p>}
      {q.data && list.length === 0 && <Empty>No batches here yet.</Empty>}
      <div className="cols">
        <div>
          {list.map(b => {
            const age = daysOld(b.arrivalDate) + 1
            return (
              <button key={b.id} className={'item' + (chosen?.id === b.id ? ' on' : '')} onClick={() => setSel(b.id)}>
                <img className="thumb" src={thumb(age)} alt="" /><div style={{ flex: 1 }}><b>{b.batchCode}</b><br /><small>{stage(age)} · day {age} · {b.currentStock.toLocaleString()} birds</small></div>
                <div style={{ width: 90 }} className="meter"><span style={{ width: Math.min(100, (age / 42) * 100) + '%' }} /></div>
              </button>
            )
          })}
          {owner && <NewBatch />}
        </div>
        <div>{chosen && <Detail key={chosen.id} b={chosen} />}</div>
      </div>
    </>
  )
}
