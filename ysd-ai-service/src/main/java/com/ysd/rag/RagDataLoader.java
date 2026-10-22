package com.ysd.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagDataLoader implements ApplicationRunner {

    private static final String RAG_KEY_PATTERN = "ysd:rag:*";

    private final VectorStore vectorStore;
    private final JedisPooled ragJedisPooled;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:rag/*.md");
        if (resources.length == 0) {
            log.warn("classpath:rag/ 下没有找到 Markdown 文档，跳过 RAG 数据加载");
            return;
        }

        // 先清掉上一次启动写入的向量，保证重复重启不会产生重复数据
        clearExistingVectors();

        MarkdownDocumentReaderConfig readerConfig = MarkdownDocumentReaderConfig.defaultConfig();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .build();

        // RedisVectorStore 检索时只回传已建索引的 content/embedding 字段，自定义 metadata 会丢失。
        // 因此把来源文件名拼进正文，保证评测与线上回答都能追溯到具体文档。
        List<Document> chunks = new ArrayList<>();
        for (Resource resource : resources) {
            List<Document> documents = new MarkdownDocumentReader(List.of(resource), readerConfig).get();
            String filename = resource.getFilename();
            for (Document chunk : splitter.apply(documents)) {
                chunks.add(new Document("【文档：" + filename + "】\n" + chunk.getText()));
            }
        }

        vectorStore.add(chunks);
        log.info("成功加载了 {} 个文档片段，来源 {} 份", chunks.size(), resources.length);
    }

    private void clearExistingVectors() {
        ScanParams params = new ScanParams().match(RAG_KEY_PATTERN).count(500);
        String cursor = ScanParams.SCAN_POINTER_START;
        long deleted = 0;
        do {
            ScanResult<String> result = ragJedisPooled.scan(cursor, params);
            List<String> keys = result.getResult();
            if (!keys.isEmpty()) {
                deleted += ragJedisPooled.del(keys.toArray(String[]::new));
            }
            cursor = result.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

        if (deleted > 0) {
            log.info("清理旧向量 {} 条", deleted);
        }
    }
}
