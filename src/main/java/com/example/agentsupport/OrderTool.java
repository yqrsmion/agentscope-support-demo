package com.example.agentsupport;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/** 订单子 Agent 专用工具：只暴露订单状态查询。 */
public class OrderTool {

    @Tool(name = "get_order_status", description = "查询模拟订单状态。", readOnly = true, concurrencySafe = true)
    public String getOrderStatus(@ToolParam(name = "orderId", description = "订单号") String orderId) {
        return switch (orderId == null ? "" : orderId.trim()) {
            case "10001" -> "已发货（顺丰，预计 2 天内送达）";
            case "10002" -> "已签收（2026-08-16）";
            case "10003" -> "退款处理中（预计 1~3 个工作日到账）";
            default -> "未找到订单 " + orderId;
        };
    }
}
