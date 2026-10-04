package plugindescriptor;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A broken plugin.yml makes the server refuse to load the plugin, so it is checked on every build.
 */
class PluginYmlTest {

    @Test
    @SuppressWarnings("unchecked")
    void pluginYmlIsValid() throws Exception {
        for (String file : new String[]{"plugin.yml", "config.yml", "lang/en.yml", "lang/ru.yml"}) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
                assertNotNull(in, file + " missing");
                Map<String, Object> yaml = new Yaml().load(in);
                assertNotNull(yaml, file + " is empty");
                if (file.equals("plugin.yml")) {
                    assertTrue(yaml.containsKey("main") && yaml.containsKey("commands") && yaml.containsKey("permissions"));
                    Map<String, Object> perms = (Map<String, Object>) yaml.get("permissions");
                    perms.values().forEach(p -> assertTrue(p instanceof Map, "permission entry must be a section"));
                }
            }
        }
    }
}
