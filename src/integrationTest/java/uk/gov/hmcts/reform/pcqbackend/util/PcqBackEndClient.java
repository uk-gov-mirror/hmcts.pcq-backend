package uk.gov.hmcts.reform.pcqbackend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.pcqbackend.model.PcqAnswerRequest;
import uk.gov.hmcts.reform.pcqbackend.model.PcqRecordWithoutCaseResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class PcqBackEndClient {

    private static final String APP_BASE_PATH = "/pcq/backend";

    private final int prdApiPort;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public PcqBackEndClient(int prdApiPort) {
        this.prdApiPort = prdApiPort;
        this.baseUrl = "http://localhost:" + prdApiPort + APP_BASE_PATH;
    }

    public Map<String, Object> createPcqAnswer(PcqAnswerRequest request) {
        return postRequest(baseUrl + "/submitAnswers", request);
    }

    public Map<String, Object> findAnswerByPcq(String pcqId) {
        return getRequest(APP_BASE_PATH + "/getAnswer/{pcqId}", pcqId);
    }

    public Map<String, Object> getPcqWithoutCase() {
        return getRequest(APP_BASE_PATH + "/consolidation/pcqWithoutCase");
    }

    public Map<String, Object> getPcqRecordWithoutCase() {
        return getPcqRecordRequestObject(APP_BASE_PATH + "/consolidation/pcqRecordWithoutCase");
    }

    public Map<String, Object> addCaseForPcq(String pcqId, String caseId) {
        return putRequest(APP_BASE_PATH + "/consolidation/addCaseForPCQ/" + pcqId, caseId);
    }

    @SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis"})
    private <T> Map<String, Object> postRequest(String uriPath, T requestBody) {

        HttpEntity<T> request = new HttpEntity<>(requestBody, getS2sTokenHeaders());
        ResponseEntity<Map> responseEntity = null;

        try {

            responseEntity = restTemplate.postForEntity(
                uriPath,
                request,
                Map.class);

        } catch (RestClientResponseException ex) {
            HashMap<String, Object> statusAndBody = new HashMap<>(2);
            statusAndBody.put("http_status", String.valueOf(ex.getRawStatusCode()));
            statusAndBody.put("response_body", ex.getResponseBodyAsString());
            return getResponse(statusAndBody);
        }

        return getResponse(responseEntity);
    }

    @SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis"})
    private <T> Map<String, Object> putRequest(String uriPath, Object... params) {

        HttpEntity<T> request = new HttpEntity<>(getCoRelationTokenHeaders());
        ResponseEntity<Map> responseEntity = null;
        //adding the query params to the URL
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl("http://localhost:" + prdApiPort + uriPath)
            .queryParam("caseId", params[0]);
        try {

            responseEntity = restTemplate.exchange(uriBuilder.toUriString(),
                                                     HttpMethod.PUT,
                                                     request,
                                                     Map.class);

        } catch (RestClientResponseException ex) {
            HashMap<String, Object> statusAndBody = new HashMap<>(2);
            statusAndBody.put("http_status", String.valueOf(ex.getRawStatusCode()));
            statusAndBody.put("response_body", ex.getResponseBodyAsString());
            return getResponse(statusAndBody);
        }

        return getResponse(responseEntity);
    }

    @SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis"})
    private Map<String, Object> getRequest(String uriPath, Object... params) {

        ResponseEntity<Map> responseEntity = null;

        try {
            HttpEntity<?> request = new HttpEntity<>(getCoRelationTokenHeaders());
            responseEntity = restTemplate
                .exchange("http://localhost:" + prdApiPort + uriPath,
                          HttpMethod.GET,
                          request,
                          Map.class,
                          params);
        } catch (HttpStatusCodeException ex) {
            HashMap<String, Object> statusAndBody = new HashMap<>(2);
            statusAndBody.put("http_status", String.valueOf(ex.getRawStatusCode()));
            statusAndBody.put("response_body", ex.getResponseBodyAsString());
            return statusAndBody;
        }

        return getResponse(responseEntity);
    }

    @SuppressWarnings({"PMD.DataflowAnomalyAnalysis"})
    private Map<String, Object> getPcqRecordRequestObject(String uriPath, Object... params) {

        ResponseEntity<PcqRecordWithoutCaseResponse> responseEntity = null;

        try {
            HttpEntity<?> request = new HttpEntity<>(getCoRelationTokenHeaders());
            responseEntity = restTemplate
                .exchange("http://localhost:" + prdApiPort + uriPath,
                          HttpMethod.GET,
                          request,
                          PcqRecordWithoutCaseResponse.class,
                          params);
        } catch (HttpStatusCodeException ex) {
            HashMap<String, Object> statusAndBody = new HashMap<>(2);
            statusAndBody.put("http_status", String.valueOf(ex.getRawStatusCode()));
            statusAndBody.put("response_body", ex.getResponseBodyAsString());
            return statusAndBody;
        }

        return getResponseObject(responseEntity);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getResponse(ResponseEntity<Map> responseEntity) {

        Map<String, Object> response = objectMapper
            .convertValue(
                responseEntity.getBody(),
                Map.class);

        response.put("http_status", responseEntity.getStatusCode().toString());
        response.put("headers", responseEntity.getHeaders().toString());

        return response;
    }

    @SuppressWarnings({"unchecked", "PMD.LooseCoupling"})
    private Map<String, Object> getResponse(HashMap<String, Object> responseBody) {

        Map<String, Object> response = objectMapper
            .convertValue(responseBody, Map.class);

        response.put("http_status", responseBody.get("http_status"));

        return response;
    }

    private Map<String, Object> getResponseObject(ResponseEntity<PcqRecordWithoutCaseResponse> responseEntity) {

        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("response_body", responseEntity);
        response.put("http_status", responseEntity.getStatusCode().toString());
        response.put("headers", responseEntity.getHeaders().toString());

        return response;
    }

    private HttpHeaders getS2sTokenHeaders() {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Correlation-Id", "INTEG-TEST-PCQ");
        headers.add("Authorization", "Bearer " + generateTestToken());
        return headers;
    }

    private HttpHeaders getCoRelationTokenHeaders() {

        HttpHeaders headers = new HttpHeaders();
        //headers.setContentType(APPLICATION_JSON);
        headers.add("X-Correlation-Id", "INTEG-TEST-PCQ");
        return headers;
    }

    private String generateTestToken() {
        List<String> authorities = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        authorities.add("TEST_AUTHORITY");

        return Jwts.builder()
            .setSubject("TEST")
            .claim("authorities", authorities)
            .setIssuedAt(new Date(currentTime))
            .setExpiration(new Date(currentTime + 500_000))  // in milliseconds
            .signWith(SignatureAlgorithm.HS256, "JwtSecretKey".getBytes())
            .compact();
    }
}
