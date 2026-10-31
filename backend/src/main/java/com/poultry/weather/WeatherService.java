package com.poultry.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.poultry.service.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastResponse(Current current, Daily daily) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Current(String time, Double temperature_2m, Double relative_humidity_2m,
            Double apparent_temperature, Integer is_day, Double precipitation, Integer weather_code,
            Double wind_speed_10m, Integer wind_direction_10m) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(List<String> time, List<Integer> weather_code, List<Double> temperature_2m_max,
            List<Double> temperature_2m_min, List<Integer> precipitation_probability_max) {}

    public record CurrentConditions(String time, double temperatureC, double feelsLikeC, int humidityPercent,
            double precipitationMm, double windKph, int windDirectionDegrees, boolean daylight, int weatherCode,
            String description) {}
    public record ForecastDay(String date, int weatherCode, String description, double highC, double lowC,
            int precipitationProbabilityPercent) {}
    public record WeatherSnapshot(String location, Instant fetchedAt, CurrentConditions current,
            List<ForecastDay> forecast) {}

    private final RestClient client;
    private final double latitude;
    private final double longitude;
    private final String location;

    @Autowired
    public WeatherService(RestClient.Builder builder,
            @Value("${app.weather.latitude:-17.8252}") double latitude,
            @Value("${app.weather.longitude:31.0335}") double longitude,
            @Value("${app.weather.location:Harare, Zimbabwe}") String location) {
        this(createClient(builder), latitude, longitude, location);
    }

    WeatherService(RestClient client, double latitude, double longitude, String location) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Weather latitude and longitude must be valid coordinates");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.location = location;
        this.client = client;
    }

    private static RestClient createClient(RestClient.Builder builder) {
        var httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(6));
        return builder.requestFactory(requestFactory).build();
    }

    public WeatherSnapshot current() {
        ForecastResponse response;
        try {
            response = client.get()
                .uri(uri -> uri.scheme("https").host("api.open-meteo.com").path("/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                    .queryParam("forecast_days", 5)
                    .queryParam("timezone", "Africa/Harare")
                    .queryParam("temperature_unit", "celsius")
                    .queryParam("wind_speed_unit", "kmh")
                    .queryParam("precipitation_unit", "mm")
                    .build())
                .retrieve()
                .body(ForecastResponse.class);
        } catch (RestClientException e) {
            log.warn("Unable to retrieve live weather for configured farm location {}", location, e);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Live weather is temporarily unavailable. Please try again shortly.");
        }

        if (response == null || response.current() == null || response.daily() == null) {
            log.warn("Weather provider returned an incomplete forecast for configured farm location {}", location);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Live weather is temporarily unavailable. Please try again shortly.");
        }
        Current c = response.current();
        Daily d = response.daily();
        if (c.time() == null || c.temperature_2m() == null || c.relative_humidity_2m() == null
                || c.apparent_temperature() == null || c.is_day() == null || c.precipitation() == null
                || c.weather_code() == null || c.wind_speed_10m() == null || c.wind_direction_10m() == null
                || d.time() == null || d.weather_code() == null || d.temperature_2m_max() == null
                || d.temperature_2m_min() == null || d.precipitation_probability_max() == null) {
            log.warn("Weather provider omitted required forecast values for configured farm location {}", location);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "Live weather is temporarily unavailable. Please try again shortly.");
        }

        List<ForecastDay> days = new ArrayList<>();
        for (int i = 0; i < d.time().size(); i++) {
            if (i >= d.weather_code().size() || i >= d.temperature_2m_max().size()
                    || i >= d.temperature_2m_min().size() || i >= d.precipitation_probability_max().size()
                    || d.weather_code().get(i) == null || d.temperature_2m_max().get(i) == null
                    || d.temperature_2m_min().get(i) == null || d.precipitation_probability_max().get(i) == null) {
                log.warn("Weather provider returned an incomplete daily forecast for configured farm location {}", location);
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live weather is temporarily unavailable. Please try again shortly.");
            }
            int code = d.weather_code().get(i);
            days.add(new ForecastDay(d.time().get(i), code, description(code), d.temperature_2m_max().get(i),
                d.temperature_2m_min().get(i), d.precipitation_probability_max().get(i)));
        }

        return new WeatherSnapshot(location, Instant.now(),
            new CurrentConditions(c.time(), c.temperature_2m(), c.apparent_temperature(),
                c.relative_humidity_2m().intValue(), c.precipitation(), c.wind_speed_10m(),
                c.wind_direction_10m(), c.is_day() == 1, c.weather_code(), description(c.weather_code())),
            List.copyOf(days));
    }

    static String description(int code) {
        if (code == 0) return "Clear sky";
        if (code <= 3) return switch (code) {
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            default -> "Overcast";
        };
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Unknown conditions";
    }
}
