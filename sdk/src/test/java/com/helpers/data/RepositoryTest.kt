package com.helpers.data

import com.helpers.data.model.Bundle
import com.helpers.data.model.Item
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RepositoryTest {
    private lateinit var repository: Repository
    private lateinit var testBundle: Bundle

    @BeforeEach
    fun setup() {
        testBundle = Bundle(
            iso = "en",
            items = listOf(
                Item("key1", type = "TEXT", value = "value1"),
                Item("key2_one", type = "TEXT", value = "one item"),
                Item("key2_many", type = "TEXT", value = "many items"),
                Item("array1", type = "ARRAY", valueArray = listOf("item1", "item2", "item3"))
            )
        )
        repository = Repository(testBundle)
    }

    @Test
    fun `test getText returns correct string`() {
        val result = repository.getText("key1")
        assert(result == "value1")
    }

    @Test
    fun `test getText returns null for non-existent key`() {
        val result = repository.getText("non_existent")
        assert(result == null)
    }

    @Test
    fun `test getPlural returns correct string for quantity`() {
        val oneResult = repository.getPlural("key2", "one")
        val manyResult = repository.getPlural("key2", "many")
        
        assert(oneResult == "one item")
        assert(manyResult == "many items")
    }

    @Test
    fun `test getTextArray returns correct array`() {
        val result = repository.getTextArray("array1")
        
        assert(result?.size == 3)
        assert(result?.get(0) == "item1")
        assert(result?.get(1) == "item2")
        assert(result?.get(2) == "item3")
    }

    @Test
    fun `test getTextArray returns null for non-existent key`() {
        val result = repository.getTextArray("non_existent")
        assert(result == null)
    }
}