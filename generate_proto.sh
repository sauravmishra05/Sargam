#!/bin/bash
# Generate Kotlin protobuf files for Android

set -e

PROTO_DIR="./Metrolist-Full/metproto"
OUT_DIR="app/src/main/java"

if [ ! -f "$PROTO_DIR/listentogether.proto" ]; then
    echo "Missing proto file at $PROTO_DIR/listentogether.proto"
    exit 1
fi

mkdir -p "$OUT_DIR"

protoc --java_out=lite:"$OUT_DIR" --kotlin_out="$OUT_DIR" \
    -I="$PROTO_DIR" \
    "$PROTO_DIR/listentogether.proto"

echo "Protobuf files generated successfully in $OUT_DIR"