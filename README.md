# Neon Auction

Folia-safe fixed-price auction plugin backed by MongoDB and Redis.

## Build

```bash
./gradlew build
```

The plugin jar is created at:

```text
build/libs/NeonAuction-0.1.0-SNAPSHOT.jar
```

## Runtime requirements

The plugin expects a reachable MongoDB server and Redis server.

Set the connection values in `plugins/NeonAuction/credentials.yml`.

## Plugin config

Database secrets are in `plugins/NeonAuction/credentials.yml`.

Each Minecraft server must use a unique `server-id` in `config.yml`, for example:

```yaml
server-id: "earth-1"
```

For a second local server:

```yaml
server-id: "skyblock-1"
```

## Commands

```text
/ah
/ah sell <price>
/ah claims
/ah listings
/ah reload
```

## Current scope

Implemented:

- fixed-price listings
- auction browser menu
- claims menu
- seller payment claims
- expired listing claims
- MongoDB atomic purchase reservation
- Redis Pub/Sub menu refresh
- Folia player-scheduler inventory/menu handoffs

Not implemented yet:

- bidding
- cancellation menu
- automated integration tests
