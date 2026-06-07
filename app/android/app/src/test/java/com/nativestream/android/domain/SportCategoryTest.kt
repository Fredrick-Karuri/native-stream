// app/src/test/java/com/nativestream/android/domain/SportCategoryTest.kt
//
// SportCategory EPG keywords

package com.nativestream.android.domain

import com.nativestream.android.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportCategoryTest {

    @Test
    fun `allKeywords contains no duplicates`() {
        val all = SportCategory.allKeywords
        val distinct = all.distinct()
        assertEquals("Duplicate keywords found: ${all - distinct.toSet()}", distinct.size, all.size)
    }

    @Test
    fun `FOOTBALL epgKeywords contains premier league`() {
        assertTrue(SportCategory.FOOTBALL.epgKeywords.contains("premier league"))
    }

    @Test
    fun `GOLF epgKeywords contains pga tour live`() {
        assertTrue(SportCategory.GOLF.epgKeywords.contains("pga tour live"))
    }

    @Test
    fun `each category keywords are all lowercase`() {
        SportCategory.entries.forEach { category ->
            category.epgKeywords.forEach { keyword ->
                assertEquals(
                    "${category.name} keyword '$keyword' is not lowercase",
                    keyword.lowercase(),
                    keyword,
                )
            }
        }
    }
}