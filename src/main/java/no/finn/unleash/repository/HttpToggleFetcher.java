package no.finn.unleash.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import no.finn.unleash.UnleashException;

public final class HttpToggleFetcher implements ToggleFetcher {
    public static final int CONNECT_TIMEOUT = 10000;
    private String etag = "";

    private final URL toggleUrl;

    public HttpToggleFetcher(URI repo) {
        try {
            toggleUrl = repo.toURL();
        } catch (MalformedURLException|IllegalArgumentException ex) {
            throw new UnleashException("Invalid unleash repository uri [" + repo.toString() + "]", ex);
        }
    }

    @Override
    public Response fetchToggles() throws UnleashException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) toggleUrl.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(CONNECT_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("If-None-Match", etag);
            connection.setUseCaches(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if(responseCode < 300) {
                return getToggleResponse(connection);
            } else {
                return new Response(Response.Status.NOT_CHANGED);
            }
        } catch (IOException e) {
            throw new UnleashException("Could not fetch toggles", e);
        } catch (IllegalStateException e) {
            throw new UnleashException(e.getMessage(), e);
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    private Response getToggleResponse(HttpURLConnection request) throws IOException {
        etag = request.getHeaderField("ETag");

        try(BufferedReader reader = new BufferedReader(
                new InputStreamReader((InputStream) request.getContent(), StandardCharsets.UTF_8))) {

            ToggleCollection toggles = JsonToggleParser.fromJson(reader);
            return new Response(Response.Status.CHANGED, toggles);
        }
    }
}
