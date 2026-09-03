package com.android.xrayfa.shared.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CMP material3 (compile) and androidx material3 1.5 (Android runtime) do not
 * share SearchBar / DockedSearchBar / ModalBottomSheet ABI. Calling those APIs
 * from commonMain is a NoSuchMethodError on device — same class as
 * SharedModalBottomSheet / ExposedDropdownMenuBox.
 */
class Material3AbiGuardTest {
    @Test
    fun commonMainDoesNotCallIncompatibleSearchBarApis() {
        val dir = File("src/commonMain/kotlin")
        assertTrue("commonMain sources missing at ${dir.absolutePath}", dir.isDirectory)
        val forbidden =
            listOf(
                "androidx.compose.material3.SearchBar",
                "androidx.compose.material3.DockedSearchBar",
                "androidx.compose.material3.SearchBarDefaults",
            )
        val hits =
            dir.walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val trimmed = line.trimStart()
                        if (trimmed.startsWith("//") ||
                            trimmed.startsWith("*") ||
                            trimmed.startsWith("/*")
                        ) {
                            null
                        } else {
                            forbidden
                                .firstOrNull { token -> line.contains(token) }
                                ?.let { token ->
                                    "${file.relativeTo(dir)}:${index + 1} uses $token"
                                }
                        }
                    }
                }
                .toList()
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }
}
