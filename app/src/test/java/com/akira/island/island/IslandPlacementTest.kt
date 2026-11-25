package com.akira.island.island

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandPlacementTest {
    @Test fun expandingOverTheBubbleKeepsItsAnchorOnEitherSide() {
        for (left in listOf(false, true)) {
            val compact = islandPlacement(400, 128, 128, 32, 38, 12, 0, left)
            for (w in listOf(130, 168, 234, 380, 420, 365)) {
                val frame = islandPlacement(400, w, 128, 32, 38, 12, 0, left)
                assertEquals(compact.bubbleX, frame.bubbleX)
                // kapsul bergeser setengah slot (38/2) menjauhi lingkaran
                assertEquals(if (left) 219 else 181, frame.bodyX + w / 2)
            }
        }
    }

    @Test fun offsetLeavesRoomForTheBubbleAndNegativeSpringOvershootIsClamped() {
        val right = islandPlacement(400, 120, 120, 32, 38, 12, 400, false)
        assertEquals(388, right.bubbleX + 32)
        val left = islandPlacement(400, 120, 120, 32, 38, 12, -400, true)
        assertEquals(12, left.bubbleX)
        assertEquals(islandPlacement(400, 360, 120, 32, 0, 12, 0, true),
            islandPlacement(400, 360, 120, 32, -7, 12, 0, true))
    }

    @Test fun capsuleAndBubbleAreCenteredTogetherAndReturnWhenBubbleLeaves() {
        for (left in listOf(false, true)) {
            val p = islandPlacement(400, 128, 128, 32, 38, 12, 0, left)
            val groupLeft = minOf(p.bodyX, p.bubbleX)
            val groupRight = maxOf(p.bodyX + 128, p.bubbleX + 32)
            assertEquals(200, (groupLeft + groupRight) / 2)
            val gone = islandPlacement(400, 128, 128, 32, 0, 12, 0, left)
            assertEquals(200, gone.bodyX + 64)
        }
    }
}
