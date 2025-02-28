package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.interfaces.ClientContext;
import com.launchdarkly.sdk.server.interfaces.EventSender;
import com.launchdarkly.sdk.server.interfaces.EventSenderFactory;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;
import com.launchdarkly.testhelpers.httptest.Handler;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestInfo;

import org.junit.Test;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.interfaces.EventSender.EventDataKind.ANALYTICS;
import static com.launchdarkly.sdk.server.interfaces.EventSender.EventDataKind.DIAGNOSTICS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DefaultEventSenderTest {
  private static final String SDK_KEY = "SDK_KEY";
  private static final String FAKE_DATA = "some data";
  private static final SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
      Locale.US);
  private static final Duration BRIEF_RETRY_DELAY = Duration.ofMillis(50);
  
  private static EventSender makeEventSender() {
    return makeEventSender(LDConfig.DEFAULT);
  }

  private static EventSender makeEventSender(LDConfig config) {
    return new DefaultEventSender(
        clientContext(SDK_KEY, config).getHttp(),
        BRIEF_RETRY_DELAY
        );
  }

  @Test
  public void factoryCreatesDefaultSenderWithDefaultRetryDelay() throws Exception {
    EventSenderFactory f = new DefaultEventSender.Factory();
    ClientContext context = clientContext(SDK_KEY, LDConfig.DEFAULT);
    try (EventSender es = f.createEventSender(context.getBasic(), context.getHttp())) {
      assertThat(es, isA(EventSender.class));
      assertThat(((DefaultEventSender)es).retryDelay, equalTo(DefaultEventSender.DEFAULT_RETRY_DELAY));
    }
  }

  @Test
  public void constructorUsesDefaultRetryDelayIfNotSpecified() throws Exception {
    ClientContext context = clientContext(SDK_KEY, LDConfig.DEFAULT);
    try (EventSender es = new DefaultEventSender(context.getHttp(), null)) {
      assertThat(((DefaultEventSender)es).retryDelay, equalTo(DefaultEventSender.DEFAULT_RETRY_DELAY));
    }
  }
  
  @Test
  public void analyticsDataIsDelivered() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void diagnosticDataIsDelivered() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();      
      assertEquals("/diagnostic", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void defaultHeadersAreSentForAnalytics() throws Exception {
    HttpConfiguration httpConfig = clientContext(SDK_KEY, LDConfig.DEFAULT).getHttp();
    
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();
      for (Map.Entry<String, String> kv: httpConfig.getDefaultHeaders()) {
        assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
      }
    }
  }

  @Test
  public void defaultHeadersAreSentForDiagnostics() throws Exception {
    HttpConfiguration httpConfig = clientContext(SDK_KEY, LDConfig.DEFAULT).getHttp();
    
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();      
      for (Map.Entry<String, String> kv: httpConfig.getDefaultHeaders()) {
        assertThat(req.getHeader(kv.getKey()), equalTo(kv.getValue()));
      }
    }
  }

  @Test
  public void eventSchemaIsSentForAnalytics() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      assertThat(req.getHeader("X-LaunchDarkly-Event-Schema"), equalTo("3"));
    }
  }

  @Test
  public void eventPayloadIdIsSentForAnalytics() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      String payloadHeaderValue = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(payloadHeaderValue, notNullValue(String.class));
      assertThat(UUID.fromString(payloadHeaderValue), notNullValue(UUID.class));
    }
  }

  @Test
  public void eventPayloadIdReusedOnRetry() throws Exception {
    Handler errorResponse = Handlers.status(429);
    Handler errorThenSuccess = Handlers.sequential(errorResponse, eventsSuccessResponse(), eventsSuccessResponse());

    try (HttpServer server = HttpServer.start(errorThenSuccess)) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
        es.sendEventData(ANALYTICS, FAKE_DATA, 1, server.getUri());
      }

      // Failed response request
      RequestInfo req = server.getRecorder().requireRequest();
      String payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      // Retry request has same payload ID as failed request
      req = server.getRecorder().requireRequest();
      String retryId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, equalTo(payloadId));
      // Second request has different payload ID from first request
      req = server.getRecorder().requireRequest();
      payloadId = req.getHeader("X-LaunchDarkly-Payload-ID");
      assertThat(retryId, not(equalTo(payloadId)));
    }
  }
  
  @Test
  public void eventSchemaNotSetOnDiagnosticEvents() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
      }

      RequestInfo req = server.getRecorder().requireRequest();
      assertNull(req.getHeader("X-LaunchDarkly-Event-Schema"));
    }
  }

  @Test
  public void http400ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(400);
  }

  @Test
  public void http401ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(401);
  }

  @Test
  public void http403ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(403);
  }

  // Cannot test our retry logic for 408, because OkHttp insists on doing its own retry on 408 so that
  // we never actually see that response status.
