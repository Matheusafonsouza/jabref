package org.jabref.gui.specialfields;

import org.jabref.logic.l10n.Localization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.jabref.model.entry.field.SpecialFieldValue;

public class SpecialFieldValueViewModelTest {
    private static final SpecialFieldValue[] valuesList = {
            SpecialFieldValue.PRINTED,
            SpecialFieldValue.CLEAR_PRIORITY,
            SpecialFieldValue.PRIORITY_HIGH,
            SpecialFieldValue.PRIORITY_MEDIUM,
            SpecialFieldValue.PRIORITY_LOW,
            SpecialFieldValue.QUALITY_ASSURED,
            SpecialFieldValue.CLEAR_RANK,
            SpecialFieldValue.RANK_1,
            SpecialFieldValue.RANK_2,
            SpecialFieldValue.RANK_3,
            SpecialFieldValue.RANK_4,
            SpecialFieldValue.RANK_5,
            SpecialFieldValue.CLEAR_READ_STATUS,
            SpecialFieldValue.READ,
            SpecialFieldValue.SKIMMED,
            SpecialFieldValue.RELEVANT,
    };

    private static final String[] expectedList = {
            "Toggle print status",
            "No priority information",
            "Priority high",
            "Priority medium",
            "Priority low",
            "Toggle quality assured",
            "No rank information",
            "One star",
            "Two stars",
            "Three stars",
            "Four stars",
            "Five stars",
            "No read status information",
            "Read status read",
            "Read status skimmed",
            "Toggle relevance"
    };

    @Test
    public void getToolTipText() {
        for (int i = 0; i < valuesList.length; i++) {
            assertEquals(
                    Localization.lang(expectedList[i]),
                    new SpecialFieldValueViewModel(valuesList[i]).getToolTipText()
            );
        }
    }
}
