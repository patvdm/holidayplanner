# Overhaul plan

Agreed with Pat on 2026-09-18 from his handwritten notes. This supersedes the milestone plan
where the two disagree. Four milestones, O1 to O4, each stopping for review.

## The shape

Four tabs: **Explore, Trips, Routes, Spots.**

**Nothing is created by hand.** The only way a spot exists is an external share. A Google Maps
share becomes a spot directly. Any other link becomes a spot only once the place is found through
Google autocomplete and selected, which files it under its city and country. Countries and cities
exist only as a side effect of that. Every add button on Explore, country, city and trip screens
goes; the share flow is the front door.

## Spots

- **Priority is High or Normal.** High shows a red "priority" tag, Normal shows nothing. Applies to
  countries, cities and spots. Migration: Must becomes High, Like and Maybe become Normal.
- **Status stays visited or unvisited.** Visited shows a green tag, unvisited shows nothing.
- **Type is mandatory: POI, restaurant, accommodation.** POI is the default and shows no tag,
  restaurant shows orange, accommodation shows blue. Blank or unknown reads as POI.
- **Free-text tags are removed.** Notes stay, and the first line of a spot's notes shows in its
  list row.
- **A spot always has a Google Maps link.** One created through autocomplete gets a link derived
  from its place id and coordinates.

**Spots tab:** every spot in one list. Filters: type, status, priority, country. Sort: name,
priority, most recent.

## Routes

A route is its own entity with its own tab. It belongs to a city and is built from that city's
spots by ticking them, ordered by drag or by proximity, opened in Google Maps as a walk. It has a
name, a **completed** tag toggled by hand, and can be deleted.

One create-route screen, reached from three places so nobody has to walk back to Explore:

- the Routes tab, which asks for a city first;
- a city's screen, which already knows;
- a trip day, which asks for a city within the trip's country.

## Trips

A trip belongs to a **country** and has a date range, both set at creation. Each day holds items
of three kinds: **flight, spot, route.**

- A **spot** item is picked from the spots in that country. Its row shows the spot's priority,
  status and type tags. A stay is simply a spot of type accommodation on a day.
- A **route** item is picked from existing routes in cities of that country, or made on the spot
  through an "add new route" button that opens the create-route screen and attaches the result.
- A **flight** item keeps its number, airports, times and zones.

Flight and route rows carry a "flight" or "route" header.

## Data changes, all in O1

| Table | Change |
|---|---|
| Countries, Cities, Spots | priority values become HIGH / NORMAL |
| Spots | type non-null, POI default (O1); tags removed together with their UI (O2) |
| Routes | new table: id, cityId, name, completed, notes |
| RouteStops | tripItemId becomes routeId |
| Trips | countryId replaces cityId and countryIds |
| TripItems | types FLIGHT / SPOT / ROUTE; routeId added; other types dropped on pull |

Old trip items of removed types are not migrated. Pat's existing trip data is test data.

## Milestones

- **O1 Model.** Every change in the table above except tags, plus the sync mapping, Room, the
  `Code.gs` schema, tests, and only as much UI as compiling needs. Done 2026-09-18; `setup()`
  needs a run. A trip's dates at creation wait for O4 with the rest of the trip screen.
- **O2 Spots and lists.** The tag chips, notes in rows, tags UI removed, every manual add
  removed, the Spots tab. Done 2026-09-18. The place search sheet now only ever adds a spot; its
  city mode went with the manual adds. `Code.gs` dropped the Spots `tags` column (harmless to
  leave in the sheet).
- **O3 Routes.** The Routes tab, the create-route funnel from all three entry points, completed.
- **O4 Trips.** Country-based creation, days, the three item kinds, headers and tags on rows.

## Later: pictures and polish

Asked for on 2026-09-18, after O2, to follow the four milestones above. Spots, cities and
countries get a picture, fetched automatically, shown on their list rows, replaceable by hand.
Unrelated to a spot's saved links. Along with it, a general pass to make the app better looking.
Not designed yet; the source of the pictures and how they sync between the two phones are open.
