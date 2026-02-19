FROM python:3.13-slim

WORKDIR /app

# Install custom CA certificates if present (for corporate proxies)
COPY docker/certs/ /usr/local/share/ca-certificates/custom/
RUN update-ca-certificates 2>/dev/null || true

RUN addgroup --system simbridge && adduser --system --ingroup simbridge simbridge

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py models.py auth.py ./

RUN mkdir -p /app/data && chown simbridge:simbridge /app/data

USER simbridge

ENV DB_PATH=/app/data/simbridge.db

EXPOSE 8100

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
