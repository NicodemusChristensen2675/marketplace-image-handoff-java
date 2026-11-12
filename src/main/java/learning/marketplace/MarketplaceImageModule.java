package learning.marketplace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class MarketplaceImageModule {
    private final InfraiClient infrai;
    private final Executor executor;

    public MarketplaceImageModule(InfraiClient infrai, Executor executor) {
        this.infrai = infrai;
        this.executor = executor;
    }

    public CompletableFuture<BuyerUpdate> prepare(SellerAsset asset) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] body = resizeBody(asset.image());
            Map<String, Object> resized = infrai.post(
                    "/v1/image/resize",
                    "application/json",
                    body,
                    "resize-" + asset.assetId());
            String payload = "{\"queue\":" + Json.quote(System.getenv().getOrDefault("INFRAI_QUEUE", "buyer-updates"))
                    + ",\"payload\":{\"asset\":" + Json.quote(asset.assetId())
                    + ",\"order\":" + Json.quote(asset.orderId()) + ",\"state\":\"READY\"}}";
            infrai.post(
                    "/v1/queue/publish",
                    "application/json",
                    payload.getBytes(StandardCharsets.UTF_8),
                    "buyer-update-" + asset.assetId());
            return new BuyerUpdate(asset.orderId(), asset.assetId(), String.valueOf(resized.get("image_id")));
        }, executor);
    }

    private static byte[] resizeBody(Path image) {
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(image));
            String body = "{\"image\":{\"base64\":" + Json.quote(base64)
                    + "},\"width\":1200,\"height\":1200,\"fit\":\"contain\",\"enlarge\":false,"
                    + "\"format\":\"webp\",\"store\":true}";
            return body.getBytes(StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read seller image " + image, e);
        }
    }

    public record SellerAsset(String orderId, String assetId, Path image) {}
    public record BuyerUpdate(String orderId, String assetId, String imageId) {}
}
