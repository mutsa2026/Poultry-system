import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, money, num } from '../api'
import { useAuth } from '../auth'
import { Card, DownloadButton, Empty, Field, Msg, Select, Tag, useForm } from '../ui'
import type { AgeBucket, Customer, Receipt, Sale } from '../types'

const STATUS: Record<Customer['status'], [string, '' | 'go' | 'late' | 'warn']> = {
  PAID_UP: ['Paid up', 'go'], CURRENT: ['Current', 'go'], LATE: ['Late', 'warn'], OVERDUE: ['Overdue', 'late'], NEAR_LIMIT: ['Near limit', 'warn'],
}

function Statement({ c }: { c: Customer }) {
  const { can } = useAuth()
  const qc = useQueryClient()
  const st = useQuery({ queryKey: ['statement', c.id], queryFn: () => api.get<{ invoices: Sale[] }>(`/customers/${c.id}/statement`) })
  const pay = useForm({ amount: '', method: 'CASH', reference: '' })
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  return (
    <Card>
      <div className="row"><h3>{c.name}</h3><Tag kind={STATUS[c.status][1]}>{STATUS[c.status][0]}</Tag></div>
      <p className="sub">Owes {money(c.balance)} of a {money(c.creditLimit)} limit · pays within {c.termsDays} days{c.phone ? ' · ' + c.phone : ''}</p>
      {can('PERM_PAY') && c.balance > 0 && (
        <form onSubmit={e => { e.preventDefault(); pay.submit(async () => {
          const r = await api.post<Receipt>(`/customers/${c.id}/payments`, { amount: num(pay.v.amount), method: pay.v.method, reference: pay.v.reference || null })
          setReceipt(r)
          for (const k of ['customers', 'statement', 'sales', 'dashboard']) qc.invalidateQueries({ queryKey: [k] })
          return r.newBalance > 0 ? `Payment saved. They still owe ${money(r.newBalance)}.` : 'Paid in full.'
        }) }}>
          <div className="form">
            <Field label="Amount ($)" type="number" min={0.01} step="0.01" required error={pay.fields.amount} {...pay.bind('amount')} />
            <Select label="Method" {...pay.bind('method')}><option value="CASH">Cash</option><option value="BANK_TRANSFER">Bank transfer</option><option value="MOBILE_MONEY">Mobile money</option></Select>
            <Field label="Reference" {...pay.bind('reference')} />
            <button className="btn" disabled={pay.busy}>Record payment</button>
          </div>
          <Msg note={pay.note} />
        </form>
      )}
      {receipt && <p className="sub">Receipt {receipt.receiptNo}: {receipt.allocations.map(a => `${money(a.amount)} to ${a.saleNo}`).join(', ')}</p>}
      <h3 style={{ marginTop: 12 }}>Invoices</h3>
      {st.data?.invoices.length === 0 && <Empty>No invoices yet.</Empty>}
      {st.data?.invoices.map(s => (
        <div className="item" key={s.id}><div><b>{s.saleNo}</b><br /><small>{s.saleDate} · due {s.dueDate}</small></div>
          <div style={{ textAlign: 'right' }}>{money(s.total)}<br /><small>{s.paymentStatus === 'PAID' ? 'Paid' : s.paymentStatus === 'CANCELLED' ? 'Cancelled' : 'Owes ' + money(s.balance)}</small></div></div>
      ))}
    </Card>
  )
}

export default function Customers() {
  const { owner, can } = useAuth()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['customers'], queryFn: () => api.get<Customer[]>('/customers') })
  const ageing = useQuery({ queryKey: ['ageing'], enabled: can('PERM_PROFIT'), queryFn: () => api.get<AgeBucket[]>('/reports/receivables-ageing') })
  const [sel, setSel] = useState<number | null>(null)
  const nf = useForm({ name: '', phone: '', address: '', lat: '', lon: '', creditLimit: '0', termsDays: '14' })
  const chosen = q.data?.find(c => c.id === sel)
  return (
    <>
      <div className="row"><div><h1>Customers</h1><p className="sub">Balances, credit limits and payments.</p></div>{can('PERM_PROFIT') && <DownloadButton path="/reports/export/customers" file="poultry-customers.csv" label="Download CSV" />}</div>
      {ageing.data && (
        <div className="stats">{ageing.data.map(a => <div key={a.bucket} className="stat"><small>{a.bucket === 'CURRENT' ? 'Not yet due' : a.bucket + ' days late'}</small><b>{money(a.amount)}</b></div>)}</div>
      )}
      {q.isLoading && <Empty>Loading…</Empty>}
      {q.isError && <p className="msg err">Couldn’t load customers.</p>}
      <div className="cols">
        <div>
          {q.data?.map(c => (
            <button key={c.id} className={'item' + (sel === c.id ? ' on' : '')} onClick={() => setSel(c.id)}>
              <div><b>{c.name}</b><br /><small>Owes {money(c.balance)}</small></div><Tag kind={STATUS[c.status][1]}>{STATUS[c.status][0]}{c.daysOverdue ? ` ${c.daysOverdue}d` : ''}</Tag>
            </button>
          ))}
          <Card>
            <h3>Add a customer</h3>
            <form onSubmit={e => { e.preventDefault(); nf.submit(async () => {
              await api.post('/customers', { name: nf.v.name.trim(), phone: nf.v.phone || null, address: nf.v.address || null, lat: nf.v.lat ? num(nf.v.lat) : null, lon: nf.v.lon ? num(nf.v.lon) : null, creditLimit: owner ? num(nf.v.creditLimit) : 0, termsDays: owner ? num(nf.v.termsDays) : 14 })
              qc.invalidateQueries({ queryKey: ['customers'] }); return 'Customer added.'
            }) }}>
              <div className="form">
                <Field label="Name" required error={nf.fields.name} {...nf.bind('name')} />
                <Field label="Phone" {...nf.bind('phone')} />
                <Field label="Address" {...nf.bind('address')} />
                <Field label="Latitude (for the map)" type="number" step="any" placeholder="-17.83" {...nf.bind('lat')} />
                <Field label="Longitude" type="number" step="any" placeholder="31.05" {...nf.bind('lon')} />
                {owner && <Field label="Credit limit ($)" type="number" min={0} {...nf.bind('creditLimit')} />}
                {owner && <Field label="Pays within (days)" type="number" min={0} {...nf.bind('termsDays')} />}
                <button className="btn" disabled={nf.busy}>Add</button>
              </div>
              <Msg note={nf.note} />
            </form>
          </Card>
        </div>
        <div>{chosen ? <Statement key={chosen.id} c={chosen} /> : <Empty>Tap a customer to see their invoices and record a payment.</Empty>}</div>
      </div>
    </>
  )
}
