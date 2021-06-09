package com.gitee.search.core;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Index Manager to add/update/delete object in indexes path
 * @author Winter Lau (javayou@gmail.com)
 */
public class IndexManager {

    private final static Logger log = LoggerFactory.getLogger(IndexManager.class);

    private Path indexPath;
    private IndexWriterConfig config;

    private IndexManager(Path idxPath) {
        this.indexPath = idxPath;
        this.config = new IndexWriterConfig(JcsegAnalyzer.INSTANCE);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    }

    /**
     * Building an IndexManager instance
     * @param idx_path
     * @return
     * @throws IOException
     */
    public final static IndexManager init(String idx_path) throws IOException {
        Path path = Paths.get(idx_path).normalize();
        if(!Files.exists(path) || Files.isDirectory(path))
            throw new FileNotFoundException("Path:" + idx_path + " is not available directory.");
        if(!Files.isReadable(path))
            throw new FileSystemException("Path:" + idx_path + " unreadable.");
        return new IndexManager(path);
    }

    /**
     * Update objects
     * @param objs
     * @throws IOException
     */
    public void update(List<SearchObject> objs) throws IOException {
        FSDirectory dir = FSDirectory.open(this.indexPath);
        IndexWriter writer = new IndexWriter(dir, config);
        try {
            for (SearchObject obj : objs) {
                Document doc = SearchHelper.obj2doc(obj);
                writer.updateDocument(new Term(SearchObject.FIELD_NAME_ID, String.valueOf(obj.id())), doc);
            }
            writer.commit();
        } finally {
            close(writer);
            close(dir);
        }
    }

    /**
     * Delete objects
     * @param ids
     * @throws IOException
     */
    public void delete(List<Long> ids) throws IOException {
        FSDirectory dir = FSDirectory.open(this.indexPath);
        IndexWriter writer = new IndexWriter(dir, config);
        try {
            int doc_count = 0;
            Term[] terms = ids.stream().map(id -> new Term(SearchObject.FIELD_NAME_ID, String.valueOf(id))).toArray(Term[]::new);
            writer.deleteDocuments(terms);
            writer.commit();
        } finally {
            close(writer);
            close(dir);
        }
    }

    /**
     * remove all index files
     * @throws IOException
     */
    public void clean() throws IOException {
        Files.walk(this.indexPath).filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
    }

    /**
     * Optimize index files
     * @throws IOException
     */
    public void optimize() throws IOException {
        FSDirectory dir = FSDirectory.open(this.indexPath);
        IndexWriter writer = new IndexWriter(dir, config);
        try {
            writer.forceMerge(1);
            writer.commit();
        } finally {
            close(writer);
            close(dir);
        }
    }

    private static void close(Closeable obj)  {
        if(obj != null) {
            try {
                obj.close();
            } catch (IOException e) {
                log.error("Unable to close resource", e);
            }
        }
    }

}
