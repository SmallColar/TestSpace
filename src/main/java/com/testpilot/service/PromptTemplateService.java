package com.testpilot.service;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/** Loads prompt text from classpath resources and substitutes named values. */
@Component
public class PromptTemplateService {
    private final Properties templates;

    public PromptTemplateService() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("prompts.yml"));
        this.templates = yaml.getObject();
        if (templates == null || templates.isEmpty()) {
            throw new IllegalStateException("无法加载提示词配置：prompts.yml");
        }
    }

    public String render(String templateName) {
        return render(templateName, Map.of());
    }

    public String render(String templateName, Map<String, ?> values) {
        String result = templates.getProperty(templateName);
        if (result == null) throw new IllegalArgumentException("提示词模板不存在：" + templateName);
        for (Map.Entry<String, ?> value : values.entrySet()) {
            String replacement = value.getValue() == null ? "" : value.getValue().toString();
            result = result.replace("{{" + value.getKey() + "}}", replacement);
        }
        return result;
    }
}
