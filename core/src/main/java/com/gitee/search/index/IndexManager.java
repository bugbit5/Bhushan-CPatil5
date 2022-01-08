package com.gitee.search.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gitee.search.queue.QueueTask;
import com.gitee.search.storage.StorageFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.gitee.search.index.ObjectMapping.FIELD_ID;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 索引管理器
 * @author Winter Lau<javayou@gmail.com>
 */
public class IndexManager {

    private final static Logger log = LoggerFactory.getLogger(IndexManager.class);

    public final static int MAX_RESULT_COUNT = 1000;
    public final static int SEARCH_THREAD_COUNT = 10; //并发搜索线程数

    public final static String KEY_SCORE = "_score_"; //在 json 中存放文档的score值
    public final static String KEY_DOC_ID = "_id_"; //在 json 中存放文档的 id

    private final static int maxNumberOfCachedQueries = 256;
    private final static long maxRamBytesUsed = 50 * 1024L * 1024L; // 50MB
    // these cache and policy instances can be shared across several queries and readers
    // it is fine to eg. store them into static variables
    private final static QueryCache queryCache = new LRUQueryCache(maxNumberOfCachedQueries, maxRamBytesUsed);
    private final static QueryCachingPolicy defaultCachingPolicy = new UsageTrackingQueryCachingPolicy();

    private final static FacetsConfig facetsConfig = new FacetsConfig();

    /**
     * search after document
     * @param type
     * @param after
     * @param query
     * @param numHits
     * @return
     * @throws IOException
     */
    public static String searchAfter(String type, ScoreDoc after, Query query, int numHits) throws IOException {

        long ct = System.currentTimeMillis();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        try (IndexReader reader = StorageFactory.getIndexReader(type)) {
            ExecutorService pool = Executors.newFixedThreadPool(SEARCH_THREAD_COUNT);//FIXED 似乎不起作用
            IndexSearcher searcher = new IndexSearcher(reader, pool);
            searcher.setQueryCache(queryCache);
            searcher.setQueryCachingPolicy(defaultCachingPolicy);

            TopDocs docs = searcher.searchAfter(after, query, numHits);

            //log.info("{} documents find, search time: {}ms", docs.totalHits.value, (System.currentTimeMillis() - ct));
            result.put("type", type);
            result.put("totalHits", docs.totalHits.value);
            result.put("totalPages", (docs.totalHits.value + numHits - 1) / numHits);
            result.put("timeUsed", (System.currentTimeMillis() - ct));
            result.put("query", query.toString());
            ArrayNode objects = result.putArray("objects");
            for(ScoreDoc sdoc : docs.scoreDocs) {
                Document doc = searcher.doc(sdoc.doc);
                Map<String, Object> pojo = ObjectMapping.doc2json(type, doc);
                pojo.put(KEY_DOC_ID, sdoc.doc);
                pojo.put(KEY_SCORE, sdoc.score);
                objects.addPOJO(pojo);

                log.info("id:{},score:{},repo:{}/{},name:{},type:{},stars:{},recomm:{},fork:{}",
                        doc.get("id"),
                        sdoc.score,
                        doc.get("namespace.path"),
                        doc.get("path"),
                        doc.get("name"),
                        doc.get("type"),
                        doc.get("count.star"),
                        doc.get("recomm"),
                        doc.get("fork")
                );
            }
            return result.toString();
        }
    }

