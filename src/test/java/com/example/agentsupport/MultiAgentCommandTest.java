package com.example.agentsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MultiAgentCommandTest {

    @Test
    void multiCommand_detectsAndStrips() {
        assertTrue(MultiAgentService.isMultiCommand("/multi 帮我分析工单"));
        assertEquals("帮我分析工单", MultiAgentService.stripMultiCommand("/multi 帮我分析工单"));
        assertFalse(MultiAgentService.isMultiCommand("帮我分析工单"));
        assertFalse(MultiAgentService.isMultiCommand("/multi-native 帮我分析工单"));
    }

    @Test
    void nativeCommand_detectsAndStrips() {
        assertTrue(NativeMultiAgentService.isNativeCommand("/multi-native 帮我分析工单"));
        assertEquals("帮我分析工单", NativeMultiAgentService.stripNativeCommand("/multi-native 帮我分析工单"));
        assertFalse(NativeMultiAgentService.isNativeCommand("帮我分析工单"));
        assertFalse(NativeMultiAgentService.isNativeCommand("/multi 帮我分析工单"));
    }
}
