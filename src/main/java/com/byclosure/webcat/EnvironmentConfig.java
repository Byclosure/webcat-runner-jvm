package com.byclosure.webcat;

import gherkin.deps.com.google.gson.GsonBuilder;
import org.junit.runners.model.InitializationError;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnvironmentConfig {
    private final static Logger logger = LoggerHelper.getLogger(EnvironmentConfig.class.getName());

    private static final String WEBCAT_SETTINGS_FILE = "webcat.properties";
    private static final String WEBCAT_SETTINGS_DIRECTORY = ".webcat";

    private static final String RUNNER_SETTINGS_FILE = "runner_config.properties";
    private static final String WEBCAT_ENDPOINT = "WEBCAT_ENDPOINT";

    public static final String DEFAULT_INTENT = "{\"type\": \"features\", \"value\": []}";

    private final Map<Config, String> configMap = new HashMap<Config, String>();

    public EnvironmentConfig() throws InitializationError {
        getEnvironmentConfiguration();
    }

    private void getEnvironmentConfiguration() throws InitializationError {
        //search home directory
        final Properties homeProperties = getHomeProperties();
        for(Config config : Config.values()) {
            final String propertyValue = homeProperties.getProperty(config.getVar());
            if(propertyValue != null) {
                configMap.put(config, propertyValue);
            }
        }

        //replace with project directory
        final Properties projectProperties = getProjectProperties();
        for(Config config : Config.values()) {
            final String propertyValue = projectProperties.getProperty(config.getVar());
            if(propertyValue != null) {
                configMap.put(config, propertyValue);
            }
        }

        //replace with env vars
        for(Config config : Config.values()) {
            final String envValue = System.getenv(config.getVar());
            if(envValue != null) {
                configMap.put(config, envValue);
            }
        }

        if(!configMap.containsKey(Config.HOST)) {
            configMap.put(Config.HOST, getRunnerProperties().getProperty(Config.HOST.getVar()));
        }

        if(!configMap.containsKey(Config.INTENT)) {
            configMap.put(Config.INTENT, DEFAULT_INTENT);
        }

        //WEBCAT_PROJECT_TOKEN and WEBCAT_PROJECT are required
        if(shouldPublishResults() &&
                !(configMap.containsKey(Config.PROJECT_TOKEN) &&
                        configMap.containsKey(Config.PROJECT))) {
            throw new InitializationError("WEBCAT_PROJECT and WEBCAT_PROJECT_TOKEN must be set.");
        }
    }

    private Properties getProjectProperties() {
        final InputStream input = getClass().getClassLoader().getResourceAsStream(WEBCAT_SETTINGS_FILE);

        return loadPropertiesFromStream(input);
    }

    private Properties getRunnerProperties() {
        final InputStream input = getClass().getClassLoader().getResourceAsStream(RUNNER_SETTINGS_FILE);

        return loadPropertiesFromStream(input);
    }

    private Properties getHomeProperties() {
        final InputStream input;

        try {
            input = new FileInputStream(System.getProperty("user.home") + File.separator + WEBCAT_SETTINGS_DIRECTORY + File.separator + WEBCAT_SETTINGS_FILE);
        } catch (FileNotFoundException e) {
            return new Properties();
        }

        return loadPropertiesFromStream(input);
    }

    private Properties loadPropertiesFromStream(InputStream input) {
        final Properties properties = new Properties();

        if(input == null) {
            return properties;
        }

        try {
            properties.load(input);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read properties file: " + e.getMessage());
            return new Properties();
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not close properties file: " + e.getMessage());
            }
        }

        return properties;
    }

    public boolean isDebug() {
        return "true".equalsIgnoreCase(configMap.get(Config.DEBUG));
    }

    public Map<Config,String> getRaw() {
        return configMap;
    }

    public String getHost() {
        return configMap.get(Config.HOST) + getRunnerProperties().getProperty(WEBCAT_ENDPOINT);
    }

    public List<String> getIntent() {
        final String intentJson = configMap.get(Config.INTENT);

        if(intentJson.isEmpty()) {
            return new ArrayList<String>();
        }

        final Map parsedIntent = new GsonBuilder().create().fromJson(intentJson, Map.class);
        final List<String> features = (List<String>)parsedIntent.get("value");

        return features;
    }

    enum Config {
        PROJECT_TOKEN("WEBCAT_PROJECT_TOKEN"),
        USER_TOKEN("WEBCAT_USER_TOKEN"),
        HOST("WEBCAT_HOST"),
        PROJECT("WEBCAT_PROJECT"),
        INTENT("WEBCAT_INTENT"),
        CI_JENKINS("BUILD_NUMBER"),
        CI_CIRCLE("CIRCLE_BUILD_NUM"),
        CI_TRAVIS("TRAVIS_JOB_NUMBER"),
        CI_BAMBOO("bamboo.buildNumber"),
        CI_CODESHIP("CI_BUILD_NUMBER"),
        DEBUG("WEBCAT_DEBUG")
        ;

        private final String var;

        Config(String var) {
            this.var = var;
        }

        public String getVar() {
            return var;
        }

        @Override
        public String toString() {
            return getVar();
        }
    };

    public String getProjectToken() {
        return configMap.get(Config.PROJECT_TOKEN);
    }

    public String getProject() {
        return configMap.get(Config.PROJECT);
    }

    public boolean shouldPublishResults() {
        return configMap.get(Config.CI_BAMBOO) != null ||
                configMap.get(Config.CI_CIRCLE) != null  ||
                configMap.get(Config.CI_CODESHIP) != null  ||
                configMap.get(Config.CI_JENKINS) != null  ||
                configMap.get(Config.CI_JENKINS) != null;
    }
}
