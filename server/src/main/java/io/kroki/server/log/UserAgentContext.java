package io.kroki.server.log;

import org.slf4j.spi.MDCAdapter;
import ua_parser.Client;
import ua_parser.Device;
import ua_parser.OS;
import ua_parser.Parser;
import ua_parser.UserAgent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserAgentContext {
  private final String UNKNOWN_VALUE = "Unknown";
  private final Parser userAgentParser;
  private static final Pattern VERSION_RX = Pattern.compile("([a-zA-Z][a-zA-Z0-9-._ ]+)[ /](\\d+(?:\\.\\d+)*)$");

  public UserAgentContext(Parser userAgentParser) {
    this.userAgentParser = userAgentParser;
  }

  public void put(String userAgentValue, MDCAdapter mdcAdapter) {
    if (userAgentValue != null) {
      mdcAdapter.put("user_agent", userAgentValue);
      // replace extra spaces
      userAgentValue = userAgentValue.trim().replaceAll(" +", " ");
      Client client = userAgentParser.parse(userAgentValue);
      UserAgent userAgent = client.userAgent;
      if (userAgent == UserAgent.OTHER) {
        Matcher versionMatcher = VERSION_RX.matcher(userAgentValue);
        if (versionMatcher.matches()) {
          String name = versionMatcher.group(1);
          String version = versionMatcher.group(2);
          mdcAdapter.put("user_agent_family", name);
          mdcAdapter.put("user_agent_version", version);
          mdcAdapter.put("user_agent_device_family", "Unknown");
          mdcAdapter.put("user_agent_os_family", "Unknown");
          return;
        }
        // continue...
      }
      mdcAdapter.put("user_agent_family", userAgent == UserAgent.OTHER ? UNKNOWN_VALUE : userAgent.family);
      if (userAgent.major != null) {
        String minor = userAgent.minor != null ? userAgent.minor : "x";
        String patch = userAgent.patch != null ? userAgent.patch : "x";
        mdcAdapter.put("user_agent_version", userAgent.major + "." + minor + "." + patch);
      }
      OS os = client.os;
      mdcAdapter.put("user_agent_os_family", os == OS.OTHER ? UNKNOWN_VALUE : os.family);
      if (os.major != null) {
        String minor = os.minor != null ? os.minor : "x";
        String patch = os.patch != null ? os.patch : "x";
        String patchMinor = os.patchMinor != null ? os.patchMinor : "x";
        mdcAdapter.put("user_agent_os_version", os.major + "." + minor + "." + patch + "." + patchMinor);
      }
      Device device = client.device;
      mdcAdapter.put("user_agent_device_family", device == Device.OTHER ? UNKNOWN_VALUE : device.family);
    }
  }
}
