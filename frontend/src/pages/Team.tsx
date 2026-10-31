import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import { Card, Empty, Field, Msg, Tag, useForm } from '../ui'
import type { AuditRow, UserRow } from '../types'

export default function Team() {
  const qc = useQueryClient()
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<UserRow[]>('/users') })
  const audit = useQuery({ queryKey: ['audit'], queryFn: () => api.get<AuditRow[]>('/users/audit?limit=15') })
  const f = useForm({ username: '', fullName: '', password: '' })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['users'] }); qc.invalidateQueries({ queryKey: ['audit'] }) }
  const perm = async (u: UserRow, k: 'pay' | 'discount' | 'profit') => {
    await api.patch(`/users/${u.id}/permissions`, { pay: u.pay, discount: u.discount, profit: u.profit, [k]: !u[k] }); refresh()
  }
  return (
    <>
      <h1>Team</h1><p className="sub">Workers only get what you allow here. The server enforces it.</p>
      {users.data?.map(u => (
        <div className="item" key={u.id}>
          <div><b>{u.fullName}</b> <small>@{u.username}</small><br /><Tag>{u.role === 'OWNER' ? 'Owner' : 'Worker'}</Tag> <Tag kind={u.active ? 'go' : 'late'}>{u.active ? 'Active' : 'Deactivated'}</Tag></div>
          {u.role === 'WORKER' && (
            <div className="row">
              {(['pay', 'discount', 'profit'] as const).map(k => <label key={k} className="note" style={{ margin: 0 }}><input type="checkbox" checked={u[k]} onChange={() => perm(u, k)} /> {k === 'pay' ? 'Record payments' : k === 'discount' ? 'Give discounts' : 'See profit'}</label>)}
              <button className="btn ghost sm" onClick={async () => { await api.patch(`/users/${u.id}/status`, { active: !u.active }); refresh() }}>{u.active ? 'Deactivate' : 'Activate'}</button>
            </div>
          )}
        </div>
      ))}
      <Card>
        <h3>Add a worker</h3>
        <form onSubmit={e => { e.preventDefault(); f.submit(async () => { await api.post('/users', { username: f.v.username.trim(), fullName: f.v.fullName.trim(), password: f.v.password }); refresh(); return 'Worker added. Give them the password in person.' }) }}>
          <div className="form">
            <Field label="Username" required error={f.fields.username} {...f.bind('username')} />
            <Field label="Full name" required error={f.fields.fullName} {...f.bind('fullName')} />
            <Field label="Password (8+ characters)" type="password" autoComplete="new-password" required error={f.fields.password} {...f.bind('password')} />
            <button className="btn" disabled={f.busy}>Add worker</button>
          </div>
          <Msg note={f.note} />
        </form>
      </Card>
      <h2>Audit log</h2>
      {audit.data?.length === 0 && <Empty>Nothing yet.</Empty>}
      {audit.data?.map(a => <p key={a.id} className="note" style={{ margin: '4px 0' }}>{new Date(a.at).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' })} · {a.actor} · {a.action.replace(/_/g, ' ').toLowerCase()}{a.details ? ' · ' + a.details : ''}</p>)}
    </>
  )
}
