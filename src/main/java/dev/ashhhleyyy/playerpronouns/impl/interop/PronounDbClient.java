package dev.ashhhleyyy.playerpronouns.impl.interop;

import dev.ashhhleyyy.playerpronouns.api.ExtraPronounProvider;
import dev.ashhhleyyy.playerpronouns.impl.PlayerPronouns;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PronounDbClient implements ExtraPronounProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(PronounDbClient.class);
    private static final Map<String, String> PRONOUNDB_ID_MAP = new HashMap<>() {{
        // short pronoun set identifier map from https://pronoundb.org/wiki/api-docs
        put("he", "he/him");
        put("it", "it/its");
        put("she", "she/her");
        put("they", "they/them");
        put("any", "any");
        put("ask", "ask");
        put("avoid", "avoid");
        put("other", "other");
    }};

    private final HttpClient client;

    public PronounDbClient() {
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public CompletableFuture<Optional<String>> provideExtras(UUID playerId) {
        try {
            URI url = new URI("https://pronoundb.org/api/v2/lookup?platform=minecraft&ids=" + playerId);
            var req = HttpRequest.newBuilder(url)
                    .header("User-Agent", PlayerPronouns.USER_AGENT)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            // Random intermediate variable to force type inference
            // (otherwise it becomes an Optional<? extends Object>)
            CompletableFuture<Optional<String>> completableFuture = client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            return Optional.empty();
                        }
                        return Optional.of(resp.body());
                    });
            return completableFuture.thenApply(b -> b.flatMap(body -> {
                        var json = JsonHelper.deserialize(body);
                        String player = playerId.toString();
                        if (json.has(player) && json.getAsJsonObject(player).getAsJsonObject("sets").has("en")) {
                            var pronounList = json.getAsJsonObject(player).getAsJsonObject("sets").getAsJsonArray("en");
                            StringBuilder pronounsBuilder = new StringBuilder();
                            if (pronounList.isEmpty()) {
                                return Optional.empty();
                            } else if (pronounList.size() == 1) {
                                pronounsBuilder.append(PRONOUNDB_ID_MAP.get(pronounList.get(0).getAsString()));
                            } else {
                                for (var pronoun : pronounList) {
                                    if (!pronounsBuilder.isEmpty()) {
                                        pronounsBuilder.append('/');
                                    }
                                    pronounsBuilder.append(pronoun.getAsString());
                                }
                            }
                            String pronouns = pronounsBuilder.toString();
                            if ("unspecified".equals(pronouns)) {
                                return Optional.empty();
                            } else {
                                return Optional.of(pronouns);
                            }
                        } else {
                            LOGGER.error("malformed response from pronoundb");
                            return Optional.empty();
                        }
                    }));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Identifier getId() {
        return PlayerPronouns.identifier("pronoundb.org");
    }

    @Override
    public Text getName() {
        return Text.literal("PronounDB");
    }

    @Override
    public boolean enabled() {
        return PlayerPronouns.config.integrations().pronounDB();
    }
}
