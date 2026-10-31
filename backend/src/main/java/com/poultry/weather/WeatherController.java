package com.poultry.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private final WeatherService weather;

    public WeatherController(WeatherService weather) {
        this.weather = weather;
    }

    @GetMapping
    public WeatherService.WeatherSnapshot current() {
        return weather.current();
    }
}
