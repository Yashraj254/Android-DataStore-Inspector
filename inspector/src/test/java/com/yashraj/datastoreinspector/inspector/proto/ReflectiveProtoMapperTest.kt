package com.yashraj.datastoreinspector.inspector.proto

import com.google.gson.Gson
import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import com.yashraj.datastoreinspector.inspector.testproto.Achievement
import com.yashraj.datastoreinspector.inspector.testproto.Choice
import com.yashraj.datastoreinspector.inspector.testproto.Envelope
import com.yashraj.datastoreinspector.inspector.testproto.Collisions
import com.yashraj.datastoreinspector.inspector.testproto.Counters
import com.yashraj.datastoreinspector.inspector.testproto.Level
import com.yashraj.datastoreinspector.inspector.testproto.Measurements
import com.yashraj.datastoreinspector.inspector.testproto.Reading
import com.yashraj.datastoreinspector.inspector.testproto.Rank
import com.yashraj.datastoreinspector.inspector.testproto.Reward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectiveProtoMapperTest {

    private val mapper = ReflectiveProtoMapper<Reward>()
    private val gson = Gson()

    private fun sample(): Reward = Reward.newBuilder()
        .setLanguage(5)
        .setApprovalPercentage(87.5f)
        .setLevel(Level.GOLD)
        .setRank(Rank.newBuilder().setPosition(1).setTitle("Diamond").build())
        .addAchievements(Achievement.newBuilder().setName("First").setPoints(10).setUnlocked(true).build())
        .addAchievements(Achievement.newBuilder().setName("Streak").setPoints(25).setUnlocked(false).build())
        .addTags("a").addTags("b")
        .build()

    private fun List<ProtoEntry>.byKey(key: String): ProtoEntry =
        first { it.key == key }

    // ---- Reading -------------------------------------------------------------------------------

    @Test
    fun scalarsAndEnumAreFlat() {
        val entries = mapper.toEntries(sample())
        assertEquals("5", entries.byKey("language").value)
        assertEquals("Int", entries.byKey("language").type)
        assertEquals("87.5", entries.byKey("approval_percentage").value)
        assertEquals("Float", entries.byKey("approval_percentage").type)
        assertEquals("GOLD", entries.byKey("level").value)
        assertEquals("Enum", entries.byKey("level").type)
    }

    @Test
    fun nestedMessageIsJson() {
        val rank = mapper.toEntries(sample()).byKey("rank")
        assertEquals("Message", rank.type)
        val obj = gson.fromJson(rank.value, Map::class.java)
        assertEquals(1.0, obj["position"])
        assertEquals("Diamond", obj["title"])
    }

    @Test
    fun repeatedMessageIsJsonArrayWithTemplate() {
        val achievements = mapper.toEntries(sample()).byKey("achievements")
        assertEquals("List", achievements.type)
        val arr = gson.fromJson(achievements.value, List::class.java)
        assertEquals(2, arr.size)
        assertEquals("First", (arr[0] as Map<*, *>)["name"])
        // Template describes a blank element so the UI can render an "Add" form.
        assertNotNull(achievements.template)
        val tpl = gson.fromJson(achievements.template, Map::class.java)
        assertEquals("", tpl["name"])
        assertEquals(0.0, tpl["points"])
        assertEquals(false, tpl["unlocked"])
    }

    @Test
    fun repeatedScalarIsJsonArray() {
        val tags = mapper.toEntries(sample()).byKey("tags")
        assertEquals("List", tags.type)
        assertEquals(listOf("a", "b"), gson.fromJson(tags.value, List::class.java))
    }

    // ---- Writing -------------------------------------------------------------------------------

    @Test
    fun updateScalar() {
        val updated = mapper.updateField(sample(), "language", "9")
        assertEquals(9, updated.language)
    }

    @Test
    fun updateEnumByName() {
        val updated = mapper.updateField(sample(), "level", "SILVER")
        assertEquals(Level.SILVER, updated.level)
    }

    @Test
    fun updateNestedMessage() {
        val updated = mapper.updateField(sample(), "rank", """{"position":3,"title":"Master"}""")
        assertEquals(3, updated.rank.position)
        assertEquals("Master", updated.rank.title)
    }

    @Test
    fun addRepeatedElement() {
        val json = """[
            {"name":"First","points":10,"unlocked":true},
            {"name":"Streak","points":25,"unlocked":false},
            {"name":"New","points":99,"unlocked":true}
        ]"""
        val updated = mapper.updateField(sample(), "achievements", json)
        assertEquals(3, updated.achievementsCount)
        assertEquals("New", updated.getAchievements(2).name)
        assertEquals(99, updated.getAchievements(2).points)
    }

    @Test
    fun removeRepeatedElement() {
        val updated = mapper.updateField(sample(), "achievements", """[{"name":"Only","points":1,"unlocked":false}]""")
        assertEquals(1, updated.achievementsCount)
        assertEquals("Only", updated.getAchievements(0).name)
    }

    @Test
    fun updateRepeatedScalar() {
        val updated = mapper.updateField(sample(), "tags", """["x","y","z"]""")
        assertEquals(listOf("x", "y", "z"), updated.tagsList)
    }

    @Test
    fun emptyRepeatedRoundTrips() {
        val updated = mapper.updateField(sample(), "achievements", "[]")
        assertEquals(0, updated.achievementsCount)
    }

    // ---- Validation ----------------------------------------------------------------------------

    @Test
    fun rejectsNonNumericScalar() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "language", "abc")
        }
    }

    @Test
    fun rejectsUnknownField() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "does_not_exist", "1")
        }
    }

    @Test
    fun rejectsInvalidEnum() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "level", "PLATINUM")
        }
    }

    @Test
    fun invalidEnumErrorListsOnlySettableValues() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "level", "PLATINUM")
        }
        assertEquals("\"PLATINUM\" is not a valid Level (one of: BRONZE, SILVER, GOLD)", e.message)
    }

    @Test
    fun rejectsUnrecognizedEnumWithClearMessage() {
        // UNRECOGNIZED is generated for unknown wire values; protobuf refuses to set it.
        val e = assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "level", "UNRECOGNIZED")
        }
        assertEquals("\"UNRECOGNIZED\" is not a valid Level (one of: BRONZE, SILVER, GOLD)", e.message)
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "rank", "{not valid json")
        }
    }

    @Test
    fun rejectsUnknownNestedField() {
        assertThrows(IllegalArgumentException::class.java) {
            mapper.updateField(sample(), "rank", """{"bogus":1}""")
        }
    }

    @Test
    fun noTemplateForScalars() {
        assertNull(mapper.toEntries(sample()).byKey("language").template)
    }

    // ---- Schema discovery ----------------------------------------------------------------------

    @Test
    fun fieldsNamedLikeGeneratedHelpersAreNotSkipped() {
        val keys = ReflectiveProtoMapper<Counters>()
            .toEntries(Counters.getDefaultInstance())
            .map { it.key }
            .toSet()
        assertEquals(
            setOf("retry_count", "total_bytes", "test_case", "scores", "items", "note", "code"),
            keys
        )
    }

    @Test
    fun fieldsNamedWithHelperSuffixesAreDiscoveredExactlyOnce() {
        val keys = ReflectiveProtoMapper<Collisions>()
            .toEntries(Collisions.getDefaultInstance())
            .map { it.key }
        assertEquals(
            listOf("a_b_c", "page", "price", "price_value", "site_map", "todo_list"),
            keys.sorted()
        )
    }

    @Test
    fun fieldsNamedWithHelperSuffixesAreWritable() {
        val mapper = ReflectiveProtoMapper<Collisions>()
        var proto = Collisions.getDefaultInstance()
        proto = mapper.updateField(proto, "price_value", "42")
        proto = mapper.updateField(proto, "page", """{"name":"home"}""")
        proto = mapper.updateField(proto, "a_b_c", "7")
        assertEquals(42, proto.priceValue)
        assertEquals("home", proto.page.name)
        assertEquals(7, proto.getABC())
    }

    // ---- Round-tripping nested messages --------------------------------------------------------

    /** Re-submits a nested message field's JSON unchanged, as the UI does when saving it. */
    private fun roundTrip(proto: Envelope, key: String): Envelope {
        val mapper = ReflectiveProtoMapper<Envelope>()
        val json = mapper.toEntries(proto).byKey(key).value
        return mapper.updateField(proto, key, json)
    }

    @Test
    fun nestedOneofKeepsItsSelectedCase() {
        for (choice in listOf(
            Choice.newBuilder().setNote("hi").build(),
            Choice.newBuilder().setCode(7).build(),
            Choice.getDefaultInstance(),
        )) {
            val proto = Envelope.newBuilder().setChoice(choice).build()
            assertEquals(choice, roundTrip(proto, "choice").choice)
        }
    }

    @Test
    fun nestedMessageWithMapCanBeSaved() {
        val proto = Envelope.newBuilder()
            .setCounters(Counters.newBuilder().setRetryCount(3).putScores("a", 1))
            .build()
        assertEquals(proto.counters, roundTrip(proto, "counters").counters)
    }

    @Test
    fun nestedOneofRejectsMoreThanOneMember() {
        assertThrows(IllegalArgumentException::class.java) {
            ReflectiveProtoMapper<Envelope>()
                .updateField(Envelope.getDefaultInstance(), "choice", """{"note":"a","code":1}""")
        }
    }

    // ---- Maps ----------------------------------------------------------------------------------

    @Test
    fun mapFieldIsWritable() {
        val updated = ReflectiveProtoMapper<Counters>()
            .updateField(Counters.newBuilder().putScores("old", 1).build(), "scores", """{"x":5,"y":"6"}""")
        assertEquals(mapOf("x" to 5, "y" to 6), updated.scoresMap)
    }

    @Test
    fun mapFieldRejectsBadValue() {
        assertThrows(IllegalArgumentException::class.java) {
            ReflectiveProtoMapper<Counters>()
                .updateField(Counters.getDefaultInstance(), "scores", """{"x":"not a number"}""")
        }
    }

    // ---- Non-finite floats ---------------------------------------------------------------------

    private fun measurements(): Measurements = Measurements.newBuilder()
        .addSamples(Float.NaN).addSamples(Float.POSITIVE_INFINITY).addSamples(1.5f)
        .setReading(Reading.newBuilder().setValue(Double.NEGATIVE_INFINITY))
        .build()

    @Test
    fun nonFiniteFloatsInNestedValuesAreReadAsStrictJson() {
        val entries = ReflectiveProtoMapper<Measurements>().toEntries(measurements())
        // Strict JSON (what the browser's JSON.parse accepts) has no NaN/Infinity literals.
        assertEquals("[\"NaN\",\"Infinity\",1.5]", entries.byKey("samples").value)
        assertEquals("{\"value\":\"-Infinity\"}", entries.byKey("reading").value)
    }

    @Test
    fun nonFiniteFloatsSurviveASave() {
        val mapper = ReflectiveProtoMapper<Measurements>()
        var proto = measurements()
        for (key in listOf("samples", "reading")) {
            proto = mapper.updateField(proto, key, mapper.toEntries(proto).byKey(key).value)
        }
        assertTrue(proto.getSamples(0).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, proto.getSamples(1))
        assertEquals(1.5f, proto.getSamples(2))
        assertEquals(Double.NEGATIVE_INFINITY, proto.reading.value, 0.0)
    }
}