    /**
     * 执行搜索
     * @param type
     * @param query
     * @param sort
     * @param page
     * @param pageSize
     * @return
     * @throws IOException
     */
    public static String search(String type, Query query, Sort sort, int page, int pageSize) throws IOException {

        long ct = System.currentTimeMillis();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        try (IndexReader reader = StorageFactory.getIndexReader(type)) {
            ExecutorService pool = Executors.newFixedThreadPool(SEARCH_THREAD_COUNT);//FIXED 似乎不起作用
            IndexSearcher searcher = new IndexSearcher(reader, pool);
            searcher.setQueryCache(queryCache);
            searcher.setQueryCachingPolicy(defaultCachingPolicy);

            TopFieldDocs docs = searcher.search(query, MAX_RESULT_COUNT, sort, true);

            //log.info("{} documents find, search time: {}ms", docs.totalHits.value, (System.currentTimeMillis() - ct));
            result.put("type", type);
            result.put("totalHits", docs.totalHits.value);
            result.put("totalPages", (docs.totalHits.value + pageSize - 1) / pageSize);
            result.put("pageIndex", page);
            result.put("pageSize", pageSize);
            result.put("timeUsed", (System.currentTimeMillis() - ct));
            result.put("query", query.toString());
            ArrayNode objects = result.putArray("objects");
            for(int i = (page-1) * pageSize; i < page * pageSize && i < docs.totalHits.value ; i++) {
                Document doc = searcher.doc(docs.scoreDocs[i].doc);
                Map<String, Object> pojo = ObjectMapping.doc2json(type, doc);
                pojo.put(KEY_DOC_ID, docs.scoreDocs[i].doc);
                pojo.put(KEY_SCORE, docs.scoreDocs[i].score);
                objects.addPOJO(pojo);
                /*
                log.info("id:{},score:{},repo:{}/{},name:{},type:{},stars:{},recomm:{},fork:{}",
                        doc.get("id"),
                        docs.scoreDocs[i].score,
                        doc.get("namespace.path"),
                        doc.get("path"),
                        doc.get("name"),
                        doc.get("type"),
                        doc.get("count.star"),
                        doc.get("recomm"),
                        doc.get("fork")
                );*/
            }
            return result.toString();
        }
    }

    /**
     * 写入索引库
     * @return
     * @exception
     */
    public static int write(QueueTask task) throws IOException {
        long ct = System.currentTimeMillis();
        List<Document> docs = ObjectMapping.task2doc(task);
        if(docs != null && docs.size() > 0) {
            try (
                IndexWriter writer = StorageFactory.getIndexWriter(task.getType());
                TaxonomyWriter taxonomyWriter = StorageFactory.getTaxonomyWriter(task.getType());
            ) {
                switch (task.getAction()) {
                    case QueueTask.ACTION_ADD:
                        writer.addDocuments(docs.stream().map(d -> buildFacet(taxonomyWriter, d)).collect(Collectors.toList()));
                        //writer.addDocuments(docs);
                        log.info("{} documents writed to index. {}ms", docs.size(), (System.currentTimeMillis()-ct));
                        break;
                    case QueueTask.ACTION_UPDATE:
                        //update documents
                        Query[] queries = docs.stream().map(d -> NumericDocValuesField.newSlowExactQuery(FIELD_ID, d.getField(FIELD_ID).numericValue().longValue())).toArray(Query[]::new);
                        writer.deleteDocuments(queries);
                        //re-add documents
                        writer.addDocuments(docs.stream().map(d -> buildFacet(taxonomyWriter, d)).collect(Collectors.toList()));
                        //writer.addDocuments(docs);
                        log.info("{} documents updated to index, {}ms", docs.size(), (System.currentTimeMillis()-ct));
                        break;
                    case QueueTask.ACTION_DELETE:
                        queries = docs.stream().map(d -> NumericDocValuesField.newSlowExactQuery(FIELD_ID, d.getField(FIELD_ID).numericValue().longValue())).toArray(Query[]::new);
                        writer.deleteDocuments(queries);
                        log.info("{} documents deleted from index, {}ms", docs.size(), (System.currentTimeMillis()-ct));
                }
            }

        }
        return (docs!=null)?docs.size():0;
    }

    private static Document buildFacet(TaxonomyWriter taxonomyWriter, Document doc) {
        try {
            return facetsConfig.build(taxonomyWriter, doc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        QueueTask task = new QueueTask();
        task.setType(QueueTask.TYPE_REPOSITORY);
        //test delete
        //task.setAction(QueueTask.ACTION_DELETE);
        //task.setBody("{\"objects\":[{\"id\":1016657},{\"id\":1495600}]}");
        //test update
        task.setAction(QueueTask.ACTION_UPDATE);
        task.setBody("{\"objects\":[{\"id\":91902,\"recomm\":333}]}");
        write(task);
    }

}
