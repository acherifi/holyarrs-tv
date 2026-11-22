#!/bin/sh
# YouTube changes often and yt-dlp follows. Update it on every start unless
# TRAILERS_UPDATE=0, so a restart fixes most "trailer failed" cases.
if [ "${TRAILERS_UPDATE:-1}" != "0" ]; then
  pip install --no-cache-dir -q -U yt-dlp || echo "[trailers] yt-dlp update failed, using the installed one"
fi
exec python /app/trailers.py
