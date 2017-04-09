package org.nzbhydra.indexers;

import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import org.nzbhydra.config.BaseConfig;
import org.nzbhydra.config.IndexerConfig;
import org.nzbhydra.database.IndexerApiAccessEntity;
import org.nzbhydra.database.IndexerApiAccessRepository;
import org.nzbhydra.database.IndexerApiAccessResult;
import org.nzbhydra.database.IndexerApiAccessType;
import org.nzbhydra.database.IndexerEntity;
import org.nzbhydra.database.IndexerRepository;
import org.nzbhydra.database.IndexerStatusEntity;
import org.nzbhydra.database.SearchResultEntity;
import org.nzbhydra.database.SearchResultRepository;
import org.nzbhydra.searching.IndexerSearchResult;
import org.nzbhydra.searching.SearchResultIdCalculator;
import org.nzbhydra.searching.SearchResultItem;
import org.nzbhydra.searching.exceptions.IndexerSearchAbortedException;
import org.nzbhydra.searching.searchrequests.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public abstract class Indexer {

    public enum BackendType {
        NZEDB,
        NNTMUX
    }

    protected static final List<Integer> DISABLE_PERIODS = Arrays.asList(0, 15, 30, 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60);
    private static final Logger logger = LoggerFactory.getLogger(Indexer.class);

    List<DateTimeFormatter> DATE_FORMATs = Arrays.asList(DateTimeFormatter.RFC_1123_DATE_TIME, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH));


    protected IndexerEntity indexer;
    protected IndexerConfig config;

    @Autowired
    protected BaseConfig baseConfig;
    @Autowired
    protected RestTemplate restTemplate;
    @Autowired
    private IndexerRepository indexerRepository;
    @Autowired
    private SearchResultRepository searchResultRepository;
    @Autowired
    private IndexerApiAccessRepository indexerApiAccessRepository;

    public void initialize(IndexerConfig config, IndexerEntity indexer) {
        this.indexer = indexer;
        this.config = config;
        //Set timeout. //TODO Timeout should be the maximum total time we wait for the client to return
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = config.getTimeout().orElse(baseConfig.getSearching().getTimeout());
        factory.setReadTimeout(timeout);
        factory.setConnectTimeout(timeout);
        restTemplate.setRequestFactory(factory);
    }

    public IndexerSearchResult search(SearchRequest searchRequest, int offset, int limit) throws IndexerSearchAbortedException {
        IndexerSearchResult indexerSearchResult;
        try {
            indexerSearchResult = searchInternal(searchRequest);
        } catch (Exception e) {
            if (e instanceof IndexerSearchAbortedException) {
                logger.warn("Unexpected error while preparing search");
                indexerSearchResult = new IndexerSearchResult(this, e.getMessage());
            } else {
                logger.error("Unexpected error while searching", e);
                try {
                    handleFailure(e.getMessage(), false, IndexerApiAccessType.SEARCH, null, IndexerApiAccessResult.CONNECTION_ERROR, null); //TODO depending on type of error, perhaps not at all because it might be a bug
                } catch (Exception e1) {
                    logger.error("Error while handling indexer failure. API access was not saved to database", e1);
                }
                indexerSearchResult = new IndexerSearchResult(this, e.getMessage());
            }
        }
        return indexerSearchResult;
    }

    protected abstract IndexerSearchResult searchInternal(SearchRequest searchRequest) throws IndexerSearchAbortedException;

    @Transactional
    protected List<SearchResultItem> persistSearchResults(List<SearchResultItem> searchResultItems) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        ArrayList<SearchResultEntity> searchResultEntities = new ArrayList<>();
        for (SearchResultItem item : searchResultItems) {
            SearchResultEntity searchResultEntity = searchResultRepository.findByIndexerAndIndexerGuid(indexer, item.getIndexerGuid());
            if (searchResultEntity == null) {
                searchResultEntity = new SearchResultEntity();

                //Set all entity relevant data
                searchResultEntity.setIndexer(indexer);
                searchResultEntity.setTitle(item.getTitle());
                searchResultEntity.setLink(item.getLink());
                searchResultEntity.setDetails(item.getDetails());
                searchResultEntity.setIndexerGuid(item.getIndexerGuid());
                searchResultEntity.setFirstFound(Instant.now());
                searchResultEntities.add(searchResultEntity);
            }
            //TODO Unify guid and searchResultId which are the same
            long guid = SearchResultIdCalculator.calculateSearchResultId(item);
            item.setGuid(guid);
            item.setSearchResultId(guid);
        }
        searchResultRepository.save(searchResultEntities);

        getLogger().debug("Persisting {} search search results took {}ms", searchResultEntities.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return searchResultItems;
    }

    protected void handleSuccess(IndexerApiAccessType accessType, long responseTime, IndexerApiAccessResult accessResult, String url) {
        IndexerStatusEntity status = indexer.getStatus();
        status.setLevel(0);
        status.setDisabledPermanently(false);
        status.setDisabledUntil(null);
        indexerRepository.save(indexer);

        IndexerApiAccessEntity apiAccess = new IndexerApiAccessEntity(indexer);
        apiAccess.setAccessType(accessType);
        apiAccess.setResponseTime(responseTime);
        apiAccess.setResult(accessResult);
        apiAccess.setTime(Instant.now());
        apiAccess.setUrl(url);
        indexerApiAccessRepository.save(apiAccess);
    }

    protected void handleFailure(String reason, Boolean disablePermanently, IndexerApiAccessType accessType, Long responseTime, IndexerApiAccessResult accessResult, String url) {
        IndexerStatusEntity status = indexer.getStatus();
        if (status.getLevel() == 0) {
            status.setFirstFailure(Instant.now());
        }
        status.setLevel(status.getLevel() + 1);
        status.setDisabledPermanently(disablePermanently);
        status.setLastFailure(Instant.now());
        long minutesToAdd = DISABLE_PERIODS.get(Math.min(DISABLE_PERIODS.size() - 1, status.getLevel() + 1));
        status.setDisabledUntil(Instant.now().plus(minutesToAdd, ChronoUnit.MINUTES));
        status.setReason(reason);
        indexerRepository.save(indexer);
        if (disablePermanently) {
            getLogger().warn("{} will be permanently disabled until reenabled by the user", indexer.getName());
        } else {
            getLogger().warn("Will disable {} until {}", indexer.getName(), status.getDisabledUntil());
        }

        IndexerApiAccessEntity apiAccess = new IndexerApiAccessEntity(indexer);
        apiAccess.setAccessType(accessType);
        apiAccess.setResponseTime(responseTime);
        apiAccess.setResult(accessResult);
        apiAccess.setTime(Instant.now());
        apiAccess.setUrl(url);
        indexerApiAccessRepository.save(apiAccess);
    }


    public String getName() {
        return config.getName();
    }

    public IndexerConfig getConfig() {
        return config;
    }

    public IndexerEntity getIndexerEntity() {
        return indexer;
    }

    public Optional<Instant> tryParseDate(String dateString) {
        try {
            for (DateTimeFormatter formatter : DATE_FORMATs) {
                Instant instant = Instant.from(formatter.parse(dateString));
                return Optional.of(instant);
            }
        } catch (DateTimeParseException e) {
            logger.debug("Unable to parse date string " + dateString);
        }
        return Optional.empty();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Indexer that = (Indexer) o;
        return Objects.equal(indexer.getName(), that.indexer.getName());
    }

    @Override
    public int hashCode() {
        return config == null ? 0 : Objects.hashCode(config.getName());
    }

    protected abstract Logger getLogger();
}