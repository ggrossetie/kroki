package io.kroki.server.action;

import io.vertx.core.Vertx;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Executor {

  public static void execute(Vertx vertx) throws InterruptedException, TimeoutException, ExecutionException {
    //WorkerExecutor workerExecutor = vertx.createSharedWorkerExecutor("plantuml", 2, 2, TimeUnit.SECONDS);
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    Future future = executor.submit(() -> {
      try {
        System.out.println("sleep for 20s");
        Thread.sleep(20000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    executor.schedule(() -> {
      System.out.println("future.cancel after 5s!");
      future.cancel(true);
    }, 5000, TimeUnit.MILLISECONDS);
    System.out.println("executor.shutdown");
    executor.shutdown();
    System.out.println("awaitTermination for 5s");
    executor.awaitTermination(5, TimeUnit.SECONDS);
/*
    Future future = Future.future(promise -> {
      System.out.println("Running long task");
      System.out.println("sleep for 20s");
      try {
        Thread.sleep(20000);
        System.out.println("AFTER!");
      } catch (InterruptedException e) {
        System.out.println("InterruptedException!!!");
      }
      promise.complete("OK");
    });

    try {
      System.out.println("wait at most 5s");
      Object o = future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
      System.out.println("result is: " + o);
    } catch (TimeoutException e) {
      System.out.println("TimeoutException");
      System.out.println("sleep for 10s");
      Thread.sleep(10000);
    }
    //System.out.println("sleep for 10s");
    //Thread.sleep(10000);
    */
/*


    Promise<String> promise = Promise.promise();
    vertx.setTimer(2000, handler -> {
      if (!promise.future().isComplete()) {
        System.out.println("FAIL PROMISE!");
        promise.fail(new RuntimeException("TOO LATE!"));
      }
    });
    promise.future().onComplete(handler -> {
      System.out.println("COMPLETED!");
    });
    promise.future().onFailure(handler -> {
      System.out.println("FAILURE!");
    });
    promise.future().onSuccess(handler -> {
      System.out.println("SUCCESS!");
    });

    Future


    vertx.executeBlocking(future -> {
      System.out.println("Executing blocking code...");
      System.out.println("Sleeping for 20s...");
      try {
        Thread.sleep(20000);
        future.complete("OK");
        promise.complete("OK");
      } catch (InterruptedException e) {
        System.out.println("InterruptedException!!");
      }
    }, res -> {
      if (res.failed()) {
        routingContext.fail(res.cause());
        return;
      }
      byte[] result = (byte[]) res.result();
      diagramResponse.end(response, sourceDecoded, fileFormat, result);
    });

    System.out.println("Executing blocking code...");
    System.out.println("Sleeping for 20s...");
    Thread.sleep(20000);
    System.out.println("future.complete!");
    promise.complete("OK");


    promise.future().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
 */
  }
}
