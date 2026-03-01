package com.tt.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Forces the Google account picker to show on every login attempt.
 * Without this, Google silently reuses the last signed-in account,
 * making it impossible for different users to log in on the same device.
 *
 * Also adds prompt=consent for LinkedIn to avoid cached sessions.
 */
public class ForceAccountSelectResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public ForceAccountSelectResolver(OAuth2AuthorizationRequestResolver defaultResolver) {
        this.defaultResolver = defaultResolver;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(defaultResolver.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request) {
        if (request == null) return null;

        Map<String, Object> extraParams = new LinkedHashMap<>(request.getAdditionalParameters());

        String authUri = request.getAuthorizationRequestUri();

        if (authUri != null && authUri.contains("google")) {
            // Force Google to always show the account chooser
            extraParams.put("prompt", "select_account");
        } else if (authUri != null && authUri.contains("linkedin")) {
            // Force LinkedIn to not cache the session
            extraParams.put("prompt", "login");
        }
        // GitHub always shows account picker by default — no change needed

        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(extraParams)
                .build();
    }
}
