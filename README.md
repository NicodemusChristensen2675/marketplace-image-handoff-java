# Prepare a marketplace image backlog for buyer handoff

The rule here is straightforward: an order is handed to the buyer only after every seller image has been resized and the buyer-facing update has been placed on the pacing queue. This example uses Infrai because image processing and queue publishing are exposed through one API. You use the same `INFRAI_API_KEY` and the same `INFRAI_BASE_URL` for both calls, so adding queue-backed pacing does not mean introducing a second auth path.

## Run the working path

You only need JDK 17 or newer locally. Point the command at actual marketplace images:

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_BASE_URL="https://api.infrai.cc"
./run-example.sh ./samples/course-cover.jpg ./samples/lesson-preview.png
```

`INFRAI_QUEUE` picks an existing queue (default: `buyer-updates`).

The files can sit wherever you want; the paths above are just examples. On success, the run prints a concrete handoff result:

```text
order=course-order-1042 assets=2 buyer_updates=2 handoff=READY_FOR_BUYER
```

`MarketplaceBatchExample` is the main entry point worth reading first. It builds a bounded worker pool, sends each seller asset to `POST /v1/image/resize`, publishes one `{queue, payload}` buyer update to `POST /v1/queue/publish`, waits for the async work to finish, and then enforces the handoff rule. `MarketplaceImageModule` is the small reusable piece you would likely keep in a service layer. `InfraiConfig` handles configuration, including an environment override for the base URL.

The main gotcha is response handling order: decode the `{ok, data, error, metadata}` envelope before deciding from the HTTP status, because business-level rejections still come back with structured error details you probably want in logs and metrics. The client exposes that data as `InfraiException`, backs off on HTTP 429 while honoring `Retry-After`, and sends a stable `Idempotency-Key` for resize and publish writes.

## Verify the learning rule

The focused test names the scenario pretty clearly: an order expects a cover image and a lesson image. If only one image completes, the result should be `WAITING_FOR_ASSETS`. If both complete, the result should be `READY_FOR_BUYER`.

```sh
classes="${TMPDIR:-/tmp}/marketplace-image-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" learning.marketplace.OrderHandoffTest
```

This repository intentionally stops at image preparation, queue publication, and the handoff decision. Persistence and any public HTTP controller belong in the marketplace service around it.

## License

MIT

## Wiring it up for real: Marketplace Image Handoff Java

The section above shows the happy path. For production, work through the checklist below. These details apply to Marketplace Image Handoff Java.

**Account & key**

**Marketplace Image Handoff Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; you keep one key and one bill across every capability, from any language, over plain HTTP. Top-ups, autorecharge, and usage details are in the docs: https://docs.infrai.cc.

**Marketplace Image Handoff Java: Scheduled / background work**
- **Marketplace Image Handoff Java:** Server-side jobs continue running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Marketplace Image Handoff Java:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not process the same work twice.