# Free deployment: Vercel (frontend) + Render (backend) + Neon (database)

Browser -> Vercel (React app) --/api/* proxied--> Render (Spring Boot) -> Neon (PostgreSQL)

Because Vercel forwards `/api/*` to Render, the browser only ever sees ONE origin.
That keeps your session cookie and CSRF token working with no CORS or backend changes.

## 0. Push to GitHub
Repo root must contain the `poultry-system` folder. Commit and push.

## 1. Neon (database)
1. neon.com -> create a free project (any region, Postgres 16).
2. Dashboard -> Connect. Turn OFF "Connection pooling" so you get the DIRECT host
   (no `-pooler` in the hostname). Flyway migrations need a direct connection.
3. From the connection string
   `postgresql://USER:PASSWORD@ep-xxxx.REGION.aws.neon.tech/neondb?sslmode=require`
   you need:
   - DB_URL      = `jdbc:postgresql://ep-xxxx.REGION.aws.neon.tech/neondb?sslmode=require`
   - DB_USER     = USER
   - DB_PASSWORD = PASSWORD
   Do NOT add `channel_binding=require` to DB_URL (the JDBC driver rejects it).

## 2. Render (backend)
1. render.com -> New -> Blueprint -> pick your repo -> Blueprint file path: `poultry-system/render.yaml`.
2. Fill in: DB_URL, DB_USER, DB_PASSWORD (from Neon), OWNER_USERNAME, OWNER_PASSWORD
   (the first login you will use; pick a strong password).
3. Deploy. First build takes ~5-10 min. When live, open
   `https://<your-service>.onrender.com/actuator/health` -> should show {"status":"UP"}.
4. Note your exact Render URL. If it is NOT `https://huku-hub.onrender.com`
   (name was taken), edit `poultry-system/frontend/vercel.json` and replace it, then push.

## 3. Vercel (frontend)
1. vercel.com -> Add New -> Project -> import the same repo.
2. Root Directory: `poultry-system/frontend`. Framework: Vite (auto-detected).
   No environment variables needed.
3. Deploy. Open the Vercel URL and sign in with OWNER_USERNAME / OWNER_PASSWORD.

## 4. Keep the backend awake (recommended)
Render free sleeps after ~15 min idle and takes up to a minute to wake, which can make
the first login fail or time out. Create a free monitor on uptimerobot.com or cron-job.org:
HTTPS GET `https://<your-service>.onrender.com/actuator/health` every 10 minutes.
(Neon's free database also sleeps when idle, but wakes in about a second; the pool is
already tuned for that.)

## Troubleshooting
- Login works then logs out / 403 on saves: make sure you use the Vercel URL, not the onrender.com URL.
- 502/504 on first request: backend was asleep; refresh after ~1 minute.
- Render deploy fails on startup: check Logs; usually a wrong DB_URL (must start with `jdbc:postgresql://`).
- Out of memory on Render: the JVM is already capped in backend/Dockerfile (JAVA_OPTS).

## Free-tier limits
Hobby/demo use only. Back up farm data (Neon has point-in-time restore on free for a short window).
