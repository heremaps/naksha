package com.here.naksha.app.service.http.auth.actions;

import io.vertx.core.json.JsonObject;
import org.jetbrains.annotations.NotNull;

public class JwtUtil {

    /**
     * Static reference to JSON property within decoded JWT needed for authorization.
     */
    public static final String APP_ID = "appId";
    /**{@link JwtUtil#APP_ID readDocHere}*/
    public static final String USER_ID = "userId";
    /**{@link JwtUtil#APP_ID readDocHere}*/
    public static final String NAKSHA = "naksha";
    /**{@link JwtUtil#APP_ID readDocHere}*/
    public static final String URM = "urm";

    public static @NotNull JsonObject getUrmFromJwt(JsonObject decodedJwt) {
        if (decodedJwt.containsKey(NAKSHA)) {
            JsonObject naksha = decodedJwt.getJsonObject(NAKSHA);
            if (naksha.containsKey(URM)) {
                return naksha.getJsonObject(URM);
            }
        }
        return new JsonObject();
    }
}
