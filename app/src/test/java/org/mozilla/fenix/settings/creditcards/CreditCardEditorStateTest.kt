/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.creditcards

import mozilla.components.concept.storage.CreditCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.settings.creditcards.CreditCardEditorFragment.Companion.NUMBER_OF_YEARS_TO_SHOW
import java.util.Calendar

class CreditCardEditorStateTest {

    private val creditCard = CreditCard(
        guid = "id",
        billingName = "Banana Apple",
        cardNumber = "4111111111111110",
        expiryMonth = 5,
        expiryYear = 2030,
        cardType = "amex",
        timeCreated = 1L,
        timeLastUsed = 1L,
        timeLastModified = 1L,
        timesUsed = 1L
    )

    @Test
    fun testToCreditCardEditorState() {
        val state = creditCard.toCreditCardEditorState()
        val startYear = creditCard.expiryYear.toInt()
        val endYear = startYear + NUMBER_OF_YEARS_TO_SHOW

        with(state) {
            assertEquals(creditCard.guid, guid)
            assertEquals(creditCard.billingName, billingName)
            assertEquals(creditCard.cardNumber, cardNumber)
            assertEquals(creditCard.expiryMonth.toInt(), expiryMonth)
            assertEquals(Pair(startYear, endYear), expiryYears)
            assertTrue(isEditing)
        }
    }

    @Test
    fun testGetInitialCreditCardEditorState() {
        val state = getInitialCreditCardEditorState()
        val calendar = Calendar.getInstance()
        val startYear = calendar.get(Calendar.YEAR)
        val endYear = startYear + NUMBER_OF_YEARS_TO_SHOW

        with(state) {
            assertEquals("", guid)
            assertEquals("", billingName)
            assertEquals("", cardNumber)
            assertEquals(1, expiryMonth)
            assertEquals(Pair(startYear, endYear), expiryYears)
            assertFalse(isEditing)
        }
    }
}
