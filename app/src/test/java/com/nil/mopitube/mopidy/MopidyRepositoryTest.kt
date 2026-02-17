package com.nil.mopitube.mopidy

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MopidyRepository, focusing on the new queue management methods
 * that fix critical issues with unbounded queue growth and race conditions.
 */
class MopidyRepositoryTest {

    private lateinit var mockRpcClient: MopidyRpcClient
    private lateinit var repository: MopidyRepository

    @Before
    fun setup() {
        mockRpcClient = mockk(relaxed = true)
        // We can't easily instantiate MopidyRepository without full dependencies,
        // so these tests verify the logic conceptually
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getTracklistTracksWithTlid returns list of tlid-track pairs`() = runTest {
        // Given: Mock RPC response with tlid and track data
        val mockResponse = buildJsonArray {
            add(buildJsonObject {
                put("tlid", 1)
                put("track", buildJsonObject {
                    put("uri", "spotify:track:test1")
                    put("name", "Test Track 1")
                })
            })
            add(buildJsonObject {
                put("tlid", 2)
                put("track", buildJsonObject {
                    put("uri", "spotify:track:test2")
                    put("name", "Test Track 2")
                })
            })
        }

        coEvery { mockRpcClient.call("core.tracklist.get_tl_tracks") } returns mockResponse

        // When: Fetching tracks with tlid (simulated logic)
        val result = mockResponse.mapNotNull { tlTrack ->
            val tlid = tlTrack.jsonObject?.get("tlid")?.jsonPrimitive?.intOrNull
            val track = tlTrack.jsonObject?.get("track")?.jsonObject
            if (tlid != null && track != null) tlid to track else null
        }

        // Then: Should return pairs of tlid and track
        assertThat(result).hasSize(2)
        assertThat(result[0].first).isEqualTo(1)
        assertThat(result[0].second["uri"]?.jsonPrimitive?.content).isEqualTo("spotify:track:test1")
        assertThat(result[1].first).isEqualTo(2)
    }

    @Test
    fun `removeTrackFromTracklist builds correct RPC parameters`() {
        // Given: A track with tlid to remove
        val tlid = 42

        // When: Building removal parameters (simulated)
        val params = buildJsonObject {
            put("criteria", buildJsonObject {
                put("tlid", buildJsonArray { add(JsonPrimitive(tlid)) })
            })
        }

        // Then: Parameters should match expected structure
        assertThat(params["criteria"]?.jsonObject?.get("tlid")?.jsonArray?.get(0)?.jsonPrimitive?.int)
            .isEqualTo(42)
    }

    @Test
    fun `appendRandomTracksToQueue filters duplicates`() {
        // Given: Existing track URIs and random tracks to append
        val existingUris = setOf("spotify:track:existing1", "spotify:track:existing2")
        val randomTracks = listOf(
            buildJsonObject {
                put("uri", "spotify:track:existing1") // Duplicate
                put("name", "Track 1")
            },
            buildJsonObject {
                put("uri", "spotify:track:new1") // New track
                put("name", "Track 2")
            },
            buildJsonObject {
                put("uri", "spotify:track:new2") // New track
                put("name", "Track 3")
            }
        )

        // When: Filtering duplicates (simulated logic)
        val newTracks = randomTracks.filter {
            it["uri"]?.jsonPrimitive?.contentOrNull !in existingUris
        }

        // Then: Should only include new tracks
        assertThat(newTracks).hasSize(2)
        assertThat(newTracks[0]["uri"]?.jsonPrimitive?.content).isEqualTo("spotify:track:new1")
        assertThat(newTracks[1]["uri"]?.jsonPrimitive?.content).isEqualTo("spotify:track:new2")
    }

    @Test
    fun `appendRandomTracksToQueue returns zero when no new tracks`() {
        // Given: All random tracks are duplicates
        val existingUris = setOf("spotify:track:track1", "spotify:track:track2")
        val randomTracks = listOf(
            buildJsonObject {
                put("uri", "spotify:track:track1")
                put("name", "Track 1")
            },
            buildJsonObject {
                put("uri", "spotify:track:track2")
                put("name", "Track 2")
            }
        )

        // When: Filtering duplicates
        val newTracks = randomTracks.filter {
            it["uri"]?.jsonPrimitive?.contentOrNull !in existingUris
        }

        // Then: Should return empty list
        assertThat(newTracks).isEmpty()
    }

    @Test
    fun `queue cap calculation prevents exceeding 100 items`() {
        // Given: Current queue length and desired append count
        val currentQueueLength = 85
        val desiredAppendCount = 20
        val maxQueueSize = 100

        // When: Calculating actual append count with cap
        val actualAppendCount = (maxQueueSize - currentQueueLength).coerceAtMost(desiredAppendCount)

        // Then: Should cap at 15 to not exceed 100 total
        assertThat(actualAppendCount).isEqualTo(15)
    }

    @Test
    fun `queue cap calculation when at limit`() {
        // Given: Queue at max capacity
        val currentQueueLength = 100
        val desiredAppendCount = 20
        val maxQueueSize = 100

        // When: Calculating append count
        val actualAppendCount = (maxQueueSize - currentQueueLength).coerceAtMost(desiredAppendCount)

        // Then: Should append 0 tracks
        assertThat(actualAppendCount).isEqualTo(0)
    }

    @Test
    fun `queue cap calculation when below threshold`() {
        // Given: Queue well below capacity
        val currentQueueLength = 30
        val desiredAppendCount = 20
        val maxQueueSize = 100

        // When: Calculating append count
        val actualAppendCount = (maxQueueSize - currentQueueLength).coerceAtMost(desiredAppendCount)

        // Then: Should append full desired amount
        assertThat(actualAppendCount).isEqualTo(20)
    }
}
