package com.akira.island.service

import java.net.URLEncoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherRepoTest {
    @Test fun dayAndNightSymbolsFollowTheSelectedCity() {
        fun parse(day: String) = WeatherRepo.parse(
            """{"current":{"temperature_2m":12,"weather_code":0$day}}""", "Kyiv", 1L).isDay
        assertEquals(true, parse(""","is_day":1"""))
        assertEquals(false, parse(""","is_day":0"""))
        assertEquals(null, parse(""))
    }

    @Test fun parsesCurrentWeatherWithTimestamp() {
        val result = WeatherRepo.parse("""{"current":{"temperature_2m":-12.5,"weather_code":73}}""", "Astana", 123L)
        assertEquals("Astana", result.city)
        assertEquals(-12.5, result.temperature, 0.001)
        assertEquals(73, result.code)
        assertEquals(123L, result.updatedAt)
    }
    @Test(expected = Exception::class) fun missingTemperatureIsNotShownAsZero() {
        WeatherRepo.parse("""{"current":{"weather_code":0}}""", "Paris", 1L)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsImpossibleTemperature() {
        WeatherRepo.parse("""{"current":{"temperature_2m":999,"weather_code":0}}""", "Paris", 1L)
    }

    @Test fun findsLocalizedCityWithEnglishAppLanguage() = runBlocking {
        val results = searchWeatherCities(" Москва ", "en") { url ->
            assertTrue(url.contains("name=" + URLEncoder.encode("Москва", "UTF-8")))
            assertTrue(url.contains("&language=ru&"))
            """{"results":[{"name":"Москва","admin1":"Москва","country":"Россия","latitude":55.75,"longitude":37.62}]}"""
        }
        assertEquals(listOf(WeatherCity("Москва, Россия", 55.75, 37.62)), results)
    }

    @Test fun searchesUkrainianSpellingOnLocalizedIndex() = runBlocking {
        val results = searchWeatherCities("Київ", "ru") { url ->
            assertTrue(url.contains("&language=uk&"))
            """{"results":[{"name":"Київ","latitude":50.45,"longitude":30.52}]}"""
        }
        assertEquals("Київ", results.single().name)
    }

    @Test fun retriesInEnglishAfterAnEmptyLocalizedResponse() = runBlocking {
        val queries = mutableListOf<String>()
        val results = searchWeatherCities("London", "ru") { url ->
            queries += url
            if (url.contains("&language=ru&")) """{"generationtime_ms":0.13}"""
            else """{"results":[{"name":"London","latitude":51.51,"longitude":-0.13}]}"""
        }
        assertEquals(2, queries.size)
        assertTrue(queries.last().contains("&language=en&"))
        assertEquals("London", results.single().name)
    }

    @Test fun returnsNoCitiesOnAValidEmptyResponse() = runBlocking {
        assertTrue(searchWeatherCities("xyzxyz", "en") { """{"generationtime_ms":0.2}""" }.isEmpty())
    }

    @Test(expected = IllegalStateException::class) fun doesNotTreatProviderErrorAsNoResults() {
        runBlocking { searchWeatherCities("London", "en") { """{"error":true,"reason":"Unavailable"}""" } }
    }
}
