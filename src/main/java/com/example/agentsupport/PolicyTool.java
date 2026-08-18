package com.example.agentsupport;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/** 政策子 Agent 专用工具：只暴露 FAQ 检索。 */
public class PolicyTool {

    @Tool(name = "search_faq", description = "检索本地客服 FAQ 知识库，关键词如退换货、物流、发票、售后。", readOnly = true, concurrencySafe = true)
    public String searchFaq(@ToolParam(name = "keyword", description = "FAQ 检索关键词") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "请提供检索关键词。";
        }
        return switch (keyword) {
            case "物流" -> "默认 48 小时内发货，物流时效 3~7 天，偏远地区延长 1~2 天。";
            case "退换货" -> "签收后 7 天内支持无理由退换货，商品需保持完好。";
            default -> "FAQ 中没有匹配「" + keyword + "」的条目。";
        };
    }
}
