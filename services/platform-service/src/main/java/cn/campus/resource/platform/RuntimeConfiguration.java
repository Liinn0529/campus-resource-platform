package cn.campus.resource.platform;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RuntimeConfiguration {
    @Bean
    InfoContributor serviceInfo(BuildProperties build) {
        return builder -> builder
                .withDetail("service", build.getArtifact())
                .withDetail("version", build.getVersion())
                .withDetail("gitCommit", build.get("gitCommit"))
                .withDetail("buildTime", build.getTime().toString());
    }

}
