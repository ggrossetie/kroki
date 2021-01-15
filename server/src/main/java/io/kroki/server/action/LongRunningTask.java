package io.kroki.server.action;

public class LongRunningTask implements Runnable {
  @Override
  public void run() {
    try {
      System.out.println("Running long task");
      while (!Thread.interrupted()) {
        System.out.println("sleep for 10s");
        Thread.sleep(10000);
      }
    } catch (InterruptedException e) {
      // log error
    }
  }
}
