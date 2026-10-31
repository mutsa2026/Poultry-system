import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth'
import { ThemeProvider } from './theme'
import App from './App'
import './styles.css'

const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: true, staleTime: 15_000 } } })

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter><App /></BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
)
