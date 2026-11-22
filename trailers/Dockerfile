FROM python:3.12-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --uid 1000 --create-home trailers \
 && mkdir /cache && chown trailers /cache

# yt-dlp lives in a user-writable venv so the container can update it on start.
ENV VIRTUAL_ENV=/opt/venv PATH=/opt/venv/bin:$PATH
RUN python -m venv /opt/venv && pip install --no-cache-dir -U yt-dlp && chown -R trailers /opt/venv

COPY trailers.py entrypoint.sh /app/
USER trailers
VOLUME /cache
EXPOSE 8099
ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
