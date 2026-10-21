package com.jinlong.web.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class VisitorCityService {
    private static final String API = "https://restapi.amap.com/v3/";
    private static final Set<String> MUNICIPALITIES = Set.of("北京市", "上海市", "天津市", "重庆市");
    private final String key;
    private final JsonMapper json;
    private final HttpClient http;

    @Autowired
    public VisitorCityService(@Value("${AMAP_WEB_SERVICE_KEY:${app.geolocation.amap-key:}}") String key,
            JsonMapper json) {
        this(key, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    VisitorCityService(String key, JsonMapper json, HttpClient http) {
        this.key = key == null ? "" : key.trim();
        this.json = json;
        this.http = http;
    }

    public String findCity(double longitude, double latitude) {
        if (key.isBlank() || !validCoordinates(longitude, latitude)) return "";
        try {
            // Browser coordinates are WGS84; Amap reverse geocoding expects Amap coordinates.
            String coordinates = String.format(Locale.ROOT, "%.3f,%.3f", longitude, latitude);
            Map<?, ?> conversion = get("assistant/coordinate/convert",
                    Map.of("locations", coordinates, "coordsys", "gps"));
            if (!successful(conversion)) return "";
            String converted = text(conversion.get("locations"));
            if (!validLocationString(converted)) return "";
            Map<?, ?> result = get("geocode/regeo", Map.of("location", converted, "extensions", "base"));
            if (!successful(result) || !(result.get("regeocode") instanceof Map<?, ?> geocode)
                    || !(geocode.get("addressComponent") instanceof Map<?, ?> address)) return "";
            return cityFromAddress(address);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException | RuntimeException exception) {
            // Never expose upstream URLs, keys, or detailed addresses in responses or logs.
            return "";
        }
    }

    private Map<?, ?> get(String path, Map<String, String> parameters) throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder("key=").append(encode(key)).append("&output=JSON");
        parameters.forEach((name, value) -> query.append('&').append(name).append('=').append(encode(value)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path + "?" + query))
                .timeout(Duration.ofSeconds(3)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) return Map.of();
        return json.readValue(response.body(), Map.class);
    }

    static String cityFromAddress(Map<?, ?> address) {
        if (!text(address.get("seaArea")).isBlank()) return "";
        String province = text(address.get("province"));
        if (MUNICIPALITIES.contains(province)) return province.substring(0, province.length() - 1);
        String city = text(address.get("city"));
        if (city.endsWith("市") && city.length() > 1) return city.substring(0, city.length() - 1);
        // A province-administered county-level city can have an empty city field.
        String district = text(address.get("district"));
        if (district.endsWith("市") && district.length() > 1) return district.substring(0, district.length() - 1);
        return "";
    }

    public static boolean validCoordinates(double longitude, double latitude) {
        return Double.isFinite(longitude) && Double.isFinite(latitude)
                && longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90;
    }

    private static boolean validLocationString(String value) {
        if (!value.matches("-?\\d{1,3}(?:\\.\\d{1,16})?,-?\\d{1,2}(?:\\.\\d{1,16})?")) return false;
        String[] parts = value.split(",");
        return validCoordinates(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }

    private static boolean successful(Map<?, ?> result) { return "1".equals(String.valueOf(result.get("status"))); }
    private static String text(Object value) { return value instanceof String string ? string.trim() : ""; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
