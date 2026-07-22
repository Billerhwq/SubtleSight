package com.subtlesight.connectors;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {
    @Test void blocksLocalPrivateMetadataAndFileDestinations() {
        for (String url : new String[]{"http://127.0.0.1/x","http://10.0.0.1/x","http://169.254.169.254/latest","file:///etc/passwd","http://[::1]/"})
            assertThatThrownBy(() -> SsrfGuard.requirePublicHttp(URI.create(url))).isInstanceOf(SecurityException.class);
    }
}

