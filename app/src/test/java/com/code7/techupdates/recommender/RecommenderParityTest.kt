package com.code7.techupdates.recommender

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the port against fixtures produced by running the web app's own JavaScript
 * (`tools/parity/gen-recommender-fixtures.js`).
 */
class RecommenderParityTest {
    @Test fun scenarioA_opensOnly() = check("A")

    @Test fun scenarioB_mixedSignalsAndImpressions() = check("B")

    @Test fun scenarioC_empty() = check("C")

    private fun check(name: String) {
        val fixture = load(name)
        val items = fixture.array("stories").map { it.jsonObject.toItem() }.associateBy { it.id }

        items.values.forEach { item ->
            val expected = fixture.obj("features").getValue(item.id).strings()
            assertEquals("features ${item.id}", expected, featuresOf(item.title, item.url))
        }

        val model = Recommender.train(events(fixture, items), impressions(fixture, items))
        checkModel(fixture.obj("model"), model)

        items.values.forEach { item ->
            val expected = fixture.obj("scores").getValue(item.id).jsonObject
            val actual = model.score(item)
            assertEquals("affinity ${item.id}", expected.number("affinity"), actual.affinity, TOLERANCE)
            assertEquals("why ${item.id}", expected.getValue("why").strings(), actual.why)
        }
        assertEquals(fixture.getValue("interests").strings(), model.interests(Model.DEFAULT_INTERESTS))
    }

    private fun checkModel(
        expected: JsonObject,
        model: Model,
    ) {
        val weights = expected.array("w").map { it.jsonArray }
        assertEquals("weight keys, in order", weights.map { it[0].string() }, model.weights.keys.toList())
        weights.forEach { (key, value) ->
            val actual = model.weights.getValue(key.string())
            assertEquals("w ${key.string()}", value.jsonPrimitive.double, actual, TOLERANCE)
        }
        assertEquals("b", expected.number("b"), model.bias, TOLERANCE)
        assertEquals("n", expected.getValue("n").jsonPrimitive.int, model.examples)
        val support = expected.array("support").map { it.jsonArray }
        assertEquals(
            "support, in order",
            support.map { it[0].string() to it[1].jsonPrimitive.int },
            model.support.toList(),
        )
    }

    private fun events(
        fixture: JsonObject,
        items: Map<String, Item>,
    ) = fixture.array("events").map { it.jsonObject }.map {
        Event(
            kind = Signal.valueOf(it.getValue("kind").string().uppercase()),
            item = items.getValue(it.getValue("id").string()),
            at = it.getValue("at").jsonPrimitive.long,
        )
    }

    private fun impressions(
        fixture: JsonObject,
        items: Map<String, Item>,
    ) = fixture.array("impressions").map { it.jsonObject }.map {
        Seen(
            item = items.getValue(it.getValue("id").string()),
            n = it.getValue("n").jsonPrimitive.int,
            at = it.getValue("at").jsonPrimitive.long,
        )
    }

    private fun load(name: String): JsonObject {
        val path = "/recommender/scenario_$name.json"
        val text = checkNotNull(javaClass.getResource(path)) { "missing $path" }.readText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.toItem() =
        Item(
            id = getValue("id").string(),
            title = getValue("title").string(),
            url = getValue("url").takeUnless { it == JsonNull }?.string(),
        )

    private fun JsonObject.array(key: String) = getValue(key).jsonArray

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.double

    private fun JsonElement.string() = jsonPrimitive.content

    private fun JsonElement.strings() = jsonArray.map { it.string() }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
