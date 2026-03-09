# Cloudflare deployment

This directory runs the existing React build and Spring Boot backend behind one Cloudflare hostname.

## What stays the same
- React still calls relative API paths like `/rest`, `/common`, `/mail`, and `/chat`.
- Messenger still uses the same-host WebSocket path `/starworks-groupware-websocket`.
- The backend still uses the same JWT cookie flow and the same PostgreSQL schema.

## What changes
- The database moves to Supabase Postgres.
- File storage moves to an S3-compatible object store.
- Supabase Storage with a public bucket is the recommended non-R2 path for this repo.
- The Spring Boot backend runs inside Cloudflare Containers Beta.
- A Worker serves `frontend/dist` and forwards backend paths to the container.
- The Worker hostname is the public app URL. Pages is not required for login in this layout.

## Deploy steps
1. Apply the PostgreSQL SQL files in `db/migration-input/ddl` to Supabase in this order:
   - `schema_postgres.sql`
   - `runtime_fixes_postgres.sql`
   - `constraints_postgres.sql`
   - `indexes_postgres.sql`
2. Build the frontend bundle.
3. Copy `.dev.vars.example` to `.dev.vars` and fill the real values.
4. Install the worker dependencies.
5. Sync the values in `.dev.vars` into Cloudflare secrets.
6. Run `npx wrangler deploy`.

Recommended public URL pattern:
- `https://modulearning02-api.<your-workers-subdomain>.workers.dev`

## Required values
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET_KEY`
- `APP_FRONTEND_BASE_URL`, `CORS_ALLOW_ORIGINS`, `COOKIE_DOMAIN`
- `SIGNUP_TURNSTILE_ENABLED`, `SIGNUP_TURNSTILE_SITE_KEY`, `SIGNUP_TURNSTILE_SECRET_KEY` when owner self-signup should be protected publicly
- `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_BUCKET`, `AWS_ENDPOINT`
- `AWS_REGION` should match the value in `wrangler.jsonc`
- `AWS_PUBLIC_BASE_URL` if you want existing direct file links to keep working without frontend changes

Non-secret defaults such as `FILE_STORAGE_MODE=s3`, `COOKIE_SAME_SITE=Lax`, and the backend port stay in `wrangler.jsonc`.

Recommended owner-signup protection defaults:
- `SIGNUP_TURNSTILE_ENABLED=true`
- `SIGNUP_RATE_LIMIT_IP_MAX_ATTEMPTS=10`
- `SIGNUP_RATE_LIMIT_IP_WINDOW_SECONDS=600`
- `SIGNUP_RATE_LIMIT_EMAIL_MAX_ATTEMPTS=5`
- `SIGNUP_RATE_LIMIT_EMAIL_WINDOW_SECONDS=3600`
- `SIGNUP_RATE_LIMIT_SLUG_MAX_ATTEMPTS=5`
- `SIGNUP_RATE_LIMIT_SLUG_WINDOW_SECONDS=3600`

When using the default `workers.dev` hostname:
- Set `APP_FRONTEND_BASE_URL` to the Worker URL.
- Set `CORS_ALLOW_ORIGINS` to the same Worker URL.
- Leave `COOKIE_DOMAIN` empty so the cookie is scoped to the current host.

## Recommended storage: Supabase Storage public bucket
Use Supabase Storage through its S3-compatible endpoint.

- Keep `FILE_STORAGE_MODE=s3`.
- Generate S3 access keys in Supabase Dashboard.
- Set `AWS_ENDPOINT` to `https://<project-ref>.storage.supabase.co/storage/v1/s3`.
- Set `AWS_REGION` to your Supabase project region.
- Make the bucket public.
- Set `AWS_PUBLIC_BASE_URL` to `https://<project-ref>.supabase.co/storage/v1/object/public/<bucket>`.

This repo already uploads through the AWS SDK with a custom endpoint, so switching from R2 to Supabase Storage does not require a new storage mode or a backend rewrite.

## Temporary fallback: local disk
If you cannot use any object storage yet, you can run with local files:

- Set `FILE_STORAGE_MODE=local`.
- Set `FILE_STORAGE_PATH` to a writable path such as `file:/data/starworks_medias/`.

This is for demos or short-lived testing only:

- Cloudflare Containers disk is ephemeral and is reset after sleep, restart, or redeploy.
- This Worker currently forwards `/rest`, `/common`, `/mail`, `/chat`, `/file`, `/folder`, and `/starworks-groupware-websocket`, but not `/starworks_medias`.
- Several current UI screens open `file.filePath` directly, so local image and attachment URLs will break unless you add a matching Worker forward rule or change those screens to use download endpoints.

## Speed note
Supabase Storage public URLs are usually a better production fallback than local disk because they stay durable and keep the current public-link UI working.
Compared with an edge-cached R2 custom domain, public downloads may be a bit less optimized globally, but this is usually a smaller tradeoff than losing files on container restart.

## Scaling note
`wrangler.jsonc` is pinned to a single backend container instance for now. Messenger uses Spring's in-memory STOMP broker, so multi-instance scale would need a separate broker migration first.
