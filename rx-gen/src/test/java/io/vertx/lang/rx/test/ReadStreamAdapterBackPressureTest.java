package io.vertx.lang.rx.test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ReadStreamAdapterBackPressureTest<O> extends ReadStreamAdapterTestBase<Buffer, O> {

  protected abstract O toObservable(ReadStream<Buffer> stream, int maxBufferSize);

  protected abstract O flatMap(O obs, Function<Buffer, O> f);

  @Override
  protected Buffer buffer(String s) {
    return Buffer.buffer(s);
  }

  @Override
  protected String string(Buffer buffer) {
    return buffer.toString("UTF-8");
  }

  protected abstract long defaultMaxBufferSize();

  @Test
  public void testPause() {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream);
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    subscribe(observable, subscriber);
    subscriber.assertEmpty();
    stream.expectPause();
    for (int i = 0; i < defaultMaxBufferSize(); i++) {
      stream.emit(buffer("" + i));
    }
    stream.check();
    subscriber.assertEmpty();
    subscriber.request(1);
    subscriber.assertItem(buffer("0")).assertEmpty();
  }

  @Test
  public void testNoPauseWhenRequestingOne() {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>() {
      @Override
      public void onNext(Buffer buffer) {
        super.onNext(buffer);
        request(1);
      }
    }.prefetch(1);
    O observable = toObservable(stream);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"), buffer("1"), buffer("2"));
    stream.check();
  }

  @Test
  public void testUnsubscribeOnFirstItemFromBufferedDeliveredWhileRequesting() {
    for (int i = 1;i <= 3;i++) {
      TestReadStream<Buffer> stream = new TestReadStream<>();
      stream.expectPause();
      stream.expectResume();
      TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>() {
        @Override
        public void onNext(Buffer buffer) {
          super.onNext(buffer);
          unsubscribe();
        }
      }.prefetch(0);
      O observable = toObservable(stream, 2);
      subscribe(observable, subscriber);
      stream.emit(buffer("0"), buffer("1"));
      stream.assertPaused();
      subscriber.request(i);
      subscriber.assertItem(Buffer.buffer("0")).assertEmpty();
      stream.check();
    }
  }

  @Test
  public void testEndWithoutRequest() {
    testEndOrFailWithoutRequest(null);
  }

  @Test
  public void testFailWithoutRequest() {
    testEndOrFailWithoutRequest(new RuntimeException());
  }

  private void testEndOrFailWithoutRequest(Throwable err) {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    if (err == null) {
      stream.end();
      subscriber.assertCompleted();
    } else {
      stream.fail(err);
      subscriber.assertError(err);
    }
    stream.check();
    subscriber.assertEmpty();
  }

  @Test
  public void testNoResumeWhenRequestingBuffered() {
    AtomicBoolean resumed = new AtomicBoolean();
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"), buffer("1"));
    subscriber.request(1);
    assertEquals(false, resumed.get());
    stream.check();
  }

  @Test
  public void testEndDuringRequestResume() {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    stream.onResume(stream::end);
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    O observable = toObservable(stream, 10);
    subscribe(observable, subscriber);
    int count = 0;
    while (true) {
      if (!stream.emit(Buffer.buffer("" + count++))) {
        break;
      }
    }
    subscriber.request(count);
    for (int i = 0;i < count;i++) {
      subscriber.assertItem(Buffer.buffer("" + i));
    }
    subscriber.assertCompleted().assertEmpty();
    stream.check();
  }

  @Test
  public void testDeliverEndWhenPaused() {
    testDeliverEndOrFailWhenPaused(null);
  }

  @Test
  public void testDeliverFailWhenPaused() {
    testDeliverEndOrFailWhenPaused(new RuntimeException());
  }

  private void testDeliverEndOrFailWhenPaused(Throwable err) {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"), buffer("1"));
    stream.check();
    // We send events even though we are paused
    if (err == null) {
      stream.end();
    } else {
      stream.fail(err);
    }
    stream.expectResume();
    subscriber.request(2);
    if (err == null) {
      subscriber.assertItems(buffer("0"), buffer("1"));
      subscriber.assertCompleted();
    } else {
      subscriber.assertError(err);
    }
    subscriber.assertEmpty();
    stream.check();
  }

  @Test
  public void testEndWhenPaused() {
    testEndOrFailWhenPaused(null);
  }

  @Test
  public void testFailWhenPaused() {
    testEndOrFailWhenPaused(new RuntimeException());
  }

  private void testEndOrFailWhenPaused(Throwable err) {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"), buffer("1"));
    stream.assertPaused();
    if (err == null) {
      stream.end();
    } else {
      stream.fail(err);
    }
    stream.expectResume();
    subscriber.request(2);
    if (err == null) {
      subscriber.assertItems(buffer("0"), buffer("1"));
      subscriber.assertCompleted();
    } else {
      subscriber.assertError(err);
    }
    subscriber.assertEmpty();
    stream.check();
  }

  @Test
  public void testRequestDuringOnNext() {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>() {
      @Override
      public void onNext(Buffer buffer) {
        super.onNext(buffer);
        request(1);
      }
    }.prefetch(1);
    O observable = toObservable(stream);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"));
    subscriber.assertItem(buffer("0")).assertEmpty();
    stream.emit(buffer("1"));
    subscriber.assertItem(buffer("1")).assertEmpty();
    stream.emit(buffer("2"));
    subscriber.assertItem(buffer("2")).assertEmpty();
    stream.end();
    subscriber.assertCompleted().assertEmpty();
  }

  @Test
  public void testDeliverDuringResume() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    stream.onResume(() -> stream.emit(buffer("2")));
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.emit(Buffer.buffer("0"));
    stream.emit(Buffer.buffer("1"));
    subscriber.request(2);
    subscriber.assertItems(buffer("0"), buffer("1")).assertEmpty();
    stream.check();
  }

  @Test
  public void testEndDuringResume() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    stream.onResume(() -> {
      stream.end();
    });
    O observable = toObservable(stream, 4);
    subscribe(observable, subscriber);
    int count = 0;
    while (true) {
      if (!stream.emit(Buffer.buffer("" + count++))) {
        break;
      }
    }
    subscriber.request(count);
    for (int i = 0;i < count;i++) {
      subscriber.assertItem(Buffer.buffer("" + i));
    }
    subscriber.assertCompleted().assertEmpty();
    stream.check();
  }

  @Test
  public void testBufferDuringResume() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    stream.onResume(() -> stream.emit(buffer("2"), buffer("3")));
    stream.expectPause();
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"), buffer("1"));
    subscriber.request(2);
    subscriber.assertItem(buffer("0")).assertItem(buffer("1")).assertEmpty();
    stream.check();
  }

  @Test
  public void testFoo() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"));
    stream.end();
    subscriber.request(1);
    subscriber.assertItem(buffer("0")).assertCompleted().assertEmpty();
  }

  @Test
  public void testBar() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    stream.expectPause();
    O observable = toObservable(stream);
    subscribe(observable, subscriber);
    for (int i = 0; i < defaultMaxBufferSize(); i++) {
      stream.emit(buffer("" + i));
    }
    stream.end();
    subscriber.request(1);
    subscriber.assertItem(buffer("0")).assertEmpty();
  }

  @Test
  public void testUnsubscribeDuringOnNext() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>() {
      @Override
      public void onNext(Buffer buffer) {
        super.onNext(buffer);
        unsubscribe();
      }
    };
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream);
    subscribe(observable, subscriber);
    stream.emit(buffer("0"));
  }

  @Test
  public void testResubscribe() {
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>().prefetch(0);
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream, 2);
    subscribe(observable, subscriber);
    stream.expectPause();
    stream.emit(buffer("0"), buffer("1"));
    stream.check();
    stream.expectResume();
    subscriber.unsubscribe();
    stream.check();
    subscriber = new TestSubscriber<Buffer>().prefetch(0);
    subscribe(observable, subscriber);
    stream.emit(buffer("2"));
    stream.expectPause();
    stream.emit(buffer("3"));
    subscriber.assertEmpty();
    stream.check();
    stream.expectResume();
    subscriber.request(2);
    subscriber.assertItems(buffer("2"), buffer("3"));
    RuntimeException cause = new RuntimeException();
    stream.fail(cause);
    subscriber.assertError(cause);
    assertTrue(subscriber.isUnsubscribed());
    subscriber = new TestSubscriber<>();
    subscribe(observable, subscriber);
    stream.end();
    subscriber.assertCompleted();
    stream.check();
  }

  @Test
  public void testBackPressureBuffer() {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream, 20);
    TestSubscriber<Buffer> subscriber = new TestSubscriber<Buffer>() {
      @Override
      public void onSubscribe(Subscription sub) {
        super.onSubscribe(sub);
        request(5);
      }
    }.prefetch(0);
    subscribe(observable, subscriber);
    waitUntil(subscriber::isSubscribed);
    final AtomicInteger count = new AtomicInteger();
    stream.expectPause();
    stream.untilPaused(() -> {
      stream.emit(buffer("" + count.get()));
      count.incrementAndGet();
    });
    for (int i = 0;i < 5;i++) {
      subscriber.assertItem(buffer("" + i));
      stream.emit(Buffer.buffer("" + count));
      count.incrementAndGet();
    }
    subscriber.assertEmpty();
    stream.expectResume();
    subscriber.request(count.get() - 5);
    for (int i = 5;i < count.get(); i++) {
      subscriber.assertItem(buffer("" + i));
    }
    subscriber.assertEmpty();
    stream.end();
    subscriber.assertCompleted().assertEmpty();
  }

  @Test
  public void testChained() throws Exception {
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream);
    TestSubscriber<Buffer> subscriber = new TestSubscriber<>();
    subscriber.prefetch(1);
    subscribe(observable, subscriber);
    waitUntil(subscriber::isSubscribed);
    stream.emit(buffer("foo"));
    stream.end();
    subscriber.assertItem(buffer("foo"));
    subscriber.assertCompleted();
  }

  @Test
  public void testFlatMap() {
    TestReadStream<Buffer> stream1 = new TestReadStream<>();
    O obs1 = toObservable(stream1);
    TestReadStream<Buffer> stream2 = new TestReadStream<>();
    O obs2 = toObservable(stream2);
    O obs3 = flatMap(obs1, s -> obs2);
    TestSubscriber<Buffer> sub = new TestSubscriber<>();
    sub.prefetch(1);
    subscribe(obs3, sub);
    stream1.emit(buffer("foo"));
    stream1.end();
    stream2.emit(buffer("bar"));
    stream2.end();
    sub.assertItem(buffer("bar"));
    sub.assertCompleted();
  }

  @Test
  public void testCancelWhenSubscribedPropagatesToStream() {
    Buffer expected = buffer("something");
    TestReadStream<Buffer> stream = new TestReadStream<>();
    O observable = toObservable(stream);
    TestSubscriber<Buffer> sub = new TestSubscriber<Buffer>() {
      @Override
      public void onNext(Buffer b) {
        assertSame(b, expected);
        super.onNext(b);
        unsubscribe();
        stream.assertHasNoItemHandler();
      }
    };
    sub.prefetch(1);
    subscribe(observable, sub);
    sub.assertEmpty();
    stream.emit(expected);
    sub.assertItem(expected);
    sub.assertEmpty();
    stream.assertHasNoItemHandler();
  }
}
