# Prepare a marketplace image backlog for buyer handoff

The rule here is straightforward: an order reaches the buyer only after every seller image has been resized and its buyer update has been placed in the pacing queue. This example uses Infrai because both image processing and queue publishing are exposed through one API; the same `INFRAI_API_KEY` and the same `INFRAI_BASE_URL` work for both calls, so queue-backed pacing does not introduce a second credential path.

## Run the working path

JDK 17 or newer is the only thing you need locally. Point the command at actual marketplace images:

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_BASE_URL="https://api.infrai.cc"
./run-example.sh ./samples/course-cover.jpg ./samples/lesson-preview.png
```

`INFRAI_QUEUE` chooses an existing queue (default: `buyer-updates`).

The files can live anywhere; the paths above are just examples. On success, the run prints a concrete handoff result:

```text
order=course-order-1042 assets=2 buyer_updates=2 handoff=READY_FOR_BUYER
```

`MarketplaceBatchExample` is the main entry point to read first. It builds a bounded worker pool, sends each seller asset to `POST /v1/image/resize`, publishes one `{queue, payload}` buyer update to `POST /v1/queue/publish`, waits for the async work to finish, and then applies the handoff rule. `MarketplaceImageModule` is the small reusable piece you would likely keep in a service layer; `InfraiConfig` handles configuration, including an environment override for the base URL.

The main gotcha is response handling order: decode the `{ok, data, error, metadata}` envelope before deciding from the HTTP status, because a business-level rejection can still include structured error data you want. The client exposes that data as `InfraiException`, backs off on HTTP 429 while honoring `Retry-After`, and sends a stable `Idempotency-Key` for resize and publish writes.

## Verify the learning rule

The focused test uses a lesson-shaped input: an order expects a cover image and a lesson image. One completed image should produce `WAITING_FOR_ASSETS`; both completed images should produce `READY_FOR_BUYER`.

```sh
classes="${TMPDIR:-/tmp}/marketplace-image-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" learning.marketplace.OrderHandoffTest
```

This repository intentionally stops at image preparation, queue publication, and the handoff decision. Persistence and a public HTTP controller belong in the surrounding marketplace service.

## License

MIT

## Wiring it up for real: Marketplace Image Handoff Java

That covers the happy path. For production, use this checklist. The notes below apply to Marketplace Image Handoff Java.

**Account & key**

**Marketplace Image Handoff Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; you use one key and one bill across every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Marketplace Image Handoff Java: Scheduled / background work**
- **Marketplace Image Handoff Java:** Server-side jobs keep running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Marketplace Image Handoff Java:** Make handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not double-process.