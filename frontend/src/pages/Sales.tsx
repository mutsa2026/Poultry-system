import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money, num } from '../api'
import { useAuth } from '../auth'
import { Printer } from 'lucide-react'
import { Card, DownloadButton, Empty, Field, Msg, Select, Tag, useForm } from '../ui'
import type { Batch, Customer, DressedBatch, Sale } from '../types'

const PS: Record<Sale['paymentStatus'], [string, '' | 'go' | 'late' | 'warn']> = {
  PAID: ['Paid', 'go'], PART_PAID: ['Part paid', 'warn'], UNPAID: ['Unpaid', ''], OVERDUE: ['Overdue', 'late'], CANCELLED: ['Cancelled', 'late'],
}

function Invoice({ s }: { s: Sale }) {
  return (
    <div className="inv">
      <h3>Huku Hub</h3>
      <small>{s.balance > 0 ? 'Invoice' : 'Receipt'} {s.saleNo} · {s.saleDate}</small>
      <p style={{ margin: '10px 0 0' }}><b>Bill to:</b> {s.customerName}</p>
      <table>
        <thead><tr><th>Item</th><th className="r">Qty</th><th className="r">Price</th><th className="r">Amount</th></tr></thead>
        <tbody>
          <tr><td>{s.product === 'LIVE_BROILER' ? `Live broiler (${s.batchCode})` : `Dressed chicken${s.batchCode ? ` (${s.batchCode})` : ''}`}</td><td className="r">{s.quantity}</td><td className="r">{money(s.unitPrice)}</td><td className="r">{money(s.quantity * s.unitPrice)}</td></tr>
          {s.discountPct > 0 && <tr><td colSpan={3}>Discount {s.discountPct}%</td><td className="r">−{money(s.quantity * s.unitPrice - s.total)}</td></tr>}
          <tr><td colSpan={3}><b>Total</b></td><td className="r"><b>{money(s.total)}</b></td></tr>
          <tr><td colSpan={3}>Paid</td><td className="r">{money(s.paid)}</td></tr>
          <tr><td colSpan={3}><b>Balance</b></td><td className="r"><b>{money(s.balance)}</b></td></tr>
        </tbody>
      </table>
      <p style={{ margin: '0 0 10px' }}>Status: <b>{PS[s.paymentStatus][0]}</b>{s.balance > 0 && ` · due ${s.dueDate}`}</p>
      <button className="btn" onClick={() => { document.body.dataset.print = 'inv'; window.print(); delete document.body.dataset.print }}><Printer size={16} aria-hidden /> Print</button>
    </div>
  )
}

