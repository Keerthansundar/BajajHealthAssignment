package com.example.BajajHealthAssignment.service;


import com.example.BajajHealthAssignment.model.FinalQueryRequest;
import com.example.BajajHealthAssignment.model.GenerateWebhookRequest;
import com.example.BajajHealthAssignment.model.GenerateWebhookResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private static final String GENERATE_WEBHOOK_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    private static final String FINAL_SQL_QUERY = """
            WITH emp_totals AS (
                SELECT
                    e.emp_id,
                    e.first_name,
                    e.last_name,
                    e.dob,
                    e.department,
                    d.department_name,
                    SUM(p.amount) AS salary,
                    ROW_NUMBER() OVER (
                        PARTITION BY e.department
                        ORDER BY SUM(p.amount) DESC
                    ) AS rn
                FROM EMPLOYEE e
                JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID
                JOIN PAYMENTS p ON p.EMP_ID = e.EMP_ID
                WHERE EXTRACT(DAY FROM p.PAYMENT_TIME) <> 1
                GROUP BY
                    e.emp_id,
                    e.first_name,
                    e.last_name,
                    e.dob,
                    e.department,
                    d.department_name
            )
            SELECT
                department_name AS DEPARTMENT_NAME,
                salary AS SALARY,
                first_name || ' ' || last_name AS EMPLOYEE_NAME,
                DATE_PART('year', AGE(CURRENT_DATE, dob)) AS AGE
            FROM emp_totals
            WHERE rn = 1
            ORDER BY department_name;
            """;

    private final RestTemplate restTemplate;

    @Value("${assignment.name}")
    private String name;

    @Value("${assignment.regNo}")
    private String regNo;

    @Value("${assignment.email}")
    private String email;

    public AssignmentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void execute() {
        log.info("Starting HealthRx assignment for name={}, regNo={}", name, regNo);

        
        GenerateWebhookRequest requestBody = new GenerateWebhookRequest(name, regNo, email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GenerateWebhookRequest> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<GenerateWebhookResponse> response;
        try {
            response = restTemplate.exchange(
                    GENERATE_WEBHOOK_URL,
                    HttpMethod.POST,
                    entity,
                    GenerateWebhookResponse.class
            );
        } catch (Exception e) {
            log.error("Error calling generateWebhook API", e);
            return;
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Failed to generate webhook. Status: {}", response.getStatusCode());
            return;
        }

        GenerateWebhookResponse body = response.getBody();
        String webhookUrl = body.getWebhook();
        String accessToken = body.getAccessToken();

        log.info("Received webhook URL: {}", webhookUrl);
        log.info("Received access token (JWT): {}", accessToken);

        int lastTwoDigits = extractLastTwoDigits(regNo);
        if (lastTwoDigits % 2 == 0) {
            log.info("Last two digits {} are EVEN -> Question 2 ", lastTwoDigits);
        } else {
            log.info("Last two digits {} are ODD -> Question 1 ", lastTwoDigits);
        }

        log.info("Final SQL query:\n{}", FINAL_SQL_QUERY);

        FinalQueryRequest finalQueryRequest = new FinalQueryRequest(FINAL_SQL_QUERY);

        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        submitHeaders.set("Authorization", accessToken);

        HttpEntity<FinalQueryRequest> submitEntity = new HttpEntity<>(finalQueryRequest, submitHeaders);

        ResponseEntity<String> submitResponse;
        try {
            submitResponse = restTemplate.exchange(
                    webhookUrl,
                    HttpMethod.POST,
                    submitEntity,
                    String.class
            );
        } catch (Exception e) {
            log.error("Error submitting finalQuery to webhook", e);
            return;
        }

        log.info("Submit response status: {}", submitResponse.getStatusCode());
        log.info("Submit response body: {}", submitResponse.getBody());
        log.info("Assignment flow finished.");
    }

    private int extractLastTwoDigits(String regNo) {
        String digits = regNo.replaceAll("\\D+", "");
        if (digits.length() < 2) {
            throw new IllegalArgumentException("regNo must contain at least two digits: " + regNo);
        }
        String lastTwo = digits.substring(digits.length() - 2);
        return Integer.parseInt(lastTwo);
    }
}
