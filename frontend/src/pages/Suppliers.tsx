import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money, num, today } from '../api'
import { Card, Empty, Field, Msg, Select, Tag, useForm } from '../ui'
import type { Batch, Supplier } from '../types'

type Expense = { id: number; category: string; description: string | null; amount: number; date: string; batchCode: string | null }

export default function Suppliers() {
  const qc = useQueryClient()
  const sup = useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/suppliers') })
  const exp = useQuery({ queryKey: ['expenses'], queryFn: () => api.get<Expense[]>('/expenses?limit=10') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const refresh = () => { for (const k of ['suppliers', 'dashboard', 'expenses']) qc.invalidateQueries({ queryKey: [k] }) }
  const ns = useForm({ name: '', phone: '' })
  const buy = useForm({ supplierId: '', item: 'FEED', quantity: '', totalCost: '', paidNow: '0' })
  const pay = useForm({ supplierId: '', amount: '' })
  const ex = useForm({ category: 'Transport', description: '', amount: '', batchId: '' })
  const total = sup.data?.reduce((s, x) => s + x.balance, 0) ?? 0
  const opts = sup.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)

  return (
    <>
      <h1>Suppliers and costs</h1><p className="sub">You owe suppliers {money(total)} in total.</p>
      <div className="cols">
        <div>
          {sup.isLoading && <Empty>Loading…</Empty>}
          {sup.data?.map(s => (
            <div className="item" key={s.id}>
              <div><b>{s.name}</b><br /><small>{s.balance > 0 ? `You owe ${money(s.balance)}${s.nextDue ? ' · due ' + s.nextDue : ''}` : 'Nothing owed'}</small></div>
              {s.balance <= 0 ? <Tag kind="go">Paid up</Tag> : s.daysLate ? <Tag kind="late">{s.daysLate} days late</Tag> : <Tag>Open</Tag>}
            </div>
          ))}
          <Card>
            <h3>Add a supplier</h3>
            <form onSubmit={e => { e.preventDefault(); ns.submit(async () => { await api.post('/suppliers', { name: ns.v.name.trim(), phone: ns.v.phone || null }); refresh(); return 'Supplier added.' }) }}>
              <div className="form"><Field label="Name" required error={ns.fields.name} {...ns.bind('name')} /><Field label="Phone" {...ns.bind('phone')} /><button className="btn" disabled={ns.busy}>Add</button></div>
              <Msg note={ns.note} />
            </form>
          </Card>
        </div>
        <div>
          <Card>
            <h3>Record a purchase</h3>
            <form onSubmit={e => { e.preventDefault(); buy.submit(async () => {
              const s = await api.post<Supplier>('/purchases', { supplierId: Number(buy.v.supplierId), item: buy.v.item, quantity: num(buy.v.quantity), totalCost: num(buy.v.totalCost), paidNow: num(buy.v.paidNow) || 0 })
              refresh(); qc.invalidateQueries({ queryKey: ['feed'] })
              return s.balance > 0 ? `Saved. You now owe ${s.name} ${money(s.balance)}.` : 'Saved and paid in full.'
            }) }}>
              <div className="form">
                <Select label="Supplier" required {...buy.bind('supplierId')}><option value="">Choose…</option>{opts}</Select>
                <Select label="Item" {...buy.bind('item')}><option value="CHICKS">Chicks</option><option value="FEED">Feed (50 kg bags)</option><option value="MEDICINE">Medicine</option><option value="PACKAGING">Packaging</option><option value="OTHER">Other</option></Select>
                <Field label="Quantity" type="number" min={0.01} step="any" required {...buy.bind('quantity')} />
                <Field label="Total cost ($)" type="number" min={0.01} step="0.01" required {...buy.bind('totalCost')} />
                <Field label="Paid now ($)" type="number" min={0} step="0.01" {...buy.bind('paidNow')} />
                <button className="btn" disabled={buy.busy}>Save purchase</button>
              </div>
              <Msg note={buy.note} />
            </form>
          </Card>
          <Card>
            <h3>Pay a supplier</h3>
            <form onSubmit={e => { e.preventDefault(); pay.submit(async () => { const s = await api.post<Supplier>(`/suppliers/${pay.v.supplierId}/payments`, { amount: num(pay.v.amount) }); refresh(); return s.balance > 0 ? `Paid. You still owe ${money(s.balance)}.` : 'Paid in full.' }) }}>
              <div className="form"><Select label="Supplier" required {...pay.bind('supplierId')}><option value="">Choose…</option>{opts}</Select><Field label="Amount ($)" type="number" min={0.01} step="0.01" required {...pay.bind('amount')} /><button className="btn" disabled={pay.busy}>Pay</button></div>
              <Msg note={pay.note} />
            </form>
          </Card>
          <Card>
            <h3>Record an expense</h3>
            <form onSubmit={e => { e.preventDefault(); ex.submit(async () => { await api.post('/expenses', { category: ex.v.category, description: ex.v.description || null, amount: num(ex.v.amount), date: today(), batchId: ex.v.batchId ? Number(ex.v.batchId) : null }); refresh(); return 'Expense saved.' }) }}>
              <div className="form">
                <Select label="Category" {...ex.bind('category')}>{['Transport', 'Labour', 'Electricity', 'Packaging', 'Veterinary', 'Other'].map(c => <option key={c}>{c}</option>)}</Select>
                <Field label="Amount ($)" type="number" min={0.01} step="0.01" required {...ex.bind('amount')} />
                <Select label="Batch (optional)" {...ex.bind('batchId')}><option value="">Whole farm</option>{batches.data?.map(b => <option key={b.id} value={b.id}>{b.batchCode}</option>)}</Select>
                <Field label="Note" {...ex.bind('description')} />
                <button className="btn" disabled={ex.busy}>Save</button>
              </div>
              <Msg note={ex.note} />
            </form>
            {exp.data?.map(e => <p key={e.id} className="note" style={{ margin: '6px 0 0' }}>{e.date} · {e.category} · {money(e.amount)}{e.batchCode ? ' · ' + e.batchCode : ''}</p>)}
          </Card>
        </div>
      </div>
    </>
  )
}
