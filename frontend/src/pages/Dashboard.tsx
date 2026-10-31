import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Cloud, CloudFog, CloudLightning, CloudRain, CloudSun, Droplets, Info, RefreshCw, Sun, Wind, type LucideIcon } from 'lucide-react'
import { api, money } from '../api'
import { useAuth } from '../auth'
import { Card, Empty, Stat, Tag } from '../ui'
import type { Dashboard, Weather } from '../types'

function iconForWeather(code: number): LucideIcon {
  if (code === 0) return Sun
  if (code <= 3) return CloudSun
  if (code === 45 || code === 48) return CloudFog
  if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return CloudRain
  if (code >= 95) return CloudLightning
  return Cloud
}

export default function DashboardPage() {
  const { me } = useAuth()
  const q = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard'), refetchInterval: 60_000 })
  const weather = useQuery({
    queryKey: ['weather'],
    queryFn: () => api.get<Weather>('/weather'),
    staleTime: 10 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  })
  const d = q.data
  const WeatherIcon = weather.data ? iconForWeather(weather.data.current.weatherCode) : CloudSun
  return (
    <>
      <h1>Good day, {me?.fullName.split(' ')[0]}</h1>
      <p className="sub">{new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}{d ? ` · ${d.alerts.length} things need attention` : ''}</p>
      {q.isError && <Card><p className="msg err">Couldn’t load the dashboard. Pull down or refresh to try again.</p></Card>}
      <section className="hero photo" style={{ backgroundImage: 'linear-gradient(90deg,rgba(30,18,6,.82),rgba(30,18,6,.3) 70%),url(/images/hero.jpg)' }}><small>Birds alive on the farm</small><div className="big">{d ? d.liveBirds.toLocaleString() : '…'}</div></section>
      <Card className="weather-card">
        <div className="row weather-heading">
          <div className="ico"><span className="weather-icon"><WeatherIcon size={24} aria-hidden /></span><div><h3>Farm weather</h3><p className="sub">{weather.data?.location ?? 'Harare, Zimbabwe'} · live conditions</p></div></div>
          <button className="btn ghost sm" onClick={() => void weather.refetch()} disabled={weather.isFetching} aria-label="Refresh weather">
            <RefreshCw size={15} aria-hidden /> {weather.isFetching ? 'Updating…' : 'Refresh'}
          </button>
        </div>
        {weather.isPending && <Empty>Loading current weather…</Empty>}
        {weather.isError && !weather.data && <p className="msg err">Live weather is unavailable right now. Check your connection and try again.</p>}
        {weather.data && <>
          <div className="weather-current">
            <div><span className="weather-temperature">{Math.round(weather.data.current.temperatureC)}°</span><span className="weather-unit">C</span></div>
            <div className="weather-summary"><b>{weather.data.current.description}</b><span>Feels like {Math.round(weather.data.current.feelsLikeC)}°C</span></div>
          </div>
          <div className="weather-details">
            <span><Droplets size={16} aria-hidden /> {weather.data.current.humidityPercent}% humidity</span>
            <span><Wind size={16} aria-hidden /> {Math.round(weather.data.current.windKph)} km/h wind</span>
            <span><CloudRain size={16} aria-hidden /> {weather.data.current.precipitationMm.toFixed(1)} mm rain</span>
          </div>
          <div className="weather-forecast">
            {weather.data.forecast.map(day => {
              const Icon = iconForWeather(day.weatherCode)
              return <div className="weather-day" key={day.date}>
                <b>{new Date(`${day.date}T12:00:00`).toLocaleDateString('en', { weekday: 'short' })}</b>
                <Icon size={20} aria-label={day.description} />
                <span>{Math.round(day.highC)}° / {Math.round(day.lowC)}°</span>
                <small><Droplets size={12} aria-hidden /> {day.precipitationProbabilityPercent}%</small>
              </div>
            })}
          </div>
          <p className="weather-footnote">Updated {new Date(weather.data.fetchedAt).toLocaleTimeString('en', { hour: 'numeric', minute: '2-digit' })} · Outdoor weather; check house thermometers for flock conditions.</p>
          {weather.isError && <p className="msg err">Couldn’t refresh weather. Showing the last received update.</p>}
        </>}
      </Card>
      {d?.showMoney && (
        <div className="stats">
          <Stat label="Sales, last 7 days" value={money(d.salesThisWeek)} />
          <Stat label="Money received" value={money(d.receivedThisWeek)} />
          <Stat label="Owed by customers" value={money(d.owedByCustomers)} bad={(d.owedByCustomers ?? 0) > 0} />
          {d.owedToSuppliers != null && <Stat label="You owe suppliers" value={money(d.owedToSuppliers)} />}
        </div>
      )}
      <h2>Needs attention</h2>
      {d && d.alerts.length === 0 && <Empty>Nothing needs attention right now.</Empty>}
      {d?.alerts.map((a, i) => <div key={i} className={'alert ' + a.severity.toLowerCase()}>{a.severity === 'HIGH' ? <AlertTriangle size={18} aria-label="Urgent" /> : <Info size={18} aria-label="Notice" />}<span>{a.message}</span></div>)}
      <h2>Feed</h2>
      {d?.feed.map(f => (
        <Card key={f.id}>
          <div className="row"><img className="thumb" src="/images/feed.jpg" alt="" style={{ objectFit: 'contain', background: '#fff' }} /><b style={{ flex: 1, marginLeft: 10 }}>{f.name}</b><Tag kind={f.low ? 'late' : 'go'}>{f.low ? 'Reorder' : 'In stock'}</Tag></div>
          <p className="sub" style={{ margin: '4px 0 8px' }}>{f.onHand} {f.unit}s in store · reorder level {f.reorderLevel}</p>
          <div className="meter"><span style={{ width: Math.min(100, (f.onHand / (f.reorderLevel * 2 || 1)) * 100) + '%', background: f.low ? 'var(--bad)' : 'var(--ok)' }} /></div>
        </Card>
      ))}
      <h2>Around the farm</h2>
      <div className="gal">
        <figure><img src="/images/g-house.jpg" alt="Broilers feeding under heat lamps in the grow-out house" /><figcaption>Broiler house</figcaption></figure>
        <figure><img src="/images/g-pasture.jpg" alt="Hens foraging on green pasture" /><figcaption>Free-range run</figcaption></figure>
        <figure><img src="/images/g-golden.jpg" alt="Golden hens in the evening light" /><figcaption>Layer flock</figcaption></figure>
      </div>
    </>
  )
}
