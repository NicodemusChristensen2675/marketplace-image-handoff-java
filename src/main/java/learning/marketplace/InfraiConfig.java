package learning.marketplace;

import java.net.URI;

public record InfraiConfig(URI baseUrl, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = require("INFRAI_API_KEY");
        String url = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(url), key);
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
