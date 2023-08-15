package io.kroki.server.action;

import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import io.kroki.server.unit.TimeValue;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.nio.ByteBuffer;

public class NuCommander {

  protected TimeValue commandTimeout;
  protected TimeValue readStdoutTimeout;
  protected TimeValue readStderrTimeout;
  private final CommandStatusHandler commandStatusHandler;

  public NuCommander(JsonObject config) {
    this(config, new CommandStatusHandler() {
      public byte[] handle(int exitValue, byte[] stdout, byte[] stderr) {
        return CommandStatusHandler.super.handle(exitValue, stdout, stderr);
      }
    });
  }

  public NuCommander(JsonObject config, CommandStatusHandler commandStatusHandler) {
    String commandTimeoutValue = config.getString("KROKI_COMMAND_TIMEOUT", "5s");
    this.commandTimeout = TimeValue.parseTimeValue(commandTimeoutValue, "KROKI_COMMAND_TIMEOUT");
    String readStdoutTimeoutValue = config.getString("KROKI_COMMAND_READ_STDOUT_TIMEOUT", "2s");
    this.readStdoutTimeout = TimeValue.parseTimeValue(readStdoutTimeoutValue, "KROKI_COMMAND_READ_STDOUT_TIMEOUT");
    String readStderrTimeoutValue = config.getString("KROKI_COMMAND_READ_STDERR_TIMEOUT", "2s");
    this.readStderrTimeout = TimeValue.parseTimeValue(readStderrTimeoutValue, "KROKI_COMMAND_READ_STDERR_TIMEOUT");
    this.commandStatusHandler = commandStatusHandler;
  }

  public void execute(byte[] source, Context context, Handler<AsyncResult<Buffer>> resultHandler, String... cmd) throws InterruptedException {
    NuProcessBuilder pb = new NuProcessBuilder(cmd);
    pb.setProcessListener(new ProcessHandler(resultHandler, commandStatusHandler));
    context.runOnContext(v -> {
      NuProcess process = pb.start();
      process.writeStdin(ByteBuffer.wrap(source));
      process.closeStdin(false);
    });
  }
}

class ProcessHandler extends NuAbstractProcessHandler {
  private final Buffer stdout = Buffer.buffer();
  private final Buffer stderr = Buffer.buffer();
  private final CommandStatusHandler commandStatusHandler;
  private NuProcess nuProcess;
  private final Handler<AsyncResult<Buffer>> resultHandler;

  public ProcessHandler(Handler<AsyncResult<Buffer>> resultHandler, CommandStatusHandler commandStatusHandler) {
    this.resultHandler = resultHandler;
    this.commandStatusHandler = commandStatusHandler;
  }

  @Override
  public void onPreStart(NuProcess nuProcess) {
    super.onPreStart(nuProcess);
  }

  @Override
  public boolean onStdinReady(ByteBuffer buffer) {
    return super.onStdinReady(buffer);
  }

  @Override
  public void onStart(NuProcess nuProcess) {
    this.nuProcess = nuProcess;
  }

  public void onStdout(ByteBuffer byteBuffer, boolean closed) {
    if (byteBuffer != null && byteBuffer.remaining() > 0) {
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes);
      stdout.appendBytes(bytes);
    }
  }

  @Override
  public void onStderr(ByteBuffer byteBuffer, boolean closed) {
    if (byteBuffer != null && byteBuffer.remaining() > 0) {
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes);
      stderr.appendBytes(bytes);
    }
  }

  @Override
  public void onExit(int statusCode) {
    try {
      byte[] result = commandStatusHandler.handle(statusCode, stdout.getBytes(), stderr.getBytes());
      resultHandler.handle(new AsyncResult<>() {
        @Override
        public Buffer result() {
          return Buffer.buffer(result);
        }

        @Override
        public Throwable cause() {
          return null;
        }

        @Override
        public boolean succeeded() {
          return true;
        }

        @Override
        public boolean failed() {
          return false;
        }
      });
    } catch (Exception e) {
      resultHandler.handle(new AsyncResult<>() {
        @Override
        public Buffer result() {
          return null;
        }

        @Override
        public Throwable cause() {
          return e;
        }

        @Override
        public boolean succeeded() {
          return false;
        }

        @Override
        public boolean failed() {
          return true;
        }
      });
    }
  }
}
