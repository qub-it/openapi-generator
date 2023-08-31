package org.openapitools.codegen.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.ParseOptions;

public class MergedSpecBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedSpecBuilder.class);

    private final String inputSpecRootDirectory;
    private final String mergeFileName;

    public MergedSpecBuilder(final String rootDirectory, final String mergeFileName) {
        this.inputSpecRootDirectory = rootDirectory;
        this.mergeFileName = mergeFileName;
    }

    public String buildMergedSpec() {
        deleteMergedFileFromPreviousRun();
        List<String> specRelatedPaths = getAllSpecFilesInDirectory();
        if (specRelatedPaths.isEmpty()) {
            throw new RuntimeException("Spec directory doesn't contain any specification");
        }
        LOGGER.info("In spec root directory {} found specs {}", inputSpecRootDirectory, specRelatedPaths);

        String openapiVersion = null;
        boolean isJson = false;
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        List<SpecWithPaths> allPaths = new ArrayList<>();

        for (String specRelatedPath : specRelatedPaths) {
            String specPath = inputSpecRootDirectory + File.separator + specRelatedPath;
            try {
                LOGGER.info("Reading spec: {}", specPath);

                OpenAPI result = new OpenAPIParser().readLocation(specPath, new ArrayList<>(), options).getOpenAPI();

                if (openapiVersion == null) {
                    openapiVersion = result.getOpenapi();
                    if (specRelatedPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
                        isJson = true;
                    }
                }

                Map<String, SecurityScheme> securitySchemes = Optional.ofNullable(result.getComponents())
                        .map(components -> components.getSecuritySchemes()).orElseGet(() -> null);

                allPaths.add(new SpecWithPaths(specRelatedPath, result.getPaths().keySet(), securitySchemes));
            } catch (Exception e) {
                LOGGER.error("Failed to read file: {}. It would be ignored", specPath);
            }
        }

        Map<String, Object> mergedSpec = generatedMergedSpec(openapiVersion, allPaths);
        String mergedFilename = this.mergeFileName + (isJson ? ".json" : ".yaml");
        Path mergedFilePath = Paths.get(inputSpecRootDirectory, mergedFilename);

        try {
            ObjectMapper objectMapper = isJson ? new ObjectMapper() : new ObjectMapper(new YAMLFactory());
            Files.write(mergedFilePath, objectMapper.writeValueAsBytes(mergedSpec), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mergedFilePath.toString();
    }

    private static Map<String, Object> generatedMergedSpec(String openapiVersion, List<SpecWithPaths> allPaths) {
        Map<String, Object> spec = generateHeader(openapiVersion);
        Map<String, Object> paths = new HashMap<>();
        spec.put("paths", paths);

        // Create a new map to hold the collected security schemes
        Map<String, SecurityScheme> collectedSecuritySchemes = new HashMap<>();

        for (SpecWithPaths specWithPaths : allPaths) {
            for (String path : specWithPaths.paths) {
                String specRelatedPath = "./" + specWithPaths.specRelatedPath + "#/paths/" + path.replace("/", "~1");
                paths.put(path, ImmutableMap.of("$ref", specRelatedPath));
            }
            if (specWithPaths.securitySchemes != null) {
                collectedSecuritySchemes.putAll(specWithPaths.securitySchemes);
            }
        }

        if (!collectedSecuritySchemes.isEmpty()) {
            Map<String, Object> components = new HashMap<>();
            Map<String, Object> securitySchemes = new HashMap<>();

            for (Map.Entry<String, SecurityScheme> entry : collectedSecuritySchemes.entrySet()) {
                Map<String, Object> securitySchemeMap = new HashMap<>();
                SecurityScheme securityScheme = entry.getValue();

                securitySchemeMap.put("type", securityScheme.getType().toString());
                securitySchemeMap.put("scheme", securityScheme.getScheme());
                securitySchemes.put(entry.getKey(), securitySchemeMap);
            }

            components.put("securitySchemes", securitySchemes);
            spec.put("components", components);
        }

        return spec;

    }

    private static Map<String, Object> generateHeader(String openapiVersion) {
        Map<String, Object> map = new HashMap<>();
        map.put("openapi", openapiVersion);
        map.put("info", ImmutableMap.of("title", "merged spec", "description", "merged spec", "version", "1.0.0"));
        map.put("servers", Collections.singleton(ImmutableMap.of("url", "http://localhost:8080")));
        return map;
    }

    private List<String> getAllSpecFilesInDirectory() {
        Path rootDirectory = new File(inputSpecRootDirectory).toPath();
        try (Stream<Path> pathStream = Files.walk(rootDirectory)) {
            return pathStream.filter(path -> !Files.isDirectory(path)).map(path -> rootDirectory.relativize(path).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Exception while listing files in spec root directory: " + inputSpecRootDirectory, e);
        }
    }

    private void deleteMergedFileFromPreviousRun() {
        try {
            Files.deleteIfExists(Paths.get(inputSpecRootDirectory + File.separator + mergeFileName + ".json"));
        } catch (IOException e) {
        }
        try {
            Files.deleteIfExists(Paths.get(inputSpecRootDirectory + File.separator + mergeFileName + ".yaml"));
        } catch (IOException e) {
        }
    }

    private static class SpecWithPaths {
        private final String specRelatedPath;
        private final Set<String> paths;
        private final Map<String, SecurityScheme> securitySchemes;

        private SpecWithPaths(final String specRelatedPath, final Set<String> paths,
                Map<String, SecurityScheme> securitySchemes) {
            this.specRelatedPath = specRelatedPath;
            this.paths = paths;
            this.securitySchemes = securitySchemes;
        }
    }
}
