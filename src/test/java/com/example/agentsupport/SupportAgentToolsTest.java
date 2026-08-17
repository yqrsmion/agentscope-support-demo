package com.example.agentsupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupportAgentToolsTest {

    private final SupportAgentTools tools = new SupportAgentTools();

    @Test
    void searchFaq_returnsMatchingEntries() {
        String result = tools.searchFaq("退换货");
        assertTrue(result.contains("退换货"), result);
        assertTrue(result.contains("7 天"), result);
    }

    @Test
    void searchFaq_unknownKeywordReturnsHint() {
        String result = tools.searchFaq("量子计算");
        assertTrue(result.contains("没有匹配"), result);
    }

    @Test
    void getOrderStatus_knownAndUnknown() {
        String known = tools.getOrderStatus("10003");
        assertTrue(known.contains("退款"), known);
        String unknown = tools.getOrderStatus("99999");
        assertTrue(unknown.contains("未找到"), unknown);
    }

    @Test
    void analyzeTicket_classifiesUrgencyAndCategory() {
        String result = tools.analyzeTicket("物流很久没更新，非常生气，要投诉");
        assertTrue(result.contains("物流类"), result);
        assertTrue(result.contains("高"), result);
    }
}
