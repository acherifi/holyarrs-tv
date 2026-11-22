"""HolyArrs trailers: a sidecar that turns a YouTube trailer id into an MP4 the
Apple TV can play in the app.

HolyArrs already sends each title's TMDB videos (YouTube ids). tvOS can't play
YouTube in an app, so this service downloads the trailer once with yt-dlp, keeps
it in a size-capped cache, and serves the file with HTTP Range support (what
AVPlayer needs). It runs next to HolyArrs and doesn't touch it, so HolyArrs
updates never conflict with it.

  GET /health                  -> {"ok": true, "yt_dlp": "<version>"}
  GET /trailers/<key>          -> {"state": "ready", "url": "/trailers/<key>.mp4"}
                                  {"state": "pending"}  (download started; poll again)
                                  {"state": "failed", "error": "..."}
  GET /trailers/<key>.mp4      -> the video (Range requests supported)

Settings (environment):
  TRAILERS_PORT      8099
  TRAILERS_DIR       /cache
  TRAILERS_MAX_GB    10      cache size; oldest-watched files go first
  TRAILERS_HEIGHT    1080    highest resolution to download
  TRAILERS_TOKEN     ""      if set, required as X-Api-Key header or ?token=
  TRAILERS_COOKIES   ""      path to a cookies.txt for yt-dlp, if YouTube asks to sign in
  TRAILERS_WORKERS   2       downloads at the same time
  TRAILERS_TTL_HOURS 4       delete a trailer this long after it was last played (0: never)
"""

import json
import os
import re
import shutil
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("TRAILERS_PORT", "8099"))
DIR = os.environ.get("TRAILERS_DIR", "/cache")
MAX_BYTES = int(float(os.environ.get("TRAILERS_MAX_GB", "10")) * 1024**3)
HEIGHT = int(os.environ.get("TRAILERS_HEIGHT", "1080"))
TOKEN = os.environ.get("TRAILERS_TOKEN", "")
COOKIES = os.environ.get("TRAILERS_COOKIES", "")
WORKERS = int(os.environ.get("TRAILERS_WORKERS", "2"))
TTL_HOURS = float(os.environ.get("TRAILERS_TTL_HOURS", "4"))

KEY = re.compile(r"^[A-Za-z0-9_-]{11}$")  # YouTube ids only: nothing else reaches yt-dlp
FAIL_RETRY = 6 * 3600  # don't retry a failed id for 6 hours

pool = ThreadPoolExecutor(max_workers=WORKERS)
lock = threading.Lock()
pending: set[str] = set()
failed: dict[str, tuple[float, str]] = {}


def path(key: str) -> str:
    return os.path.join(DIR, key + ".mp4")


def download(key: str) -> None:
    tmp = os.path.join(DIR, "tmp", key)
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)
    # H.264 + AAC in MP4 plays everywhere on tvOS; faststart lets playback begin at once.
    fmt = (f"bv*[height<={HEIGHT}][vcodec^=avc1]+ba[ext=m4a]/"
           f"b[height<={HEIGHT}][ext=mp4]/bv*[height<={HEIGHT}]+ba/b")
    cmd = ["yt-dlp", "--no-playlist", "--no-progress", "-f", fmt,
           "--merge-output-format", "mp4", "--remux-video", "mp4",
           "--postprocessor-args", "ffmpeg:-movflags +faststart",
           "-o", os.path.join(tmp, "v.%(ext)s"), "https://www.youtube.com/watch?v=" + key]
    if COOKIES:
        cmd[1:1] = ["--cookies", COOKIES]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        out = os.path.join(tmp, "v.mp4")
        if r.returncode != 0 or not os.path.exists(out):
            msg = (r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1]
            with lock:
                failed[key] = (time.time(), msg[:300])
            print(f"[trailers] {key} failed: {msg}", flush=True)
            return
        os.replace(out, path(key))
        print(f"[trailers] {key} ready ({os.path.getsize(path(key)) // 1024**2} MB)", flush=True)
        prune()
    except Exception as e:  # noqa: BLE001 - report any failure to the client
        with lock:
            failed[key] = (time.time(), str(e)[:300])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
        with lock:
            pending.discard(key)


