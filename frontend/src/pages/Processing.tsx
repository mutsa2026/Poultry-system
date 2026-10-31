import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, num, today } from '../api'
import { Card, Empty, Field, Msg, Select, Tag, useForm } from '../ui'
import type { Batch, DressedBatch } from '../types'

export default function Processing() {
  const qc = useQueryClient()
  const batches = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const dressed = useQuery({ queryKey: ['dressed-batches'], queryFn: () => api.get<DressedBatch[]>('/dressed-batches') })
  const f = useForm({ sourceBatchId: '', processedDate: today(), birdsProcessed: '', dressedKg: '', notes: '' })

  return (
    <>
      <h1>Dressed-chicken processing</h1>
      <p className="sub">Record birds processed and the dressed weight produced. Live stock and traceable dressed lots update together.</p>
      <Card>
        <h3>Record a processing batch</h3>
        <form onSubmit={e => { e.preventDefault(); f.submit(async () => {
          const batch = await api.post<DressedBatch>('/processing', {
            sourceBatchId: Number(f.v.sourceBatchId), processedDate: f.v.processedDate,
            birdsProcessed: num(f.v.birdsProcessed), dressedKg: num(f.v.dressedKg), notes: f.v.notes.trim() || null,
          })
          for (const key of ['dressed-batches', 'batches', 'dashboard']) qc.invalidateQueries({ queryKey: [key] })
          return `${batch.batchNo} recorded with ${batch.stockKg.toLocaleString()} kg in stock.`
        }) }}>
          <div className="form">
            <Select label="Source live-bird batch" required {...f.bind('sourceBatchId')}>
              <option value="">Choose a batch…</option>
              {batches.data?.filter(batch => batch.status === 'ACTIVE' && batch.currentStock > 0).map(batch =>
                <option key={batch.id} value={batch.id}>{batch.batchCode} · {batch.currentStock.toLocaleString()} birds alive</option>)}
            </Select>
            <Field label="Processing date" type="date" max={today()} required error={f.fields.processedDate} {...f.bind('processedDate')} />
            <Field label="Whole birds processed" type="number" min={1} step={1} required error={f.fields.birdsProcessed} {...f.bind('birdsProcessed')} />
            <Field label="Total dressed weight (kg)" type="number" min={0.001} step="0.001" required error={f.fields.dressedKg} {...f.bind('dressedKg')} />
            <Field label="Notes (optional)" maxLength={255} {...f.bind('notes')} />
            <button className="btn" disabled={f.busy || batches.isLoading || !batches.data?.some(batch => batch.status === 'ACTIVE' && batch.currentStock > 0)}>
              {f.busy ? 'Recording…' : 'Record processing'}
            </button>
          </div>
          <Msg note={f.note} />
        </form>
        {batches.isError && <p className="msg err">Couldn’t load live-bird batches.</p>}
      </Card>

      <h2>Dressed stock lots</h2>
      {dressed.isLoading && <Empty>Loading dressed stock…</Empty>}
      {dressed.isError && <p className="msg err">Couldn’t load dressed stock.</p>}
      {dressed.data?.length === 0 && <Empty>No processing batches yet.</Empty>}
      {dressed.data?.map(batch => (
        <div className="item" key={batch.id}>
          <div>
            <b>{batch.batchNo}</b><br />
            <small>From {batch.sourceBatchCode} · {batch.birdsProcessed.toLocaleString()} birds · {batch.processedDate}</small>
            {batch.notes && <><br /><small>{batch.notes}</small></>}
          </div>
          <div className="row">
            <div><b>{batch.stockKg.toLocaleString()} kg</b><br /><small>of {batch.dressedKg.toLocaleString()} kg produced</small></div>
            <Tag kind={batch.stockKg > 0 ? 'go' : 'warn'}>{batch.stockKg > 0 ? 'In stock' : 'Depleted'}</Tag>
          </div>
        </div>
      ))}
    </>
  )
}
