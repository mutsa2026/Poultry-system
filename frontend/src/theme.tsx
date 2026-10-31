import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from 'react'

export type ThemePreference = 'system' | 'light' | 'dark'

type ThemeContextValue = { preference: ThemePreference; setPreference: (value: ThemePreference) => void }
const ThemeContext = createContext<ThemeContextValue | null>(null)
const STORAGE_KEY = 'poultry-theme'

function readPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark' || saved === 'system') return saved
  } catch {
    // Storage may be unavailable in private browsing; the theme still works for this tab.
  }
  return 'system'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState<ThemePreference>(readPreference)

  useLayoutEffect(() => {
    const root = document.documentElement
    if (preference === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', preference)
    root.style.colorScheme = preference === 'system' ? 'light dark' : preference
    try {
      localStorage.setItem(STORAGE_KEY, preference)
    } catch {
      // Keep the selected theme active for this tab if storage is unavailable.
    }
  }, [preference])

  return <ThemeContext.Provider value={{ preference, setPreference }}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}
