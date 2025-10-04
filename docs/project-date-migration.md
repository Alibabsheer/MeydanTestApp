# Legacy project date migration

Existing project documents may still contain stringified timestamp values such as
`"Timestamp(seconds=..., nanoseconds=...)"` in the `startDate` field or may be
missing an `endDate` field altogether. The application now normalises these
values to Firestore `Timestamp` objects at read time and schedules an automatic
update via `DocumentSnapshot.migrateTimestampIfNeeded` once a valid timestamp is
resolved.【F:app/src/main/java/com/example/meydantestapp/utils/FirestoreMigrationExtensions.kt†L8-L24】

To backfill historical data run the following steps:

1. Deploy the updated application or backend so that each read of a project
   document automatically migrates the timestamp fields when possible.
2. Optionally trigger a manual pass by opening the project list and details
   screens for each organisation; these screens now load the first page of
   projects ordered by `updatedAt` and request migrations where necessary.【F:app/src/main/java/com/example/meydantestapp/data/ProjectsRepository.kt†L6-L31】【F:app/src/main/java/com/example/meydantestapp/ProjectDetailsActivity.kt†L130-L176】
3. For documents that still contain non-parsable strings, export them and update
   the `startDate`/`endDate` fields with valid `Timestamp` JSON payloads using
   the Firebase console or a one-off script. The application will treat any
   unresolved value as `null` and block saving updates until both timestamps are
   valid.【F:app/src/main/java/com/example/meydantestapp/CreateProjectViewModel.kt†L46-L90】

No destructive migration is required; documents with valid `Timestamp` values
remain unchanged.
