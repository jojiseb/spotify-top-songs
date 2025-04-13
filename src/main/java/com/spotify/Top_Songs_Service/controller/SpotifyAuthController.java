package com.spotify.Top_Songs_Service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.Top_Songs_Service.SpotifyTokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class SpotifyAuthController {

    @Value("${spotify.client-id}")
    private String clientId;

    @Value("${spotify.client-secret}")
    private String clientSecret;

    @Value("${spotify.redirect-uri}")
    private String redirectUri;

    @GetMapping("/callback")
    public ResponseEntity<String> handleSpotifyCallback(@RequestParam("code") String code) {
        try {
            System.out.println("Received code: " + code);
            System.out.println("clientId = " + clientId);
            System.out.println("clientSecret = " + clientSecret);
            System.out.println("redirectUri = " + redirectUri);

            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64Utils.encodeToString(auth.getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://accounts.spotify.com/api/token",
                    request,
                    String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response.getBody());

            SpotifyTokenHolder.accessToken = jsonNode.get("access_token").asText();
            SpotifyTokenHolder.refreshToken = jsonNode.get("refresh_token").asText();

            log.info("Access token saved successfully:\n"+SpotifyTokenHolder.accessToken);
            return ResponseEntity.ok("✅ Tokens received: \n" + response.getBody());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Something went wrong: " + e.getMessage());
        }
    }

    @GetMapping("/spotify")
    public ResponseEntity<Map<String, Object>> getSpotifyData() {
        String accessToken = SpotifyTokenHolder.accessToken;

        if(accessToken == null || accessToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Access token is missing. Please authenticate first."));
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        Map<String, Object> responseData = new HashMap<>();

        try {
            ResponseEntity<String> topTracksResponse = restTemplate.exchange("https://api.spotify.com/v1/me/top/tracks?limit=10",
                    HttpMethod.GET,
                    request,
                    String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode topTracks = mapper.readTree(topTracksResponse.getBody());
            responseData.put("topTracks", topTracks);

            ResponseEntity<String> nowPlayingResponse = restTemplate.exchange(
                    "https://api.spotify.com/v1/me/player/currently-playing",
                    HttpMethod.GET,
                    request,
                    String.class
            );
            String nowPlayingBody = nowPlayingResponse.getBody();
            if (nowPlayingBody != null && !nowPlayingBody.isEmpty()) {
                JsonNode nowPlaying = mapper.readTree(nowPlayingBody);
                responseData.put("currentlyPlaying", nowPlaying);
            } else {
                responseData.put("currentlyPlaying", "No song is currently playing.");
            }

            return ResponseEntity.ok(responseData);

        }
        catch (Exception e)
        {
            e.printStackTrace();
            responseData.put("error", "Something went wrong: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseData);
        }
    }

    @PostMapping("/spotify/play")
    public ResponseEntity<String> playTrack(@RequestParam("trackUri") String trackUri) {
        String token = SpotifyTokenHolder.accessToken;
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access token is missing.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 🔥 JSON body like: {"uris": ["spotify:track:..."]}
        Map<String, Object> body = new HashMap<>();
        body.put("uris", Collections.singletonList(trackUri));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.exchange(
                    "https://api.spotify.com/v1/me/player/play",
                    HttpMethod.PUT,
                    request,
                    String.class
            );
            return ResponseEntity.ok("▶️ Track is playing.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to play: " + e.getMessage());
        }
    }


    @PostMapping("/spotify/pause")
    public ResponseEntity<String> pausePlayback() {
        String token = SpotifyTokenHolder.accessToken;
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access token is missing.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.exchange(
                    "https://api.spotify.com/v1/me/player/pause",
                    HttpMethod.PUT,
                    request,
                    String.class
            );
            return ResponseEntity.ok("⏸️ Playback paused.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to pause: " + e.getMessage());
        }
    }








}
