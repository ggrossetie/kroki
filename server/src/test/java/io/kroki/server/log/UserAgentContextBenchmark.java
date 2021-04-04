package io.kroki.server.log;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import ua_parser.Parser;

@State(Scope.Benchmark)
public class UserAgentContextBenchmark {

  private final UserAgentContext userAgentContext = new UserAgentContext(new Parser());

  @Benchmark
  @Fork(value = 1, warmups = 0)
  @Warmup(iterations = 3, time = 5)
  @BenchmarkMode(Mode.Throughput)
  public void manySpaces() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki-go                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            1.2.3", mdcAdapter);
  }

  @Benchmark
  @Fork(value = 1, warmups = 0)
  @Warmup(iterations = 3, time = 5)
  @BenchmarkMode(Mode.Throughput)
  public void a_standard() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki-go 1.2.3", mdcAdapter);
  }


  @Benchmark
  @Fork(value = 1, warmups = 0)
  @Warmup(iterations = 3, time = 5)
  @BenchmarkMode(Mode.Throughput)
  public void a_nonGreedy() {
    MDCAdapter mdcAdapter = new BasicMDCAdapter();
    userAgentContext.put("kroki a b c d e f g h i j k l m n o p q r s t u v w x y z a b c d e f g h i j k l m n o p q r s t u v w x y z 1.2.3", mdcAdapter);
  }
}
