package com.example;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.HttpEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BulkFileUploader {

    private static final String DEFAULT_API_URL = "http://localhost:8090/apiAuthorization/vendor-attachment-upload";
    private static final String DEFAULT_CSV_PATH = "C://VATTaxAttachments/Result_91.csv";

    public static void main(String[] args) throws Exception {
        String csvPath = args.length > 0 ? args[0] : DEFAULT_CSV_PATH;
        String apiUrl = args.length > 1 ? args[1] : DEFAULT_API_URL;

        Path csvFile = Paths.get(csvPath);
        if (!Files.exists(csvFile) || !Files.isRegularFile(csvFile)) {
            System.out.println("CSV file not found: " + csvFile.toAbsolutePath());
            return;
        }

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        if (lines.size() <= 1) {
            System.out.println("CSV is empty or only has header.");
            return;
        }

        int total = lines.size() - 1;
        int success = 0;
        int failed = 0;
        int count = 0;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            for (int i = 1; i < lines.size(); i++) {
                count++;
                String line = lines.get(i);
                List<String> parts = parseCsvLine(line);

                if (parts.size() < 3) {
                    failed++;
                    System.out.printf("[%d/%d] INVALID CSV ROW: %s%n", count, total, line);
                    continue;
                }

                try {
                    Long refId = Long.parseLong(parts.get(0).trim());
                    String type = parts.get(1).trim();
                    String filePathStr = parts.get(2).trim();

                    Path filePath = Paths.get(filePathStr);
                    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                        failed++;
                        System.out.printf("[%d/%d] FILE NOT FOUND: %s%n", count, total, filePath);
                        continue;
                    }

                    boolean uploaded = uploadSingleFile(httpClient, apiUrl, filePath, type, refId);
                    if (uploaded) {
                        success++;
                        System.out.printf("[%d/%d] SUCCESS: %s%n", count, total, filePath);
                    } else {
                        failed++;
                        System.out.printf("[%d/%d] FAILED: %s%n", count, total, filePath);
                    }
                } catch (NumberFormatException e) {
                    failed++;
                    System.out.printf("[%d/%d] INVALID REF ID: %s%n", count, total, line);
                } catch (Exception e) {
                    failed++;
                    System.out.printf("[%d/%d] ERROR: %s -> %s%n", count, total, line, e.getMessage());
                }
            }
        }

        System.out.println("Finished.");
        System.out.println("Success: " + success);
        System.out.println("Failed : " + failed);
    }

    private static boolean uploadSingleFile(
            CloseableHttpClient httpClient,
            String apiUrl,
            Path filePath,
            String type,
            Long refId) throws IOException {

        HttpPost post = new HttpPost(apiUrl);

        HttpEntity entity = MultipartEntityBuilder.create()
                .addBinaryBody("file", filePath.toFile())
                .addTextBody("type", type)
                .addTextBody("refId", String.valueOf(refId))
                .build();

        post.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null
                    ? readResponseBody(response.getEntity())
                    : "";

            System.out.println("Server response: " + statusCode + " - " + responseBody);
            return statusCode >= 200 && statusCode < 300;
        }
    }

    private static String readResponseBody(HttpEntity entity) throws IOException {
        try (InputStream inputStream = entity.getContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        values.add(current.toString());
        return values;
    }
}
