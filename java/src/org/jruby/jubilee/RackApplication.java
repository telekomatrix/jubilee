package org.jruby.jubilee;

import org.jruby.Ruby;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.jubilee.impl.DefaultRackEnvironment;
import org.jruby.jubilee.impl.RubyIORackInput;
import org.jruby.runtime.builtin.IRubyObject;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;

import java.util.concurrent.*;

/**
 * Created with IntelliJ IDEA.
 * User: isaiah
 * Date: 11/29/12
 * Time: 5:40 PM
 */
public class RackApplication {
  private IRubyObject app;
  private boolean ssl;
  private Buffer bodyBuf;

  private ExecutorService exec;

  public RackApplication(IRubyObject app, boolean ssl) {
    this.app = app;
    this.ssl = ssl;
    bodyBuf = new Buffer(0);
    exec = Executors.newCachedThreadPool();
  }

  public void call(final HttpServerRequest request) {
    final Ruby runtime = app.getRuntime();
    final CountDownLatch bodyLatch = new CountDownLatch(1);
    request.dataHandler(new Handler<Buffer>() {
      @Override
      public void handle(Buffer buffer) {
        bodyBuf.appendBuffer(buffer);
      }
    });
    // TODO optimize by use NullIO when there is no body here.
    Runnable task = new Runnable() {
      @Override
      public void run() {
        RackInput input = new RubyIORackInput(runtime, bodyBuf, bodyLatch);
        RackEnvironment env = new DefaultRackEnvironment(runtime, request, input, ssl);
        IRubyObject result = app.callMethod(runtime.getCurrentContext(), "call", env.getEnv());
        RackResponse response = (RackResponse) JavaEmbedUtils.rubyToJava(runtime, result, RackResponse.class);
        response.respond(request.response);
      }
    };
    exec.execute(task);
    request.endHandler(new SimpleHandler() {
      @Override
      protected void handle() {
        bodyLatch.countDown();
      }
    });
  }

  public void shutdown() {
    exec.shutdown();
  }
}
