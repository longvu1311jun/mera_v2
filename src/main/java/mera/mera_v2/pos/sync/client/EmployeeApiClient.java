package mera.mera_v2.pos.sync.client;

import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.dto.EmployeeApiResponse;
import mera.mera_v2.pos.sync.exception.ApiClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class EmployeeApiClient {

    private final RestTemplate restTemplate;

    @Value("${pos.api.base-url:https://pos.pages.fm/api/v1}")
    private String baseUrl;

    @Value("${pos.api.shop-id:1546758}")
    private String shopId;

    @Value("${pos.api.api-key}")
    private String apiKey;

    public EmployeeApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch all users from POS API.
     * URL: https://pos.pages.fm/api/v1/shops/{shopId}/users?api_key=xxx
     */
    public EmployeeApiResponse fetchAllUsers() {
        String url = baseUrl + "/shops/" + shopId + "/users?api_key=" + apiKey;
        
        log.info("Fetching users from: {}", maskApiKey(url));

        try {
            ResponseEntity<EmployeeApiResponse> response = restTemplate.getForEntity(
                url, EmployeeApiResponse.class);
            
            EmployeeApiResponse body = response.getBody();
            int count = body != null && body.getData() != null ? body.getData().size() : 0;
            log.info("Fetched {} users from API", count);
            
            return body;

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("API client error {}: {}", e.getStatusCode(), body);
            throw new ApiClientException(
                "API returned client error: " + e.getStatusCode(), e,
                e.getStatusCode(), body);
        } catch (HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("API server error {}: {}", e.getStatusCode(), body);
            throw new ApiClientException(
                "API returned server error: " + e.getStatusCode(), e,
                e.getStatusCode(), body);
        } catch (ResourceAccessException e) {
            log.error("Connection error: {}", e.getMessage());
            throw new ApiClientException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new ApiClientException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private String maskApiKey(String url) {
        if (url == null) return "null";
        return url.replaceAll("api_key=[^&]*", "api_key=***");
    }
}
