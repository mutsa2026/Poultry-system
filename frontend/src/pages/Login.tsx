import { Bird } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth'
import { Card, Field, Msg, useForm } from '../ui'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const f = useForm({ username: '', password: '' })
  return (
    <div className="login">
      <h1 className="login-brand"><span className="brand-mark"><Bird size={22} aria-hidden /></span><span>Huku Hub</span></h1>
      <p className="sub">Sign in to continue</p>
      {searchParams.get('passwordChanged') === '1' && <p className="banner" role="status">Password changed. All devices have been signed out. Sign in with your new password.</p>}
      <Card className="login-card">
        <form onSubmit={e => { e.preventDefault(); f.submit(async () => {
          await login(f.v.username, f.v.password)
          if (searchParams.get('passwordChanged') === '1') navigate('/', { replace: true })
        }, false) }}>
          <div className="form" style={{ gridTemplateColumns: '1fr' }}>
            <Field label="Username" autoComplete="username" required {...f.bind('username')} />
            <Field label="Password" type="password" autoComplete="current-password" required {...f.bind('password')} />
            <button className="btn" disabled={f.busy}>{f.busy ? 'Signing in…' : 'Sign in'}</button>
          </div>
          <Msg note={f.note?.kind === 'err' ? f.note : null} />
        </form>
      </Card>
    </div>
  )
}
