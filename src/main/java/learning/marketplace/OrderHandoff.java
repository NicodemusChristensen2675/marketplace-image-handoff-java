package learning.marketplace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrderHandoff {
    public enum State { WAITING_FOR_ASSETS, READY_FOR_BUYER }

    public State decide(List<MarketplaceImageModule.SellerAsset> expected,
                        List<MarketplaceImageModule.BuyerUpdate> completed) {
        Set<String> expectedIds = expected.stream().map(MarketplaceImageModule.SellerAsset::assetId).collect(Collectors.toSet());
        Set<String> completedIds = completed.stream().map(MarketplaceImageModule.BuyerUpdate::assetId).collect(Collectors.toSet());
        return !expectedIds.isEmpty() && completedIds.containsAll(expectedIds)
                ? State.READY_FOR_BUYER : State.WAITING_FOR_ASSETS;
    }
}
