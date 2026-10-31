import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { api } from '../api'
import { ChartCard, Donut, GroupedBars, HBars } from '../charts'
import { Card, DownloadButton, Empty, Field } from '../ui'

type Analytics = {
  weekly: { start: string; sales: number; received: number }[]
  mortality: { batch: string; initial: number; deaths: number; ratePct: number }[]
  deathsPerDay: { date: string; deaths: number }[]
  expensesByCategory: { label: string; amount: number }[]
  salesByProduct: { label: string; amount: number }[]
  topDebtors: { label: string; amount: number }[]
  ageing: { bucket: string; amount: number }[]
}
const short = (iso: string) => new Date(iso + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })

const EXPORTS: { name: string; label: string; owner?: boolean }[] = [
  { name: 'sales', label: 'Sales' }, { name: 'payments', label: 'Payments' }, { name: 'customers', label: 'Customers and balances' },
  { name: 'expenses', label: 'Expenses', owner: true }, { name: 'suppliers', label: 'Suppliers', owner: true },
  { name: 'mortality', label: 'Deaths' }, { name: 'stock-movements', label: 'Stock movements' },
]

export default function AnalyticsPage({ owner }: { owner: boolean }) {
  const q = useQuery({ queryKey: ['analytics'], queryFn: () => api.get<Analytics>('/reports/analytics') })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const d = q.data
  const qs = (from ? `from=${from}` : '') + (from && to ? '&' : '') + (to ? `to=${to}` : '')
  return (
    <>
      <div className="row"><div><h1>Analytics</h1><p className="sub">How the business is doing. Save any chart as a picture, or download the records behind them.</p></div>
        <button className="btn ghost sm noprint" onClick={() => window.print()}><Printer size={15} aria-hidden /> Print or save as PDF</button></div>
      {q.isLoading && <Empty>Loading…</Empty>}
      {q.isError && <p className="msg err">Couldn’t load the analytics.</p>}
      {d && <>
        <div className="cols">
          <ChartCard title="Sales and money received" file="sales-and-received" note="Last 8 weeks. Tan is invoiced, green is cash collected.">
            <GroupedBars label="Weekly sales and money received" labels={d.weekly.map(w => short(w.start))} every={1}
              series={[{ name: 'Sales', color: 'var(--tan)', values: d.weekly.map(w => w.sales) }, { name: 'Received', color: 'var(--ok)', values: d.weekly.map(w => w.received) }]} />
          </ChartCard>
          <ChartCard title="Deaths per day" file="deaths-per-day" note="All batches, last 30 days.">
            <GroupedBars money={false} every={3} label="Deaths per day, all batches" labels={d.deathsPerDay.map(x => short(x.date))}
              series={[{ name: 'Deaths', color: 'var(--bad)', values: d.deathsPerDay.map(x => x.deaths) }]} />
          </ChartCard>
        </div>
        <div className="cols">
          <ChartCard title="Mortality by batch" file="mortality-by-batch" note="Deaths as a share of chicks received. Red is above 5%.">
            <HBars fmt={n => n.toFixed(1) + '%'} label="Mortality rate by batch" data={d.mortality.map(m => ({ label: m.batch, value: m.ratePct, color: m.ratePct > 5 ? 'var(--bad)' : 'var(--tan)' }))} />
          </ChartCard>
          <ChartCard title="Who owes you most" file="top-debtors">
            <HBars label="Customers owing the most" data={d.topDebtors.map(x => ({ label: x.label, value: x.amount }))} />
          </ChartCard>
        </div>
        <div className="cols">
          <ChartCard title="Sales by product" file="sales-by-product" note="Last 30 days.">
            <Donut label="Sales by product" data={d.salesByProduct.map(x => ({ label: x.label, value: x.amount }))} />
          </ChartCard>
          {owner && <ChartCard title="Expenses by category" file="expenses-by-category" note="Last 30 days.">
            <Donut label="Expenses by category" data={d.expensesByCategory.map(x => ({ label: x.label, value: x.amount }))} />
          </ChartCard>}
        </div>
        <ChartCard title="Money owed by age" file="receivables-ageing" note="How late the unpaid invoices are.">
          <HBars label="Receivables by age" data={d.ageing.map((a, i) => ({ label: a.bucket === 'CURRENT' ? 'Not yet due' : a.bucket + ' days late', value: a.amount, color: i < 2 ? 'var(--ok)' : i < 3 ? 'var(--tan)' : 'var(--bad)' }))} />
        </ChartCard>
      </>}
      <Card className="noprint">
        <h3>Download your records</h3>
        <p className="note">Spreadsheet files (CSV) that open in Excel. Leave the dates empty to get everything.</p>
        <div className="form" style={{ maxWidth: 420, marginBottom: 12 }}>
          <Field label="From" type="date" value={from} onChange={e => setFrom(e.target.value)} />
          <Field label="To" type="date" value={to} onChange={e => setTo(e.target.value)} />
        </div>
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          {EXPORTS.filter(x => !x.owner || owner).map(x => <DownloadButton key={x.name} path={`/reports/export/${x.name}${qs ? '?' + qs : ''}`} file={`poultry-${x.name}.csv`} label={x.label} />)}
        </div>
      </Card>
    </>
  )
}
