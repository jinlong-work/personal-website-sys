package com.jinlong.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jinlong.web.controller.system.PublicLocationController;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class VisitorCityServiceTest {
    @Test
    void recognizesMunicipalitiesWithEmptyCityArrays() {
        assertEquals("重庆", VisitorCityService.cityFromAddress(Map.of("province", "重庆市", "city", List.of())));
        assertEquals("北京", VisitorCityService.cityFromAddress(Map.of("province", "北京市", "city", "")));
        assertEquals("上海", VisitorCityService.cityFromAddress(Map.of("province", "上海市")));
    }

    @Test
    void usesCityNamesWithoutMistakingAProvinceOrCountyForACity() {
        assertEquals("杭州", VisitorCityService.cityFromAddress(Map.of("province", "浙江省", "city", "杭州市")));
        assertEquals("文昌", VisitorCityService.cityFromAddress(Map.of("province", "海南省", "city", List.of(), "district", "文昌市")));
        assertEquals("", VisitorCityService.cityFromAddress(Map.of("province", "四川省", "city", List.of(), "district", "理县")));
        assertEquals("", VisitorCityService.cityFromAddress(Map.of("province", "浙江省", "district", "西湖区")));
        assertEquals("", VisitorCityService.cityFromAddress(Map.of("province", "广东省", "city", "珠海市", "seaArea", "南海")));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String json) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);
        return response;
    }

    @Test
    void convertsGpsBeforeReverseLookupAndOnlyReturnsTheCity() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> converted = response("{\"status\":\"1\",\"locations\":\"106.553733723959,29.607178819445\"}");
        HttpResponse<String> reversed = response("{\"status\":\"1\",\"regeocode\":{\"formatted_address\":\"private address\",\"addressComponent\":{\"province\":\"重庆市\",\"city\":[]}}}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(converted, reversed);
        VisitorCityService service = new VisitorCityService("test-key", JsonMapper.builder().build(), http);
        assertEquals("重庆", service.findCity(106.5504819, 29.6102738));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertTrue(requests.getAllValues().get(0).uri().getPath().endsWith("coordinate/convert"));
        assertTrue(requests.getAllValues().get(0).uri().getQuery().contains("coordsys=gps"));
        assertTrue(requests.getAllValues().get(0).uri().getQuery().contains("locations=106.550,29.610"));
        assertTrue(requests.getAllValues().get(1).uri().getQuery().contains("location=106.553733723959,29.607178819445"));
    }

    @Test
    void skipsTheProviderWithoutAKeyOrWithInvalidCoordinates() {
        HttpClient http = mock(HttpClient.class);
        VisitorCityService disabled = new VisitorCityService("", JsonMapper.builder().build(), http);
        assertEquals("", disabled.findCity(106, 29));
        VisitorCityService enabled = new VisitorCityService("test-key", JsonMapper.builder().build(), http);
        assertEquals("", enabled.findCity(Double.NaN, 29));
        assertEquals("", enabled.findCity(181, 29));
        assertEquals("", enabled.findCity(106, 91));
        verifyNoInteractions(http);
    }

    @Test
    void upstreamErrorsAndMalformedConversionFallBackWithoutLeakingProviderDetails() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> rejected = response("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\"}");
        HttpResponse<String> malformed = response("{\"status\":\"1\",\"locations\":[]}");
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(rejected, malformed);
        VisitorCityService service = new VisitorCityService("test-key", JsonMapper.builder().build(), http);
        assertEquals("", service.findCity(106, 29));
        assertEquals("", service.findCity(106, 29));
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void networkFailureDoesNotBreakVisitorLocation() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.<String>send(any(HttpRequest.class), any())).thenThrow(new IOException("private upstream URL"));
        assertEquals("", new VisitorCityService("test-key", JsonMapper.builder().build(), http).findCity(106, 29));
    }

    @Test
    void publicEndpointValidatesInputAndDoesNotCacheCoordinates() {
        VisitorCityService service = mock(VisitorCityService.class);
        PublicLocationController controller = new PublicLocationController(service);
        assertEquals(400, controller.city(null).getStatusCode().value());
        assertEquals(400, controller.city(new PublicLocationController.Coordinates(null, 29d)).getStatusCode().value());
        assertEquals(400, controller.city(new PublicLocationController.Coordinates(106d, 91d)).getStatusCode().value());
        verifyNoInteractions(service);
        when(service.findCity(106, 29)).thenReturn("重庆");
        var result = controller.city(new PublicLocationController.Coordinates(106d, 29d));
        assertEquals(200, result.getStatusCode().value());
        assertEquals("no-store", result.getHeaders().getCacheControl());
        assertEquals(Map.of("city", "重庆"), result.getBody().get("data"));
    }
}