def prune() -> None:
    """Delete the least recently watched files until the cache fits."""
    files = [os.path.join(DIR, f) for f in os.listdir(DIR) if f.endswith(".mp4")]
    files.sort(key=lambda f: os.stat(f).st_atime)
    total = sum(os.path.getsize(f) for f in files)
    for f in files:
        if total <= MAX_BYTES:
            break
        total -= os.path.getsize(f)
        os.remove(f)


def expire_loop() -> None:
    """Delete trailers not played for TTL_HOURS. Checks every 10 minutes."""
    while True:
        cutoff = time.time() - TTL_HOURS * 3600
        for f in os.listdir(DIR):
            p = os.path.join(DIR, f)
            try:
                # mtime is set when the file is downloaded and on every play (os.utime).
                if f.endswith(".mp4") and os.stat(p).st_mtime < cutoff:
                    os.remove(p)
                    print(f"[trailers] {f[:-4]} expired", flush=True)
            except FileNotFoundError:
                pass
        time.sleep(600)


def state(key: str) -> dict:
    if os.path.exists(path(key)):
        return {"state": "ready", "url": f"/trailers/{key}.mp4"}
    with lock:
        if key in pending:
            return {"state": "pending"}
        if key in failed and time.time() - failed[key][0] < FAIL_RETRY:
            return {"state": "failed", "error": failed[key][1]}
        failed.pop(key, None)
        pending.add(key)
    pool.submit(download, key)
    return {"state": "pending"}


class Handler(BaseHTTPRequestHandler):
    server_version = "holyarrs-trailers/1"

    def log_message(self, fmt, *args):  # quieter than the default
        pass

    def send_json(self, code: int, body: dict) -> None:
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self, query: dict) -> bool:
        if not TOKEN:
            return True
        return self.headers.get("X-Api-Key") == TOKEN or query.get("token", [""])[0] == TOKEN

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head: bool = False):
        url = urlparse(self.path)
        query = parse_qs(url.query)
        if url.path == "/health":
            v = subprocess.run(["yt-dlp", "--version"], capture_output=True, text=True).stdout.strip()
            return self.send_json(200, {"ok": True, "yt_dlp": v})
        if not self.authorized(query):
            return self.send_json(401, {"error": "Missing or wrong token."})
        m = re.fullmatch(r"/trailers/([^/]+?)(\.mp4)?", url.path)
        if not m or not KEY.match(m.group(1)):
            return self.send_json(404, {"error": "Not found."})
        key, file = m.group(1), m.group(2)
        if not file:
            return self.send_json(200, state(key))
        self.send_file(path(key), head)

    def send_file(self, p: str, head: bool) -> None:
        if not os.path.exists(p):
            return self.send_json(404, {"error": "Not downloaded yet."})
        size = os.path.getsize(p)
        start, end = 0, size - 1
        rng = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        if rng and (rng.group(1) or rng.group(2)):
            if rng.group(1):
                start = int(rng.group(1))
                end = int(rng.group(2)) if rng.group(2) else end
            else:  # suffix range: the last N bytes
                start = max(0, size - int(rng.group(2)))
            end = min(end, size - 1)
            if start > end:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        length = end - start + 1
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        self.send_header("Cache-Control", "public, max-age=86400")
        self.end_headers()
        if head:
            return
        os.utime(p)  # marks it as recently watched for pruning
        try:
            with open(p, "rb") as f:
                f.seek(start)
                while length > 0:
                    chunk = f.read(min(1 << 20, length))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    length -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass  # AVPlayer often drops a request and asks for another range


if __name__ == "__main__":
    os.makedirs(DIR, exist_ok=True)
    shutil.rmtree(os.path.join(DIR, "tmp"), ignore_errors=True)
    if TTL_HOURS > 0:
        threading.Thread(target=expire_loop, daemon=True).start()
    print(f"[trailers] listening on :{PORT}, cache {DIR} ({MAX_BYTES // 1024**3} GB max, "
          f"deleted {TTL_HOURS:g} h after last play)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
