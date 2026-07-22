package com.subtlesight.story;

import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class StoryClusterEngineTest {
    private DocumentVersion doc(String text){return new DocumentVersion(UUID.randomUUID(),UUID.randomUUID(),"标题",null,Instant.now(),"zh","https://x.test",text,Hashing.sha256(text),null,"v1",null,false,Instant.now());}
    private Story story(){return new Story(UUID.randomUUID(),"事件",null,StoryStatus.ACTIVE,Instant.now(),Instant.now(),1,1,Set.of(),Set.of(),false,Instant.now());}
    @Test void exactDuplicatesAreIndependentOfInputOrder(){var engine=new StoryClusterEngine();var d=doc("监管部门正式发布人工智能安全评估新规");var result=engine.decide(d,List.of(new StoryClusterEngine.Candidate(story(),d,false)));assertThat(result.decision()).isEqualTo(StoryClusterEngine.Decision.EXACT_DUPLICATE);}
    @Test void manualSplitWinsOverReclustering(){var engine=new StoryClusterEngine();var d=doc("发布 新版本 2.0");var result=engine.decide(d,List.of(new StoryClusterEngine.Candidate(story(),d,true)));assertThat(result.decision()).isEqualTo(StoryClusterEngine.Decision.NEW_STORY);}
    @Test void sameStoryNeedsTimeAndHandlesPolarityAndVersions(){var engine=new StoryClusterEngine();var a=doc("监管部门发布支持人工智能安全评估新规并正式实施");var close=new DocumentVersion(UUID.randomUUID(),UUID.randomUUID(),"标题",null,a.publishedAt().plusSeconds(60),"zh","https://y.test","监管部门发布支持人工智能安全评估新规并正式实施补充细则",Hashing.sha256("other"),null,"v1",null,false,Instant.now());assertThat(engine.decide(close,List.of(new StoryClusterEngine.Candidate(story(),a,false))).decision()).isEqualTo(StoryClusterEngine.Decision.SAME_STORY);var old=new DocumentVersion(a.id(),a.rawDocumentId(),a.title(),null,a.publishedAt().minusSeconds(31L*86400),"zh",a.canonicalUrl(),a.text(),a.textHash()+"x",null,"v1",null,false,a.createdAt());assertThat(engine.decide(close,List.of(new StoryClusterEngine.Candidate(story(),old,false))).decision()).isEqualTo(StoryClusterEngine.Decision.NEW_STORY);assertThat(engine.similarity("","")).isZero();assertThat(engine.simHash("abc")).isNotZero();}
    @Test void extractorFindsReposModelsDomainsTopicsAndToleratesBadUrl(){var extractor=new EntityTopicExtractor();var d=new DocumentVersion(UUID.randomUUID(),UUID.randomUUID(),"GPT-5 与 GitHub.com/OpenAI/Codex",null,Instant.now(),"zh","https://NEWS.Example/path","Claude DeepSeek-R1 AI监管",Hashing.sha256("x"),null,"v1",null,false,Instant.now());var result=extractor.extract(d,Set.of("AI监管","不存在"));assertThat(result.entities()).contains("repo:openai/codex","model:GPT-5","model:Claude","model:DeepSeek-R1","domain:news.example");assertThat(result.topics()).containsExactly("AI监管");var bad=new DocumentVersion(UUID.randomUUID(),UUID.randomUUID(),"x",null,null,"zh",":::bad","",Hashing.sha256("y"),null,"v1",null,false,Instant.now());assertThat(extractor.extract(bad,Set.of()).entities()).isEmpty();}
}
