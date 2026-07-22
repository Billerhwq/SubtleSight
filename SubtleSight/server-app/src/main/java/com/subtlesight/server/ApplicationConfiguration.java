package com.subtlesight.server;

import com.subtlesight.application.SubtleSightFacade;
import com.subtlesight.application.Ports.*;
import com.subtlesight.calendar.*;
import com.subtlesight.calendar.connectors.*;
import com.subtlesight.calendar.storage.sqlite.SqliteCalendarRepository;
import com.subtlesight.connectors.*;
import com.subtlesight.discovery.DiscoveryEngine;
import com.subtlesight.discovery.DiscoveryPlanner;
import com.subtlesight.document.DocumentProcessor;
import com.subtlesight.evidence.EvidenceService;
import com.subtlesight.jobs.*;
import com.subtlesight.observability.BackupService;
import com.subtlesight.provider.ai.OpenAiCompatibleProvider;
import com.subtlesight.provider.search.JsonWebSearchProvider;
import com.subtlesight.report.ReportService;
import com.subtlesight.research.DeepResearchService;
import com.subtlesight.search.LuceneHybridIndex;
import com.subtlesight.signal.SignalEngine;
import com.subtlesight.storage.blob.ContentAddressedBlobStore;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import com.subtlesight.storage.sqlite.SqliteIntelligenceRepository;
import com.subtlesight.story.EntityTopicExtractor;
import com.subtlesight.story.StoryClusterEngine;
import com.subtlesight.watchlist.WatchlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
public class ApplicationConfiguration {
    @Bean Clock clock(){return Clock.systemUTC();}
    @Bean Path dataDirectory(@Value("${subtlesight.data-dir}")String value){try{Path path=Path.of(value).toAbsolutePath().normalize();for(String sub:List.of("blobs","lucene","reports","backups","logs"))Files.createDirectories(path.resolve(sub));return path;}catch(Exception e){throw new IllegalStateException("cannot initialize data directory",e);}}
    @Bean(destroyMethod="close")DataDirectoryLock dataDirectoryLock(Path dataDirectory){return new DataDirectoryLock(dataDirectory);}
    @Bean DataSource dataSource(Path dataDirectory){return SqliteDataSourceFactory.create(dataDirectory.resolve("subtlesight.db"));}
    @Bean IntelligenceRepository repository(DataSource dataSource,ObjectMapper json){return new SqliteIntelligenceRepository(dataSource,json);}
    @Bean CalendarRepository calendarRepository(DataSource dataSource,ObjectMapper json){return new SqliteCalendarRepository(dataSource,json);}
    @Bean BlobStore blobStore(Path dataDirectory){return new ContentAddressedBlobStore(dataDirectory.resolve("blobs"));}
    @Bean(destroyMethod="close")SearchIndex searchIndex(Path dataDirectory){return new LuceneHybridIndex(dataDirectory.resolve("lucene"));}
    @Bean SubtleSightFacade facade(IntelligenceRepository repository,BlobStore blobStore,SearchIndex search,Clock clock){return new SubtleSightFacade(repository,blobStore,search,clock);}
    @Bean SafeHttpClient safeHttpClient(@Value("${subtlesight.connectors.min-delay-ms:1500}")long minDelayMs){return new SafeHttpClient(Duration.ofSeconds(30),5,50*1024*1024,Duration.ofMillis(Math.max(0,minDelayMs)));}
    @Bean ConnectorRegistry connectorRegistry(SafeHttpClient http,ObjectMapper json,Path dataDirectory,@Value("${subtlesight.opencli.command:opencli}")String openCliCommand){
        SourceConnector videoConnector=new DelegatingVideoSourceConnector(new InternetArchiveVideoSourceConnector(http,json),new OpenCliVideoSourceConnector(json,openCliCommand,dataDirectory.resolve("opencli-downloads")));
        ConnectorRegistry registry=new ConnectorRegistry()
                .register(new RssSourceConnector(http))
                .register(new HackerNewsSourceConnector(http,json))
                .register(new GitHubTrendingSourceConnector(http,json))
                .register(new ArxivSourceConnector(http))
                .register(new HuggingFaceTrendingSourceConnector(http,json))
                .register(new ProductHuntSourceConnector(http))
                .register(videoConnector);
        for(var type:com.subtlesight.domain.Models.SourceType.values())
            if(type!=com.subtlesight.domain.Models.SourceType.RSS&&type!=com.subtlesight.domain.Models.SourceType.UPLOAD
                    &&type!=com.subtlesight.domain.Models.SourceType.HN&&type!=com.subtlesight.domain.Models.SourceType.GITHUB
                    &&type!=com.subtlesight.domain.Models.SourceType.ARXIV&&type!=com.subtlesight.domain.Models.SourceType.HUGGING_FACE
                    &&type!=com.subtlesight.domain.Models.SourceType.PRODUCT_HUNT
                    &&type!=com.subtlesight.domain.Models.SourceType.VIDEO)
                registry.register(new WebSourceConnector(http,type));
        return registry;
    }
    @Bean IndicatorDictionary indicatorDictionary(){return new IndicatorDictionary();}
    @Bean IcsCalendarParser icsCalendarParser(IndicatorDictionary dictionary){return new IcsCalendarParser(dictionary);}
    @Bean CalendarFetcher calendarFetcher(SafeHttpClient http){return new SafeCalendarFetcher(http);}
    @Bean CalendarConnectorRegistry calendarConnectorRegistry(CalendarFetcher fetcher,IcsCalendarParser ics,IndicatorDictionary dictionary){
        return new CalendarConnectorRegistry()
                .register(new IcsCalendarSourceConnector(fetcher,ics))
                .register(new HtmlCalendarSourceConnector(fetcher,dictionary));
    }
    @Bean FinancialCalendarEngine financialCalendarEngine(CalendarRepository repository,ObjectMapper json){return new FinancialCalendarEngine(repository,json);}
    @Bean FinancialCalendarService financialCalendarService(CalendarRepository repository,CalendarConnectorRegistry connectors,FinancialCalendarEngine engine,Clock clock){return new FinancialCalendarService(repository,connectors,engine,clock);}
    @Bean List<WebSearchProvider> searchProviders(@Value("${subtlesight.provider.search-url:}")String url,@Value("${subtlesight.provider.search-key:}")String key,ObjectMapper json){List<WebSearchProvider> providers=new ArrayList<>();if(url!=null&&!url.isBlank())providers.add(new JsonWebSearchProvider("configured-search",URI.create(url),key,json,Duration.ofSeconds(30)));return List.copyOf(providers);}
    @Bean AiProvider aiProvider(@Value("${subtlesight.provider.ai-base-url:}")String url,@Value("${subtlesight.provider.ai-key:}")String key,@Value("${subtlesight.provider.ai-model}")String model,ObjectMapper json){if(url==null||url.isBlank()||key==null||key.isBlank())return request->new AiProvider.AiResult("{}",0,0,"disabled","none");return new OpenAiCompatibleProvider("configured-ai",URI.create(url),key,model,json,Duration.ofSeconds(30));}
    @Bean DiscoveryPlanner discoveryPlanner(Clock clock){return new DiscoveryPlanner(clock);}
    @Bean DiscoveryEngine discoveryEngine(@org.springframework.beans.factory.annotation.Qualifier("searchProviders") List<WebSearchProvider> providers,Clock clock){return new DiscoveryEngine(providers,clock);}
    @Bean DocumentProcessor documentProcessor(Clock clock){return new DocumentProcessor(clock);}
    @Bean StoryClusterEngine storyClusterEngine(){return new StoryClusterEngine();}
    @Bean EntityTopicExtractor entityTopicExtractor(){return new EntityTopicExtractor();}
    @Bean SignalEngine signalEngine(Clock clock){return new SignalEngine(clock);}
    @Bean EvidenceService evidenceService(IntelligenceRepository repository,Clock clock){return new EvidenceService(repository,clock);}
    @Bean WatchlistService watchlistService(IntelligenceRepository repository,ObjectMapper json,Clock clock){return new WatchlistService(repository,json,clock);}
    @Bean ReportService reportService(IntelligenceRepository repository,ObjectMapper json,Clock clock){return new ReportService(repository,json,clock);}
    @Bean DeepResearchService researchService(IntelligenceRepository repository,@org.springframework.beans.factory.annotation.Qualifier("searchProviders") List<WebSearchProvider> providers,AiProvider ai,Clock clock){return new DeepResearchService(repository,providers,ai,(question,hits,max)->repository.listDocumentVersions(Math.min(Math.max(max,0),100)),clock);}
    @Bean DurableJobQueue jobQueue(DataSource dataSource,Clock clock){return new DurableJobQueue(dataSource,clock,Duration.ofSeconds(30),new RetryPolicy(Duration.ofSeconds(2),Duration.ofMinutes(5),42));}
    @Bean(destroyMethod="close")DurableJobRunner jobRunner(DurableJobQueue queue,DeepResearchService research,ObjectMapper json){DurableJobRunner runner=new DurableJobRunner(queue);runner.register("RESEARCH",2,(job,context)->{context.heartbeat();research.execute(java.util.UUID.fromString(json.readTree(job.payloadJson()).path("researchId").asText()));});return runner;}
    @Bean BackupService backupService(DataSource dataSource,Path dataDirectory,ObjectMapper json,Clock clock){return new BackupService(dataSource,dataDirectory,json,clock);}
}
