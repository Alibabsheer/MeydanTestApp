# Project Location Data Audit

## Overview
This document summarizes how the Android client captures, stores, and renders project location data within the Meydan Test App. The investigation covers the create/edit flows, the dedicated map selection screen, storage and repository layers, and downstream consumers such as project lists and report generation.

## Flow Summary
### Location selection (map screen)
* `SelectLocationActivity` hosts the Google Map picker. When the user taps *Save*, it reverse-geocodes the selected marker into an address string and returns it alongside latitude/longitude extras (`"address"`, `"latitude"`, `"longitude"`).
* If the activity is opened with previously saved coordinates, they are injected via the intent extras and pre-centered on the map.

### Project creation
* `CreateNewProjectActivity` launches the map picker. On result, it stores the returned address in the `etProjectLocation` field and the coordinate doubles in `selectedLatitude` / `selectedLongitude`.
* `CreateProjectViewModel#createProject` forwards the textual `location`, `latitude`, and `longitude` to `ProjectRepository#createProject`, which writes the non-null values into `organizations/{orgId}/projects/{projectId}`.

### Project editing
* `ProjectSettingsActivity` fetches the existing `Project` document and pre-fills `projectLocationEditText`, `selectedLatitude`, and `selectedLongitude`.
* When saving, it sends the current address text and coordinates back to Firestore through a direct `.update(...)` call. The intent extras ensure the map picker starts from the stored coordinates on re-entry.

### Downstream consumption
* Project list cards (`ProjectAdapter` / `item_project.xml`) bind `project.location` into the `projectLocation` `TextView`.
* Daily report fetching normalizes `projectLocation` by preferring the embedded report field, then falling back to `location` when present; the PDF builder prints the value under the "موقع المشروع" label.

## Display Table ("Project Location" presentations)
| Screen / Context | UI Element | Label Type | Value Source | Notes |
| --- | --- | --- | --- | --- |
| Create project form | `TextInputLayout` hint (`android:hint="موقع المشروع"`) | Static label | N/A | Hint text only; input field is read-only and populated from the picker.
| Create project form | `etProjectLocation` value | Imported value | Intent extra `"address"` from `SelectLocationActivity`; persisted as `location` | Coordinates saved separately in ViewModel before calling repository.
| Project settings form | `TextInputLayout` hint (`android:hint="موقع المشروع"`) | Static label | N/A | Same material component as create flow.
| Project settings form | `projectLocationEditText` value | Imported value | `Project.location` from Firestore; updates push `location` plus `latitude`/`longitude` | Field re-launches picker while editing.
| Project list card | `TextView` `@id/projectLocation` | Imported value | `Project.location` from Firestore | Placeholder label in XML is replaced at bind time with the raw address.
| Daily report list / PDF | Rendered label "موقع المشروع" | Static label | `DailyReport.projectLocation` fallback to embedded `location` | Reports embed textual location when saved; downstream displays rely on this string.

## Stored Fields
* `Project` documents currently persist:
  * `location` – the human-readable address string returned by the Android `Geocoder`.
  * `latitude` and `longitude` – nullable doubles saved during create/update.
* No plus code is generated or stored; address text is the only fallback when coordinates are absent.

## Proposed `googleMapsUrl` field
Introduce a persistent `googleMapsUrl: String?` property on the `Project` entity (and any embedded report payloads) computed as follows:
1. If `latitude` & `longitude` are both non-null ⇒ `https://www.google.com/maps/search/?api=1&query=<lat>,<lng>` (plain doubles without URL encoding).
2. Else if a Plus Code string becomes available ⇒ URL-encode the plus code (preserving `+` as `%2B` when necessary) and plug into `query`.
3. Else if only the address text exists ⇒ UTF-8 encode the address string via `URLEncoder.encode` and use it as the query parameter.

Storing the canonical URL alongside the other location fields keeps downstream consumers (reports, share intents, deep links) in sync while respecting the existing persistence model.