export default function Sales() {
  const { owner, can } = useAuth()
  const qc = useQueryClient()
  const customers = useQuery({ queryKey: ['customers'], queryFn: () => api.get<Customer[]>('/customers') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const dressedBatches = useQuery({ queryKey: ['dressed-batches'], queryFn: () => api.get<DressedBatch[]>('/dressed-batches') })
  const sales = useQuery({ queryKey: ['sales'], queryFn: () => api.get<Sale[]>('/sales?limit=30') })
  const [shown, setShown] = useState<Sale | null>(null)
  const f = useForm({ customerId: '', product: 'LIVE_BROILER', batchId: '', dressedBatchId: '', quantity: '', unitPrice: '5.50', discountPct: '0', paidNow: '0', method: 'CASH' })
  const live = f.v.product === 'LIVE_BROILER'
  const dressed = f.v.product === 'DRESSED_CHICKEN'
  const total = (num(f.v.quantity) || 0) * (num(f.v.unitPrice) || 0) * (1 - (num(f.v.discountPct) || 0) / 100)

  return (
    <>
      <h1>Sales</h1><p className="sub">Create an invoice. Live birds come out of the batch you choose, and credit limits are checked.</p>
      <div className="cols">
        <div>
          <Card>
            <form onSubmit={e => { e.preventDefault(); f.submit(async () => {
              const s = await api.post<Sale>('/sales', {
                customerId: Number(f.v.customerId), product: f.v.product, batchId: live ? Number(f.v.batchId) : null,
                dressedBatchId: dressed ? Number(f.v.dressedBatchId) : null,
                quantity: num(f.v.quantity), unitPrice: num(f.v.unitPrice), discountPct: can('PERM_DISCOUNT') ? num(f.v.discountPct) || 0 : 0,
                paidNow: can('PERM_PAY') ? num(f.v.paidNow) || 0 : 0, method: can('PERM_PAY') && num(f.v.paidNow) > 0 ? f.v.method : null,
              })
              setShown(s)
              for (const k of ['sales', 'customers', 'batches', 'dressed-batches', 'dashboard', 'statement']) qc.invalidateQueries({ queryKey: [k] })
              return `${s.saleNo} created.`
            }, false) }}>
              <div className="form">
                <Select label="Customer" required {...f.bind('customerId')}><option value="">Choose…</option>{customers.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</Select>
                <Select label="Product" {...f.bind('product')}><option value="LIVE_BROILER">Live broiler</option><option value="DRESSED_CHICKEN">Dressed chicken</option></Select>
                {live && <Select label="From batch" required {...f.bind('batchId')}><option value="">Choose…</option>{batches.data?.filter(b => b.status === 'ACTIVE').map(b => <option key={b.id} value={b.id}>{b.batchCode} ({b.currentStock} alive)</option>)}</Select>}
                {dressed && <Select label="From dressed batch" required disabled={dressedBatches.isLoading || dressedBatches.isError} {...f.bind('dressedBatchId')}><option value="">Choose…</option>{dressedBatches.data?.filter(b => b.stockKg > 0).map(b => <option key={b.id} value={b.id}>{b.batchNo} ({b.stockKg.toLocaleString()} kg available)</option>)}</Select>}
                <Field label={live ? 'Birds' : 'Kilograms'} type="number" min={live ? 1 : 0.001} step={live ? 1 : 0.001} required error={f.fields.quantity} {...f.bind('quantity')} />
                <Field label={live ? 'Price per bird ($)' : 'Price per kg ($)'} type="number" min={0} step="0.01" required {...f.bind('unitPrice')} />
                {can('PERM_DISCOUNT') && <Field label="Discount (%)" type="number" min={0} max={100} {...f.bind('discountPct')} />}
                {can('PERM_PAY') && <Field label="Paid now ($)" type="number" min={0} step="0.01" {...f.bind('paidNow')} />}
                {can('PERM_PAY') && <Select label="Paid by" {...f.bind('method')}><option value="CASH">Cash</option><option value="BANK_TRANSFER">Bank transfer</option><option value="MOBILE_MONEY">Mobile money</option></Select>}
              </div>
              <p className="sub" style={{ margin: '10px 0' }}>Total: <b>{money(total)}</b></p>
              <button className="btn" disabled={f.busy}>Create invoice</button>
              <Msg note={f.note} />
              {dressed && dressedBatches.isError && <p className="msg err">Couldn’t load dressed stock lots.</p>}
              {dressed && dressedBatches.data?.every(batch => batch.stockKg <= 0) && <p className="note">No dressed chicken is in stock yet. Record a processing batch first.</p>}
            </form>
          </Card>
          {shown && <Invoice s={shown} />}
        </div>
        <div>
          <div className="row"><h3>Recent sales</h3>{can('PERM_PROFIT') && <DownloadButton path="/reports/export/sales" file="poultry-sales.csv" label="Download CSV" />}</div>
          {sales.isLoading && <Empty>Loading…</Empty>}
          {sales.data?.length === 0 && <Empty>No sales yet.</Empty>}
          {sales.data?.map(s => (
            <div className="item" key={s.id}>
              <button className="chip" onClick={() => setShown(s)}>{s.saleNo}</button>
              <div style={{ flex: 1 }}><b>{s.customerName}</b><br /><small>{money(s.total)} · {s.saleDate}</small></div>
              <Tag kind={PS[s.paymentStatus][1]}>{PS[s.paymentStatus][0]}</Tag>
              {owner && s.status === 'OPEN' && s.paid === 0 && <button className="btn ghost sm" onClick={async () => {
                if (!window.confirm(`Cancel ${s.saleNo}? Any stock sold will be returned to its original batch.`)) return
                await api.post(`/sales/${s.id}/cancel`); for (const k of ['sales', 'customers', 'batches', 'dressed-batches', 'dashboard']) qc.invalidateQueries({ queryKey: [k] })
              }}>Cancel</button>}
            </div>
          ))}
        </div>
      </div>
    </>
  )
}
