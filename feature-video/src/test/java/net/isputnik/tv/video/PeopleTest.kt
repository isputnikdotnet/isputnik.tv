package net.isputnik.tv.video

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the engine (0.26+) says of a title's people and parts, and of a person, as this app reads it. */
class PeopleTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun `a film in parts plays the part this person got to, and names its parts as episodes`() {
        val movie = json.decodeFromString<Movie>(
            """{"id":"m1","title":"Д'Артаньян и три мушкетёра","playId":"p2",
               "parts":[{"id":"m1","part":1,"title":"Атос, Портос, Арамис и Д'Артаньян","still":"/s1.jpg","progress":{"position":3600,"duration":3600,"watched":true}},
                        {"id":"p2","part":2,"title":"Подвески королевы","progress":{"position":600,"duration":3600}},
                        {"id":"p3","part":3}],
               "cast":[{"id":"t8","name":"Михаил Боярский","role":"Д'Артаньян","photo":"/video/api/people/t8/photo?v=1"}],
               "crew":[{"id":"t5","name":"Георгий Юнгвальд-Хилькевич","role":"director"}]}"""
        )
        assertEquals("p2", movie.playId)
        assertEquals(listOf(1, 2, 3), movie.parts.map { it.part })
        assertEquals("Подвески королевы", movie.parts[1].title)
        assertNull(movie.parts[2].title)
        assertTrue(movie.parts[0].progress?.watched == true)
        assertEquals("Д'Артаньян", movie.cast.single().role)
        assertEquals("director", movie.crew.single().role)
    }

    @Test fun `an engine before people and parts still reads, with none of either`() {
        val movie = json.decodeFromString<Movie>("""{"id":"m1","title":"Old"}""")
        assertNull(movie.playId)
        assertTrue(movie.parts.isEmpty() && movie.cast.isEmpty() && movie.crew.isEmpty())
        val show = json.decodeFromString<Show>("""{"id":"s1","title":"Old Show"}""")
        assertTrue(show.cast.isEmpty() && show.crew.isEmpty())
    }

    @Test fun `a person with their titles, and people found by name`() {
        val person = json.decodeFromString<Person>(
            """{"id":"t8","name":"Михаил Боярский","tmdbId":"8","photo":null,
               "titles":[{"kind":"movies","id":"m1","title":"Д'Артаньян","year":1979,"poster":null,"characters":["Д'Артаньян"],"jobs":[],"actor":true},
                         {"kind":"shows","id":"s1","title":"A Show","year":1990,"characters":[],"jobs":["director"],"actor":false}]}"""
        )
        assertEquals(listOf("movies", "shows"), person.titles.map { it.kind })
        assertEquals(listOf("director"), person.titles[1].jobs)
        val found = json.decodeFromString<PeopleList>("""{"people":[{"id":"t8","name":"Михаил Боярский","photo":null,"titles":2}]}""")
        assertEquals(2, found.people.single().titles)
    }

    @Test fun `initials stand in for a missing picture`() {
        assertEquals("МБ", initials("Михаил Боярский"))
        assertEquals("C", initials("Cher"))
        assertEquals("JK", initials("J. K. Simmons"))
    }
}
