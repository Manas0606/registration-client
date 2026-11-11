/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.registration.util.control.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.mosip.kernel.core.transliteration.exception.InvalidTransliterationException;
import io.mosip.kernel.core.transliteration.spi.Transliteration;
import io.mosip.kernel.transliteration.icu4j.impl.TransliterationImpl;

/**
 * Exercises the real MOSIP ICU4J implementation used by registration text fields.
 * These are script transliteration tests, not language-translation tests.
 */
public class TransliterationImplTest {

    private final Transliteration<String> transliteration = new TransliterationImpl();

    @Test
    public void latinToArabicTransliterationTest() {
        String original = "salam";
        String result = transliteration.transliterate("Latin", "Arabic", original);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertFalse(original.equals(result));
        assertTrue("Expected Arabic script characters in transliteration",
                result.codePoints().anyMatch(codePoint ->
                        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC));
    }

    @Test
    public void emptyTextTransliterationTest() {
        assertEquals("", transliteration.transliterate("Latin", "Arabic", ""));
    }

    @Test(expected = InvalidTransliterationException.class)
    public void invalidLanguageCodeTransliterationTest() {
        transliteration.transliterate("not-a-language", "not-a-script", "salam");
    }
}
