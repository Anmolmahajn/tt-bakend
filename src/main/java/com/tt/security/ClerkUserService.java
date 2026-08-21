package com.tt.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Talks to the Clerk Backend API (api.clerk.com) to fetch full user profile
 * data by Clerk user id. There's no official Clerk Java SDK, so this is a
 * thin REST wrapper — same data @clerk/express's clerkClient.users.getUser()
 * gives you on the Node side.
 */
@Service
public class ClerkUserService {

    @Value("${clerk.secret-key}")
    private String clerkSecretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public record ClerkProfile(String clerkId, String email, String firstName, String lastName, String imageUrl) {}

    @SuppressWarnings("unchecked")
    public ClerkProfile fetchProfile(String clerkUserId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + clerkSecretKey);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "https://api.clerk.com/v1/users/" + clerkUserId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Map<String, Object> body = resp.getBody();
        if (body == null) throw new RuntimeException("Empty response from Clerk for user " + clerkUserId);

        String email = null;
        List<Map<String, Object>> emailAddresses = (List<Map<String, Object>>) body.get("email_addresses");
        if (emailAddresses != null && !emailAddresses.isEmpty()) {
            email = (String) emailAddresses.get(0).get("email_address");
        }

        String firstName = (String) body.get("first_name");
        String lastName = (String) body.get("last_name");
        String imageUrl = (String) body.get("image_url");

        return new ClerkProfile(clerkUserId, email, firstName, lastName, imageUrl);
    }
}
