import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { BarChart3, Bird, HeartPulse, Home, KeyRound, Layers, LogOut, Menu, MapPin, Pill, ShoppingCart, Truck, UserCog, Users, Factory, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useAuth } from './auth'
import Login from './pages/Login'
import DashboardPage from './pages/Dashboard'
import Batches from './pages/Batches'
import Customers from './pages/Customers'
import Sales from './pages/Sales'
import Suppliers from './pages/Suppliers'
import Farm from './pages/Farm'
import Team from './pages/Team'
import Medication from './pages/Medication'
import DeliveryMap from './pages/DeliveryMap'
import AnalyticsPage from './pages/Analytics'
import Account from './pages/Account'
import Processing from './pages/Processing'
import ThemeControl from './components/ThemeControl'

type Item = { to: string; label: string; icon: LucideIcon; owner?: boolean; perm?: string }
const ITEMS: Item[] = [
  { to: '/', label: 'Home', icon: Home },
  { to: '/batches', label: 'Batches', icon: Layers },
  { to: '/sales', label: 'Sales', icon: ShoppingCart },
  { to: '/customers', label: 'Customers', icon: Users },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, perm: 'PERM_PROFIT' },
  { to: '/farm', label: 'Feed & health', icon: HeartPulse },
  { to: '/medication', label: 'Medication', icon: Pill },
  { to: '/map', label: 'Delivery map', icon: MapPin },
  { to: '/suppliers', label: 'Suppliers', icon: Truck, owner: true },
  { to: '/team', label: 'Team', icon: UserCog, owner: true },
  { to: '/processing', label: 'Processing & dressed stock', icon: Factory },
  { to: '/account', label: 'Account & password', icon: KeyRound },
]

function More() {
  const { me, logout, owner, can } = useAuth()
  const items = ITEMS.slice(4).filter(i => (!i.owner || owner) && (!i.perm || can(i.perm)))
  return (
    <>
      <h1>More</h1><p className="sub">Signed in as {me?.fullName}</p>
      {items.map(i => <NavLink key={i.to} to={i.to} className="item"><b className="ico"><i.icon size={18} aria-hidden /> {i.label}</b><span>›</span></NavLink>)}
      <ThemeControl />
      <button className="btn ghost" onClick={logout}><LogOut size={16} aria-hidden /> Sign out</button>
    </>
  )
}

function Shell({ children }: { children: ReactNode }) {
  const { me, logout, owner, can } = useAuth()
  const visible = ITEMS.filter(i => (!i.owner || owner) && (!i.perm || can(i.perm)))
  const tabs: Item[] = [...ITEMS.slice(0, 4), { to: '/more', label: 'More', icon: Menu }]
  return (
    <div className="app">
      <aside aria-label="Main">
        <div className="logo"><span className="brand-mark"><Bird size={20} aria-hidden /></span><span>Huku Hub</span></div>
        {visible.map(i => <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}><i.icon size={18} aria-hidden /> {i.label}</NavLink>)}
        <div className="who">{me?.fullName} · {me?.role === 'OWNER' ? 'Owner' : 'Worker'}<ThemeControl /><button className="btn ghost sm" style={{ marginTop: 8 }} onClick={logout}><LogOut size={14} aria-hidden /> Sign out</button></div>
      </aside>
      <main>{children}</main>
      <nav className="tabs" aria-label="Main">
        {tabs.map(i => <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}><i.icon size={20} aria-hidden /><span>{i.label}</span></NavLink>)}
      </nav>
    </div>
  )
}

export default function App() {
  const { me, loading, owner, can } = useAuth()
  if (loading) return <p className="note" style={{ padding: 24 }}>Loading…</p>
  if (!me) return <Routes><Route path="*" element={<Login />} /></Routes>
  return (
    <Shell>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/batches" element={<Batches />} />
        <Route path="/sales" element={<Sales />} />
        <Route path="/customers" element={<Customers />} />
        <Route path="/farm" element={<Farm />} />
        <Route path="/analytics" element={can('PERM_PROFIT') ? <AnalyticsPage owner={owner} /> : <Navigate to="/" />} />
        <Route path="/medication" element={<Medication />} />
        <Route path="/map" element={<DeliveryMap />} />
        <Route path="/more" element={<More />} />
        <Route path="/account" element={<Account />} />
        <Route path="/processing" element={<Processing />} />
        <Route path="/suppliers" element={owner ? <Suppliers /> : <Navigate to="/" />} />
        <Route path="/team" element={owner ? <Team /> : <Navigate to="/" />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Shell>
  )
}
