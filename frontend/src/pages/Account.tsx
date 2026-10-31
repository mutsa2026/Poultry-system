import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { Card, Field, Msg, useForm } from '../ui'

export default function Account() {
  const { passwordChanged } = useAuth()
  const navigate = useNavigate()
  const f = useForm({ currentPassword: '', newPassword: '', confirmPassword: '' })

  const submit = () => {
    if (f.v.newPassword !== f.v.confirmPassword) {
      f.setNote({ kind: 'err', text: 'The new passwords do not match.' })
      return
    }
    void f.submit(async () => {
      await api.post('/auth/password', { currentPassword: f.v.currentPassword, newPassword: f.v.newPassword })
      passwordChanged()
      navigate('/?passwordChanged=1', { replace: true })
    }, false)
  }

  return (
    <>
      <h1>Account & password</h1>
      <p className="sub">Change your sign-in password. All signed-in devices will need to sign in again.</p>
      <Card>
        <form onSubmit={e => { e.preventDefault(); submit() }}>
          <div className="form" style={{ gridTemplateColumns: '1fr' }}>
            <Field label="Current password" type="password" autoComplete="current-password" required {...f.bind('currentPassword')} />
            <Field label="New password" type="password" autoComplete="new-password" minLength={8} required {...f.bind('newPassword')} />
            <Field label="Confirm new password" type="password" autoComplete="new-password" minLength={8} required {...f.bind('confirmPassword')} />
            <button className="btn" disabled={f.busy}>{f.busy ? 'Updating…' : 'Change password'}</button>
          </div>
          <Msg note={f.note} />
        </form>
      </Card>
    </>
  )
}
