package io.github.absketches.mitbauen.nativeapp.http;

import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

public class ResponseUtil {

    private ResponseUtil() {
    }

    public static HttpObject create(final Event<HttpObject, HttpObject> event) {
        return event.payload().createCorsResponse(null, null, null, -1, true);
    }

    public static void respondJson(final Event<HttpObject, HttpObject> event, final int statusCode, final Map<String, Object> body) {
        create(event)
            .statusCode(statusCode)
            .body(body)
            .respond(event);
    }

    public static void respondEmpty(final Event<HttpObject, HttpObject> event, final int statusCode) {
        create(event)
            .statusCode(statusCode)
            .respond(event);
    }

    public static void respondOk(final Event<HttpObject, HttpObject> event, final Map<String, Object> body) {
        respondJson(event, 200, body);
    }

    public static void respondBytes(final Event<HttpObject, HttpObject> event, final int statusCode, final String contentType, final byte[] body) {
        create(event)
            .statusCode(statusCode)
            .contentType(contentType)
            .header("Cache-Control", "private, max-age=3600")
            .body(body)
            .respond(event);
    }

    public static void respondCreated(final Event<HttpObject, HttpObject> event, final Map<String, Object> body) {
        respondJson(event, 201, body);
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 400, code);
    }

    public static void respondUnauthorized(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 401, code);
    }

    public static void respondForbidden(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 403, code);
    }

    public static void respondNotFound(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 404, code);
    }

    public static void respondConflict(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 409, code);
    }

    public static void respondTooManyRequests(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 429, code);
    }

    public static void respondServerError(final Event<HttpObject, HttpObject> event, final String code) {
        respondCode(event, 500, code);
    }

    public static void respondOptions(final Event<HttpObject, HttpObject> event) {
        create(event).respond(event);
    }

    public static void respondMethodNotAllowed(final Event<HttpObject, HttpObject> event, final String code) {
        respondJson(event, 405, Map.of("code", code, "path", event.payload().path()));
    }

    private static void respondCode(final Event<HttpObject, HttpObject> event, final int statusCode, final String code) {
        respondJson(event, statusCode, Map.of("code", code));
    }
}
