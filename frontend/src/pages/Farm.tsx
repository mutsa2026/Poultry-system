import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, num } from '../api'
import { Card, Empty, Field, Msg, Select, Tag, useForm } from '../ui'
import type { Batch, Feed, Visitor } from '../types'

export default function Farm() {
  const qc = useQueryClient()
  const feed = useQuery({ queryKey: ['feed'], queryFn: () => api.get<Feed[]>('/feed/stock') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const visitors = useQuery({ queryKey: ['visitors'], queryFn: () => api.get<Visitor[]>('/visitors') })
  const use = useForm({ feedTypeId: '', batchId: '', quantity: '' })
  const vis = useForm({ name: '', purpose: 'Vet', plate: '', footbath: 'yes', contact: 'no' })
  return (
    <>
      <h1>Feed and health</h1><p className="sub">Feed in store, feed used, and who has visited the farm.</p>
      <div className="cols">
        <div>
          <h3>Feed in store</h3>
          {feed.data?.map(f => <div className="item" key={f.id}><div><b>{f.name}</b><br /><small>{f.onHand} {f.unit}s · reorder at {f.reorderLevel}</small></div><Tag kind={f.low ? 'late' : 'go'}>{f.low ? 'Reorder' : 'In stock'}</Tag></div>)}
          <Card>
            <h3>Record feed used</h3>
            <form onSubmit={e => { e.preventDefault(); use.submit(async () => {
              await api.post('/feed/consumption', { feedTypeId: Number(use.v.feedTypeId), batchId: use.v.batchId ? Number(use.v.batchId) : null, quantity: num(use.v.quantity) })
              for (const k of ['feed', 'dashboard']) qc.invalidateQueries({ queryKey: [k] }); return 'Feed use recorded.'
            }) }}>
              <div className="form">
                <Select label="Feed" required {...use.bind('feedTypeId')}><option value="">Choose…</option>{feed.data?.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}</Select>
                <Select label="Batch" {...use.bind('batchId')}><option value="">Not specific</option>{batches.data?.map(b => <option key={b.id} value={b.id}>{b.batchCode}</option>)}</Select>
                <Field label="Amount used" type="number" min={0.01} step="any" required {...use.bind('quantity')} />
                <button className="btn" disabled={use.busy}>Save</button>
              </div>
              <Msg note={use.note} />
            </form>
          </Card>
        </div>
        <div>
          <Card>
            <h3>Log a visitor</h3>
            <form onSubmit={e => { e.preventDefault(); vis.submit(async () => {
              await api.post('/visitors', { name: vis.v.name.trim(), purpose: vis.v.purpose, plate: vis.v.plate || null, footbath: vis.v.footbath === 'yes', poultryContact: vis.v.contact === 'yes' })
              for (const k of ['visitors', 'dashboard']) qc.invalidateQueries({ queryKey: [k] }); return 'Visit logged.'
            }) }}>
              <div className="form">
                <Field label="Visitor name" required error={vis.fields.name} {...vis.bind('name')} />
                <Select label="Reason" {...vis.bind('purpose')}>{['Vet', 'Buyer', 'Supplier', 'Other'].map(p => <option key={p}>{p}</option>)}</Select>
                <Field label="Vehicle plate" {...vis.bind('plate')} />
                <Select label="Used the footbath?" {...vis.bind('footbath')}><option value="yes">Yes</option><option value="no">No</option></Select>
                <Select label="Near other poultry in the last 2 days?" {...vis.bind('contact')}><option value="no">No</option><option value="yes">Yes</option></Select>
                <button className="btn" disabled={vis.busy}>Log visit</button>
              </div>
              <Msg note={vis.note} />
            </form>
          </Card>
          {visitors.data?.length === 0 && <Empty>No visitors logged.</Empty>}
          {visitors.data?.map(v => (
            <div className="item" key={v.id}><div><b>{v.name}</b><br /><small>{v.purpose} · {new Date(v.at).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })}</small></div>
              <Tag kind={v.risk ? 'late' : 'go'}>{v.poultryContact ? 'Poultry contact' : !v.footbath ? 'No footbath' : 'Clean entry'}</Tag></div>
          ))}
        </div>
      </div>
    </>
  )
}
