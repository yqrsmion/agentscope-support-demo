package com.example.agentsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 简化版 RAG 检索器：本地 Markdown 文档按 "## " 标题切块，
 * 用查询词的二元组做关键词匹配打分，返回 Top-K 知识块。
 * 不引入向量库 / embedding，纯本地零依赖。
 */
@Component
public class SimpleRetriever {

    private final int topK;
    private final List<String> chunks;

    public SimpleRetriever(
            @Value("${app.rag.doc-path:docs/support-policy.md}") String docPath,
            @Value("${app.rag.top-k:2}") int topK) {
        this.topK = topK;
        this.chunks = loadAndChunk(docPath);
    }

    public List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> Map.entry(chunk, score(chunk, queryTerms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    public String toContext(List<String> hits) {
        if (hits.isEmpty()) {
            return null;
        }
        return "【知识库资料】\n" + String.join("\n---\n", hits);
    }

    private List<String> loadAndChunk(String docPath) {
        try (InputStream in = SimpleRetriever.class.getClassLoader().getResourceAsStream(docPath)) {
            if (in == null) {
                return List.of();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return chunk(text);
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> chunk(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.startsWith("## ") && !current.isEmpty()) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private Set<String> terms(String query) {
        String q = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        Set<String> terms = new HashSet<>();
        for (int i = 0; i + 2 <= q.length(); i++) {
            terms.add(q.substring(i, i + 2));
        }
        if (q.length() >= 2) {
            terms.add(q);
        }
        return terms;
    }

    private int score(String chunk, Set<String> terms) {
        String lower = chunk.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                score++;
            }
        }
        return score;
    }
}
