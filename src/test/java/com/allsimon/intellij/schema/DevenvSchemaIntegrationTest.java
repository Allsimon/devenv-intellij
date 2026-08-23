package com.allsimon.intellij.schema;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

/**
 * Pins what {@link DevenvSchemaFileProvider} assumes about the schema devenv publishes: that it is
 * still served at that URL, that it is a draft 2020-12 document - the version the provider declares,
 * and under which the platform reads its '$defs' - and that it describes devenv.yaml rather than some
 * other devenv file. A plugin mapping a URL that has moved would leave devenv.yaml silently
 * unvalidated.
 * <p>
 * Skips when the machine has no route to devenv.sh.
 */
public class DevenvSchemaIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    public void publishesADraft202012SchemaForDevenvYaml() {
        JsonObject schema = fetchSchema();

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema").getAsString());
        assertEquals("object", schema.get("type").getAsString());
        JsonObject properties = schema.getAsJsonObject("properties");
        assertTrue("the schema should describe devenv.yaml's top-level keys, got: " + properties.keySet(),
                properties.has("inputs") && properties.has("imports"));
    }

    private static JsonObject fetchSchema() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(DevenvSchemaFileProvider.SCHEMA_URL))
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals("devenv should still serve its schema at " + DevenvSchemaFileProvider.SCHEMA_URL,
                    200, response.statusCode());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException e) {
            assumeNoException("devenv.sh must be reachable to check the published schema", e);
            throw new AssertionError("unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
