package com.example.agentsupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleRetrieverTest {

    private final SimpleRetriever retriever = new SimpleRetriever("docs/support-policy.md", 2);

    @Test
    void search_findsPolicyByKeyword() {
        List<String> hits = retriever.search("质量问题的退货运费由谁承担");
        assertFalse(hits.isEmpty());
        assertTrue(String.join(" ", hits).contains("退换货"));
    }

    @Test
    void search_unrelatedQueryReturnsEmpty() {
        assertTrue(retriever.search("今天天气怎么样").isEmpty());
    }

    @Test
    void toContextJoinsHits() {
        String context = retriever.toContext(List.of("知识块A", "知识块B"));
        assertTrue(context.contains("知识块A"));
        assertTrue(context.contains("知识块B"));
    }
}
