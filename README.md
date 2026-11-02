# McrepVerification

Paper plugin for the mcrep.gg **Link Server**. Players join it, type `/link`, and get a Link Code to enter at
[mcrep.gg/sign-in](https://mcrep.gg/sign-in). That links their Minecraft account to the website.

The website trusts this server to say who a Player is, so only the Link Server holds the secret that issues Link Codes.
Participating Servers never get it.

## Requirements

- Paper 26.1 or newer, Java 25.
- Online mode. The plugin disables itself on an offline-mode server, because anyone could join with any username. Behind
  a Velocity proxy, use modern forwarding with the proxy in online mode.

## Setup

1. Put `McrepVerification-<version>.jar` in `plugins/` and start the server once.
2. Edit `plugins/McrepVerification/config.yml`:

   ```yaml
   api-url: "https://mcrep.gg"            # the website
   secret: "..."                          # the website's LINK_SERVER_SECRET
   sign-in-url: "https://mcrep.gg/sign-in"
   cooldown-seconds: 10                   # wait between /link commands
   ```

3. Restart the server.

## What Players see

- On join: "Welcome to the mcrep.gg Link Server. Type /link to get your code." (`/link` is clickable.)
- After `/link`: their code (click to copy), where to enter it, and how long it lasts (5 minutes, single use).
- If the website can't be reached: "Could not get a Link Code right now. Try again in a minute." The console says why.

## How it works

`/link` sends `POST {api-url}/api/link-codes` with `Authorization: Bearer {secret}` and
`{ "uuid": "...", "username": "..." }`. The website answers `{ "code": "ABC234", "expiresAt": "..." }`.

## Build

```bash
mvn package
```

The jar is written to `target/`. `mvn test` runs the API client tests against a stand-in website.

### Testing with a local website

Run the website with `npm run dev`, add `LINK_SERVER_SECRET=<anything>` to its `.env.local`, then set `api-url` to
`http://localhost:3000` and `secret` to the same value.
