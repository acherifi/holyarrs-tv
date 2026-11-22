# HolyArrs trailers

A small service that lets the Apple TV app play trailers in the app, muted behind
the title page like Netflix, without opening YouTube.

HolyArrs already returns each title's TMDB videos, which are YouTube ids. tvOS
can't play YouTube inside an app, so this service downloads each trailer once
with [yt-dlp](https://github.com/yt-dlp/yt-dlp), caches it as an H.264/AAC MP4,
and serves it to the app.

It runs **next to HolyArrs and doesn't change it**: no patch to keep up to date,
and you update HolyArrs as usual.

## Run it

```sh
cd trailers
docker compose up -d --build
curl http://localhost:8099/health      # {"ok": true, "yt_dlp": "..."}
```

Then on the Apple TV: **Settings → Trailers → Trailers address** =
`http://<host>:8099`. The Apple TV must be able to reach that address.

Without Docker: `pip install yt-dlp`, install ffmpeg, then
`TRAILERS_DIR=./cache python trailers.py`.

## How it behaves

- The first time a title page is opened, the app asks for its trailer. The service
  starts the download and answers `pending`. The app checks again every 3 s and
  starts the trailer when it's ready. This takes a few seconds; after that it's instant.
- Each trailer is deleted 4 hours after it was last played (`TRAILERS_TTL_HOURS`).
  The service checks every 10 minutes. Opening the title again downloads it again.
- The cache is also capped (`TRAILERS_MAX_GB`, 10 GB by default). When it's full, the
  trailers watched least recently are deleted first.
- A failed id isn't retried for 6 hours.
- On every start the container updates yt-dlp, so when YouTube changes something
  and trailers stop working, restart the container (`TRAILERS_UPDATE=0` turns this off).

## Settings

| Variable | Default | |
|---|---|---|
| `TRAILERS_PORT` | `8099` | |
| `TRAILERS_DIR` | `/cache` | where the MP4s go |
| `TRAILERS_MAX_GB` | `10` | cache size |
| `TRAILERS_HEIGHT` | `1080` | highest resolution downloaded |
| `TRAILERS_TOKEN` | empty | if set, the app must send it (Settings → Trailers → Token) |
| `TRAILERS_COOKIES` | empty | a cookies.txt path, only if YouTube asks yt-dlp to sign in (common from datacenter IPs, rare at home) |
| `TRAILERS_WORKERS` | `2` | downloads at the same time |
| `TRAILERS_TTL_HOURS` | `4` | delete a trailer this long after its last play (`0`: never) |

## API

| Request | Answer |
|---|---|
| `GET /health` | `{"ok": true, "yt_dlp": "<version>"}` |
| `GET /trailers/<youtube id>` | `{"state":"ready","url":"/trailers/<id>.mp4"}`, `{"state":"pending"}` or `{"state":"failed","error":"..."}` |
| `GET /trailers/<youtube id>.mp4` | the video, with Range support |

Only 11-character YouTube ids are accepted. With a token, send it as `X-Api-Key`
or `?token=`.
