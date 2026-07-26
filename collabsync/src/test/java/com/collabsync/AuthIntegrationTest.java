package com.collabsync;

import com.collabsync.dto.auth.AuthResponse;
import com.collabsync.dto.auth.LoginRequest;
import com.collabsync.dto.auth.RegisterRequest;
import com.collabsync.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_whenValidRequest_returnsAuthResponse() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .displayName("Test User")
                .build();

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
        assertThat(response.getBody().getRefreshToken()).isNotEmpty();
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getDisplayName()).isEqualTo("Test User");

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }

    @Test
    void register_whenDuplicateEmail_returnsBadRequest() {
        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("password123")
                .displayName("Test User")
                .build();

        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_whenValidCredentials_returnsAuthResponse() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("login@example.com")
                .password("password123")
                .displayName("Login User")
                .build();
        restTemplate.postForEntity("/api/auth/register", registerRequest, AuthResponse.class);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("login@example.com")
                .password("password123")
                .build();

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/login", loginRequest, AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
    }

    @Test
    void login_whenInvalidPassword_returnsUnauthorized() {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("wrongpassword")
                .build();

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/login", loginRequest, AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_whenValidRefreshToken_returnsNewTokens() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("refresh@example.com")
                .password("password123")
                .displayName("Refresh User")
                .build();
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register", registerRequest, AuthResponse.class
        );

        String refreshToken = registerResponse.getBody().getRefreshToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AuthResponse> response = restTemplate.exchange(
                "/api/auth/refresh", HttpMethod.POST, entity, AuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
        assertThat(response.getBody().getRefreshToken()).isNotEqualTo(refreshToken);
    }
}