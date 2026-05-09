package io.github.absketches.mitbauen.nativeapp.shell;

import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class AppShellUtil {

    private static final String FRONTEND_RESOURCE_ROOT = "frontend/";
    private static final String INDEX_RESOURCE = FRONTEND_RESOURCE_ROOT + "index.html";
    private static final String API_PREFIX = "/api";
    private static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";
    private static final String FRONTEND_BUNDLE_MISSING_CODE = "FRONTEND_BUNDLE_MISSING";
    private static final String STATIC_ASSET_NOT_FOUND_CODE = "STATIC_ASSET_NOT_FOUND";

    public sealed interface RoutesMatch permits AssetRoute, NoMatch {
    }

    public record AssetRoute(String resourcePath, String contentType, boolean spaShell) implements RoutesMatch {
    }

    public record NoMatch() implements RoutesMatch {
    }

    private AppShellUtil() {
    }

    public static RoutesMatch match(final HttpObject request) {
        final String path = request.uri().getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return new AssetRoute(INDEX_RESOURCE, ContentType.TEXT_HTML.value(), true);
        }
        if (!path.startsWith("/")) {
            return new NoMatch();
        }
        if (path.equals(API_PREFIX) || path.startsWith(API_PREFIX + "/")) {
            return new NoMatch();
        }

        final String resourcePath = FRONTEND_RESOURCE_ROOT + stripLeadingSlash(path);
        if (resourceExists(resourcePath)) {
            return new AssetRoute(resourcePath, contentType(path), false);
        }

        if (!looksLikeAsset(path)) {
            return new AssetRoute(INDEX_RESOURCE, ContentType.TEXT_HTML.value(), true);
        }
        return new NoMatch();
    }

    public static void respondAsset(final Event<HttpObject, HttpObject> event, final AssetRoute route) {
        readResource(route.resourcePath())
            .ifPresentOrElse(
                bytes -> event.payload().createResponse()
                    .statusCode(200)
                    .contentType(route.contentType())
                    .header("Cache-Control", route.spaShell() ? "no-cache" : "public, max-age=31536000, immutable")
                    .body(bytes)
                    .respond(event),
                () -> respondMissingFrontend(event, route)
            );
    }

    public static void respondOptions(final Event<HttpObject, HttpObject> event) {
        event.payload().createResponse().respond(event);
    }

    public static void respondMethodNotAllowed(final Event<HttpObject, HttpObject> event) {
        event.payload().createResponse()
            .statusCode(405)
            .body(Map.of("code", METHOD_NOT_ALLOWED_CODE, "path", event.payload().path()))
            .respond(event);
    }

    private static void respondMissingFrontend(final Event<HttpObject, HttpObject> event, final AssetRoute route) {
        final int statusCode = route.spaShell() ? 503 : 404;
        final String code = route.spaShell() ? FRONTEND_BUNDLE_MISSING_CODE : STATIC_ASSET_NOT_FOUND_CODE;
        event.payload().createResponse()
            .statusCode(statusCode)
            .body(Map.of("code", code))
            .respond(event);
    }

    private static boolean resourceExists(final String resourcePath) {
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) != null;
    }

    private static Optional<byte[]> readResource(final String resourcePath) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read frontend resource " + resourcePath, exception);
        }
    }

    private static String stripLeadingSlash(final String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static boolean looksLikeAsset(final String path) {
        final int lastSlash = path.lastIndexOf('/');
        final int lastDot = path.lastIndexOf('.');
        return lastDot > lastSlash;
    }

    private static String contentType(final String path) {
        final String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".html")) {
            return ContentType.TEXT_HTML.value();
        }
        if (lowerPath.endsWith(".css")) {
            return ContentType.TEXT_CSS.value();
        }
        if (lowerPath.endsWith(".js") || lowerPath.endsWith(".mjs")) {
            return ContentType.APPLICATION_JAVASCRIPT.value();
        }
        if (lowerPath.endsWith(".json") || lowerPath.endsWith(".map")) {
            return ContentType.APPLICATION_JSON.value();
        }
        if (lowerPath.endsWith(".svg")) {
            return ContentType.IMAGE_SVG.value();
        }
        if (lowerPath.endsWith(".png")) {
            return ContentType.IMAGE_PNG.value();
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return ContentType.IMAGE_JPEG.value();
        }
        if (lowerPath.endsWith(".gif")) {
            return ContentType.IMAGE_GIF.value();
        }
        if (lowerPath.endsWith(".webp")) {
            return ContentType.IMAGE_WEBP.value();
        }
        if (lowerPath.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lowerPath.endsWith(".txt")) {
            return ContentType.TEXT_PLAIN.value();
        }
        if (lowerPath.endsWith(".woff")) {
            return "font/woff";
        }
        if (lowerPath.endsWith(".woff2")) {
            return "font/woff2";
        }
        return ContentType.APPLICATION_OCTET_STREAM.value();
    }
}
