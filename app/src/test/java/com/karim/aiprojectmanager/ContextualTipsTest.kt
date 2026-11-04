package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualTipsTest {
    @Test fun newProjectTipExplainsRequiredInputAndNextStep() {
        val tip = contextualTip("new_project")
        assertTrue(tip.contains("اسمًا واضحًا"))
        assertTrue(tip.contains("Blueprint"))
    }

    @Test fun blueprintTipDescribesRequirements() {
        assertTrue(contextualTip("blueprint").contains("متطلباته"))
    }

    @Test fun roadmapTipDescribesSmallMilestones() {
        assertTrue(contextualTip("roadmap").contains("مراحل صغيرة"))
    }

    @Test fun tasksTipDoesNotClaimAutomaticVerification() {
        assertTrue(contextualTip("tasks").contains("لا تعني تحققًا آليًا"))
    }

    @Test fun unknownSectionDoesNotShowUnrelatedHelp() {
        assertEquals("", contextualTip("unknown"))
    }
}
