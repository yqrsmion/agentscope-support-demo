package com.example.agentsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.agentsupport.AgentService.ParsedCommand;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void parseCommand_extractsModelAliasAndMessage() {
        ParsedCommand parsed = AgentService.parseCommand("/model reasoner 帮我分析工单");
        assertEquals("reasoner", parsed.modelAlias());
        assertEquals("帮我分析工单", parsed.message());
    }

    @Test
    void parseCommand_withoutCommandKeepsMessage() {
        ParsedCommand parsed = AgentService.parseCommand("你好");
        assertNull(parsed.modelAlias());
        assertEquals("你好", parsed.message());
    }

    @Test
    void resolveModelId_mapsAliases() {
        assertEquals("deepseek:deepseek-chat", AgentService.resolveModelId("chat"));
        assertEquals("deepseek:deepseek-reasoner", AgentService.resolveModelId("reasoner"));
        assertEquals("deepseek:deepseek-v4-flash", AgentService.resolveModelId("flash"));
        assertEquals("deepseek:deepseek-v4-pro", AgentService.resolveModelId("pro"));
        assertEquals("deepseek:deepseek-chat", AgentService.resolveModelId("不存在的模型"));
        assertEquals("deepseek:deepseek-chat", AgentService.resolveModelId(null));
    }
}
