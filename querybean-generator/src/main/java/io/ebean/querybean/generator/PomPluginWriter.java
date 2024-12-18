package io.ebean.querybean.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

final class PomPluginWriter {

  private static final String PLUGIN =
      "  <!-- generated by ebean query generator -->\n"
          + "      <plugin>\n"
          + "        <groupId>io.ebean</groupId>\n"
          + "        <artifactId>ebean-maven-plugin</artifactId>\n"
          + "        <version>%s</version>\n"
          + "        <executions>\n"
          + "          <execution>\n"
          + "            <!-- Will enhance entity classes to add missing spi provides -->"
          + "            <goals>\n"
          + "              <goal>enhance</goal>\n"
          + "              <goal>testEnhance</goal>\n"
          + "            </goals>\n"
          + "          </execution>\n"
          + "        </executions>\n"
          + "      </plugin>\n"
          + "    ";

  static void addPlugin2Pom() throws IOException {

    if (disabled()) {
      return;
    }

    var pomPath = APContext.getBuildResource("").getParent().resolve("pom.xml");
    if (!pomPath.toFile().exists()) {
      return;
    }

    var pomContent = Files.readString(pomPath);
    // if not already present in pom.xml
    if (pomContent.contains("ebean-maven-plugin")) {
      return;
    }
    APContext.logNote("Adding ebean-maven-plugin to pom");
    var pluginsIndex = pomContent.indexOf("</plugins>");
    var builder = new StringBuilder(pomContent);
    if (pluginsIndex != -1) {
      builder.insert(
          pluginsIndex,
          String.format(PLUGIN, PomPluginWriter.class.getPackage().getImplementationVersion()));

      Files.writeString(
          pomPath, builder.toString(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
  }

  private static boolean disabled() {
    return !APContext.getOption("buildPlugin").map(Boolean::valueOf).orElse(true);
  }
}
