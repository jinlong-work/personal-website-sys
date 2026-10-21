package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.web.service.VisitorCityService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicLocationController {
    private final VisitorCityService cities;
    public PublicLocationController(VisitorCityService cities) { this.cities = cities; }

    public record Coordinates(Double longitude, Double latitude) { }

    @PostMapping("/public/location/city")
    public ResponseEntity<AjaxResult> city(@RequestBody(required = false) Coordinates coordinates) {
        if (coordinates == null || coordinates.longitude() == null || coordinates.latitude() == null
                || !VisitorCityService.validCoordinates(coordinates.longitude(), coordinates.latitude())) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(AjaxResult.error(400, "位置参数无效"));
        }
        String city = cities.findCity(coordinates.longitude(), coordinates.latitude());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AjaxResult.success(Map.of("city", city)));
    }
}
