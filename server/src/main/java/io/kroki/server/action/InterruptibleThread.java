package io.kroki.server.action;

public class InterruptibleThread extends Thread {
  protected volatile boolean running = true;

  public InterruptibleThread(Runnable target) {
    super(target);
  }

  @Override
  public void interrupt() {
    super.interrupt();
    System.out.println("Assign running to false!");
    running = false;
  }

  @Override
  public void run() {
    while (running && !isInterrupted()) {
      System.out.println("Running...");
      super.run();
      System.out.println("Completed! assign running to false!");
      running = false;
    }
    System.out.println("???");
  }
}
