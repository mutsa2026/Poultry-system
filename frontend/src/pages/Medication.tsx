import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import type { Batch, Dashboard } from '../types'
import { daysOld } from '../api'

const PROD = {
  stress: { name: 'Phenix Stresspac', img: '/images/stresspac.jpg', text: 'Vitamins and electrolytes to help birds through heat, handling and other stress.' },
  boost: { name: 'Broiler Booster', img: '/images/booster.jpg', text: 'Water-soluble growth supplement for broilers.' },
}
type Key = keyof typeof PROD
const COND: Record<string, { label: string; products: Key[]; vet?: boolean }> = {
  heat: { label: 'Heat stress', products: ['stress'] },
  growth: { label: 'Slow growth', products: ['boost'] },
  sudden: { label: 'Sudden deaths', products: [], vet: true },
  resp: { label: 'Sneezing or gasping', products: ['stress'], vet: true },
  runs: { label: 'Watery droppings', products: ['stress'], vet: true },
  legs: { label: 'Leg problems', products: [], vet: true },
}

export default function Medication() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: () => api.get<Batch[]>('/batches') })
  const [picked, setPicked] = useState<Set<string> | null>(null)
  // Suggest "sudden deaths" automatically when the dashboard reports a death spike.
  const auto = new Set<string>(dash.data?.alerts.some(a => a.message.includes('lost') && a.message.includes('birds in 2 days')) ? ['sudden'] : [])
  const sel = picked ?? auto
  const toggle = (k: string) => { const n = new Set(sel); n.has(k) ? n.delete(k) : n.add(k); setPicked(n) }
  const prods = [...new Set([...sel].flatMap(k => COND[k].products))] as Key[]
  const vet = [...sel].some(k => COND[k].vet)
  const oldest = Math.max(0, ...(batches.data ?? []).filter(b => b.status === 'ACTIVE').map(b => daysOld(b.arrivalDate) + 1))
  return (
    <>
      <h1>Medication guide</h1>
      <p className="sub">Supportive guidance only. It does not replace your vet.</p>
      <div className="chips">{Object.entries(COND).map(([k, c]) => <button key={k} className={'chip' + (sel.has(k) ? ' on' : '')} onClick={() => toggle(k)}>{c.label}</button>)}</div>
      {picked === null && auto.size > 0 && <p className="sub">Suggested for you: the dashboard shows a recent rise in deaths.</p>}
      <div className={'banner' + (vet ? ' bad' : '')}>
        {sel.size === 0 && 'Pick the signs you can see.'}
        {vet && 'Call your vet today. These signs can mean infection or disease, and only a vet should choose antibiotics or other treatment. '}
        {sel.size > 0 && oldest >= 28 && `Your oldest batch is day ${oldest}. Any treatment other than vitamins has a withdrawal period that must pass before birds are sold or dressed.`}
      </div>
      {prods.map(k => (
        <div className="prod" key={k}><img src={PROD[k].img} alt={PROD[k].name + ' pack'} />
          <div><h3>{PROD[k].name}</h3><p>{PROD[k].text}</p><p><b>Dose and days:</b> follow the pack label.</p></div></div>
      ))}
    </>
  )
}
