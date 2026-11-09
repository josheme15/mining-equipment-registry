package com.reto.mining.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indica qué commit está corriendo. deploy.sh lo usa para confirmar
 * que Render ya publicó la versión nueva (y no sigue la anterior).
 * Render inyecta RENDER_GIT_COMMIT automáticamente.
 */
@RestController
public class VersionController {

    private final String commit;
    private final Instant startedAt = Instant.now();

    public VersionController(@Value("${RENDER_GIT_COMMIT:local}") String commit) {
        this.commit = commit;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("commit", commit, "startedAt", startedAt.toString());
    }
}
