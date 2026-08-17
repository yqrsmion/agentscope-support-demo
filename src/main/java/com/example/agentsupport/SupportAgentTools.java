package com.example.agentsupport;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地确定性工具：客服 FAQ、模拟订单与投诉分类定级，
 * 让 ReAct 的"推理 -> 工具调用 -> 观察结果"循环可复现、可讲清。
 */
public class SupportAgentTools {

    private static final Logger log = LoggerFactory.getLogger(SupportAgentTools.class);
    private static final String FAQ_PATH = "faq.md";

    private static final Map<String, String> ORDERS = Map.of(
            "10001", "已发货（顺丰，预计 2 天内送达）",
            "10002", "已签收（2026-08-16）",
            "10003", "退款处理中（预计 1~3 个工作日到账）"
    );

    @Tool(
            name = "search_faq",
            description = "检索本地客服 FAQ 知识库，返回与关键词相关的政策条目；关键词如退换货、物流、发票、售后、会员。",
            readOnly = true,
            concurrencySafe = true)
    public String searchFaq(
            @ToolParam(name = "keyword", description = "FAQ 检索关键词") String keyword) {
        log.info("Tool invoked: search_faq, keyword={}", keyword);
        if (keyword == null || keyword.isBlank()) {
            return "请提供检索关键词。";
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<String> matches = faqLines().stream()
                .filter(line -> {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains(normalized)) {
                        return true;
                    }
                    // 关键词包含条目名（如"退换货政策"包含"退换货"）也算命中
                    int idx = lower.indexOf("：");
                    if (idx > 0) {
                        String tag = lower.substring(0, idx).replace("-", "").trim();
                        return tag.length() >= 2 && normalized.contains(tag);
                    }
                    return false;
                })
                .collect(Collectors.toList());
        return matches.isEmpty()
                ? "FAQ 中没有匹配「" + keyword + "」的条目。"
                : String.join("\n", matches);
    }

    @Tool(
            name = "get_order_status",
            description = "查询模拟订单状态；只在用户提供订单号时调用。",
            readOnly = true,
            concurrencySafe = true)
    public String getOrderStatus(
            @ToolParam(name = "orderId", description = "订单号，例如 10003") String orderId) {
        log.info("Tool invoked: get_order_status, orderId={}", orderId);
        if (orderId == null || orderId.isBlank()) {
            return "请提供订单号。";
        }
        return ORDERS.getOrDefault(orderId.trim(), "未找到订单 " + orderId + "，请核对订单号。");
    }

    @Tool(
            name = "analyze_ticket",
            description = "根据用户投诉/反馈内容做分类与紧急度分析，输出建议处理方式。",
            readOnly = true,
            concurrencySafe = true)
    public String analyzeTicket(
            @ToolParam(name = "description", description = "用户反馈内容") String description) {
        log.info("Tool invoked: analyze_ticket");
        if (description == null || description.isBlank()) {
            return "请提供反馈内容。";
        }
        String text = description.toLowerCase(Locale.ROOT);
        String urgent = (text.contains("生气") || text.contains("投诉")
                || text.contains("非常") || text.contains("差评")) ? "高" : "中";
        String category;
        if (text.contains("物流") || text.contains("发货") || text.contains("快递")) {
            category = "物流类";
        } else if (text.contains("退款") || text.contains("退换") || text.contains("退货")) {
            category = "售后类";
        } else if (text.contains("发票")) {
            category = "发票类";
        } else {
            category = "咨询类";
        }
        return "分类：" + category + "；紧急度：" + urgent
                + "；建议：优先安抚情绪，核对订单与政策后再给出处理方案。";
    }

    private static List<String> faqLines() {
        try (InputStream in = SupportAgentTools.class.getClassLoader().getResourceAsStream(FAQ_PATH)) {
            if (in == null) {
                return List.of("（未找到 FAQ 知识库文件 faq.md）");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(text.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of("读取 FAQ 知识库失败：" + e.getMessage());
        }
    }
}
