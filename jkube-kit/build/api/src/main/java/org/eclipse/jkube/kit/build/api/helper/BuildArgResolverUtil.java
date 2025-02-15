/*
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.kit.build.api.helper;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jkube.kit.common.JKubeConfiguration;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class BuildArgResolverUtil {

  private static final String ARG_PREFIX = "docker.buildArg.";

  private BuildArgResolverUtil() {
  }

  /**
   * Merges Docker Build Args from the following source in the mentioned order of precedence (moving from higher to lower precedence):
   * <ul>
   *   <li>Build Args specified directly in ImageConfiguration</li>
   *   <li>Build Args specified via System Properties</li>
   *   <li>Build Args specified via Project Properties</li>
   *   <li>Build Args specified via Plugin configuration</li>
   *   <li>Docker Proxy Build Args detected from ~/.docker/config.json</li>
   * </ul>
   *
   * <i><b>Note:</b> When identical Build Args are specified in multiple sources, their values are overridden according to the established order of precedence. A warning is also logged to highlight the Build Arg and the updated value.</i>
   *
   * @param imageConfig ImageConfiguration where to get the Build Args from.
   * @param configuration {@link JKubeConfiguration}.
   * @return a Map containing merged Build Args from all sources.
   */
  public static Map<String, String> mergeBuildArgsIncludingLocalDockerConfigProxySettings(ImageConfiguration imageConfig,
      JKubeConfiguration configuration, KitLogger logger) {
    List<Map<String, String>> buildArgSources = new ArrayList<>();

    // Add build arg sources following order of precedence
    buildArgSources.add(buildArgsFromDockerConfig());
    buildArgSources.add(configuration.getBuildArgs());
    buildArgSources.add(buildArgsFromProperties(configuration.getProject().getProperties()));
    buildArgSources.add(buildArgsFromProperties(System.getProperties()));
    buildArgSources.add(imageConfig.getBuild().getArgs());
    return mergeBuildArgsFrom(buildArgSources, logger);
  }

  /**
   * Merges Docker Build Args from the following source in the mentioned order of precedence (moving from higher to lower precedence):
   * <ul>
   *   <li>Build Args specified directly in ImageConfiguration</li>
   *   <li>Build Args specified via System Properties</li>
   *   <li>Build Args specified via Project Properties</li>
   *   <li>Build Args specified via Plugin configuration</li>
   * </ul>
   *
   * <i><b>Note:</b> When identical Build Args are specified in multiple sources, their values are overridden according to the established order of precedence. A warning is also logged to highlight the Build Arg and the updated value.</i>
   *
   * @param imageConfig ImageConfiguration where to get the Build Args from.
   * @param configuration {@link JKubeConfiguration}.
   * @return a Map containing merged Build Args from all sources.
   */
  public static Map<String, String> mergeBuildArgsWithoutLocalDockerConfigProxySettings(ImageConfiguration imageConfig,
      JKubeConfiguration configuration, KitLogger logger) {

    List<Map<String, String>> buildArgSources = new ArrayList<>();

    // Add build arg sources following increasing order of precedence
    buildArgSources.add(configuration.getBuildArgs());
    buildArgSources.add(buildArgsFromProperties(configuration.getProject().getProperties()));
    buildArgSources.add(buildArgsFromProperties(System.getProperties()));
    buildArgSources.add(imageConfig.getBuild().getArgs());
    return mergeBuildArgsFrom(buildArgSources, logger);
  }

  private static Map<String, String> mergeBuildArgsFrom(List<Map<String, String>> buildArgSources, KitLogger logger) {
    final Map<String, String> buildArgs = new HashMap<>();
    buildArgSources.stream()
        .filter(Objects::nonNull)
        .flatMap(map -> map.entrySet().stream())
        .forEach(entry -> {
          if (buildArgs.containsKey(entry.getKey())) {
            logger.warn(
                String.format("Multiple Build Args with the same key: %s=%s and %s=%s, overriding value of key to %s=%s",
                    entry.getKey(), buildArgs.get(entry.getKey()), entry.getKey(), entry.getValue(), entry.getKey(),
                    entry.getValue())
            );
          }
          buildArgs.put(entry.getKey(), entry.getValue());
        });
    return buildArgs;
  }

  private static Map<String, String> buildArgsFromProperties(Properties properties) {
    Map<String, String> buildArgs = new HashMap<>();
    for (Object keyObj : properties.keySet()) {
      String key = (String) keyObj;
      if (key.startsWith(ARG_PREFIX)) {
        String argKey = key.replaceFirst(ARG_PREFIX, "");
        String value = properties.getProperty(key);

        if (StringUtils.isNotBlank(value)) {
          buildArgs.put(argKey, value);
        }
      }
    }
    return buildArgs;
  }

  private static Map<String, String> buildArgsFromDockerConfig() {
    final Map<String, Object> dockerConfig = DockerFileUtil.readDockerConfig();
    if (dockerConfig == null) {
      return Collections.emptyMap();
    }

    // add proxies
    Map<String, String> buildArgs = new HashMap<>();
    if (dockerConfig.containsKey("proxies")) {
      final Map<String, Object> proxies = (Map<String, Object>) dockerConfig.get("proxies");
      if (proxies.containsKey("default")) {
        final Map<String, String> defaultProxyObj = (Map<String, String>) proxies.get("default");
        String[] proxyMapping = new String[] {
            "httpProxy", "http_proxy",
            "httpsProxy", "https_proxy",
            "noProxy", "no_proxy",
            "ftpProxy", "ftp_proxy"
        };

        for (int index = 0; index < proxyMapping.length; index += 2) {
          if (defaultProxyObj.containsKey(proxyMapping[index])) {
            buildArgs.put(ARG_PREFIX + proxyMapping[index + 1], defaultProxyObj.get(proxyMapping[index]));
          }
        }
      }
    }
    return buildArgs;
  }
}
