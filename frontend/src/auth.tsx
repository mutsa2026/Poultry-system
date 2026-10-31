import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setUnauthorizedHandler } from './api'
import type { Me } from './types'

type Ctx = { me: Me | null; loading: boolean; login: (u: string, p: string) => Promise<void>; logout: () => Promise<void>; passwordChanged: () => void; can: (perm: string) => boolean; owner: boolean }
const AuthCtx = createContext<Ctx>(null as unknown as Ctx)
export const useAuth = () => useContext(AuthCtx)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)
  const qc = useQueryClient()

  useEffect(() => {
    setUnauthorizedHandler(() => { setMe(null); qc.clear() })
    api.get<Me>('/auth/me').then(setMe).catch(() => setMe(null)).finally(() => setLoading(false))
  }, [qc])

  const login = async (username: string, password: string) => { setMe(await api.post<Me>('/auth/login', { username, password })) }
  const logout = async () => { try { await api.post('/auth/logout') } finally { setMe(null); qc.clear() } }
  const passwordChanged = () => { setMe(null); qc.clear() }
  const owner = me?.role === 'OWNER'
  const can = (perm: string) => owner || !!me?.permissions.includes(perm)
  return <AuthCtx.Provider value={{ me, loading, login, logout, passwordChanged, can, owner }}>{children}</AuthCtx.Provider>
}