//  @Test
//  public void http408ErrorIsRecoverable() throws Exception {
//    testRecoverableHttpError(408);
//  }

  @Test
  public void http429ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(429);
  }

  @Test
  public void http500ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(500);
  }
 
  @Test
  public void serverDateIsParsed() throws Exception {
    long fakeTime = ((new Date().getTime() - 100000) / 1000) * 1000; // don't expect millisecond precision
    Handler resp = Handlers.all(eventsSuccessResponse(), addDateHeader(new Date(fakeTime)));

    try (HttpServer server = HttpServer.start(resp)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
        
        assertNotNull(result.getTimeFromServer());
        assertEquals(fakeTime, result.getTimeFromServer().getTime());
      }
    }
  }

  @Test
  public void invalidServerDateIsIgnored() throws Exception {
    Handler resp = Handlers.all(eventsSuccessResponse(), Handlers.header("Date", "not a date"));

    try (HttpServer server = HttpServer.start(resp)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
        
        assertTrue(result.isSuccess());
        assertNull(result.getTimeFromServer());
      }
    }
  }

  @Test
  public void testSpecialHttpConfigurations() throws Exception {
    Handler handler = eventsSuccessResponse();
    
    TestHttpUtil.testWithSpecialHttpConfigurations(handler,
        (targetUri, goodHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(goodHttpConfig).build();
          
          try (EventSender es = makeEventSender(config)) {
            EventSender.Result result = es.sendEventData(ANALYTICS, FAKE_DATA, 1, targetUri);
            
            assertTrue(result.isSuccess());
            assertFalse(result.isMustShutDown());
          }
        },
        
        (targetUri, badHttpConfig) -> {
          LDConfig config = new LDConfig.Builder().http(badHttpConfig).build();
          
          try (EventSender es = makeEventSender(config)) {
            EventSender.Result result = es.sendEventData(ANALYTICS, FAKE_DATA, 1, targetUri);
            
            assertFalse(result.isSuccess());
            assertFalse(result.isMustShutDown());
          }
        }
        );
  }
  
  @Test
  public void baseUriDoesNotNeedTrailingSlash() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        URI uriWithoutSlash = URI.create(server.getUri().toString().replaceAll("/$", ""));
        EventSender.Result result = es.sendEventData(ANALYTICS, FAKE_DATA, 1, uriWithoutSlash);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void baseUriCanHaveContextPath() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        URI baseUri = server.getUri().resolve("/context/path");
        EventSender.Result result = es.sendEventData(ANALYTICS, FAKE_DATA, 1, baseUri);
        
        assertTrue(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }
      
      RequestInfo req = server.getRecorder().requireRequest();   
      assertEquals("/context/path/bulk", req.getPath());
      assertThat(req.getHeader("content-type"), equalToIgnoringCase("application/json; charset=utf-8"));
      assertEquals(FAKE_DATA, req.getBody());
    }
  }

  @Test
  public void nothingIsSentForNullData() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result1 = es.sendEventData(ANALYTICS, null, 0, server.getUri());
        EventSender.Result result2 = es.sendEventData(DIAGNOSTICS, null, 0, server.getUri());
        
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals(0, server.getRecorder().count());
      }
    }
  }

  @Test
  public void nothingIsSentForEmptyData() throws Exception {
    try (HttpServer server = HttpServer.start(eventsSuccessResponse())) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result1 = es.sendEventData(ANALYTICS, "", 0, server.getUri());
        EventSender.Result result2 = es.sendEventData(DIAGNOSTICS, "", 0, server.getUri());
        
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals(0, server.getRecorder().count());
      }
    }
  }
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    Handler errorResponse = Handlers.status(status);
    
    try (HttpServer server = HttpServer.start(errorResponse)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
        
        assertFalse(result.isSuccess());
        assertTrue(result.isMustShutDown());
      }

      server.getRecorder().requireRequest();
      
      // it does not retry after this type of error, so there are no more requests
      server.getRecorder().requireNoRequests(Duration.ofMillis(100));
    }
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    Handler errorResponse = Handlers.status(status);
    Handler errorsThenSuccess = Handlers.sequential(errorResponse, errorResponse, eventsSuccessResponse());
    // send two errors in a row, because the flush will be retried one time
    
    try (HttpServer server = HttpServer.start(errorsThenSuccess)) {
      try (EventSender es = makeEventSender()) {
        EventSender.Result result = es.sendEventData(DIAGNOSTICS, FAKE_DATA, 1, server.getUri());
        
        assertFalse(result.isSuccess());
        assertFalse(result.isMustShutDown());
      }

      server.getRecorder().requireRequest();
      server.getRecorder().requireRequest();
      server.getRecorder().requireNoRequests(Duration.ofMillis(100)); // only 2 requests total
    }
  }

  private Handler eventsSuccessResponse() {
    return Handlers.status(202);
  }
  
  private Handler addDateHeader(Date date) {
    return Handlers.header("Date", httpDateFormat.format(date));
  }
}
