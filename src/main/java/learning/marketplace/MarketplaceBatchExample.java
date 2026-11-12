package learning.marketplace;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public final class MarketplaceBatchExample {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Pass one or more image paths");
            System.exit(2);
        }
        var assets = Arrays.stream(args)
                .map(Path::of)
                .map(path -> new MarketplaceImageModule.SellerAsset("course-order-1042", stableAssetId(path), path))
                .toList();
        var workers = Executors.newFixedThreadPool(Math.min(4, assets.size()));
        try {
            // infrai.image.resize and infrai.queue.publish share this one configuration and credential.
            var module = new MarketplaceImageModule(new InfraiClient(InfraiConfig.fromEnvironment()), workers);
            List<MarketplaceImageModule.BuyerUpdate> updates = assets.stream()
                    .map(module::prepare)
                    .toList()
                    .stream()
                    .map(java.util.concurrent.CompletableFuture::join)
                    .toList();
            OrderHandoff.State state = new OrderHandoff().decide(assets, updates);
            System.out.printf("order=%s assets=%d buyer_updates=%d handoff=%s%n",
                    assets.get(0).orderId(), assets.size(), updates.size(), state);
        } finally {
            workers.shutdown();
        }
    }

    private static String stableAssetId(Path path) {
        return Integer.toUnsignedString(path.toAbsolutePath().normalize().toString().hashCode(), 36);
    }
}
