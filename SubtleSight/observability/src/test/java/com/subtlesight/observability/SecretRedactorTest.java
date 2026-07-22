package com.subtlesight.observability;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactorTest {
    @Test void redactsHeadersKeysCookiesAndOpenAiTokens(){String raw="Authorization: Bearer abcdef api_key=topsecret password=hunter2 sk-abcdefghijklmnop";String clean=SecretRedactor.redact(raw);assertThat(clean).doesNotContain("abcdef","topsecret","hunter2","sk-abc").contains("***");}
}
