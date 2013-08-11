package com.yrek.jackson.dataformat.msgpack;

import java.util.Properties;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;
import com.fasterxml.jackson.core.util.VersionUtil;

public class MessagePackVersion implements Versioned {
    public static final Version VERSION;
    static {
        Version version = Version.unknownVersion();
        try {
            Properties properties = new Properties();
            properties.load(MessagePackVersion.class.getClassLoader().getResourceAsStream("META-INF/maven/com.yrek/jackson-format-msgpack/pom.properties"));
            version = VersionUtil.parseVersion(properties.getProperty("version"),properties.getProperty("groupId"),properties.getProperty("artifactId"));
        } catch (Exception e) {
        }
        VERSION = version;
    }

    public Version version() {
        return VERSION;
    }
}
