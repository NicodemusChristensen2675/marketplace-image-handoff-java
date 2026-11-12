package learning.marketplace;

import java.nio.file.Path;
import java.util.List;

public final class OrderHandoffTest {
    public static void main(String[] args) {
        var assets = List.of(
                new MarketplaceImageModule.SellerAsset("order-7", "cover", Path.of("cover.jpg")),
                new MarketplaceImageModule.SellerAsset("order-7", "lesson", Path.of("lesson.jpg")));
        var oneUpdate = List.of(new MarketplaceImageModule.BuyerUpdate("order-7", "cover", "img-1"));
        var bothUpdates = List.of(
                new MarketplaceImageModule.BuyerUpdate("order-7", "cover", "img-1"),
                new MarketplaceImageModule.BuyerUpdate("order-7", "lesson", "img-2"));
        var policy = new OrderHandoff();

        check(policy.decide(assets, oneUpdate) == OrderHandoff.State.WAITING_FOR_ASSETS,
                "an order with an unfinished lesson image must wait");
        check(policy.decide(assets, bothUpdates) == OrderHandoff.State.READY_FOR_BUYER,
                "an order with every seller image prepared should reach the buyer");
        System.out.println("OrderHandoffTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
