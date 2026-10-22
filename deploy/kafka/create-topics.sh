#!/usr/bin/env bash
# Cria o tópico de eventos e a DLT (03 §4, §7.2). Idempotente: pode rodar a cada "compose up".
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:29092}"
TOPICS=/opt/kafka/bin/kafka-topics.sh
PARTITIONS=3                     # chave = comercianteId; ordenação por comerciante
DLT_RETENTION_MS=2592000000      # 30 dias: DLT preservada para reprocessamento (03 §7.2)

"$TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
  --topic cashflow.lancamentos.v1 --partitions "$PARTITIONS" --replication-factor 1

"$TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
  --topic cashflow.lancamentos.v1.DLT --partitions "$PARTITIONS" --replication-factor 1 \
  --config retention.ms="$DLT_RETENTION_MS"

"$TOPICS" --bootstrap-server "$BOOTSTRAP" --describe --topic 'cashflow\.lancamentos\.v1.*'
