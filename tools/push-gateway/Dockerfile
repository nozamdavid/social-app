FROM golang:1.25-alpine AS builder
RUN apk add --no-cache gcc musl-dev git
WORKDIR /app
RUN git clone https://github.com/DracoBlue/atproto-push-gateway.git . \
    && git checkout 100fd29f03348bf69491b32f461b2738bae3113d
COPY caelum-expo.patch /tmp/caelum-expo.patch
RUN git apply /tmp/caelum-expo.patch
RUN go test ./... && CGO_ENABLED=1 go build -o /push-gateway ./cmd/server
FROM alpine:3.21
RUN apk add --no-cache ca-certificates
COPY --from=builder /push-gateway /usr/local/bin/push-gateway
ENV SQLITE_PATH=/data/push-gateway.db FCM_DATA_ONLY=true
EXPOSE 8080
CMD ["push-gateway"]
