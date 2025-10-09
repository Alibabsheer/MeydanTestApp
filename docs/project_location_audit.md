# Project Location Data Audit

## Overview
This document summarizes how the Android client captures, stores, and renders project location data within the Meydan Test App. The investigation covers the create/edit flows, the dedicated map selection screen, storage and repository layers, and downstream consumers such as project lists and report generation.

## Flow Summary
### Location selection (map screen)
* `SelectLocationActivity` hosts the Google Map picker. When the user taps *Save*, it reverse-geocodes the selected marker into an address string and returns it alongside latitude/longitude extras (`"address"`, `"latitude"`, `"longitude"`).
* If the activity is opened with previously saved coordinates, they are injected via the intent extras and pre-centered on the map.

### Project creation
* `CreateNewProjectActivity` launches the map picker. On result, it stores the returned address in the `etProjectLocation` field and the coordinate doubles in `selectedLatitude` / `selectedLongitude`.
* `CreateProjectViewModel#createProject` forwards the textual `addressText`, `latitude`, and `longitude` to `ProjectRepository#createProject`, which writes the canonical fields (`addressText`, derived `googleMapsUrl`, coordinates) into `organizations/{orgId}/projects/{projectId}`.

### Project editing
* `ProjectSettingsActivity` fetches the existing `Project` document and pre-fills `projectLocationEditText`, `selectedLatitude`, and `selectedLongitude`.
* When saving, it sends the current address text, coordinates, and resolved `googleMapsUrl` back to Firestore through a direct `.update(...)` call. The intent extras ensure the map picker starts from the stored coordinates on re-entry.

### Downstream consumption
* Project list cards (`ProjectAdapter` / `item_project.xml`) bind `project.addressText` into the `projectLocation` `TextView`.
* Daily report fetching consumes the embedded `addressText`/`googleMapsUrl` snapshot stored on each report; the PDF builder prints the same snapshot under the "موقع المشروع" label.

## Display Table ("Project Location" presentations)
| Screen / Context | UI Element | Label Type | Value Source | Notes |
| --- | --- | --- | --- | --- |
| Create project form | `TextInputLayout` hint (`android:hint="موقع المشروع"`) | Static label | N/A | Hint text only; input field is read-only and populated from the picker.
| Create project form | `etProjectLocation` value | Imported value | Intent extra `"address"` from `SelectLocationActivity`; persisted as `addressText` + `googleMapsUrl` | Coordinates saved separately in ViewModel before calling repository.
| Project settings form | `TextInputLayout` hint (`android:hint="موقع المشروع"`) | Static label | N/A | Same material component as create flow.
| Project settings form | `projectLocationEditText` value | Imported value | `Project.addressText` from Firestore; updates push `addressText` plus `latitude`/`longitude` + `googleMapsUrl` | Field re-launches picker while editing.
| Project list card | `TextView` `@id/projectLocation` | Imported value | `Project.addressText` from Firestore | Placeholder label in XML is replaced at bind time with the raw address.
| Daily report list / PDF | Rendered label "موقع المشروع" | Static label | `DailyReport.addressText` snapshot with optional `googleMapsUrl` | Reports embed textual location when saved; downstream displays rely on this snapshot.

## Stored Fields
* `Project` documents currently persist:
  * `addressText` – the human-readable address string returned by the Android `Geocoder`.
  * `googleMapsUrl` – a deep link derived from saved coordinates or the address itself.
  * `latitude` and `longitude` – nullable doubles saved during create/update.
* Daily reports snapshot the same pair (`addressText`, `googleMapsUrl`) at creation time to freeze the location per report.
