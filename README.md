# SmartAnnouncer

SmartAnnouncer is a Java 17 announcement plugin for Spigot, Paper and Folia. It supports interval announcements, wall-clock announcements, first-join messages and location-triggered messages.

## Features

- Interval announcements with sequential or random message order.
- Clock announcements using a configured timezone.
- First-join announcements with delayed delivery.
- Location announcements for cuboid and sphere regions.
- Click and hover text through the Spigot chat component API.
- Optional MySQL/PostgreSQL cross-server de-duplication.
- Folia-safe scheduling for global, region, entity and async work.

## Commands

- `/smartannouncer reload` reloads `config.yml`, `announcements.yml` and `message.yml`.
- `/smartannouncer list` lists configured announcements.
- `/smartannouncer test <id>` previews an announcement.

Alias: `/sa`

## Permissions

- `smartannouncer.admin` grants all administration permissions.
- `smartannouncer.reload` allows `/smartannouncer reload`.
- `smartannouncer.test` allows `/smartannouncer test`.

## Configuration

`config.yml` controls global settings and database de-duplication.

```yaml
settings:
  timezone: "Asia/Shanghai"
  prefix: ""
  debug: false

database:
  enabled: false
  type: "mysql"
  jdbc-url: ""
  host: "localhost"
  port: 3306
  database: "smartannouncer"
  username: "root"
  password: ""
  table-prefix: "smartannouncer_"
  server-id: "server-1"
  dispatch-dedupe-seconds: 60
  cleanup-days: 14
```

`announcements.yml` contains announcement definitions.

```yaml
announcements:
  - id: "tips"
    type: "interval"
    enabled: true
    interval-seconds: 300
    order: "RANDOM"
    messages:
      - text: "&aRemember to protect your base."
    worlds: []
    permission: ""

  - id: "night_reminder"
    type: "clock"
    enabled: true
    times: ["23:00"]
    messages:
      - text: "&dIt is late. Take a break."
```

Boolean values are strict. Use only `true` or `false`; values such as `yes`, `on` or `1` fail reload and keep the previous snapshot active.

## Folia Compatibility

SmartAnnouncer does not use BukkitScheduler on Folia. It reflects Paper/Folia public scheduler APIs for:

- Global scheduler: non-entity global work only.
- Region scheduler: location-owned coordination only.
- Entity scheduler: player messages, permissions and player location reads.
- Async scheduler: pure async work only.

If Folia is detected and scheduler initialization fails, the plugin disables itself with a clear error instead of falling back to BukkitScheduler.

## Database De-Duplication

When multiple servers share one database, enable `database.enabled` on every server and give each server a unique `server-id`.

SmartAnnouncer creates:

- `<prefix>dispatch_state` for current cross-server dispatch claims.
- `<prefix>dispatch_log` for legacy compatibility and migration of old player once records.

Interval announcements use an atomic cooldown claim. Clock announcements use a deterministic `date + time + timezone` claim. First-join announcements use a per-player once claim. If the database claim fails, the plugin skips that dispatch instead of locally sending on every server.

## Build

```bash
./gradlew build
```

The shaded plugin jar is written to `build/libs/SmartAnnouncer-<version>.jar`.
