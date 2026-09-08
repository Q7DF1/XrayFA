package com.android.xrayfa.shared.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/** 0 = title fully shown, 1 = title fully hidden. */
class TitleCollapseState {
    var collapsedFraction by mutableFloatStateOf(0f)
        private set
    var maxHeightPx: Float = 0f

    fun expand() {
        collapsedFraction = 0f
    }

    val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val max = maxHeightPx
                if (max <= 0f) return Offset.Zero
                val delta = available.y
                if (delta >= 0f || collapsedFraction >= 1f) return Offset.Zero
                val current = collapsedFraction * max
                val next = (current - delta).coerceIn(0f, max)
                collapsedFraction = next / max
                return Offset(0f, current - next)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val max = maxHeightPx
                if (max <= 0f) return Offset.Zero
                val delta = available.y
                if (delta <= 0f || collapsedFraction <= 0f) return Offset.Zero
                val current = collapsedFraction * max
                val next = (current - delta).coerceIn(0f, max)
                collapsedFraction = next / max
                return Offset(0f, current - next)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity.Zero
        }
}

@Composable
fun rememberTitleCollapseState(): TitleCollapseState = remember { TitleCollapseState() }
