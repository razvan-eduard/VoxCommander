package com.voxcommander.app.domain.intent.model

import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NluIntentTest {

    @Test
    fun `param retrieves value from parameters map`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.AUDIO,
            action = IntentTaxonomy.Actions.PLAY,
            parameters = mapOf(NluIntent.PARAM_ARTIST to "Smiley", NluIntent.PARAM_TRACK to "Perfect")
        )
        assertEquals("Smiley", intent.param(NluIntent.PARAM_ARTIST))
        assertEquals("Perfect", intent.param(NluIntent.PARAM_TRACK))
    }

    @Test
    fun `param returns null for missing key`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.AUDIO,
            action = IntentTaxonomy.Actions.PLAY
        )
        assertNull(intent.param(NluIntent.PARAM_ARTIST))
    }

    @Test
    fun `default confidence is 1_0`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.MAPS,
            action = IntentTaxonomy.Actions.NAVIGATE
        )
        assertEquals(1.0f, intent.confidence)
    }

    @Test
    fun `default targetApp is null`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.AUDIO,
            action = IntentTaxonomy.Actions.PLAY
        )
        assertNull(intent.targetApp)
    }

    @Test
    fun `default parameters is empty map`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.AUDIO,
            action = IntentTaxonomy.Actions.PLAY
        )
        assertEquals(emptyMap<String, String>(), intent.parameters)
    }

    @Test
    fun `intentAction and uriTemplate are null by default`() {
        val intent = NluIntent(
            domain = IntentTaxonomy.Domains.AUDIO,
            action = IntentTaxonomy.Actions.PLAY
        )
        assertNull(intent.intentAction)
        assertNull(intent.uriTemplate)
    }

    @Test
    fun `all companion param constants are unique`() {
        val params = listOf(
            NluIntent.PARAM_ARTIST,
            NluIntent.PARAM_TRACK,
            NluIntent.PARAM_ALBUM,
            NluIntent.PARAM_DESTINATION,
            NluIntent.PARAM_CONTACT,
            NluIntent.PARAM_QUERY,
            NluIntent.PARAM_MESSAGE
        )
        assertEquals(params.size, params.toSet().size)
    }
}
