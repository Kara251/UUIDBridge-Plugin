package dev.kara.uuidbridge.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandOptionsTest {
    @Test
    void parsesPluginFlagAndQuotedValues() {
        CommandOptions options = CommandOptions.parse(
            "--mapping \"maps/users.csv\" --targets uuidbridge/targets.json --singleplayer-name Alice --plugins");

        assertEquals("maps/users.csv", options.mapping().orElseThrow());
        assertEquals("uuidbridge/targets.json", options.targets().orElseThrow());
        assertEquals("Alice", options.singleplayerName().orElseThrow());
        assertTrue(options.plugins());
        assertTrue(options.unknownOptions().isEmpty());
    }

    @Test
    void parsesConfirmAndUnknownOptions() {
        CommandOptions options = CommandOptions.parse("--confirm --unknown");

        assertTrue(options.confirm());
        assertEquals("--unknown", options.unknownOptions().getFirst());
    }
}
