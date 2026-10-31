export class ApiError extends Error {
  status: number
  fields: Record<string, string>
  constructor(status: number, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fields = fields
  }
}

let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn }

const xsrf = () => {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return m ? decodeURIComponent(m[1]) : ''
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = xsrf()
  let res: Response
  try {
    res = await fetch('/api/v1' + path, { method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'Can’t reach the server. Check your internet connection and try again.')
  }
  if (res.status === 401 && path !== '/auth/login') onUnauthorized()
  if (!res.ok) {
    let msg = 'Something went wrong. Please try again.'
    let fields: Record<string, string> = {}
    try { const j = await res.json(); msg = j.message ?? msg; fields = j.fieldErrors ?? {} } catch { /* body wasn't JSON */ }
    throw new ApiError(res.status, msg, fields)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T,>(p: string) => request<T>('GET', p),
  post: <T,>(p: string, b?: unknown) => request<T>('POST', p, b ?? {}),
  patch: <T,>(p: string, b: unknown) => request<T>('PATCH', p, b),
}

export const money = (n: number | null | undefined) =>
  n == null ? '–' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(n)
export const today = () => new Date().toLocaleDateString('en-CA')
export const daysOld = (iso: string) => Math.max(0, Math.round((Date.now() - new Date(iso + 'T00:00:00').getTime()) / 864e5))
export const num = (s: string) => (s.trim() === '' ? NaN : Number(s))

/** Downloads a file from the API (used for CSV exports) using the signed-in session. */
export async function download(path: string, fallbackName: string) {
  const res = await fetch('/api/v1' + path, { credentials: 'same-origin' })
  if (res.status === 401) onUnauthorized()
  if (!res.ok) {
    let msg = 'The download failed. Please try again.'
    try { msg = (await res.json()).message ?? msg } catch { /* not JSON */ }
    throw new ApiError(res.status, msg)
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}
