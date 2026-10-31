package com.poultry.weather;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poultry.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeatherServiceTest {
    @Test
    void mapsLiveConditionsAndFiveDayForecast() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("latitude=-17.8")))
            .andRespond(withSuccess("""
                {
                  "current": {
                    "time": "2026-10-26T10:00",
                    "temperature_2m": 24.3,
                    "relative_humidity_2m": 48,
                    "apparent_temperature": 25.1,
                    "is_day": 1,
                    "precipitation": 0.2,
                    "weather_code": 2,
                    "wind_speed_10m": 12.4,
                    "wind_direction_10m": 135
                  },
                  "daily": {
                    "time": ["2026-10-26", "2026-10-27"],
                    "weather_code": [2, 61],
                    "temperature_2m_max": [28.2, 23.0],
                    "temperature_2m_min": [15.1, 14.0],
                    "precipitation_probability_max": [10, 75]
                  }
                }
                """, MediaType.APPLICATION_JSON));
        var service = new WeatherService(builder.build(), -17.8, 31.0, "Harare, Zimbabwe");

        var weather = service.current();

        assertEquals("Harare, Zimbabwe", weather.location());
        assertEquals(24.3, weather.current().temperatureC());
        assertEquals(48, weather.current().humidityPercent());
        assertEquals("Partly cloudy", weather.current().description());
        assertEquals(2, weather.forecast().size());
        assertEquals("Rain", weather.forecast().get(1).description());
        assertEquals(75, weather.forecast().get(1).precipitationProbabilityPercent());
        server.verify();
    }

    @Test
    void reportsProviderFailureInsteadOfReturningFakeWeather() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("api.open-meteo.com")))
            .andRespond(withServerError());
        var service = new WeatherService(builder.build(), -17.8, 31.0, "Harare, Zimbabwe");

        BusinessException error = assertThrows(BusinessException.class, service::current);

        assertEquals(503, error.status().value());
        assertTrue(error.getMessage().contains("temporarily unavailable"));
        server.verify();
    }

    @Test
    void rejectsInvalidFarmCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> new WeatherService(RestClient.create(), 91, 31, "Invalid location"));
    }
}
