package com.subtlesight.signal;

import com.subtlesight.domain.Models.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalEngineTest {
    @Test void independentSourcesIncreaseConfidenceAndProvideReasons(){Instant now=Instant.parse("2026-07-16T00:00:00Z");var engine=new SignalEngine(Clock.fixed(now,ZoneOffset.UTC));var story=new Story(UUID.randomUUID(),"监管新规",null,StoryStatus.ACTIVE,now,now,4,4,Set.of(),Set.of(),false,now);var signal=engine.project(story,ViewType.IMPORTANT,null,new SignalEngine.Context(.7,.9,.6,.8,10,4,.9,4,1));assertThat(signal.reasonCodes()).contains("HIGH_IMPACT","MULTIPLE_INDEPENDENT_SOURCES","SOURCE_DISAGREEMENT");assertThat(signal.score()).isGreaterThan(.7);}
    @Test void parsesSafeSavedViewAst(){var ast=new SavedViewParser().parse("topic:\"AI Agent\" AND NOT tier:SOCIAL");assertThat(ast).isInstanceOf(SavedViewParser.And.class);assertThatThrownBy(()->new SavedViewParser().parse("sql:drop")).isInstanceOf(IllegalArgumentException.class);}
}
