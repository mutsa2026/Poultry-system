# Poultry Manager

React + TypeScript frontend, Spring Boot 3 / Java 17 backend, PostgreSQL 16 with Flyway.

## Run it
1. Database:   `docker compose up -d`
2. Backend:    `cd backend`
   ```
   export DB_URL=jdbc:postgresql://localhost:5433/poultry DB_USER=poultry DB_PASSWORD=change-me
   export OWNER_USERNAME=owner OWNER_PASSWORD='pick-a-strong-password'
   mvn spring-boot:run
   ```
   Flyway builds the schema on first start. The first owner account is created from the two OWNER_* variables.
3. Frontend:   `cd frontend && npm install && npm run dev` then open http://localhost:5173
   (Vite proxies /api to :8080, so cookies and CSRF work without CORS.)

## Free hobby deployment
The frontend is included in the backend container so the UI and API share one HTTPS origin
(required for the session-cookie login). The included Render Blueprint deploys the app as a
free Docker web service. Use Supabase Free for PostgreSQL; do not use Render's free Postgres
for persistent data because it expires after 30 days.

1. Push the repository containing the `poultry-system` folder to a Git provider and
   create a free Supabase project.
2. In Supabase, open **Connect**, choose **Session pooler**, and copy its connection details.
   In Render's Blueprint setup, provide:
   - `DB_URL`: `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`
   - `DB_USER`: the pooler username from Supabase (usually `postgres.<project-ref>`)
   - `DB_PASSWORD`: the Supabase database password
   - `OWNER_USERNAME`: the initial owner login name
   - `OWNER_PASSWORD`: a unique, strong initial password
3. In Render, create a Blueprint and select `poultry-system/render.yaml` as its
   Blueprint file. The configuration sets the app folder as the service root.
   Render builds and serves both frontend and backend from the same URL.
4. Open the Render URL and sign in with the owner credentials above.

Free-tier caveats: Render may spin down the web service after inactivity and the first
request can be slow. Supabase Free projects can pause after a week without activity.
This setup is for demos and hobby use, not production; review the providers' current
limits and arrange backups before storing important farm data.

## Test
`cd backend && mvn test` (needs Docker; Testcontainers starts a real PostgreSQL).
Covers: opening stock, mortality, concurrent stock safety, live sales, credit limits, payment allocation, cancel/restore.

## What is built
Backend (all under /api/v1, session cookie + CSRF, roles OWNER/WORKER + permissions pay/discount/profit)
- Auth: login, logout, me. Deactivated users can't sign in.
- Batches, stock ledger, mortality (row-locked), daily mortality, climate advice, vaccination plan
- Dressed-chicken processing batches trace back to live-bird batches, deduct processed birds, and create kilogram-based stock lots
- Live current weather and a five-day forecast for Harare, sourced from Open-Meteo. Set `WEATHER_LOCATION`, `WEATHER_LATITUDE`, and `WEATHER_LONGITUDE` to use another farm location.
- Customers (credit limit, terms, balance, overdue), sales/invoices (live birds and dressed lots deducted once, credit limit enforced, cancellation restores the original lot), payments (partial, allocated oldest-first)
- Suppliers, purchases (feed purchases add stock), supplier payments, expenses
- Feed stock and use, visitor/biosecurity log
- Dashboard (alerts for death spikes, overdue debt, low feed, vaccines, risky visits), profit per batch, receivables ageing
- Audit log for sensitive actions
- Self-service password change requires the current password, audits the update, and signs out active sessions

Frontend (mobile-first; bottom tab bar on phones, sidebar on desktop; System/Light/Dark appearance selector with saved preference)
- Login, Home (live weather and five-day forecast), Batches (deaths, chart, climate, vaccines), Processing (dressed stock lots), Sales (invoice + print), Customers (payments), Suppliers and costs, Feed and health, Team and permissions, Account and password

## Analytics and downloads
- Analytics page (owner, or workers given "see profit"): weekly sales vs cash, deaths per day, mortality by batch, sales by product, expenses by category, top debtors, receivables by age. Each chart has Save picture (PNG); the page has Print or save as PDF.
- CSV downloads (Excel-ready, optional date range): sales, payments, customers, expenses, suppliers, deaths, stock movements. Exports are audit-logged.

## Not built yet
Real map tiles and driver GPS, live team chat, order status workflow (orders vs sales),
credit notes, production hardening, backups. Processing costs are not yet tracked separately.
Existing dressed-chicken sales remain in history; only new sales from recorded processing lots affect dressed stock.
