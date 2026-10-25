# CloudStream Supabase Import System

This repository builds a customized CloudStream debug APK with an Import System panel.

## Features currently implemented
- Lists providers available in the CloudStream runtime and lets you select them.
- Supabase project URL and publishable/anon key configuration.
- Supabase email/password sign-in and account creation.
- Year-range and content-type filters.
- Imports provider homepage metadata and link callbacks into `public.media_items` and `public.media_sources`.
- Uses normalized title/type/year keys and a unique media-item/direct-URL constraint to reduce duplicates.
- Schedules a daily WorkManager sync after the first successful manual import.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## Setup
1. Ensure the Supabase project has the `media_items` and `media_sources` tables.
2. Create or sign in to a Supabase Auth email/password account in the app.
3. Enter the project URL and publishable key (never use a service-role/secret key in the app).
4. Select providers, year range and categories, then choose **Start Import**.
5. Download the APK artifact from the latest successful GitHub Actions run.

## Important limitations
- The importer can only index content returned by providers installed/enabled in CloudStream. Provider failures are skipped so the rest can continue.
- Provider homepage pagination is capped: up to 20 homepage categories, 20 pages per category (when the provider supports pagination), and 100 results per page per provider per run.
- Some provider link callbacks return extractor/landing URLs rather than a permanent raw media URL. URLs are stored as returned by CloudStream; expiring links may later stop working.
- Android WorkManager daily execution is best-effort and may be delayed by battery optimization, network availability or the OS.
- Refresh tokens are stored in local app preferences for scheduled refresh. Use this only on a trusted device; remove app data to clear local access.
- The APK is a debug build, not a signed production release.

## Build
Open **Actions**, select the latest successful run, and download `cloudstream-import-system-apk`.
