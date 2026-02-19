# SimBridge Container Deployment

## Prerequisites

- Docker 20.10+

## Build

From the repository root:

```bash
docker build -f docker/Dockerfile -t simbridge .
```

## Run

```bash
docker run -d \
  --name simbridge \
  -p 8100:8100 \
  -e JWT_SECRET="$(openssl rand -hex 32)" \
  -v simbridge-data:/app/data \
  simbridge
```

This starts the server on port 8100 with a named volume for the SQLite database.

### Environment Variables

| Variable     | Required | Default              | Description                          |
|------------- |----------|----------------------|--------------------------------------|
| `JWT_SECRET` | Yes      | `change-me`          | Signing key for JWT tokens           |
| `DB_PATH`    | No       | `/app/data/simbridge.db` | SQLite database file path        |

### Verify

```bash
curl http://localhost:8100/docs
```

## Docker Compose

Create a `docker-compose.yml` in the repository root:

```yaml
services:
  simbridge:
    build:
      context: .
      dockerfile: docker/Dockerfile
    ports:
      - "8100:8100"
    environment:
      JWT_SECRET: "${JWT_SECRET}"
    volumes:
      - simbridge-data:/app/data
    restart: unless-stopped

volumes:
  simbridge-data:
```

Then run:

```bash
# Set the secret (once)
echo 'JWT_SECRET=your-random-secret-here' > .env

docker compose up -d
```

## Data Persistence

The SQLite database is stored at `/app/data/simbridge.db` inside the container. Mount a volume to `/app/data` to persist data across container restarts. The examples above use a named volume (`simbridge-data`). To use a host directory instead:

```bash
docker run -d \
  --name simbridge \
  -p 8100:8100 \
  -e JWT_SECRET="your-secret" \
  -v /path/on/host:/app/data \
  simbridge
```

## Logs

```bash
docker logs simbridge
docker logs -f simbridge   # follow
```

## Stop / Remove

```bash
docker stop simbridge
docker rm simbridge
```

To also remove the database volume:

```bash
docker volume rm simbridge-data
```

## Rebuild After Code Changes

```bash
docker build -f docker/Dockerfile -t simbridge .
docker stop simbridge && docker rm simbridge
# re-run with the same docker run command above
```

Or with Compose:

```bash
docker compose up -d --build
```
