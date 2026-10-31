import { SunMoon } from 'lucide-react'
import { useTheme, type ThemePreference } from '../theme'

export default function ThemeControl() {
  const { preference, setPreference } = useTheme()

  return (
    <label className="theme-control">
      <span><SunMoon size={17} aria-hidden /> Appearance</span>
      <select aria-label="Color theme" value={preference} onChange={event => {
        const value = event.target.value
        if (value === 'system' || value === 'light' || value === 'dark') setPreference(value)
      }}>
        <option value="system">System</option>
        <option value="light">Light</option>
        <option value="dark">Dark</option>
      </select>
    </label>
  )
}
