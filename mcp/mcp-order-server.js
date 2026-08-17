// 本地 MCP 服务器（stdio）：暴露一个订单查询工具，供 AgentScope 客户端调用。
// 零依赖：仅使用 Node 内置模块，按 MCP 协议（JSON-RPC over stdio）应答。
const readline = require("readline");

const ORDERS = {
  "10001": "已发货（顺丰，预计 2 天内送达）",
  "10002": "已签收（2026-08-16）",
  "10003": "退款处理中（预计 1~3 个工作日到账）",
};

function send(message) {
  process.stdout.write(JSON.stringify(message) + "\n");
}

function toolResult(text) {
  return { content: [{ type: "text", text }] };
}

const rl = readline.createInterface({ input: process.stdin });
rl.on("line", (line) => {
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return;
  }
  const { id, method, params } = msg;

  switch (method) {
    case "initialize":
      send({
        jsonrpc: "2.0",
        id,
        result: {
          protocolVersion: "2024-11-05",
          capabilities: { tools: {} },
          serverInfo: { name: "order-mcp-server", version: "0.1.0" },
        },
      });
      break;
    case "notifications/initialized":
      break;
    case "ping":
      send({ jsonrpc: "2.0", id, result: {} });
      break;
    case "tools/list":
      send({
        jsonrpc: "2.0",
        id,
        result: {
          tools: [
            {
              name: "check_order_via_mcp",
              description: "通过 MCP 查询模拟订单状态，参数 orderId 如 10003",
              annotations: { readOnlyHint: true },
              inputSchema: {
                type: "object",
                properties: { orderId: { type: "string" } },
                required: ["orderId"],
              },
            },
          ],
        },
      });
      break;
    case "tools/call":
      const { name, arguments: args } = params;
      if (name === "check_order_via_mcp") {
        const orderId = (args && args.orderId || "").trim();
        const text = orderId
          ? (ORDERS[orderId] ? "订单 " + orderId + "：" + ORDERS[orderId] : "未找到订单 " + orderId)
          : "请提供订单号。";
        send({ jsonrpc: "2.0", id, result: toolResult(text) });
      } else {
        send({ jsonrpc: "2.0", id, error: { code: -32601, message: "未知工具: " + name } });
      }
      break;
    default:
      send({ jsonrpc: "2.0", id, error: { code: -32601, message: "未知方法: " + method } });
  }
});
