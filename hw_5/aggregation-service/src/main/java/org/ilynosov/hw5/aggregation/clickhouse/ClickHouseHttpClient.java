package org.ilynosov.hw5.aggregation.clickhouse;

import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickHouseHttpClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ClickHouseHttpClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Long queryForLong(String sql) {
        String url = baseUrl + "?default_format=TabSeparated";
        String result = restTemplate.postForObject(url, sql, String.class);
        if (result == null || result.isBlank()) return 0L;
        return Long.parseLong(result.trim());
    }

    public Double queryForDouble(String sql) {
        String url = baseUrl + "?default_format=TabSeparated";
        String result = restTemplate.postForObject(url, sql, String.class);
        if (result == null || result.isBlank()) return 0.0;
        String trimmed = result.trim();
        if (trimmed.isEmpty() || trimmed.equals("nan") || trimmed.equals("inf")) return 0.0;
        return Double.parseDouble(trimmed);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        String url = baseUrl + "?default_format=JSONEachRow";
        String result = restTemplate.postForObject(url, sql + " FORMAT JSONEachRow", String.class);
        if (result == null || result.isBlank()) return List.of();
        return parseJsonEachRow(result);
    }

    public Map<String, Object> queryForMap(String sql) {
        List<Map<String, Object>> rows = queryForList(sql);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public void execute(String sql) {
        restTemplate.postForObject(baseUrl, sql, String.class);
    }

    private static List<Map<String, Object>> parseJsonEachRow(String raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            result.add(parseJsonObject(line));
        }
        return result;
    }

    private static Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        for (String entry : splitTopLevel(json)) {
            int colon = entry.indexOf(':');
            if (colon < 0) continue;
            String key = entry.substring(0, colon).trim().replaceAll("\"", "");
            String val = entry.substring(colon + 1).trim();
            if (val.startsWith("\"")) {
                map.put(key, val.substring(1, val.length() - 1));
            } else {
                try {
                    if (val.contains(".")) map.put(key, Double.parseDouble(val));
                    else map.put(key, Long.parseLong(val));
                } catch (NumberFormatException e) {
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            else if (!inStr && (c == '{' || c == '[')) depth++;
            else if (!inStr && (c == '}' || c == ']')) depth--;
            else if (!inStr && c == ',' && depth == 0) {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) parts.add(s.substring(start).trim());
        return parts;
    }
}
